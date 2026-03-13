from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from modcompiler.common import (
    ModCompilerError,
    expand_minecraft_version_spec,
    find_text_files,
    load_json,
    safe_extract_zip,
    safe_rmtree,
    write_json,
)
from modcompiler.decompile import DecompileResult, decompile_jar_internal
from modcompiler.modrinth import ModrinthClient, build_modrinth_user_agent, normalize_modrinth_project_ref

MAX_SRC_SIZE_BYTES = 100 * 1024
PRIORITY_JAVA_PATTERNS = [
    r".*Mod\.java$",
    r".*Client\.java$",
    r".*Common\.java$",
    r".*Init\.java$",
    r".*Registry\.java$",
    r"main/.*\.java$",
]
EXCLUDE_EXTENSIONS = {".json", ".lang", ".properties", ".png", ".ogg", ".txt", ".mcmeta", ".cfg"}


@dataclass
class AutoUpdateConfig:
    mod_jar_path: str
    modrinth_project_url: str | None
    mod_description: str
    version_range: str
    update_mode: str
    publish_mode: str
    manifest_path: str
    output_dir: Path


@dataclass
class DecomposedMod:
    src_path: Path
    mod_info: dict[str, Any]
    current_version: str
    current_loader: str
    metadata: dict[str, Any]


@dataclass
class VersionTarget:
    minecraft_version: str
    loader: str
    slug: str


def parse_version_input(version_input: str, manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    version_input = version_input.strip().lower()
    if not version_input or version_input == "all":
        return _get_all_supported_versions(manifest, current_loader)

    if "," in version_input:
        targets = []
        for part in version_input.split(","):
            part = part.strip()
            if not part:
                continue
            parsed = _parse_explicit_version_pair(part, manifest)
            for p in parsed:
                targets.append(p)
        return targets

    if "-" in version_input:
        return _parse_version_range(version_input, manifest, current_loader)

    return _parse_explicit_version_pair(version_input, manifest)


def _get_all_supported_versions(manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    targets = []
    for range_entry in manifest["ranges"]:
        folder = range_entry["folder"]
        loaders = range_entry.get("loaders", {})
        if current_loader not in loaders:
            continue
        loader_config = loaders[current_loader]
        supported_versions = loader_config.get("supported_versions", [])
        if not supported_versions:
            continue
        for mc_version in supported_versions:
            targets.append(VersionTarget(
                minecraft_version=mc_version,
                loader=current_loader,
                slug=f"{mc_version}-{current_loader}",
            ))
    return targets


def _parse_explicit_version_pair(part: str, manifest: dict[str, Any]) -> list[VersionTarget]:
    match = re.match(r"^(\d+\.\d+(?:\.\d+)?)(fabric|forge|neoforge)?$", part)
    if not match:
        raise ModCompilerError(f"Invalid version format: {part}")
    version = match.group(1)
    loader = match.group(2) if match.group(2) else None
    return [VersionTarget(minecraft_version=version, loader=loader or "fabric", slug=f"{version}-{loader or 'fabric'}")]


def _parse_version_range(version_input: str, manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    if "-" not in version_input:
        raise ModCompilerError(f"Invalid version range: {version_input}")

    lower_raw, upper_raw = version_input.split("-", 1)
    lower_raw = lower_raw.strip()
    upper_raw = upper_raw.strip()

    lower_parts = lower_raw.split(".")
    upper_parts = upper_raw.split(".")
    major = lower_parts[0]
    minor = lower_parts[1] if len(lower_parts) > 1 else "0"
    lower = f"{major}.{minor}"
    upper = f"{major}.{upper_parts[1]}" if len(upper_parts) > 1 else f"{major}.{minor}"

    targets = []
    for range_entry in manifest["ranges"]:
        mc_version = range_entry.get("min_version", "")
        if not mc_version.startswith(lower.split(".")[0]):
            continue

        mc_parts = mc_version.split(".")
        if len(mc_parts) < 2:
            continue
        mc_minor = mc_parts[1]
        if mc_minor < lower.split(".")[1] or mc_minor > upper.split(".")[1]:
            continue

        loaders = range_entry.get("loaders", {})
        if current_loader not in loaders:
            continue

        loader_config = loaders[current_loader]
        supported_versions = loader_config.get("supported_versions", [])
        for mc_ver in supported_versions:
            targets.append(VersionTarget(
                minecraft_version=mc_ver,
                loader=current_loader,
                slug=f"{mc_ver}-{current_loader}",
            ))

    return targets


def check_modrinth_versions(modrinth_project_url: str, loader: str) -> dict[str, Any]:
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        raise ModCompilerError("MODRINTH_TOKEN not configured")

    project_ref = normalize_modrinth_project_ref(modrinth_project_url)
    user_agent = build_modrinth_user_agent()
    client = ModrinthClient(token=token, user_agent=user_agent)

    project = client.resolve_project(project_ref)
    versions = client.request_json("GET", f"/project/{project_ref}/version")

    existing = {"versions": [], "loaders": set()}
    if isinstance(versions, list):
        for v in versions:
            game_versions = v.get("game_versions", [])
            loaders = v.get("loaders", [])
            for gv in game_versions:
                existing["versions"].append(gv)
            for l in loaders:
                existing["loaders"].add(l)

    existing["versions"] = list(set(existing["versions"]))
    return {
        "project_id": project.get("id", ""),
        "project_slug": project.get("slug", ""),
        "existing_versions": existing["versions"],
        "existing_loaders": list(existing["loaders"]),
    }


def filter_versions_to_build(
    version_targets: list[VersionTarget],
    modrinth_info: dict[str, Any] | None,
    update_mode: str,
) -> list[VersionTarget]:
    if not modrinth_info or update_mode == "all-versions":
        return version_targets

    existing = set(modrinth_info.get("existing_versions", []))
    filtered = [vt for vt in version_targets if vt.minecraft_version not in existing]
    return filtered


def trim_src_for_context(src_path: Path) -> Path:
    temp_dir = src_path.parent / "_trimmed_src"
    safe_rmtree(temp_dir)

    all_files = list(src_path.rglob("*"))
    total_size = sum(f.stat().st_size for f in all_files if f.is_file())

    if total_size <= MAX_SRC_SIZE_BYTES:
        shutil.copytree(src_path, temp_dir)
        return temp_dir

    temp_dir.mkdir(parents=True)

    java_files = [f for f in all_files if f.suffix == ".java" and f.is_file()]
    resource_files = [f for f in all_files if f.suffix.lower() in EXCLUDE_EXTENSIONS and f.is_file()]

    priority_java = []
    other_java = []
    for jf in java_files:
        is_priority = any(re.match(p, jf.name) for p in PRIORITY_JAVA_PATTERNS)
        if is_priority:
            priority_java.append(jf)
        else:
            other_java.append(jf)

    current_size = 0
    kept_files = []

    for pf in priority_java:
        size = pf.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(pf)
            current_size += size

    for of in other_java:
        size = of.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(of)
            current_size += size

    for rf in resource_files:
        size = rf.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(rf)
            current_size += size

    for kf in kept_files:
        rel_path = kf.relative_to(src_path)
        dest = temp_dir / rel_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(kf, dest)

    (temp_dir / "CONTEXT_NOTE.txt").write_text(
        f"Context was trimmed from {total_size // 1024}KB to ~{current_size // 1024}KB. "
        "Priority was given to main mod classes (*Mod.java, *Client.java, etc.).",
        encoding="utf-8",
    )

    return temp_dir


def generate_version_context(
    decomposed: DecomposedMod,
    target: VersionTarget,
    info_txt: str,
    trimmed_src: Path,
) -> dict[str, Any]:
    return {
        "target_version": target.minecraft_version,
        "target_loader": target.loader,
        "current_version": decomposed.current_version,
        "current_loader": decomposed.current_loader,
        "mod_info": decomposed.metadata,
        "user_description": info_txt,
        "src_size": sum(f.stat().st_size for f in trimmed_src.rglob("*") if f.is_file()),
    }


def build_ai_system_prompt(context: dict[str, Any]) -> str:
    target_v = context["target_version"]
    target_l = context["target_loader"]
    current_v = context["current_version"]
    current_l = context["current_loader"]
    mod_info = context["mod_info"]
    desc = context["user_description"]

    return f"""You are an expert Minecraft mod developer. Your task is to update this mod from {current_l} {current_v} to {target_l} {target_v}.

MOD INFORMATION:
- Name: {mod_info.get('name', 'Unknown')}
- Mod ID: {mod_info.get('mod_id', 'unknown')}
- Current Version: {mod_info.get('mod_version', '1.0.0')}
- Description: {desc}

INSTRUCTIONS:
1. Read all source files in src/ to understand the mod structure
2. Update gradle/build files for {target_l} {target_v}
3. Update mod metadata (fabric.mod.json / mods.toml) for the new version
4. Fix any breaking API changes between {current_v} and {target_v}
5. Update any version-specific imports, dependencies, or registrations
6. Use the Build tool to compile and verify success
7. Use Complete when the mod builds successfully

TOOLS AVAILABLE:
- Read File: Read source files to understand the code
- List Files: See directory structure
- File Write: Create or overwrite files
- File Edit: Modify existing files using diff format
- Move File: Move files within this version folder
- Build: Compile the mod using Gradle
- Complete: Mark the update as done (only after successful build)

The workspace is contained in this version's folder. You cannot access files outside of it."""


def create_version_folder(
    output_dir: Path,
    decomposed: DecomposedMod,
    target: VersionTarget,
    context: dict[str, Any],
    trimmed_src: Path,
) -> Path:
    version_folder = output_dir / f"{target.minecraft_version}-{target.loader}"
    safe_rmtree(version_folder)
    version_folder.mkdir(parents=True)

    write_json(version_folder / "versions.txt", {
        "minecraft_version": target.minecraft_version,
        "loader": target.loader,
        "slug": target.slug,
    })

    write_json(version_folder / "context.json", context)

    info_kit = version_folder / "info-kit"
    info_kit.mkdir(parents=True)

    (info_kit / "mod_info.txt").write_text(
        _format_mod_info_txt(decomposed.mod_info),
        encoding="utf-8",
    )

    if decomposed.mod_info.get("description"):
        (info_kit / "description.txt").write_text(decomposed.mod_info["description"], encoding="utf-8")

    src_dest = version_folder / "src"
    shutil.copytree(trimmed_src, src_dest)

    return version_folder


def _format_mod_info_txt(mod_info: dict[str, Any]) -> str:
    lines = [
        f"jar_name={mod_info.get('jar_name', 'unknown.jar')}",
        f"loader={mod_info.get('loader', 'unknown')}",
        f"supported_minecraft={mod_info.get('supported_minecraft', 'unknown')}",
        f"primary_mod_id={mod_info.get('primary_mod_id', 'unknown')}",
        f"name={mod_info.get('name', 'Unknown')}",
        f"mod_version={mod_info.get('mod_version', '1.0.0')}",
        f"entrypoint_class={mod_info.get('entrypoint_class', '')}",
        f"description={mod_info.get('description', '')}",
        f"authors={', '.join(mod_info.get('authors', []))}",
    ]
    return "\n".join(lines)


def command_auto_update_decompose(args: argparse.Namespace) -> int:
    config = AutoUpdateConfig(
        mod_jar_path=args.mod_jar_path,
        modrinth_project_url=args.modrinth_project_url if hasattr(args, "modrinth_project_url") else None,
        mod_description=args.mod_description if hasattr(args, "mod_description") else "",
        version_range=args.version_range if hasattr(args, "version_range") else "all",
        update_mode=args.update_mode if hasattr(args, "update_mode") else "all-versions",
        publish_mode=args.publish_mode if hasattr(args, "publish_mode") else "bundle-only",
        manifest_path=args.manifest,
        output_dir=Path(args.output_dir),
    )

    safe_rmtree(config.output_dir)
    config.output_dir.mkdir(parents=True)

    jar_path = Path(config.mod_jar_path)
    if not jar_path.is_absolute():
        jar_path = Path.cwd() / jar_path

    if not jar_path.exists():
        raise ModCompilerError(f"Mod jar not found: {jar_path}")

    manifest = load_json(Path(config.manifest_path))

    decomp_dir = config.output_dir / "_decompiled"
    decomp_result = decompile_jar_internal(jar_path, manifest, output_dir=decomp_dir)
    src_path = decomp_result.extracted_src
    mod_info = _parse_mod_info(src_path)

    info_txt = ""
    mod_description_input = config.mod_description.strip()
    if mod_description_input:
        potential_path = Path(mod_description_input)
        if potential_path.exists() and potential_path.is_file():
            info_txt = potential_path.read_text(encoding="utf-8")
        else:
            info_txt = mod_description_input

    current_loader = mod_info.get("loader", "fabric")
    current_version = mod_info.get("supported_minecraft", "1.20.1")

    version_targets = parse_version_input(config.version_range, manifest, current_loader)

    modrinth_info = None
    if config.modrinth_project_url:
        try:
            modrinth_info = check_modrinth_versions(config.modrinth_project_url, current_loader)
        except Exception as e:
            print(f"Warning: Could not fetch Modrinth versions: {e}", file=sys.stderr)

    filtered_targets = filter_versions_to_build(version_targets, modrinth_info, config.update_mode)

    trimmed_src = trim_src_for_context(src_path)

    decomposed = DecomposedMod(
        src_path=src_path,
        mod_info=mod_info,
        current_version=current_version,
        current_loader=current_loader,
        metadata=mod_info,
    )

    for target in filtered_targets:
        context = generate_version_context(decomposed, target, info_txt, trimmed_src)
        create_version_folder(config.output_dir, decomposed, target, context, trimmed_src)

    state = {
        "mod_jar_path": str(jar_path),
        "modrinth_project_url": config.modrinth_project_url,
        "info_txt": info_txt,
        "current_version": current_version,
        "current_loader": current_loader,
        "version_targets": [
            {"minecraft_version": t.minecraft_version, "loader": t.loader, "slug": t.slug}
            for t in filtered_targets
        ],
        "update_mode": config.update_mode,
        "publish_mode": config.publish_mode,
        "modrinth_info": modrinth_info,
    }
    write_json(config.output_dir / "state.json", state)

    matrix = {"include": [{"slug": t.slug, "version": t.minecraft_version, "loader": t.loader} for t in filtered_targets]}
    write_json(config.output_dir / "matrix.json", matrix)

    print(json.dumps(matrix, separators=(",", ":")))
    return 0


def _parse_mod_info(src_path: Path) -> dict[str, Any]:
    mod_info_path = src_path / "mod_info.txt"
    if mod_info_path.exists():
        return _parse_key_value_file(mod_info_path)

    fabric_json = src_path / "fabric.mod.json"
    if fabric_json.exists():
        data = load_json(fabric_json)
        return {
            "jar_name": "unknown.jar",
            "loader": "fabric",
            "supported_minecraft": data.get("schema_version", "1.20"),
            "primary_mod_id": data.get("id", "unknown"),
            "name": data.get("name", "Unknown"),
            "mod_version": data.get("version", "1.0.0"),
            "entrypoint_class": data.get("entrypoint", ""),
            "description": data.get("description", ""),
            "authors": data.get("authors", []),
        }

    mods_toml = src_path / "mods.toml"
    if mods_toml.exists():
        text = mods_toml.read_text(encoding="utf-8")
        mod_id_match = re.search(r'modId\s*=\s*"([^"]+)"', text)
        name_match = re.search(r'name\s*=\s*"([^"]+)"', text)
        version_match = re.search(r'version\s*=\s*"([^"]+)"', text)
        return {
            "jar_name": "unknown.jar",
            "loader": "forge",
            "supported_minecraft": "1.20",
            "primary_mod_id": mod_id_match.group(1) if mod_id_match else "unknown",
            "name": name_match.group(1) if name_match else "Unknown",
            "mod_version": version_match.group(1) if version_match else "1.0.0",
            "entrypoint_class": "",
            "description": "",
            "authors": [],
        }

    return {
        "jar_name": "unknown.jar",
        "loader": "fabric",
        "supported_minecraft": "1.20",
        "primary_mod_id": "unknown",
        "name": "Unknown",
        "mod_version": "1.0.0",
        "entrypoint_class": "",
        "description": "",
        "authors": [],
    }


def _parse_key_value_file(path: Path) -> dict[str, Any]:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def load_state(state_path: Path) -> dict[str, Any]:
    return load_json(state_path)


def command_ai_rebuild(args: argparse.Namespace) -> int:
    version_dir = Path(args.version_dir)
    output_dir = Path(args.output_dir)
    artifact_dir = Path(args.artifact_dir)

    safe_rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True)
    output_dir.mkdir(parents=True, exist_ok=True)

    versions_txt = version_dir / "versions.txt"
    context_json = version_dir / "context.json"

    if not versions_txt.exists():
        raise ModCompilerError(f"versions.txt not found in {version_dir}")
    if not context_json.exists():
        raise ModCompilerError(f"context.json not found in {version_dir}")

    version_info = load_json(versions_txt)
    context = load_json(context_json)

    target_version = version_info["minecraft_version"]
    target_loader = version_info["loader"]
    slug = version_info["slug"]

    result: dict[str, Any] = {
        "slug": slug,
        "loader": target_loader,
        "minecraft_version": target_version,
        "status": "failed",
        "chat_history": [],
        "warnings": [],
    }

    log_path = artifact_dir / "ai_rebuild.log"
    messages = []
    build_attempted = False
    build_success = False

    try:
        from modcompiler.openrouter import OpenRouterClient

        client = OpenRouterClient()

        system_prompt = build_ai_system_prompt(context)
        messages = [
            {"role": "system", "content": system_prompt},
        ]

        src_files = _list_src_files(version_dir / "src")
        src_summary = _generate_src_summary(version_dir / "src", src_files)

        initial_message = f"""Please analyze and update this mod for {target_loader} {target_version}.

Current source files ({len(src_files)} files):
{src_summary}

The user description for this mod:
{context.get('user_description', 'No description provided')}

Please start by reading the key source files to understand the mod structure, then update it for {target_loader} {target_version}. Use the Build tool when ready to compile."""
        messages.append({"role": "user", "content": initial_message})

        max_iterations = 20
        for iteration in range(max_iterations):
            response = client.chat_completion_with_fallback(messages, temperature=0.7, max_tokens=4000)
            assistant_message = response["choices"][0]["message"]["content"]
            messages.append({"role": "assistant", "content": assistant_message})

            tool_calls = response["choices"][0].get("message", {}).get("tool_calls", [])
            if not tool_calls:
                break

            for tool_call in tool_calls:
                function = tool_call.get("function", {})
                name = function.get("name", "")
                arguments_str = function.get("arguments", "{}")

                try:
                    arguments = json.loads(arguments_str) if arguments_str else {}
                except json.JSONDecodeError:
                    arguments = {}

                result_msg = ""
                if name == "read_file":
                    result_msg = _tool_read_file(version_dir, arguments)
                elif name == "list_files":
                    result_msg = _tool_list_files(version_dir, arguments)
                elif name == "file_write":
                    result_msg = _tool_file_write(version_dir, arguments)
                elif name == "file_edit":
                    result_msg = _tool_file_edit(version_dir, arguments)
                elif name == "move_file":
                    result_msg = _tool_move_file(version_dir, arguments)
                elif name == "build":
                    result_msg, build_success = _tool_build(version_dir, artifact_dir, context, arguments)
                    build_attempted = True
                elif name == "complete":
                    if build_attempted and build_success:
                        result["status"] = "success"
                        result_msg = "Marked as complete. Build was successful."
                    else:
                        result_msg = "Cannot complete: No successful build found. Use Build tool first."
                else:
                    result_msg = f"Unknown tool: {name}"

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.get("id", "unknown"),
                    "content": result_msg,
                })

            if result["status"] == "success":
                break

        result["chat_history"] = messages[:50]

    except Exception as e:
        result["warnings"].append(str(e))
        with log_path.open("w", encoding="utf-8") as f:
            f.write(f"AI rebuild error: {e}\n")
            f.write("\n".join(str(m) for m in messages))

    write_json(artifact_dir / "result.json", result)
    return 0 if result["status"] == "success" else 1


def _list_src_files(src_dir: Path) -> list[dict[str, Any]]:
    files = []
    if src_dir.exists():
        for f in sorted(src_dir.rglob("*")):
            if f.is_file():
                files.append({
                    "path": str(f.relative_to(src_dir)),
                    "size": f.stat().st_size,
                })
    return files


def _generate_src_summary(src_dir: Path, files: list[dict[str, Any]]) -> str:
    if not files:
        return "No source files found."

    summary_lines = []
    for f in files[:20]:
        summary_lines.append(f"  - {f['path']} ({f['size']} bytes)")

    if len(files) > 20:
        summary_lines.append(f"  ... and {len(files) - 20} more files")

    return "\n".join(summary_lines)


def _tool_read_file(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"

    file_path = version_dir / "src" / path
    if not file_path.exists():
        return f"Error: file not found: {path}"

    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 10000:
            content = content[:10000] + "\n... (truncated)"
        return f"File: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading file: {e}"


def _tool_list_files(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "src")
    dir_path = version_dir / path
    if not dir_path.exists():
        return f"Error: directory not found: {path}"

    items = []
    for item in sorted(dir_path.iterdir()):
        rel_path = item.relative_to(version_dir)
        items.append(f"  - {rel_path}{'/' if item.is_dir() else ''}")

    return "\n".join(items) if items else "  (empty)"


def _tool_file_write(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"File written: {path} ({len(content)} bytes)"


def _tool_file_edit(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    old_content = args.get("old_content", "")
    new_content = args.get("new_content", "")

    if not path:
        return "Error: path is required"

    file_path = version_dir / "src" / path
    if not file_path.exists():
        return f"Error: file not found: {path}"

    try:
        current = file_path.read_text(encoding="utf-8")
        if old_content not in current:
            return f"Error: old_content not found in file. File has {len(current)} chars, old_content has {len(old_content)} chars."

        updated = current.replace(old_content, new_content)
        file_path.write_text(updated, encoding="utf-8")
        return f"File edited: {path}"
    except Exception as e:
        return f"Error editing file: {e}"


def _tool_move_file(version_dir: Path, args: dict[str, str]) -> str:
    source = args.get("source", "")
    destination = args.get("destination", "")

    if not source or not destination:
        return "Error: source and destination are required"

    if ".." in source or ".." in destination:
        return "Error: cannot use .. in paths"

    src_path = version_dir / source
    dst_path = version_dir / destination

    if not src_path.exists():
        return f"Error: source not found: {source}"

    dst_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src_path), str(dst_path))

    return f"Moved: {source} -> {destination}"


def _tool_build(version_dir: Path, artifact_dir: Path, context: dict[str, Any], args: dict[str, str]) -> tuple[str, bool]:
    target_version = context["target_version"]
    target_loader = context["target_loader"]
    metadata = context["mod_info"]

    manifest = load_json(Path("version-manifest.json"))

    try:
        resolved_range = None
        for range_entry in manifest["ranges"]:
            if target_version >= range_entry["min_version"] and target_version <= range_entry["max_version"]:
                resolved_range = range_entry
                break

        if not resolved_range:
            return "Error: No version folder found for " + target_version, False

        if target_loader not in resolved_range["loaders"]:
            return f"Error: {target_loader} not supported for {target_version}", False

        loader_config = resolved_range["loaders"][target_loader]
        template_dir = loader_config["template_dir"]

    except Exception as e:
        return f"Error resolving build configuration: {e}", False

    workspace = version_dir / "_build_workspace"
    safe_rmtree(workspace)
    workspace.mkdir(parents=True)

    try:
        from modcompiler.common import copy_tree

        copy_tree(Path(template_dir) / "template", workspace)

        mod_dir = workspace / "src" / "main"
        if (version_dir / "src").exists():
            copy_tree(version_dir / "src", mod_dir / "java")

        metadata_json = workspace / "metadata.json"
        from modcompiler.common import write_json
        write_json(metadata_json, metadata)

        adapter_script = Path(resolved_range["folder"]) / "build_adapter.py"
        adapter_command = [
            sys.executable,
            str(adapter_script),
            "--loader",
            target_loader,
            "--metadata-json",
            str(metadata_json),
            "--source-dir",
            str(mod_dir / "java"),
            "--template-workspace",
            str(workspace),
            "--minecraft-version",
            target_version,
            "--manifest",
            "version-manifest.json",
        ]

        from modcompiler.common import java_home_for_version, sanitize_env_path, find_built_jars, copy_file

        env = os.environ.copy()
        java_version = 17
        if target_version.startswith("1.12") or target_version.startswith("1.8"):
            java_version = 8
        elif target_version.startswith("1.16") or target_version.startswith("1.15"):
            java_version = 16

        java_home = java_home_for_version(java_version, env)
        env["JAVA_HOME"] = java_home
        env["PATH"] = sanitize_env_path(java_home, env.get("PATH"))

        build_command = list(loader_config["build_command"])

        log_path = artifact_dir / "build.log"
        with log_path.open("w", encoding="utf-8") as log_file:
            log_file.write("$ " + " ".join(adapter_command) + "\n\n")
            adapter_run = subprocess.run(
                adapter_command,
                cwd=Path.cwd(),
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )

            if adapter_run.returncode != 0:
                log_file.write(f"\nAdapter failed with exit code {adapter_run.returncode}")
                return f"Build failed. Check {log_path}", False

            log_file.write("\n$ " + " ".join(build_command) + "\n\n")
            build_run = subprocess.run(
                build_command,
                cwd=workspace,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )

        jars = find_built_jars(workspace, loader_config["jar_glob"])

        if not jars:
            return f"Build completed but no jar found. Check {log_path}", False

        jars_dir = artifact_dir / "jars"
        jars_dir.mkdir(parents=True, exist_ok=True)

        for jar_path in jars:
            copy_file(jar_path, jars_dir / jar_path.name)

        return f"Build successful! Found: {[j.name for j in jars]}", True

    except Exception as e:
        return f"Build error: {e}", False
    finally:
        safe_rmtree(workspace)

