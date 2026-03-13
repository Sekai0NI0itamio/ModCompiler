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
    copy_tree,
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
            min_ver = range_entry.get("min_version", "")
            max_ver = range_entry.get("max_version", "")
            if min_ver and max_ver:
                supported_versions = [max_ver]
        
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
        
        if not supported_versions:
            min_ver = range_entry.get("min_version", "")
            max_ver = range_entry.get("max_version", "")
            if min_ver and max_ver:
                supported_versions = [max_ver]
        
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
    manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    template_dir = ""
    if manifest:
        for range_entry in manifest.get("ranges", []):
            if target.minecraft_version >= range_entry.get("min_version", "") and target.minecraft_version <= range_entry.get("max_version", ""):
                if target.loader in range_entry.get("loaders", {}):
                    loader_config = range_entry["loaders"][target.loader]
                    template_dir = loader_config.get("template_dir", "")
                    break
    
    return {
        "target_version": target.minecraft_version,
        "target_loader": target.loader,
        "current_version": decomposed.current_version,
        "current_loader": decomposed.current_loader,
        "mod_info": decomposed.metadata,
        "user_description": info_txt,
        "src_size": sum(f.stat().st_size for f in trimmed_src.rglob("*") if f.is_file()),
        "template_dir": template_dir,
    }


def get_tools_definition() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Read a Java source file from the original mod source",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Relative path (e.g., 'src/java/com/itamio/fpsdisplay/FpsDisplay.java')"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "list_files",
                "description": "List files in a directory",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory path (e.g., '.', 'src', 'src/java')"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_reference",
                "description": "Read an example file from the reference/template folder to see correct imports and patterns for the target version",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path in reference folder (e.g., 'src/main/java/com/example/ExampleMod.java')"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "list_reference",
                "description": "List files in the reference folder to see available example files",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory in reference (default: root)"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_mod_file",
                "description": "Write a Java file. Auto-adds src/main/java/ prefix if needed.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path like 'com/itamio/fpsdisplay/FpsDisplay.java' or full path"
                        },
                        "content": {
                            "type": "string",
                            "description": "Java source code"
                        }
                    },
                    "required": ["path", "content"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_resource",
                "description": "Write a resource file. Auto-adds src/main/resources/ prefix.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path like 'pack.mcmeta'"
                        },
                        "content": {
                            "type": "string",
                            "description": "Resource content"
                        }
                    },
                    "required": ["path", "content"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "copy_to_structure",
                "description": "Copy a source file to the proper build location. E.g., 'src/java/com/example/Mod.java' → 'src/main/java/com/example/Mod.java'",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "source_path": {
                            "type": "string",
                            "description": "Source path like 'src/java/com/itamio/fpsdisplay/FpsDisplay.java'"
                        }
                    },
                    "required": ["source_path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "build",
                "description": "Build the mod - handles gradle setup automatically",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "complete",
                "description": "Mark as complete after successful build",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        }
    ]


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

{current_v} uses SRG names (e.g., Minecraft.func_71410_x()), {target_v} uses MCP names (e.g., Minecraft.getMinecraft()).

**HOW THIS BUILD SYSTEM WORKS:**

1. FOLDER STRUCTURE:
   - Original source: src/java/ (decompiled from original jar)
   - Reference files: reference/ (example mod for {target_v} - READ THESE!)
   - Your output: src/main/java/ (where you write updated Java files)
   - Resources go to: src/main/resources/

2. YOUR JOB:
   a) Read reference/ files to understand correct imports and patterns for {target_v}
   b) Read original source from src/java/
   c) Update code for {target_v} API changes
   d) Write updated files to src/main/java/
   e) Run Build when ready

3. REFERENCE FILES - IMPORTANT!
   - Use list_reference to see available examples
   - Use read_reference to see correct imports, annotations, event registration
   - These show you exactly what the target version expects!

4. TOOLS:
   - list_files/read_file: Work with src/ (original source)
   - list_reference/read_reference: See example files for {target_v}
   - write_mod_file: Write Java to src/main/java/ (auto-adds prefix)
   - write_resource: Write resources to src/main/resources/
   - copy_to_structure: Quick-copy from src/java/ to src/main/java/
   - build: Compile (build system handles gradle automatically!)
   - complete: Done after successful build

**CRITICAL**: Always check reference/ files first to see correct patterns for {target_v}!

Start by:
1. list_reference to see available examples
2. read_reference to see correct imports and structure
3. list_files to see original source files"""


def create_version_folder(
    output_dir: Path,
    decomposed: DecomposedMod,
    target: VersionTarget,
    context: dict[str, Any],
    trimmed_src: Path,
    manifest: dict[str, Any] | None = None,
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

    template_dir = context.get("template_dir", "")
    if template_dir:
        template_source = Path(template_dir)
        if template_source.exists():
            ref_dest = version_folder / "reference"
            shutil.copytree(template_source, ref_dest)
            print(f"DEBUG: Copied template to reference folder: {ref_dest}")

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
        context = generate_version_context(decomposed, target, info_txt, trimmed_src, manifest)
        create_version_folder(config.output_dir, decomposed, target, context, trimmed_src, manifest)

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
    import sys
    print(f"DEBUG: Starting ai-rebuild", file=sys.stderr)
    
    version_dir = Path(args.version_dir)
    output_dir = Path(args.output_dir)
    artifact_dir = Path(args.artifact_dir)
    
    print(f"DEBUG: version_dir={version_dir}, exists={version_dir.exists()}", file=sys.stderr)
    print(f"DEBUG: output_dir={output_dir}, exists={output_dir.exists()}", file=sys.stderr)
    print(f"DEBUG: artifact_dir={artifact_dir}", file=sys.stderr)

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
    
    print(f"DEBUG: Loaded version_info={version_info}", file=sys.stderr)
    print(f"DEBUG: context target_version={context.get('target_version')}", file=sys.stderr)

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
        import os
        print(f"DEBUG: Checking for OpenRouter keys in env...", file=sys.stderr)
        for i in range(1, 6):
            key_val = os.environ.get(f"OPENROUTER_API_KEY_{i}", "")
            print(f"DEBUG: OPENROUTER_API_KEY_{i} = {'set' if key_val else 'not set'}", file=sys.stderr)
        
        from modcompiler.openrouter import OpenRouterClient

        client = OpenRouterClient()
        print(f"DEBUG: OpenRouterClient initialized with {len(client.key_states)} keys", file=sys.stderr)
        
        if not client.key_states:
            raise ModCompilerError("No OpenRouter API keys available. Please set OPENROUTER_API_KEY_1 through OPENROUTER_API_KEY_20 secrets.")

        print(f"DEBUG: Building system prompt...", file=sys.stderr)
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

        max_iterations = 1000
        print(f"DEBUG: Starting AI conversation loop (max {max_iterations} iterations)...", file=sys.stderr)
        
        for iteration in range(max_iterations):
            print(f"DEBUG: Iteration {iteration+1}: Calling API...", file=sys.stderr)
            try:
                response = client.chat_completion_with_fallback(messages, temperature=0.7, max_tokens=4000, tools=get_tools_definition())
            except Exception as e:
                print(f"DEBUG: API call failed: {e}", file=sys.stderr)
                raise
            
            print(f"DEBUG: Got response, processing...", file=sys.stderr)
            print(f"DEBUG: response keys: {response.keys()}", file=sys.stderr)
            print(f"DEBUG: choices: {response.get('choices', [])[:1]}", file=sys.stderr)
            
            choice = response["choices"][0]
            assistant_message = choice.get("message", {}).get("content")
            if assistant_message:
                messages.append({"role": "assistant", "content": assistant_message})
            else:
                messages.append({"role": "assistant", "content": ""})

            tool_calls = choice.get("message", {}).get("tool_calls", [])
            print(f"DEBUG: tool_calls: {len(tool_calls)} found", file=sys.stderr)
            
            if not tool_calls:
                msg_preview = (assistant_message[:200] + "...") if assistant_message else "(no content)"
                print(f"DEBUG: No tool calls, assistant message: {msg_preview}", file=sys.stderr)
                print(f"DEBUG: Sending continue prompt...", file=sys.stderr)
                messages.append({
                    "role": "user", 
                    "content": "Please continue with a tool call. Don't respond without using a tool - use Read File, List Files, File Write, Move File, or Build when ready to continue updating the mod."
                })
                continue

            for tool_call in tool_calls:
                function = tool_call.get("function", {})
                name = function.get("name", "")
                arguments_str = function.get("arguments", "{}")
                print(f"DEBUG: Processing tool_call: name={name}, args={arguments_str[:100]}...", file=sys.stderr)

                try:
                    arguments = json.loads(arguments_str) if arguments_str else {}
                except json.JSONDecodeError:
                    arguments = {}

                result_msg = ""
                try:
                    if name == "read_file":
                        print(f"DEBUG: Calling _tool_read_file...", file=sys.stderr)
                        result_msg = _tool_read_file(version_dir, arguments)
                    elif name == "list_files":
                        print(f"DEBUG: Calling _tool_list_files...", file=sys.stderr)
                        result_msg = _tool_list_files(version_dir, arguments)
                    elif name == "write_mod_file":
                        print(f"DEBUG: Calling _tool_write_mod_file...", file=sys.stderr)
                        result_msg = _tool_write_mod_file(version_dir, arguments)
                    elif name == "write_resource":
                        print(f"DEBUG: Calling _tool_write_resource...", file=sys.stderr)
                        result_msg = _tool_write_resource(version_dir, arguments)
                    elif name == "read_reference":
                        print(f"DEBUG: Calling _tool_read_reference...", file=sys.stderr)
                        result_msg = _tool_read_reference(version_dir, arguments)
                    elif name == "list_reference":
                        print(f"DEBUG: Calling _tool_list_reference...", file=sys.stderr)
                        result_msg = _tool_list_reference(version_dir, arguments)
                    elif name == "copy_to_structure":
                        print(f"DEBUG: Calling _tool_copy_to_structure...", file=sys.stderr)
                        result_msg = _tool_copy_to_structure(version_dir, arguments)
                    elif name == "file_write":
                        print(f"DEBUG: Calling _tool_file_write...", file=sys.stderr)
                        result_msg = _tool_file_write(version_dir, arguments)
                    elif name == "file_edit":
                        print(f"DEBUG: Calling _tool_file_edit...", file=sys.stderr)
                        result_msg = _tool_file_edit(version_dir, arguments)
                    elif name == "move_file":
                        print(f"DEBUG: Calling _tool_move_file...", file=sys.stderr)
                        result_msg = _tool_move_file(version_dir, arguments)
                    elif name == "build":
                        print(f"DEBUG: Calling _tool_build...", file=sys.stderr)
                        result_msg, build_success = _tool_build(version_dir, artifact_dir, context, arguments)
                        build_attempted = True
                    elif name == "complete":
                        print(f"DEBUG: Processing complete tool...", file=sys.stderr)
                        if build_attempted and build_success:
                            result["status"] = "success"
                            result_msg = "Marked as complete. Build was successful."
                        else:
                            result_msg = "Cannot complete: No successful build found. Use Build tool first."
                    else:
                        result_msg = f"Unknown tool: {name}"
                except Exception as tool_e:
                    print(f"DEBUG: Tool execution error: {tool_e}", file=sys.stderr)
                    result_msg = f"Error executing tool: {tool_e}"

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.get("id", "unknown"),
                    "content": result_msg,
                })

            if result["status"] == "success":
                print(f"DEBUG: Build successful, exiting loop", file=sys.stderr)
                break

        print(f"DEBUG: AI loop completed. result_status={result['status']}, build_attempted={build_attempted}, build_success={build_success}", file=sys.stderr)
        result["chat_history"] = messages[:50]

    except Exception as e:
        import traceback
        print(f"DEBUG: Exception caught: {e}", file=sys.stderr)
        print(f"DEBUG: Traceback: {traceback.format_exc()}", file=sys.stderr)
        result["warnings"].append(str(e))
        with log_path.open("w", encoding="utf-8") as f:
            f.write(f"AI rebuild error: {e}\n")
            f.write(traceback.format_exc())
            if messages:
                f.write("\n--- Messages ---\n")
                f.write("\n".join(str(m) for m in messages))

    print(f"DEBUG: Writing result.json with status: {result.get('status')}", file=sys.stderr)
    write_json(artifact_dir / "result.json", result)
    print(f"DEBUG: Returning exit code: {0 if result['status'] == 'success' else 1}", file=sys.stderr)
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


def _tool_read_template(version_dir: Path, context: dict[str, Any], args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    template_dir = context.get("template_dir", "")
    if not template_dir:
        return "Error: No template directory configured"
    
    full_template_path = Path(template_dir) / path
    if not full_template_path.exists():
        return f"Error: template file not found: {path}"
    
    try:
        content = full_template_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"Template file: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading template file: {e}"


def _tool_list_template(version_dir: Path, context: dict[str, Any], args: dict[str, str]) -> str:
    path = args.get("path", "")
    
    template_dir = context.get("template_dir", "")
    if not template_dir:
        return "Error: No template directory configured"
    
    full_template_path = Path(template_dir) / path if path else Path(template_dir)
    if not full_template_path.exists():
        return f"Error: template directory not found: {path or template_dir}"
    
    items = []
    for item in sorted(full_template_path.rglob("*")):
        if item.is_file():
            rel_path = item.relative_to(Path(template_dir))
            items.append(f"  - {rel_path}")
    
    return "\n".join(items) if items else "  (empty)"


def _tool_write_mod_file(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"
    
    if not path.startswith("src/main/java/"):
        if path.startswith("src/java/"):
            path = path.replace("src/java/", "src/main/java/")
        elif not path.startswith("src/main/"):
            path = "src/main/java/" + path

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"Java file written to {path} ({len(content)} bytes)"


def _tool_write_resource(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"
    
    if not path.startswith("src/main/resources/"):
        if path.startswith("src/resources/"):
            path = path.replace("src/resources/", "src/main/resources/")
        elif not path.startswith("src/main/"):
            path = "src/main/resources/" + path

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"Resource written to {path} ({len(content)} bytes)"


def _tool_read_reference(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    ref_dir = version_dir / "reference"
    if not ref_dir.exists():
        return "Error: reference folder not found"
    
    file_path = ref_dir / path
    if not file_path.exists():
        return f"Error: reference file not found: {path}"
    
    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"Reference file: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading reference: {e}"


def _tool_list_reference(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    
    ref_dir = version_dir / "reference"
    if not ref_dir.exists():
        return "Error: reference folder not found"
    
    if path:
        full_path = ref_dir / path
    else:
        full_path = ref_dir
    
    if not full_path.exists():
        return f"Error: reference path not found: {path}"
    
    items = []
    for item in sorted(full_path.rglob("*")):
        if item.is_file():
            rel_path = item.relative_to(ref_dir)
            items.append(f"  - {rel_path}")
    
    return "\n".join(items) if items else "  (empty)"


def _tool_copy_to_structure(version_dir: Path, args: dict[str, str]) -> str:
    source_path = args.get("source_path", "")

    if not source_path:
        return "Error: source_path is required"
    
    src_dir = version_dir / "src"
    
    if not source_path.startswith("src/"):
        source_path = "src/" + source_path
    
    source_file = version_dir / source_path
    
    if not source_file.exists():
        return f"Error: source file not found: {source_path}"
    
    if source_path.startswith("src/java/"):
        dest_path = source_path.replace("src/java/", "src/main/java/")
    elif source_path.startswith("src/resources/"):
        dest_path = source_path.replace("src/resources/", "src/main/resources/")
    else:
        dest_path = source_path.replace("src/", "src/main/")
    
    dest_file = version_dir / dest_path
    dest_file.parent.mkdir(parents=True, exist_ok=True)
    
    if source_file.is_file():
        import shutil
        shutil.copy2(source_file, dest_file)
        return f"Copied {source_path} to {dest_path}"
    else:
        import shutil
        if dest_file.exists():
            shutil.rmtree(dest_file)
        shutil.copytree(source_file, dest_file)
        return f"Copied directory {source_path} to {dest_path}"


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
    import sys
    from modcompiler.common import load_json
    print(f"DEBUG[_tool_build]: Starting build for {context.get('target_version')}-{context.get('target_loader')}", file=sys.stderr)
    
    target_version = context["target_version"]
    target_loader = context["target_loader"]
    metadata = context["mod_info"]
    
    print(f"DEBUG[_tool_build]: target_version={target_version}, target_loader={target_loader}", file=sys.stderr)

    local_template = version_dir / "template"
    resolved_range_folder = ""
    build_command_list = []
    jar_glob_pattern = "build/libs/*.jar"
    template_dir = ""
    
    if local_template.exists():
        template_dir = str(local_template)
        print(f"DEBUG[_tool_build]: Using local template: {template_dir}", file=sys.stderr)
        
        manifest = load_json(Path("version-manifest.json"))
        
        for range_entry in manifest["ranges"]:
            if target_version >= range_entry["min_version"] and target_version <= range_entry["max_version"]:
                if target_loader in range_entry["loaders"]:
                    resolved_range_folder = range_entry["folder"]
                    loader_config = range_entry["loaders"][target_loader]
                    build_command_list = loader_config.get("build_command", ["./gradlew", "build", "--stacktrace", "--no-daemon"])
                    jar_glob_pattern = loader_config.get("jar_glob", "build/libs/*.jar")
                    print(f"DEBUG[_tool_build]: Found range folder: {resolved_range_folder}", file=sys.stderr)
                    break
    else:
        manifest_path = Path("version-manifest.json")
        if not manifest_path.exists():
            return f"Error: version-manifest.json not found", False
            
        manifest = load_json(manifest_path)

        for range_entry in manifest["ranges"]:
            if target_version >= range_entry["min_version"] and target_version <= range_entry["max_version"]:
                if target_loader in range_entry["loaders"]:
                    resolved_range_folder = range_entry["folder"]
                    loader_config = range_entry["loaders"][target_loader]
                    template_dir = loader_config["template_dir"]
                    build_command_list = loader_config.get("build_command", ["./gradlew", "build", "--stacktrace", "--no-daemon"])
                    jar_glob_pattern = loader_config.get("jar_glob", "build/libs/*.jar")
                    print(f"DEBUG[_tool_build]: Found range folder: {resolved_range_folder}, template: {template_dir}", file=sys.stderr)
                    break
        
        if not resolved_range_folder:
            return f"Error: No version folder found for {target_version}", False

    workspace = version_dir / "_build_workspace"
    safe_rmtree(workspace)
    workspace.mkdir(parents=True)

    try:
        from modcompiler.common import copy_tree

        source_path = Path(template_dir)
        if (source_path / "template").exists():
            copy_tree(source_path / "template", workspace)
        else:
            copy_tree(source_path, workspace)

        mod_dir = workspace / "src" / "main"
        if (version_dir / "src").exists():
            copy_tree(version_dir / "src", mod_dir / "java")

        metadata_json = workspace / "metadata.json"
        from modcompiler.common import write_json
        write_json(metadata_json, metadata)

        adapter_script = Path(resolved_range_folder) / "build_adapter.py"
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

        build_command = list(build_command_list)

        version_info = f"""=== BUILD VERSION INFO ===
Target Version: {target_version}
Target Loader: {target_loader}
Range Folder: {resolved_range_folder}
Template Dir: {template_dir}
Build Command: {build_command}
Java Version: {java_version}
Java Home: {java_home}
Manifest: version-manifest.json
==================="""

        print(f"DEBUG[_tool_build]: {version_info}", file=sys.stderr)

        log_path = artifact_dir / "build.log"
        version_log_path = artifact_dir / "build_version_info.log"
        version_log_path.write_text(version_info, encoding="utf-8")
        
        build_output = ""
        with log_path.open("w", encoding="utf-8") as log_file:
            log_file.write(version_info + "\n\n")
            log_file.write("$ " + " ".join(adapter_command) + "\n\n")
            adapter_run = subprocess.run(
                adapter_command,
                cwd=Path.cwd(),
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            build_output += log_file.read() if hasattr(log_file, 'read') else ""
            
            if adapter_run.returncode != 0:
                log_file.write(f"\n\n=== ADAPTER FAILED with exit code {adapter_run.returncode} ===\n")
                log_file.write(f"STDOUT: {adapter_run.stdout}\n")
                log_file.write(f"STDERR: {adapter_run.stderr}\n")
                log_file.write(f"\n{version_info}\n")
                log_content = log_path.read_text(encoding="utf-8")
                return f"Build failed (exit {adapter_run.returncode}). VERSION INFO: {version_info}\n\nBuild log:\n\n{log_content[-8000:]}", False

            log_file.write("\n$ " + " ".join(build_command) + "\n\n")
            build_run = subprocess.run(
                build_command,
                cwd=workspace,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )

        jars = find_built_jars(workspace, jar_glob_pattern)

        if not jars:
            log_content = log_path.read_text(encoding="utf-8")
            return f"Build completed but no jar found. VERSION INFO: {version_info}\n\nBuild log:\n\n{log_content[-8000:]}", False

        jars_dir = artifact_dir / "jars"
        jars_dir.mkdir(parents=True, exist_ok=True)

        for jar_path in jars:
            copy_file(jar_path, jars_dir / jar_path.name)

        return f"Build successful! Found: {[j.name for j in jars]}", True

    except Exception as e:
        return f"Build error: {e}", False
    finally:
        safe_rmtree(workspace)

