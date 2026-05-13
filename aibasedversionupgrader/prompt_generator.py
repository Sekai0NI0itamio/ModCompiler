#!/usr/bin/env python3
"""
prompt_generator.py — Generate Background Info + AI Prompt Files
=================================================================
This module creates two types of files for each target version/loader combo:

1. Background Info.txt (one per target in bundle/<mc>-<loader>/)
   - Template source files from the repo (formatted with filepath + codeblock)
   - Top 4 DIF (Developer Issue Finder) entries relevant to this version/loader

2. prompt.txt (one per target, also in bundle/<mc>-<loader>/)
   - Project info summary + metadata
   - Background Info content
   - Predesigned AI prompt instructing an AI to generate ALL source files
     in the format:
       filepath (relative, not full)
       ```
       code_here_
       ```
   - Explicit requirement that ALL files must be provided

Usage:
    python3 aibasedversionupgrader/prompt_generator.py [--output-dir DIR]

Or imported:
    from aibasedversionupgrader.prompt_generator import generate_all_prompts
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))


# ─────────────────────────────────────────────────────────────────────────────
# DIF Helpers
# ─────────────────────────────────────────────────────────────────────────────

def _parse_dif_frontmatter(content: str) -> Tuple[Dict, str]:
    """Parse YAML-like frontmatter from a DIF markdown file."""
    if not content.startswith("---"):
        return {}, content
    end = content.find("\n---", 3)
    if end == -1:
        return {}, content
    fm_text = content[3:end].strip()
    body = content[end + 4:].strip()
    meta: Dict = {}
    for line in fm_text.splitlines():
        if ":" not in line:
            continue
        key, _, val = line.partition(":")
        key = key.strip()
        val = val.strip()
        if val.startswith("[") and val.endswith("]"):
            items = [x.strip().strip('"').strip("'") for x in val[1:-1].split(",") if x.strip()]
            meta[key] = items
        else:
            try:
                meta[key] = int(val)
            except ValueError:
                meta[key] = val
    return meta, body


def _search_dif(repo_root: Path, minecraft_version: str, loader: str, max_results: int = 4) -> List[Dict]:
    """
    Search the DIF (Developer Issue Finder) knowledge base for entries
    relevant to a specific minecraft_version + loader combination.
    Returns up to max_results entries, scored by relevance.
    """
    dif_dir = repo_root / "dif"
    if not dif_dir.exists():
        return []

    results = []
    query_terms = set()
    query_terms.add(loader.lower())
    query_terms.add(minecraft_version)

    # Also add version prefixes for broader matching (e.g. "1.21" matches "1.21.9")
    version_parts = minecraft_version.split(".")
    if len(version_parts) >= 2:
        query_terms.add(".".join(version_parts[:2]))

    for f in sorted(dif_dir.glob("*.md")):
        try:
            content = f.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        meta, body = _parse_dif_frontmatter(content)

        entry_versions = [v.lower() for v in meta.get("versions", [])]
        entry_loaders = [l.lower() for l in meta.get("loaders", [])]
        entry_tags = [t.lower() for t in meta.get("tags", [])]
        entry_title = meta.get("title", "").lower()

        # Score based on matches
        score = 0

        # Version match (exact or prefix)
        mc_lower = minecraft_version.lower()
        if mc_lower in entry_versions:
            score += 10
        else:
            # Check prefix match
            for v in entry_versions:
                if v.startswith(version_parts[0]) and mc_lower.startswith(".".join(version_parts[:2])):
                    # Check if versions share the same major.minor prefix
                    ev_parts = v.split(".")
                    if len(ev_parts) >= 2 and ev_parts[:2] == version_parts[:2]:
                        score += 5
                        break

        # Loader match
        if loader.lower() in entry_loaders:
            score += 8

        # Tag relevance
        for tag in (loader.lower(), minecraft_version):
            if tag in entry_tags:
                score += 3

        # Title relevance
        if mc_lower in entry_title:
            score += 2
        if loader.lower() in entry_title:
            score += 2

        if score > 0:
            results.append({
                "id": meta.get("id", f.stem),
                "title": meta.get("title", f.stem),
                "tags": meta.get("tags", []),
                "versions": entry_versions,
                "loaders": entry_loaders,
                "score": score,
                "content": content,
                "body": body,
                "fix": _extract_fix_section(body),
            })

    # Sort by score descending, then by usage_count
    results.sort(key=lambda x: (x["score"], x.get("meta", {}).get("usage_count", 0)), reverse=True)
    return results[:max_results]


def _extract_fix_section(body: str) -> str:
    """Extract the ## Fix section from a DIF markdown body."""
    # Find the ## Fix section
    fix_match = re.search(r"##\s*Fix\s*\n(.*?)(?=\n##\s|\Z)", body, re.DOTALL | re.IGNORECASE)
    if fix_match:
        fix_text = fix_match.group(1).strip()
        # Limit to reasonable size
        if len(fix_text) > 2000:
            fix_text = fix_text[:2000] + "\n... (truncated)"
        return fix_text
    return "(No fix section found)"


# ─────────────────────────────────────────────────────────────────────────────
# Template File Collector
# ─────────────────────────────────────────────────────────────────────────────

def _collect_template_files(template_dir: Path, repo_root: Path) -> List[Dict]:
    """
    Read all files from a template directory and return them as a list of
    dicts with 'path' (relative to repo_root) and 'content'.
    Skips binary files, large license files, gradlew binaries, and .jar files.
    """
    SKIP_EXTENSIONS = {
        ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".ico",
        ".ttf", ".otf", ".woff", ".woff2", ".eot", ".svg",
        ".zip", ".gz", ".tar", ".7z", ".rar",
        ".mp3", ".wav", ".ogg", ".mp4", ".mov", ".avi",
        ".pyc", ".pyo", ".bin", ".lock",
    }
    SKIP_NAMES = {
        "gradlew", "gradlew.bat",
        "LICENSE", "LICENSE.txt", "LICENSE.md",
        "CREDITS.txt", "changelog.txt",
        "README.txt", "README.md",
        "build.log", ".ds_store", ".ds_Store",
    }
    # Directories (or prefixes) to skip entirely — build artifacts, hidden dirs
    SKIP_DIR_PREFIXES = {
        ".gradle", "build", ".git", "__pycache__",
    }

    files = []
    if not template_dir or not template_dir.exists():
        return files

    for p in sorted(template_dir.rglob("*")):
        if not p.is_file():
            continue
        rel = str(p.relative_to(template_dir))
        # Skip files in hidden or build directories
        parts = Path(rel).parts
        if any(part.startswith(".") or part in SKIP_DIR_PREFIXES for part in parts):
            continue
        # Skip by extension
        if p.suffix.lower() in SKIP_EXTENSIONS:
            continue
        # Skip by exact file name
        if p.name in SKIP_NAMES:
            continue
        # Skip files larger than 100 KB
        if p.stat().st_size > 100 * 1024:
            continue

        try:
            content = p.read_text(encoding="utf-8", errors="replace")
            files.append({
                "path": str(p.relative_to(repo_root)),
                "filename": rel,
                "content": content,
            })
        except (UnicodeDecodeError, ValueError):
            pass  # Skip binary files

    return files


# ─────────────────────────────────────────────────────────────────────────────
# Background Info.txt Generator
# ─────────────────────────────────────────────────────────────────────────────

def _format_file_block(file_entry: Dict) -> str:
    """Format a single file as filepath + codeblock."""
    filename = file_entry["filename"]
    content = file_entry["content"]
    # Determine language for syntax highlighting
    ext = Path(filename).suffix
    lang = {
        ".java": "java",
        ".gradle": "groovy",
        ".toml": "toml",
        ".json": "json",
        ".properties": "properties",
        ".md": "markdown",
        ".txt": "text",
        ".cfg": "cfg",
        ".yml": "yaml",
        ".yaml": "yaml",
        ".xml": "xml",
        ".css": "css",
        ".js": "javascript",
    }.get(ext, "")
    return f"{filename}\n```{lang}\n{content}\n```"


def _format_dif_entry(entry: Dict) -> str:
    """Format a single DIF entry for inclusion in Background Info.txt."""
    title = entry.get("title", "Unknown Issue")
    entry_id = entry.get("id", "?")
    fix = entry.get("fix", "(No fix available)")
    return (
        f"### [{entry_id}] {title}\n\n"
        f"{fix}\n"
    )


def generate_background_info(
    target_dir: Path,          # bundle/<mc>-<loader>/
    minecraft_version: str,
    loader: str,
    template_dir: Path,
    repo_root: Path,
) -> str:
    """
    Generate the content for Background Info.txt for a single target.
    Returns the full text to write to the file.
    """
    lines = []
    lines.append(f"# Background Info — {minecraft_version} ({loader})")
    lines.append("")
    lines.append(f"This file contains working template code from the repository for {minecraft_version}/{loader},")
    lines.append("plus common issues and solutions for this target combination.")
    lines.append("")

    # ── Section 1: Template Source Files ──────────────────────────────────
    lines.append("─" * 60)
    lines.append("## Section 1: Working Template Code from Repository")
    lines.append("─" * 60)
    lines.append("")
    lines.append("Below are ALL the template files from the repository. These are WORKING reference")
    lines.append("implementations — use them as the foundation for writing the mod source code.")
    lines.append("Each file is shown with its relative path followed by the full code.")
    lines.append("")

    template_files = _collect_template_files(template_dir, repo_root)

    if not template_files:
        lines.append("*No template files found.*")
    else:
        lines.append(f"Total template files: {len(template_files)}")
        lines.append("")
        for f_entry in template_files:
            lines.append(_format_file_block(f_entry))
            lines.append("")

    # ── Section 2: DIF Issues ────────────────────────────────────────────
    lines.append("─" * 60)
    lines.append("## Section 2: Common Issues & Solutions for This Target")
    lines.append("─" * 60)
    lines.append("")
    lines.append(f"The following are the most relevant known issues for {minecraft_version}/{loader}")
    lines.append("from the Developer Issue Finder (DIF) knowledge base. Review these BEFORE")
    lines.append("writing code to avoid common pitfalls.")
    lines.append("")

    dif_entries = _search_dif(repo_root, minecraft_version, loader, max_results=4)

    if not dif_entries:
        lines.append("*No known issues found for this version/loader combination.*")
    else:
        lines.append(f"Found {len(dif_entries)} relevant issue(s):")
        lines.append("")
        for i, entry in enumerate(dif_entries):
            lines.append(f"--- Issue {i + 1} ---")
            lines.append(_format_dif_entry(entry))
            lines.append("")

    lines.append("─" * 60)
    lines.append("End of Background Info")
    lines.append("─" * 60)

    return "\n".join(lines)


# ─────────────────────────────────────────────────────────────────────────────
# prompt.txt Generator
# ─────────────────────────────────────────────────────────────────────────────

def _read_project_info(project_info_dir: Path) -> Dict:
    """Read project info from the project_info directory."""
    info = {
        "title": "?",
        "slug": "?",
        "description": "",
        "loaders": [],
        "game_versions": [],
        "license": "?",
        "client_side": "?",
        "server_side": "?",
        "summary_text": "",
    }

    project_json = project_info_dir / "project.json"
    if project_json.exists():
        try:
            proj = json.loads(project_json.read_text(encoding="utf-8"))
            info["title"] = proj.get("title", "?")
            info["slug"] = proj.get("slug", "?")
            info["description"] = proj.get("description", "")
            info["loaders"] = proj.get("loaders", [])
            info["game_versions"] = proj.get("game_versions", [])
            info["license"] = proj.get("license", {}).get("id", "?")
            info["client_side"] = proj.get("client_side", "?")
            info["server_side"] = proj.get("server_side", "?")
        except Exception:
            pass

    summary_txt = project_info_dir / "summary.txt"
    if summary_txt.exists():
        try:
            info["summary_text"] = summary_txt.read_text(encoding="utf-8")
        except Exception:
            pass

    return info


def _read_metadata(project_info_dir: Path) -> Dict:
    """Try to read metadata from a pre-generated metadata.json or build it."""
    # Check if metadata.json exists in the session root or project_info
    for candidate in [
        project_info_dir.parent / "metadata.json",
        project_info_dir / "metadata.json",
    ]:
        if candidate.exists():
            try:
                return json.loads(candidate.read_text(encoding="utf-8"))
            except Exception:
                pass

    # Fall back to project.json with best-effort values
    project_json = project_info_dir / "project.json"
    if project_json.exists():
        try:
            proj = json.loads(project_json.read_text(encoding="utf-8"))
            slug = proj.get("slug", "mod")
            mod_id = slug.replace("-", "_").lower()
            group = f"asd.itamio.{mod_id.replace('-', '').replace('_', '')}"
            ep_class = "".join(w.capitalize() for w in re.split(r"[-_]", mod_id)) + "Mod"
            return {
                "mod_id": mod_id,
                "name": proj.get("title", slug),
                "mod_version": "1.0.0",
                "group": group,
                "entrypoint_class": f"{group}.{ep_class}",
                "runtime_side": "both",
                "description": proj.get("description", "")[:200],
                "authors": ["Itamio"],
                "license": proj.get("license", {}).get("id", "ARR") if isinstance(proj.get("license"), dict) else str(proj.get("license", "ARR")),
            }
        except Exception:
            pass

    return {}


def _read_decompiled_source(project_info_dir: Path) -> str:
    """Read decompiled source files from the first version."""
    decompiled_dir = project_info_dir / "first_version" / "decompiled"
    if not decompiled_dir.exists():
        return ""

    content = ""
    source_files = sorted(decompiled_dir.glob("*.java")) or sorted(decompiled_dir.glob("*.txt"))
    for f in source_files[:6]:
        try:
            limit = 3000 if f.suffix == ".java" else 600
            content += f"\n### {f.name}\n```java\n{f.read_text(encoding='utf-8')[:limit]}\n```\n"
        except Exception:
            pass
    return content


def generate_prompt(
    project_info_dir: Path,
    minecraft_version: str,
    loader: str,
    background_info: str,
    template_files: List[Dict],
    metadata: Dict,
) -> str:
    """
    Generate the prompt.txt content for a single target.
    Combines project info, background info, and a predesigned AI coding prompt.
    """
    # Read project info
    proj_info = _read_project_info(project_info_dir)
    decompiled_source = _read_decompiled_source(project_info_dir)

    # Derive package details from metadata
    pkg = metadata.get("group", "asd.itamio.mod").replace(".", "/")
    entrypoint = metadata.get("entrypoint_class", "")
    mod_id = metadata.get("mod_id", "mod")
    mod_name = metadata.get("name", "Mod")
    mod_version = metadata.get("mod_version", "1.0.0")
    description = metadata.get("description", "")
    authors_list = metadata.get("authors", ["Itamio"])
    license_str = metadata.get("license", "ARR")

    if entrypoint:
        main_class_name = entrypoint.split(".")[-1]
    else:
        main_class_name = "".join(w.capitalize() for w in re.split(r"[-_]", mod_id)) + "Mod"

    # Build the list of expected output files based on template
    expected_files = []
    resource_file = _determine_resource_file(minecraft_version, loader, mod_id, mod_name, mod_version,
                                              description, authors_list, license_str)

    for tf in template_files:
        # We use the filename relative to template_dir
        template_rel = tf["filename"]
        if template_rel.endswith(".java"):
            # This is a template .java file we need to replicate
            expected_files.append({
                "bundle_path": f"bundle/{minecraft_version}-{loader}/src/main/java/{pkg}/{Path(template_rel).name}",
                "template_rel": template_rel,
                "description": f"Java source file ({Path(template_rel).name})",
            })
        elif "mcmod.info" in template_rel or "mods.toml" in template_rel or "fabric.mod.json" in template_rel or "neoforge.mods.toml" in template_rel:
            # This is already a resource file, we'll include it via template
            expected_files.append({
                "bundle_path": f"bundle/{minecraft_version}-{loader}/src/main/resources/{template_rel}",
                "template_rel": template_rel,
                "description": f"Mod descriptor resource file ({Path(template_rel).name})",
            })

    # Add the resource file (deduplicated)
    resource_paths = {ef["template_rel"] for ef in expected_files if "resources" in ef["bundle_path"]}
    if resource_file and resource_file["path"] not in resource_paths:
        expected_files.append({
            "bundle_path": f"bundle/{minecraft_version}-{loader}/src/main/resources/{resource_file['path']}",
            "template_rel": resource_file["path"],
            "description": f"Mod descriptor resource file ({resource_file['path']})",
        })

    # Build the comprehensive prompt
    lines = []
    lines.append("=" * 70)
    lines.append(f"AI CODING PROMPT — {mod_name} for Minecraft {minecraft_version} ({loader})")
    lines.append("=" * 70)
    lines.append("")

    # ── Section 1: Project Information ─────────────────────────────────────
    lines.append("─" * 60)
    lines.append("PART 1: PROJECT INFORMATION")
    lines.append("─" * 60)
    lines.append("")
    lines.append(f"Mod Name:        {mod_name}")
    lines.append(f"Mod ID:          {mod_id}")
    lines.append(f"Mod Version:     {mod_version}")
    lines.append(f"Description:     {description}")
    lines.append(f"Authors:         {', '.join(authors_list) if isinstance(authors_list, list) else authors_list}")
    lines.append(f"License:         {license_str}")
    lines.append(f"Target Minecraft:{minecraft_version}")
    lines.append(f"Target Loader:   {loader}")
    lines.append(f"Java Package:    {metadata.get('group', 'asd.itamio.mod')}")
    lines.append(f"Main Class:      {main_class_name}")
    lines.append(f"Package Path:    {pkg}")
    lines.append("")

    if proj_info["summary_text"]:
        lines.append("Project Summary:")
        for line in proj_info["summary_text"].splitlines():
            lines.append(f"  {line}")
        lines.append("")

    if decompiled_source:
        lines.append("Decompiled Source Code (original mod implementation — port this logic):")
        lines.append(decompiled_source)
        lines.append("")

    # ── Section 2: Background Info ─────────────────────────────────────────
    lines.append("─" * 60)
    lines.append("PART 2: BACKGROUND INFO (Template Code + Known Issues)")
    lines.append("─" * 60)
    lines.append("")
    lines.append(background_info)
    lines.append("")

    # ── Section 3: AI Instructions ─────────────────────────────────────────
    lines.append("=" * 70)
    lines.append("PART 3: AI CODING INSTRUCTIONS")
    lines.append("=" * 70)
    lines.append("")

    lines.append(f"Your task: Write ALL source files needed to build the mod '{mod_name}' ")
    lines.append(f"(mod_id: '{mod_id}') for Minecraft {minecraft_version} using the {loader} loader.")
    lines.append("")
    lines.append("## CRITICAL REQUIREMENTS")
    lines.append("")
    lines.append("1. **YOU MUST OUTPUT ALL FILES** — Every single file listed below must be provided.")
    lines.append("   Missing a single file will cause the build to fail.")
    lines.append("")
    lines.append("2. **EXACT OUTPUT FORMAT** — Each file must be in this EXACT two-block format:")
    lines.append("")
    lines.append("   ```filepath")
    lines.append(f"   bundle/{minecraft_version}-{loader}/src/main/java/{pkg}/{main_class_name}.java")
    lines.append("   ```")
    lines.append("   ```java")
    lines.append("   package asd.itamio.mod;")
    lines.append("")
    lines.append("   public class MyMod {")
    lines.append("       // ...")
    lines.append("   }")
    lines.append("   ```")
    lines.append("")
    lines.append("   The FIRST backtick block (language `filepath`) contains ONLY the file path.")
    lines.append("   The SECOND backtick block contains the actual source code.")
    lines.append("")
    lines.append("4. **ALL filepaths must be relative** — do NOT include full system paths.")
    lines.append("")
    lines.append("5. **Implement REAL logic** from the decompiled source above. Do NOT write stubs,")
    lines.append("   placeholder comments, or TODO markers.")
    lines.append("")
    lines.append("6. **Use the EXACT package** `{metadata.get('group', 'asd.itamio.mod')}` in all Java files.")
    lines.append("")
    lines.append("7. **The main class name is `{main_class_name}`** — the filename must match exactly")
    lines.append("   (`{main_class_name}.java`), including capitalisation.")
    lines.append("")
    lines.append("8. **Loader-specific API patterns:**")
    lines.append("")

    # Add loader-specific instructions
    lines.extend(_get_loader_instructions(loader, minecraft_version))

    lines.append("")
    lines.append("## FILES TO CREATE")
    lines.append("")
    lines.append(f"You MUST create ALL of the following files for {minecraft_version}/{loader}:")
    lines.append("")

    if not expected_files:
        lines.append("  (Files will be determined from the template structure above)")
    else:
        for ef in expected_files:
            lines.append(f"  - `{ef['bundle_path']}` — {ef['description']}")

    lines.append("")
    lines.append("## LOADER-SPECIFIC RESOURCE FILE")
    lines.append("")

    if resource_file:
        lines.append(f"  Resource file path: `bundle/{minecraft_version}-{loader}/src/main/resources/{resource_file['path']}`")
        lines.append(f"  Format: {resource_file['format_hint']}")
        lines.append("")

    lines.append("## TEMPLATE REFERENCE")
    lines.append("")
    lines.append("You have the working template code from the repository in PART 2 above.")
    lines.append("Use it as your EXACT reference for:")
    lines.append("")
    lines.append("  - Build system configuration (build.gradle, settings.gradle)")
    lines.append("  - Mod descriptor format (fabric.mod.json, mods.toml, mcmod.info)")
    lines.append("  - Gradle wrapper files (gradlew, gradlew.bat)")
    lines.append("  - The correct package structure")
    lines.append("")
    lines.append("Copy the template's structure EXACTLY, then replace the placeholder mod")
    lines.append("implementation with the real logic from the decompiled source code.")
    lines.append("")
    lines.append("## IMPORTANT NOTES")
    lines.append("")
    lines.append(f"- Minecraft version: {minecraft_version}")
    lines.append(f"- Loader: {loader}")
    lines.append(f"- Package: {metadata.get('group', 'asd.itamio.mod')}")
    lines.append(f"- Main class: {main_class_name}")
    lines.append("")

    # Add DIF-known issues warning
    dif_entries = _search_dif(_REPO_ROOT, minecraft_version, loader, max_results=4)
    if dif_entries:
        lines.append("## KNOWN ISSUES FOR THIS TARGET (CHECK YOUR CODE)")
        lines.append("")
        lines.append("The following common issues have been identified for this version/loader combo.")
        lines.append("Review each one and ensure your code handles them correctly:")
        lines.append("")
        for entry in dif_entries:
            lines.append(f"  ⚠ [{entry['id']}] {entry['title']}")
        lines.append("")
        lines.append("Full details for each issue are in PART 2 above.")
        lines.append("")

    lines.append("=" * 70)
    lines.append("END OF PROMPT")
    lines.append("=" * 70)

    return "\n".join(lines)


def _determine_resource_file(minecraft_version: str, loader: str,
                              mod_id: str, mod_name: str, mod_version: str,
                              description: str, authors: list, license_str: str) -> Dict:
    """
    Determine the correct mod descriptor resource file path and format
    based on the Minecraft version and loader.
    """
    # Parse version
    try:
        major_minor = tuple(int(x) for x in minecraft_version.split(".")[:2])
    except ValueError:
        major_minor = (0, 0)

    # Handle 26.x NeoForge (which follows 1.21.4+ patterns)
    if minecraft_version.startswith("26") and loader == "neoforge":
        major_minor = (1, 21)

    # Handle 1.12-1.12.2 as 1.12
    if minecraft_version.startswith("1.12"):
        major_minor = (1, 12)

    if loader == "fabric":
        return {
            "path": "fabric.mod.json",
            "format_hint": "JSON — use fabric.mod.json format",
            "suggested_content": json.dumps({
                "schemaVersion": 1,
                "id": mod_id,
                "version": mod_version,
                "name": mod_name,
                "description": description[:250] if description else "",
                "authors": authors if isinstance(authors, list) else [authors],
                "contact": {"homepage": "", "sources": "", "issues": ""},
                "license": license_str,
                "icon": "assets/modid/icon.png",
                "environment": "*",
                "entrypoints": {"main": [mod_id]},
                "depends": {"fabricloader": ">=0.14.0", "minecraft": f"~{minecraft_version}"},
            }, indent=2),
        }

    if loader == "forge":
        if major_minor <= (1, 12):
            return {
                "path": "mcmod.info",
                "format_hint": "JSON array — use mcmod.info format for Forge 1.12 and earlier",
                "suggested_content": json.dumps([{
                    "modid": mod_id, "name": mod_name, "version": mod_version,
                    "description": description[:250] if description else "",
                    "authorList": authors if isinstance(authors, list) else [authors],
                    "credits": "", "logoFile": "", "url": "", "updateUrl": "",
                    "parents": "", "screenshots": [], "dependencies": [],
                }], indent=2),
            }
        elif major_minor >= (1, 13) and major_minor <= (1, 20):
            # Forge 1.13-1.20.x uses META-INF/mods.toml
            return {
                "path": "META-INF/mods.toml",
                "format_hint": "TOML — use mods.toml format",
                "suggested_content": (
                    f'modLoader="javafml"\n'
                    f'loaderVersion="[36,)"\n'
                    f'license="{license_str}"\n'
                    f'\n[[mods]]\n'
                    f'modId="{mod_id}"\n'
                    f'version="{mod_version}"\n'
                    f'displayName="{mod_name}"\n'
                    f'authors="{", ".join(authors) if isinstance(authors, list) else authors}"\n'
                    f'description=\'\'\'{description[:500] if description else ""}\'\'\'\n'
                ),
            }
        else:
            # Forge 1.20.2+ (though most use NeoForge now)
            return {
                "path": "META-INF/mods.toml",
                "format_hint": "TOML — use mods.toml format",
                "suggested_content": (
                    f'modLoader="javafml"\n'
                    f'loaderVersion="[1,)"\n'
                    f'license="{license_str}"\n'
                    f'\n[[mods]]\n'
                    f'modId="{mod_id}"\n'
                    f'version="{mod_version}"\n'
                    f'displayName="{mod_name}"\n'
                    f'authors="{", ".join(authors) if isinstance(authors, list) else authors}"\n'
                    f'description=\'\'\'{description[:500] if description else ""}\'\'\'\n'
                ),
            }

    if loader == "neoforge":
        if major_minor >= (1, 21) or minecraft_version.startswith("26"):
            # NeoForge 1.21+ uses META-INF/neoforge.mods.toml
            return {
                "path": "META-INF/neoforge.mods.toml",
                "format_hint": "TOML — use neoforge.mods.toml format",
                "suggested_content": (
                    f'modLoader="javafml"\n'
                    f'loaderVersion="[1,)"\n'
                    f'license="{license_str}"\n'
                    f'\n[[mods]]\n'
                    f'modId="{mod_id}"\n'
                    f'version="{mod_version}"\n'
                    f'displayName="{mod_name}"\n'
                    f'authors="{", ".join(authors) if isinstance(authors, list) else authors}"\n'
                    f'description=\'\'\'{description[:500] if description else ""}\'\'\'\n'
                ),
            }
        else:
            # NeoForge 1.20.2-1.20.6 uses META-INF/mods.toml
            return {
                "path": "META-INF/mods.toml",
                "format_hint": "TOML — use mods.toml format for NeoForge 1.20.x",
                "suggested_content": (
                    f'modLoader="javafml"\n'
                    f'loaderVersion="[1,)"\n'
                    f'license="{license_str}"\n'
                    f'\n[[mods]]\n'
                    f'modId="{mod_id}"\n'
                    f'version="{mod_version}"\n'
                    f'displayName="{mod_name}"\n'
                    f'authors="{", ".join(authors) if isinstance(authors, list) else authors}"\n'
                    f'description=\'\'\'{description[:500] if description else ""}\'\'\'\n'
                ),
            }

    # Fallback
    return {
        "path": "fabric.mod.json",
        "format_hint": "JSON — fallback fabric.mod.json format",
        "suggested_content": "",
    }


def _get_loader_instructions(loader: str, minecraft_version: str) -> List[str]:
    """Return loader-specific API instructions for the AI prompt."""
    try:
        major_minor = tuple(int(x) for x in minecraft_version.split(".")[:2])
    except ValueError:
        major_minor = (0, 0)

    # Handle 1.12-1.12.2 as 1.12
    if minecraft_version.startswith("1.12"):
        major_minor = (1, 12)

    if loader == "forge":
        if major_minor <= (1, 12):
            return [
                "  Forge 1.8.9 / 1.12.2 (Legacy):",
                "    @Mod(modid = \"modid\", name = \"Mod Name\", version = \"1.0.0\")",
                "    @Mod.EventHandler for preInit(FMLPreInitializationEvent), init(FMLInitializationEvent), postInit(FMLPostInitializationEvent)",
                "    MinecraftForge.EVENT_BUS.register(handler) in preInit",
                "    Resource file: mcmod.info (JSON array in src/main/resources/)",
                "    Use StringTextComponent for chat (not TextComponentString in 1.12)",
                "    Registry names: use new ResourceLocation(\"modid\", \"name\")",
            ]
        elif major_minor >= (1, 16) and major_minor <= (1, 19):
            return [
                "  Forge 1.16.5 – 1.19.x:",
                "    @Mod(\"modid\") with constructor taking no args or FMLJavaModLoadingContext",
                "    Use FMLJavaModLoadingContext.get().getModEventBus() for mod bus events",
                "    Use MinecraftForge.EVENT_BUS for forge events",
                "    Common setup: addListener(this::setup) on mod event bus with FMLCommonSetupEvent",
                "    Resource file: META-INF/mods.toml (TOML format)",
                "    Registry: DeferredRegister with MOD_EVENT_BUS.register(REGISTRY)",
            ]
        else:
            return [
                "  Forge 1.20.x+ (modern Forge):",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    IEventBus injected via constructor parameter",
                "    MinecraftForge.EVENT_BUS for forge events",
                "    Resource file: META-INF/mods.toml (TOML)",
                "    Use DeferredRegister pattern for registry objects",
            ]

    elif loader == "neoforge":
        if minecraft_version.startswith("26") or major_minor >= (1, 21):
            return [
                "  NeoForge 1.21+ / 26.x (Modern):",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    IEventBus injected via constructor parameter (NeoForge 1.21+)",
                "    NeoForge.EVENT_BUS (not MinecraftForge) for game events",
                "    Import: net.neoforged.neoforge.common.NeoForge",
                "    Import: net.neoforged.bus.api.IEventBus",
                "    Import: net.neoforged.fml.common.Mod",
                "    Do NOT import FMLPreInitializationEvent — it does not exist in NeoForge",
                "    Resource file: META-INF/neoforge.mods.toml (for 1.21+) or META-INF/mods.toml (1.20.2-1.20.6)",
                "    Use DeferredRegister pattern: registers.register(modEventBus)",
            ]
        else:
            return [
                "  NeoForge 1.20.2 – 1.20.6:",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    IEventBus injected via constructor parameter",
                "    NeoForge.EVENT_BUS for game events",
                "    Resource file: META-INF/mods.toml (TOML)",
                "    Use DeferredRegister pattern",
            ]

    elif loader == "fabric":
        return [
            "  Fabric:",
            "    Implement ModInitializer interface",
            "    @Override onInitialize() for mod setup",
            "    Use ServerTickEvents, ClientTickEvents, etc. for tick handlers",
            "    Use FabricDefaultAttributeRegistry for entity attributes",
            "    Registry: Registry.register(Registries.ITEM, identifier, item)",
            "    Resource file: fabric.mod.json (JSON in src/main/resources/)",
            "    Environment: use fabric-loom gradle plugin",
            "    For mixins: @Mixin, @Inject(method=..., at=@At(...))",
        ]

    return []


# ─────────────────────────────────────────────────────────────────────────────
# Main orchestration function
# ─────────────────────────────────────────────────────────────────────────────

def generate_all_prompts(
    project_info_dir: Path,
    target_list: List[Dict],
    output_base_dir: Path,
    repo_root: Path = _REPO_ROOT,
) -> List[Path]:
    """
    Generate Background Info.txt and prompt.txt for all targets.

    Args:
        project_info_dir: Path to project_info/ directory
        target_list: List of target dicts with minecraft_version, loader, range_folder
        output_base_dir: Base output directory (e.g. session dir or ai-sessions/<id>/bundle)
        repo_root: Repository root path

    Returns:
        List of paths to generated prompt.txt files
    """
    # Load manifest
    manifest_path = repo_root / "version-manifest.json"
    manifest: Dict = {"ranges": []}
    if manifest_path.exists():
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    # Load metadata
    metadata = _read_metadata(project_info_dir)

    generated_prompts = []

    for target in target_list:
        mc_version = target["minecraft_version"]
        loader = target["loader"]
        range_folder = target.get("range_folder", "")

        # Find the range folder if not provided
        if not range_folder:
            rng = _find_range_for_version(manifest, mc_version, loader)
            if rng:
                range_folder = rng["folder"]

        # Locate template directory
        template_dir = None
        if range_folder:
            for rng in manifest.get("ranges", []):
                if rng["folder"] == range_folder and loader in rng.get("loaders", {}):
                    template_dir = repo_root / rng["loaders"][loader]["template_dir"]
                    break

        target_dir_name = f"{mc_version}-{loader}"
        target_dir = output_base_dir / target_dir_name
        target_dir.mkdir(parents=True, exist_ok=True)

        # ── Step 1: Generate Background Info.txt ──────────────────────────
        background_content = generate_background_info(
            target_dir=target_dir,
            minecraft_version=mc_version,
            loader=loader,
            template_dir=template_dir,
            repo_root=repo_root,
        )

        bg_path = target_dir / "Background Info.txt"
        bg_path.write_text(background_content, encoding="utf-8")
        print(f"  ✅ Background Info.txt written: {bg_path}")

        # ── Step 2: Generate prompt.txt ────────────────────────────────────
        template_files = _collect_template_files(template_dir, repo_root) if template_dir else []

        prompt_content = generate_prompt(
            project_info_dir=project_info_dir,
            minecraft_version=mc_version,
            loader=loader,
            background_info=background_content,
            template_files=template_files,
            metadata=metadata,
        )

        prompt_path = target_dir / "prompt.txt"
        prompt_path.write_text(prompt_content, encoding="utf-8")
        print(f"  ✅ prompt.txt written: {prompt_path}")

        generated_prompts.append(prompt_path)

    return generated_prompts


def _find_range_for_version(manifest: Dict, minecraft_version: str, loader: str) -> Optional[Dict]:
    """Find the manifest range entry that covers a given MC version + loader."""
    try:
        from packaging.version import Version, InvalidVersion

        def ver(v: str):
            try:
                return Version(v)
            except InvalidVersion:
                return Version("0")

        target = ver(minecraft_version)
        for rng in manifest.get("ranges", []):
            if loader not in rng.get("loaders", {}):
                continue
            loader_cfg = rng["loaders"][loader]
            supported = loader_cfg.get("supported_versions", [])
            if supported and minecraft_version in supported:
                return rng
            min_v = ver(rng.get("min_version", "0"))
            max_v = ver(rng.get("max_version", "99999"))
            if min_v <= target <= max_v:
                return rng
    except ImportError:
        # Fallback without packaging
        for rng in manifest.get("ranges", []):
            if loader not in rng.get("loaders", {}):
                continue
            loader_cfg = rng["loaders"][loader]
            supported = loader_cfg.get("supported_versions", [])
            if supported and minecraft_version in supported:
                return rng
    return None


# ─────────────────────────────────────────────────────────────────────────────
# CLI Entry Point
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    """CLI entry point"""
    import argparse

    parser = argparse.ArgumentParser(
        description="Generate Background Info and AI prompt files for each target version/loader combo",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--project-info-dir", default="project_info/",
        help="Path to project_info/ directory (default: project_info/)",
    )
    parser.add_argument(
        "--target-list", default="target_list.json",
        help="Path to target_list.json (default: target_list.json)",
    )
    parser.add_argument(
        "--output-dir", default=None,
        help="Output base directory. If not set, uses the bundle/ dir inside a new session dir.",
    )
    parser.add_argument(
        "--session-dir", default=None,
        help="Session directory to output prompts into (ai-sessions/<id>/bundle/). Overrides --output-dir.",
    )

    args = parser.parse_args()

    project_info_dir = Path(args.project_info_dir)
    if not project_info_dir.exists():
        print(f"ERROR: Project info directory not found: {project_info_dir}", file=sys.stderr)
        sys.exit(1)

    target_list_path = Path(args.target_list)
    if not target_list_path.exists():
        print(f"ERROR: Target list not found: {target_list_path}", file=sys.stderr)
        sys.exit(1)

    target_list = json.loads(target_list_path.read_text(encoding="utf-8"))
    if not target_list:
        print("No targets to generate prompts for.")
        sys.exit(0)

    # Determine output directory
    if args.session_dir:
        output_base = Path(args.session_dir) / "bundle"
        print(f"Using session bundle dir: {output_base}")
    elif args.output_dir:
        output_base = Path(args.output_dir)
    else:
        # Create a new session-style directory
        import uuid
        session_id = uuid.uuid4().hex[:8]
        output_base = Path(f"ai-sessions/{session_id}/bundle")
        print(f"Created new session: ai-sessions/{session_id}/")

    output_base.mkdir(parents=True, exist_ok=True)

    print(f"\nGenerating prompts for {len(target_list)} target(s)...")
    print(f"Project info: {project_info_dir}")
    print(f"Output base:  {output_base}")
    print()

    generated = generate_all_prompts(
        project_info_dir=project_info_dir,
        target_list=target_list,
        output_base_dir=output_base,
        repo_root=_REPO_ROOT,
    )

    print(f"\n{'=' * 60}")
    print(f"Generation complete! {len(generated)} prompt(s) created:")
    for p in generated:
        print(f"  📄 {p}")


if __name__ == "__main__":
    main()
