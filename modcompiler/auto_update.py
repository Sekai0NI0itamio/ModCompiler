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
    
    lib_sources = _get_library_sources(Path("."), target.minecraft_version, target.loader)
    library_sources_path = str(lib_sources) if lib_sources else ""
    
    return {
        "target_version": target.minecraft_version,
        "target_loader": target.loader,
        "current_version": decomposed.current_version,
        "current_loader": decomposed.current_loader,
        "mod_info": decomposed.metadata,
        "user_description": info_txt,
        "src_size": sum(f.stat().st_size for f in trimmed_src.rglob("*") if f.is_file()),
        "template_dir": template_dir,
        "library_sources": library_sources_path,
    }


def get_tools_definition() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Read original mod source. Use path like 'java/com/package/ClassName.java'",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path from src/ root, e.g., 'java/com/itamio/fpsdisplay/FpsDisplay.java'"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "description": "List original source files in src/",
                "name": "list_files",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory: '.', 'src', 'java', 'java/com/package'"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_reference",
                "description": "Read example mod file. Use path like 'src/main/java/com/example/ExampleMod.java'",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path in reference/, e.g., 'src/main/java/com/example/ExampleMod.java'"
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
                "description": "List example files available for target version",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory in reference/ (default: root)"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_mod_file",
                "description": "Write updated Java file. CRITICAL: Write to 'src/java/' (not src/main/java/). Auto-adds src/java/ prefix. Use path like 'com/package/ClassName.java'",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Package path: 'com/itamio/fpsdisplay/FpsDisplay.java'"
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
                "description": "Write resource file (mcmod.info, pack.mcmeta). Auto-adds 'src/main/resources/' prefix.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Filename: 'mcmod.info', 'pack.mcmeta'"
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
                "description": "Copy file from original src/ to build src/main/java/",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "source_path": {
                            "type": "string",
                            "description": "Path like 'java/com/itamio/fpsdisplay/FpsDisplay.java'"
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
                "description": "Build the mod. Gradle setup is automatic - just call this when ready!",
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
                "description": "Mark task complete after successful build",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_dir",
                "description": "List directory structure of decompiled Minecraft sources for the target version",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory path relative to Minecraft sources root, e.g., 'net/minecraft/client' or 'net/minecraftforge/fml/common'"
                        },
                        "depth": {
                            "type": "integer",
                            "description": "Depth of directory listing (default: 2)"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_read",
                "description": "Read a decompiled Minecraft source file to understand API patterns",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Full path to Minecraft source file, e.g., 'net/minecraft/client/Minecraft.java'"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_search",
                "description": "Search for a class or method in Minecraft sources",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search query - class name, method name, or pattern"
                        }
                    },
                    "required": ["query"]
                }
            }
        }
    ]


def _identify_key_files(files: list[dict[str, Any]]) -> list[tuple[str, str]]:
    priority_patterns = [
        r".*[Mm]od\.java$",
        r".*[Cc]lient\.java$",
        r".*[Cc]ommon\.java$",
        r".*[Ii]nit\.java$",
        r".*[Rr]egistry\.java$",
        r".*[Mm]ain\.java$",
    ]
    import re
    
    key_files = []
    for f in files:
        path = f["path"]
        for pattern in priority_patterns:
            if re.search(pattern, path):
                key_files.append((path, "priority"))
                break
    
    for f in files:
        if f not in [x[0] for x in key_files]:
            key_files.append((f["path"], "other"))
    
    return key_files[:15]


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

**IMPORTANT - ALL FILES ALREADY PROVIDED IN MESSAGE:**
The original source files AND reference examples are ALREADY INCLUDED in the user message below. 
DO NOT re-read files that are already provided - use the content directly!
The directory structure is also provided showing all available files.

**PATH RULES:**
- Original source: src/java/ (already in message)
- Reference: reference/ (already in message)
- YOUR output: src/java/ (use write_mod_file)
- Resources: src/resources/ (use write_resource)

**CRITICAL - YOUR TASK:**
1. The file contents are already in the message below - START WRITING updated code now
2. Write updated files to src/java/ using write_mod_file (path like "com/package/File.java")
3. Write resources like mcmod.info using write_resource
4. Call build IMMEDIATELY after writing files - don't explore more!

**CRITICAL RULES:**
- All source and reference files are already provided - don't use list_files, read_file, or list_reference!
- Just write the updated files and call build
- The build system handles gradle automatically
- If build fails, fix errors and rebuild"""


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

        src_dir = version_dir / "src"
        ref_dir = version_dir / "reference"

        def get_dir_tree(path: Path, prefix: str = "") -> str:
            if not path.exists():
                return ""
            items = []
            for item in sorted(path.iterdir()):
                rel = item.relative_to(path)
                items.append(f"{prefix}{rel}{'/' if item.is_dir() else ''}")
            return "\n".join(items)

        def get_all_java_files(base_dir: Path) -> list[tuple[str, str]]:
            files = []
            if base_dir.exists():
                for f in sorted(base_dir.rglob("*.java")):
                    try:
                        content = f.read_text(encoding="utf-8")
                        rel_path = str(f.relative_to(base_dir))
                        files.append((rel_path, content))
                    except Exception:
                        pass
            return files

        def get_all_ref_files(base_dir: Path) -> list[tuple[str, str]]:
            files = []
            if base_dir.exists():
                for f in sorted(base_dir.rglob("*")):
                    if f.is_file() and f.suffix in [".java", ".info", ".mcmeta", ".json"]:
                        try:
                            content = f.read_text(encoding="utf-8")
                            rel_path = str(f.relative_to(base_dir))
                            files.append((rel_path, content))
                        except Exception:
                            pass
            return files

        src_tree = get_dir_tree(src_dir)
        ref_tree = get_dir_tree(ref_dir) if ref_dir.exists() else ""
        
        initial_msg = f"""Update this mod for {target_loader} {target_version}.

USER DESCRIPTION:
{context.get('user_description', 'No description provided')}

DIRECTORY STRUCTURE - ORIGINAL SOURCE:
{src_tree}

DIRECTORY STRUCTURE - REFERENCE TEMPLATE:
{ref_tree}

The above shows all available files. Use read_file, read_reference tools to read them.

PATH INFO:
- READ original source: "java/com/package/File.java"
- WRITE updated source: "com/package/File.java" (goes to src/java/)
- Resources: "mcmod.info" (goes to src/resources/)

ACTION: Read files, update them, write to src/java/, then call build."""

        messages = [
            {"role": "system", "content": system_prompt},
        ]

        all_src_contents = []
        for rel_path, content in get_all_java_files(src_dir):
            truncated = content[:5000] + ("... (truncated)" if len(content) > 5000 else "")
            all_src_contents.append(f"\n=== FILE: {rel_path} ===\n{truncated}")

        all_ref_contents = []
        for rel_path, content in get_all_ref_files(ref_dir) if ref_dir.exists() else []:
            truncated = content[:4000] + ("... (truncated)" if len(content) > 4000 else "")
            all_ref_contents.append(f"\n=== REFERENCE: {rel_path} ===\n{truncated}")

        full_context = f"""Update this mod for {target_loader} {target_version}.

USER DESCRIPTION:
{context.get('user_description', 'No description provided')}

DIRECTORY STRUCTURE - ORIGINAL SOURCE:
{src_tree}

DIRECTORY STRUCTURE - REFERENCE TEMPLATE:
{ref_tree}

=== ORIGINAL SOURCE FILES ===
{''.join(all_src_contents)}

=== REFERENCE FILES ===
{''.join(all_ref_contents)}

PATH INFO:
- READ: "java/com/package/File.java"
- WRITE: "com/package/File.java" (goes to src/java/)
- RESOURCES: "mcmod.info" (goes to src/resources/)

ACTION: Update these files for {target_version}, write to src/java/, then build."""

        messages.append({"role": "user", "content": full_context})

        debug_history_path = artifact_dir / "ai_context_debug.txt" if artifact_dir else None
        if debug_history_path:
            debug_content = f"=== SYSTEM PROMPT ===\n{system_prompt}\n\n=== USER MESSAGE (first 15000 chars) ===\n{full_context[:15000]}"
            debug_history_path.write_text(debug_content, encoding="utf-8")
            print(f"DEBUG: Saved AI context to {debug_history_path}", file=sys.stderr)

        max_iterations = 1000
        print(f"DEBUG: Starting AI conversation loop (max {max_iterations} iterations)...", file=sys.stderr)
        
        files_written_count = 0
        last_tool = None
        tools_without_build = 0
        
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
                        tools_without_build = 0
                    elif name == "library_dir":
                        print(f"DEBUG: Calling _tool_library_dir...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_dir(arguments, lib_sources)
                    elif name == "library_read":
                        print(f"DEBUG: Calling _tool_library_read...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_read(arguments, lib_sources)
                    elif name == "library_search":
                        print(f"DEBUG: Calling _tool_library_search...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_search(arguments, lib_sources)
                    elif name == "write_mod_file" or name == "write_resource":
                        files_written_count += 1
                        tools_without_build += 1
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

            if tools_without_build >= 3 and not build_attempted:
                nudge = f"\n\n[HINT: You've written {files_written_count} files but haven't tried building yet. Call 'build' now to compile!]"
                messages.append({"role": "user", "content": nudge})
                tools_without_build = 0
                print(f"DEBUG: Sending build nudge...", file=sys.stderr)

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

    if not path.startswith("java/"):
        if path.startswith("src/java/"):
            path = path.replace("src/java/", "java/")
        elif not path.startswith("src/"):
            path = "java/" + path

    file_path = version_dir / "src" / path
    if not file_path.exists():
        available = []
        src_dir = version_dir / "src"
        if src_dir.exists():
            for f in sorted(src_dir.rglob("*.java"))[:10]:
                rel = f.relative_to(src_dir)
                available.append(str(rel))
        avail_str = "\n  - ".join(available) if available else "none"
        return f"File not found: {path}\n\nAvailable Java files:\n  - {avail_str}\n\nTip: Use path like 'java/com/package/ClassName.java'"

    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 10000:
            content = content[:10000] + "\n... (truncated)"
        return f"File: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading file: {e}"


def _tool_list_files(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", ".")
    
    if not path.startswith("java/") and path != "." and path != "src" and not path.startswith("src/"):
        path = "java/" + path if not path.startswith("src/") else path.replace("src/", "")

    dir_path = version_dir / "src" / path
    if not dir_path.exists():
        return f"Directory not found: src/{path}\n\nOriginal source is in src/java/. Try 'java' or 'java/com/yourpackage'"

    items = []
    for item in sorted(dir_path.iterdir()):
        rel_path = item.relative_to(version_dir / "src")
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
    
    if not path.startswith("src/java/"):
        if path.startswith("src/main/java/"):
            path = path.replace("src/main/java/", "src/java/")
        elif path.startswith("main/java/"):
            path = path.replace("main/java/", "src/java/")
        elif not path.startswith("src/"):
            path = "src/java/" + path

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"Java file written to {path} ({len(content)} bytes)"


def _tool_write_resource(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"
    
    if not path.startswith("src/resources/"):
        if path.startswith("src/main/resources/"):
            path = path.replace("src/main/resources/", "src/resources/")
        elif path.startswith("main/resources/"):
            path = path.replace("main/resources/", "src/resources/")
        elif not path.startswith("src/"):
            path = "src/resources/" + path

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


def _ensure_library_sources(target_version: str, target_loader: str, workspace: Path) -> Path | None:
    import subprocess
    
    gradle_cache = Path.home() / ".gradle" / "caches"
    
    library_paths = [
        gradle_cache / "forge-loom" / f"{target_version}-{target_loader}" / "minecraft-project-@-common-merged-intermediary-named" / "sources",
        gradle_cache / "forge-loom" / target_version / "minecraft-project-@-common-merged-intermediary-named" / "sources",
        gradle_cache / "fabric-loom" / target_version / "mine" / "sources",
        gradle_cache / "minecraft" / "client" / target_version / "sources",
    ]
    
    for lib_path in library_paths:
        if lib_path.exists():
            return lib_path
    
    gradle_bootstrap = workspace / "build.gradle"
    if not gradle_bootstrap.exists():
        return None
    
    print(f"DEBUG[_ensure_library_sources]: Setting up Minecraft sources for {target_version}...", file=sys.stderr)
    
    try:
        result = subprocess.run(
            ["./gradlew", "setupDecompWorkspace", "--no-daemon"],
            cwd=workspace,
            capture_output=True,
            text=True,
            timeout=600,
        )
        
        if result.returncode == 0:
            for lib_path in library_paths:
                if lib_path.exists():
                    print(f"DEBUG[_ensure_library_sources]: Found sources at {lib_path}", file=sys.stderr)
                    return lib_path
    except Exception as e:
        print(f"DEBUG[_ensure_library_sources]: Failed to setup: {e}", file=sys.stderr)
    
    return None


_library_sources_cache: dict[str, Path] = {}


def _get_library_sources(version_dir: Path, target_version: str, target_loader: str) -> Path | None:
    cache_key = f"{target_version}-{target_loader}"
    
    if cache_key in _library_sources_cache:
        cached_path = _library_sources_cache[cache_key]
        if cached_path.exists():
            return cached_path
    
    gradle_caches = Path.home() / ".gradle" / "caches"
    
    patterns = [
        f"forge-loom/{target_version}*/minecraft-project-@-common-merged-intermediary-named/sources",
        f"forge-loom/{target_version}-{target_loader}/minecraft-project-@-common-merged-intermediary-named/sources",
        f"fabric-loom/{target_version}/mine/sources",
        f"minecraft/client/{target_version}/sources",
    ]
    
    for pattern in patterns:
        for path in gradle_caches.rglob("sources"):
            if path.is_dir() and len(list(path.glob("net/minecraft/*"))) > 0:
                _library_sources_cache[cache_key] = path
                return path
    
    return None


def _tool_library_dir(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available. Run build first to download/setup Minecraft sources."
    
    path = args.get("path", "")
    depth = int(args.get("depth", 2))
    
    base_path = library_sources
    if path:
        base_path = library_sources / path
        if not base_path.exists():
            return f"Error: Directory not found: {path}"
    
    def list_dir_tree(p: Path, current_depth: int, max_depth: int) -> str:
        if current_depth >= max_depth:
            return ""
        items = []
        try:
            for item in sorted(p.iterdir()):
                items.append(f"{'  ' * current_depth}{item.name}{'/' if item.is_dir() else ''}")
                if item.is_dir():
                    items.append(list_dir_tree(item, current_depth + 1, max_depth))
        except PermissionError:
            return f"{'  ' * current_depth}[permission denied]"
        return "\n".join(items)
    
    result = f"Minecraft sources ({library_sources.name}):\n"
    result += list_dir_tree(base_path, 0, depth)
    return result


def _tool_library_read(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available."
    
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    if not path.endswith(".java"):
        path = path.replace(".", "/") + ".java"
    
    file_path = library_sources / path
    if not file_path.exists():
        return f"Error: File not found: {path}\n\nSearch for it using library_search first."
    
    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"=== Minecraft: {path} ===\n{content}"
    except Exception as e:
        return f"Error reading file: {e}"


def _tool_library_search(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available."
    
    query = args.get("query", "").lower()
    if not query:
        return "Error: query is required"
    
    results = []
    query_parts = query.replace(".", "/").split("/")
    
    search_pattern = f"*{query}*.java"
    if len(query_parts) > 1:
        search_pattern = f"*{query_parts[-1]}*.java"
    
    try:
        for java_file in library_sources.rglob(search_pattern):
            if java_file.is_file():
                rel = java_file.relative_to(library_sources)
                results.append(str(rel))
    except Exception:
        pass
    
    if not results:
        for java_file in library_sources.rglob("*.java"):
            if query in java_file.stem.lower():
                rel = java_file.relative_to(library_sources)
                results.append(str(rel))
                if len(results) >= 20:
                    break
    
    if not results:
        return f"No results found for: {query}"
    
    return f"Found {len(results)} matches for '{query}':\n" + "\n".join(f"  - {r}" for r in results[:30])


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
    metadata = context["mod_info"].copy()
    
    if "mod_id" not in metadata and "primary_mod_id" in metadata:
        metadata["mod_id"] = metadata["primary_mod_id"]
    
    print(f"DEBUG[_tool_build]: target_version={target_version}, target_loader={target_loader}", file=sys.stderr)
    print(f"DEBUG[_tool_build]: metadata keys: {list(metadata.keys())}", file=sys.stderr)

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
        src_dir = version_dir / "src"
        if src_dir.exists():
            if (src_dir / "java").exists():
                copy_tree(src_dir / "java", mod_dir / "java")
            if (src_dir / "resources").exists():
                copy_tree(src_dir / "resources", mod_dir / "resources")

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
        
        print(f"DEBUG[_tool_build]: adapter_command={' '.join(str(x) for x in adapter_command)}", file=sys.stderr)
        
        repo_root = Path.cwd()
        adapter_cwd = repo_root
        print(f"DEBUG[_tool_build]: adapter_cwd (repo root): {adapter_cwd}", file=sys.stderr)
        print(f"DEBUG[_tool_build]: adapter_script exists: {adapter_script.exists()}", file=sys.stderr)
        with log_path.open("w", encoding="utf-8") as log_file:
            log_file.write(version_info + "\n\n")
            log_file.write(f"Working directory: {adapter_cwd}\n")
            log_file.write("$ " + " ".join(str(x) for x in adapter_command) + "\n\n")
            log_file.flush()
            adapter_run = subprocess.run(
                adapter_command,
                cwd=adapter_cwd,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            log_file.flush()
            
            if adapter_run.returncode != 0:
                log_file.write(f"\n\n=== ADAPTER FAILED with exit code {adapter_run.returncode} ===\n")
                log_file.write(f"STDOUT: {adapter_run.stdout}\n")
                log_file.write(f"STDERR: {adapter_run.stderr}\n")
                log_file.write(f"\n{version_info}\n")
                log_file.flush()
                log_content = log_path.read_text(encoding="utf-8")
                print(f"DEBUG[_tool_build]: Adapter failed with exit {adapter_run.returncode}", file=sys.stderr)
                print(f"DEBUG[_tool_build]: Adapter output (first 2000 chars): {log_content[:2000]}", file=sys.stderr)
                return f"Build failed (exit {adapter_run.returncode}). Adapter error:\n\n{adapter_run.stderr[:1500] if adapter_run.stderr else 'No stderr'}\n\nCheck build.log for full details.", False

            log_file.write("\n$ " + " ".join(build_command) + "\n\n")
            log_file.flush()
            build_run = subprocess.run(
                build_command,
                cwd=workspace,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            log_file.flush()
            
            if build_run.returncode != 0:
                log_file.write(f"\n\n=== GRADLE BUILD FAILED with exit code {build_run.returncode} ===\n")
                log_file.write(f"STDOUT: {build_run.stdout}\n")
                log_file.write(f"STDERR: {build_run.stderr}\n")
                log_file.flush()

        jars = find_built_jars(workspace, jar_glob_pattern)
        print(f"DEBUG[_tool_build]: Found jars: {jars}", file=sys.stderr)

        if not jars:
            log_content = log_path.read_text(encoding="utf-8")
            print(f"DEBUG[_tool_build]: No jars found, log content length: {len(log_content)}", file=sys.stderr)
            return f"Build completed but no jar found. Check build.log for errors.\n\nLast 3000 chars:\n\n{log_content[-3000:]}", False

        jars_dir = artifact_dir / "jars"
        jars_dir.mkdir(parents=True, exist_ok=True)

        for jar_path in jars:
            copy_file(jar_path, jars_dir / jar_path.name)

        return f"Build successful! Found: {[j.name for j in jars]}", True

    except Exception as e:
        return f"Build error: {e}", False
    finally:
        safe_rmtree(workspace)

