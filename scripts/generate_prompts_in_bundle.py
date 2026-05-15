#!/usr/bin/env python3
"""
generate_prompts_in_bundle.py — Add Background Info.txt + prompt.txt to each
                                 per-target folder in a diagnosis bundle.
=============================================================================
Takes an existing diagnosis bundle (output from assemble_diagnosis_bundle.py)
and for each per-target version/loader folder, adds:

  1. Background Info.txt  — Template code + DIF known issues for this target
  2. prompt.txt           — Combines project info + Background Info +
                            a predesigned AI prompt instructing an AI to
                            write ALL source files in the format:
                              filepath (relative, not full)
                              ```language
                              code_here_
                              ```

Usage:
  python3 scripts/generate_prompts_in_bundle.py \\
    --bundle-dir .workflow_artifacts/bundle \\
    --manifest version-manifest.json \\
    --project-info .workflow_downloads/info
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

_VERSION_GUIDE_DIR = _REPO_ROOT / "Version guide"


# ─────────────────────────────────────────────────────────────────────────────
# Version Guide Helpers
# ─────────────────────────────────────────────────────────────────────────────

def _get_version_range_for_version(minecraft_version: str, manifest: Dict) -> str:
    for rng in manifest.get("ranges", []):
        folder = rng.get("folder", "")
        for loader_cfg in rng.get("loaders", {}).values():
            supported = loader_cfg.get("supported_versions", [])
            if supported and minecraft_version in supported:
                return folder
        min_v = rng.get("min_version", "")
        max_v = rng.get("max_version", "")
        if min_v and max_v:
            try:
                from packaging.version import Version, InvalidVersion
                target = Version(minecraft_version)
                if Version(min_v) <= target <= Version(max_v):
                    return folder
            except (ImportError, InvalidVersion):
                pass
    return ""


def _find_guide_file(range_folder: str, loader: str) -> Path | None:
    guide_path = _VERSION_GUIDE_DIR / f"{range_folder}-{loader}.txt"
    if guide_path.exists():
        return guide_path
    return None


def _get_fallback_mod_values(meta: Dict) -> Dict[str, str]:
    mod_name = meta.get("mod_name", "").strip()

    if not mod_name:
        mod_name = "Example Mod"

    mod_id = re.sub(r"[^a-z0-9]", "", mod_name.lower())
    mod_class = "".join(w.capitalize() for w in re.split(r"[^a-zA-Z0-9]", mod_name)) + "Mod"
    mod_client_class = mod_class + "Client"
    mod_display_name = mod_name
    package_path = f"asd.itamio.{mod_id}"
    author_name = "Itamio"

    return {
        "${MOD_ID}": mod_id,
        "${MOD_CLASS}": mod_class,
        "${MOD_CLIENT_CLASS}": mod_client_class,
        "${MOD_DISPLAY_NAME}": mod_display_name,
        "${PACKAGE_PATH}": package_path,
        "${AUTHOR_NAME}": author_name,
    }


def _load_version_guide(
    minecraft_version: str,
    loader: str,
    manifest: Dict,
    meta: Dict,
    *,
    substitute: bool = False,
) -> str:
    range_folder = _get_version_range_for_version(minecraft_version, manifest)
    if not range_folder:
        return ""

    guide_path = _find_guide_file(range_folder, loader)
    if not guide_path:
        return ""

    guide = guide_path.read_text(encoding="utf-8")

    if substitute:
        subs = _get_fallback_mod_values(meta)
        for var, value in subs.items():
            guide = guide.replace(var, value)

    return guide

# ─────────────────────────────────────────────────────────────────────────────
# DIF Helpers (same as prompt_generator.py)
# ─────────────────────────────────────────────────────────────────────────────

def _parse_dif_frontmatter(content: str) -> Tuple[Dict, str]:
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
    dif_dir = repo_root / "dif"
    if not dif_dir.exists():
        return []
    results = []
    query_terms = set()
    query_terms.add(loader.lower())
    query_terms.add(minecraft_version)
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
        entry_title = meta.get("title", "").lower()
        score = 0
        mc_lower = minecraft_version.lower()
        if mc_lower in entry_versions:
            score += 10
        else:
            for v in entry_versions:
                ev_parts = v.split(".")
                if len(ev_parts) >= 2 and len(version_parts) >= 2 and ev_parts[:2] == version_parts[:2]:
                    score += 5
                    break
        if loader.lower() in entry_loaders:
            score += 8
        for tag in (loader.lower(), minecraft_version):
            if tag in entry_tags if (entry_tags := [t.lower() for t in meta.get("tags", [])]) else []:
                pass
        if mc_lower in entry_title:
            score += 2
        if loader.lower() in entry_title:
            score += 2
        if score > 0:
            results.append({
                "id": meta.get("id", f.stem),
                "title": meta.get("title", f.stem),
                "versions": entry_versions,
                "loaders": entry_loaders,
                "score": score,
                "body": body,
                "fix": _extract_fix_section(body),
            })
    results.sort(key=lambda x: (x["score"],), reverse=True)
    return results[:max_results]


def _extract_fix_section(body: str) -> str:
    fix_match = re.search(r"##\s*Fix\s*\n(.*?)(?=\n##\s|\Z)", body, re.DOTALL | re.IGNORECASE)
    if fix_match:
        fix_text = fix_match.group(1).strip()
        if len(fix_text) > 2000:
            fix_text = fix_text[:2000] + "\n... (truncated)"
        return fix_text
    return "(No fix section found)"


# ─────────────────────────────────────────────────────────────────────────────
# Template File Collector (same as prompt_generator.py)
# ─────────────────────────────────────────────────────────────────────────────

def _collect_template_files(template_dir: Path, repo_root: Path) -> List[Dict]:
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
        "build.log", ".ds_store", ".DS_Store",
        # Build files - DO NOT include these as they are provided by the build workflow
        "build.gradle", "build.gradle.kts",
        "settings.gradle", "settings.gradle.kts",
        "gradle.properties",
        ".gitignore", ".gitattributes",
        "LICENSE.txt", "CREDITS.txt", "changelog.txt", "README.txt",
    }
    SKIP_DIR_PREFIXES = {".gradle", "build", ".git", "__pycache__", "gradle"}

    files = []
    if not template_dir or not template_dir.exists():
        return files

    for p in sorted(template_dir.rglob("*")):
        if not p.is_file():
            continue
        rel = str(p.relative_to(template_dir))
        parts = Path(rel).parts
        if any(part.startswith(".") or part in SKIP_DIR_PREFIXES for part in parts):
            continue
        if p.suffix.lower() in SKIP_EXTENSIONS:
            continue
        if p.name in SKIP_NAMES:
            continue
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
            pass
    return files


# ─────────────────────────────────────────────────────────────────────────────
# Background Info.txt Generator
# ─────────────────────────────────────────────────────────────────────────────

def _format_file_block(file_entry: Dict) -> str:
    filename = file_entry["filename"]
    content = file_entry["content"]
    ext = Path(filename).suffix
    lang = {
        ".java": "java", ".gradle": "groovy", ".toml": "toml",
        ".json": "json", ".properties": "properties", ".md": "markdown",
        ".txt": "text", ".cfg": "cfg", ".yml": "yaml", ".yaml": "yaml",
        ".xml": "xml", ".css": "css", ".js": "javascript",
    }.get(ext, "")
    return f"{filename}\n```{lang}\n{content}\n```"


def _format_dif_entry(entry: Dict) -> str:
    title = entry.get("title", "Unknown Issue")
    entry_id = entry.get("id", "?")
    fix = entry.get("fix", "(No fix available)")
    return f"### [{entry_id}] {title}\n\n{fix}\n"


def generate_background_info(
    minecraft_version: str,
    loader: str,
    template_dir: Path,
    repo_root: Path,
) -> str:
    lines = []
    lines.append(f"# Background Info — {minecraft_version} ({loader})")
    lines.append("")
    lines.append("This file contains working template code from the repository,")
    lines.append("plus common issues and solutions for this target combination.")
    lines.append("")
    lines.append("─" * 60)
    lines.append("## Section 1: Working Template Code from Repository")
    lines.append("─" * 60)
    lines.append("")
    lines.append("Below are the template files. Each is shown with path + full code.")
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

    lines.append("─" * 60)
    lines.append("## Section 2: Common Issues & Solutions for This Target")
    lines.append("─" * 60)
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
    return "\n".join(lines)


# ─────────────────────────────────────────────────────────────────────────────
# prompt.txt Generator
# ─────────────────────────────────────────────────────────────────────────────

def _parse_projectinfo(projectinfo_text: str) -> Dict:
    """Parse key fields from an existing projectinfo.txt."""
    info = {
        "mod_name": "",
        "mod_author": "",
        "mod_path": "",
        "summary": "",
        "description": "",
        "source_version": "",
    }
    for line in projectinfo_text.splitlines():
        if line.startswith("Mod Name: "):
            info["mod_name"] = line[len("Mod Name: "):]
        elif line.startswith("Mod Author: "):
            info["mod_author"] = line[len("Mod Author: "):]
        elif line.startswith("Mod Path: "):
            info["mod_path"] = line[len("Mod Path: "):]
        elif line.startswith("  Source: "):
            info["source_version"] = line[len("  Source: "):]
        elif line.startswith("Mod Summary:") and not info["summary"]:
            # summary is the next line with content
            pass
    # Extract summary (line after "Mod Summary:")
    lines = projectinfo_text.splitlines()
    for i, line in enumerate(lines):
        if line.strip() == "Mod Summary:" and i + 1 < len(lines):
            info["summary"] = lines[i + 1].strip()
        if line.strip() == "Mod Description (what it intends to do):" and i + 1 < len(lines):
            info["description"] = lines[i + 1].strip()
    return info


def _determine_resource_filename(minecraft_version: str, loader: str) -> str:
    """Determine the correct mod descriptor resource file path for a target."""
    try:
        major_minor = tuple(int(x) for x in minecraft_version.split(".")[:2])
    except ValueError:
        major_minor = (0, 0)
    if minecraft_version.startswith("1.12"):
        major_minor = (1, 12)
    if minecraft_version.startswith("26") and loader == "neoforge":
        major_minor = (1, 21)

    if loader == "fabric":
        return "src/main/resources/fabric.mod.json"
    if loader == "forge":
        if major_minor <= (1, 12):
            return "src/main/resources/mcmod.info"
        else:
            return "src/main/resources/META-INF/mods.toml"
    if loader == "neoforge":
        if major_minor >= (1, 21) or minecraft_version.startswith("26"):
            return "src/main/resources/META-INF/neoforge.mods.toml"
        else:
            return "src/main/resources/META-INF/mods.toml"
    return "src/main/resources/fabric.mod.json"


def _get_loader_instructions(loader: str, minecraft_version: str) -> List[str]:
    try:
        major_minor = tuple(int(x) for x in minecraft_version.split(".")[:2])
    except ValueError:
        major_minor = (0, 0)
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
                '    WARNING - Forge 1.12.x uses MCP-mapped names (NOT SRG/obfuscated names):',
                '      Minecraft.getMinecraft()   - NOT Minecraft.func_71410_x()',
                '      mc.player                  - NOT mc.field_71439_g',
                '      mc.world                   - NOT mc.field_71441_e',
                '      mc.gameSettings            - NOT mc.field_71474_y',
                '      mc.playerController        - NOT mc.field_71442_b',
                '      heldItem.isEmpty()         - NOT heldItem.func_190926_b()',
                '      heldItem.getItem()         - NOT heldItem.func_77973_b()',
                '      Items.EXPERIENCE_BOTTLE    - NOT Items.field_151062_by',
                '    If decompiled source shows func_***() or field_*** names, translate',
                '    them using the table above. Treat SRG names as hints only.',
            ]
        elif major_minor >= (1, 16) and major_minor <= (1, 19):
            return [
                "  Forge 1.16.5 – 1.19.x:",
                "    @Mod(\"modid\") with constructor taking no args or FMLJavaModLoadingContext",
                "    Use FMLJavaModLoadingContext.get().getModEventBus() for mod bus events",
                "    Use MinecraftForge.EVENT_BUS for forge events",
                "    Resource file: META-INF/mods.toml (TOML format)",
            ]
        else:
            return [
                "  Forge 1.20.x+ (modern Forge):",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    IEventBus injected via constructor parameter",
                "    MinecraftForge.EVENT_BUS for forge events",
                "    Resource file: META-INF/mods.toml (TOML)",
            ]
    elif loader == "neoforge":
        if minecraft_version.startswith("26") or major_minor >= (1, 21):
            return [
                "  NeoForge 1.21+ / 26.x (Modern):",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    NeoForge.EVENT_BUS (not MinecraftForge) for game events",
                "    Import: net.neoforged.neoforge.common.NeoForge",
                "    Import: net.neoforged.bus.api.IEventBus",
                "    Import: net.neoforged.fml.common.Mod",
                "    Resource file: META-INF/neoforge.mods.toml (for 1.21+)",
            ]
        else:
            return [
                "  NeoForge 1.20.2 – 1.20.6:",
                "    @Mod(\"modid\") with constructor(IEventBus modEventBus)",
                "    NeoForge.EVENT_BUS for game events",
                "    Resource file: META-INF/mods.toml",
            ]
    elif loader == "fabric":
        return [
            "  Fabric:",
            "    Implement ModInitializer interface",
            "    @Override onInitialize() for mod setup",
            "    Use ServerTickEvents, ClientTickEvents, etc. for tick handlers",
            "    Registry: Registry.register(Registries.ITEM, identifier, item)",
            "    Resource file: fabric.mod.json (JSON in src/main/resources/)",
        ]
    return []


def generate_prompt(
    minecraft_version: str,
    loader: str,
    projectinfo_text: str,
    background_info: str,
    template_dir: Path,
    repo_root: Path,
    manifest: Dict | None = None,
    *,
    substitute_guide: bool = False,
) -> str:
    """Generate a complete prompt.txt for one target."""
    meta = _parse_projectinfo(projectinfo_text)
    resource_file = _determine_resource_filename(minecraft_version, loader)
    template_files = _collect_template_files(template_dir, repo_root)

    # Determine main class from mod path
    mod_path = meta.get("mod_path", "asd.itamio.mod")
    mod_name_clean = mod_path.split(".")[-1] if "." in mod_path else "mod"
    main_class_name = "".join(w.capitalize() for w in re.split(r"[-_]", mod_name_clean)) + "Mod"
    pkg_path = mod_path.replace(".", "/")

    lines = []
    lines.append("=" * 70)
    lines.append(f"AI CODING PROMPT — {meta.get('mod_name', 'Mod')} for Minecraft {minecraft_version} ({loader})")
    lines.append("=" * 70)
    lines.append("")

    # ── Part 1: Project Information ─────────────────────────────────────
    lines.append("─" * 60)
    lines.append("PART 1: PROJECT INFORMATION")
    lines.append("─" * 60)
    lines.append("")
    lines.append(projectinfo_text)
    lines.append("")

    # ── Part 2: Background Info ─────────────────────────────────────────
    lines.append("─" * 60)
    lines.append("PART 2: BACKGROUND INFO (Template Code + Known Issues)")
    lines.append("─" * 60)
    lines.append("")
    lines.append(background_info)
    lines.append("")

    # ── Part 2.5: Version Guide ─────────────────────────────────────────
    if manifest is not None:
        version_guide = _load_version_guide(minecraft_version, loader, manifest, meta, substitute=substitute_guide)
        if version_guide:
            lines.append("─" * 60)
            lines.append(f"VERSION GUIDE — {minecraft_version} ({loader})")
            lines.append("─" * 60)
            lines.append("")
            lines.append("This section contains version-specific coding guidelines, API patterns,")
            lines.append("common pitfalls, import paths, and best practices for this exact")
            lines.append("Minecraft version + loader combination. Study this carefully — it")
            lines.append("contains crucial information that will prevent build failures.")
            lines.append("")
            lines.append(version_guide)
            lines.append("")

    # ── Part 3: AI Instructions ─────────────────────────────────────────
    lines.append("=" * 70)
    lines.append("PART 3: AI CODING INSTRUCTIONS")
    lines.append("=" * 70)
    lines.append("")
    lines.append(f"Your task: Write ALL source files needed to build the mod '{meta.get('mod_name', 'Mod')}'")
    lines.append(f"(package: '{mod_path}') for Minecraft {minecraft_version} using the {loader} loader.")
    lines.append("")
    lines.append("## CRITICAL REQUIREMENTS")
    lines.append("")
    lines.append("1. **YOU MUST OUTPUT ALL FILES** — Every single file must be provided.")
    lines.append("   Missing a single file will cause the build to fail.")
    lines.append("")
    lines.append("2. **EXACT OUTPUT FORMAT** — Each file MUST be in this EXACT two-block format.")
    lines.append("   This is the ONLY accepted format. The parser strictly reads blocks in pairs:")
    lines.append("")
    lines.append("   ```filepath")
    lines.append(f"   bundle/{minecraft_version}-{loader}/src/main/java/{pkg_path}/{main_class_name}.java")
    lines.append("   ```")
    lines.append("   ```java")
    lines.append("   package net.itamio.skypvp;")

    lines.append("")
    lines.append("   public class SkypvpMod {")
    lines.append("       // ...")
    lines.append("   }")
    lines.append("   ```")
    lines.append("")
    lines.append("   The FIRST backtick block (language `filepath`) contains ONLY the file path.")
    lines.append("   The SECOND backtick block contains the actual source code.")
    lines.append("")
    lines.append("3. **❌ COMMON MISTAKES — DO NOT do any of these:**")
    lines.append("   - DO NOT wrap the filepath in single backticks: `path/to/file.java` ← WRONG")
    lines.append("   - DO NOT put the filepath on a bare line without a ```filepath block")
    lines.append("   - DO NOT add extra text between the filepath block and the code block")
    lines.append("   - DO NOT use `filepath` as the language for the code block — use `java`, `json`, `toml`, etc.")
    lines.append("   - DO NOT forget to include ALL files — every file must be listed")
    lines.append("   - DO NOT create build files (build.gradle, settings.gradle, etc.)")
    lines.append("")
    lines.append("4. **ALL filepaths must be relative** — no full system paths.")
    lines.append("")
    lines.append("5. **Implement REAL logic** from the decompiled source in PART 1. No stubs or TODOs.")
    lines.append("")
    lines.append(f"6. **Use the EXACT package** `{mod_path}` in all Java files.")
    lines.append("")
    lines.append(f"7. **The main class name is `{main_class_name}`** — filename must match exactly.")
    lines.append("")
    lines.append("8. **NEVER use obfuscated/SRG field names** — if decompiled source code in")
    lines.append("   PART 1 shows `func_***()` methods or `field_***` variables, those are")
    lines.append("   obfuscated SRG names that will NOT compile. You MUST translate them to")
    lines.append("   the proper deobfuscated names that exist in the build environment.")
    lines.append("   Common translations for all Forge versions:")
    lines.append('     `Minecraft.getMinecraft()`            -> use instead of `func_71410_x()`')
    lines.append('     `mc.player` / `mc.thePlayer`          -> use instead of `field_71439_g`')
    lines.append('     `mc.world` / `mc.theWorld`            -> use instead of `field_71441_e`')
    lines.append('     `mc.gameSettings`                     -> use instead of `field_71474_y`')
    lines.append('     `heldItem.isEmpty()`                  -> use instead of `func_190926_b()`')
    lines.append('     `heldItem.getItem()`                  -> use instead of `func_77973_b()`')
    lines.append('     `Items.EXPERIENCE_BOTTLE` / similar   -> use instead of `field_151062_by`')
    lines.append("   If the decompiled source you were given contains SRG/obfuscated names,")
    lines.append("   this is a known issue with the decompiler. ALWAYS translate to proper")
    lines.append("   mapped names. When in doubt, prefer the simple English method name")
    lines.append("   (e.g. `getMinecraft`, `isEmpty`, `getItem`) over the obfuscated one.")
    lines.append("")
    lines.append("9. **Loader-specific API patterns:**")
    lines.append("")
    lines.extend(_get_loader_instructions(loader, minecraft_version))
    lines.append("")

    lines.append("10. **DO NOT create build files** — The build system already provides:")
    lines.append("    - build.gradle / build.gradle.kts")
    lines.append("    - settings.gradle / settings.gradle.kts")
    lines.append("    - gradle.properties")
    lines.append("    - gradlew / gradlew.bat")
    lines.append("    - gradle/wrapper/*")
    lines.append("    Creating these files will cause build failures. Only create source files.")
    lines.append("")

    # Expected files based on template
    lines.append("## FILES TO CREATE")
    lines.append("")
    lines.append("You MUST create ALL of the following files:")
    lines.append("")
    for tf in template_files:
        tf_rel = tf["filename"]
        if tf_rel.endswith(".java"):
            lines.append(f"  - `bundle/{minecraft_version}-{loader}/src/main/java/{pkg_path}/{Path(tf_rel).name}`")
        elif "resources" in tf_rel:
            lines.append(f"  - `bundle/{minecraft_version}-{loader}/{tf_rel}`")

    lines.append("")
    lines.append("## RESOURCE FILE")
    lines.append("")
    lines.append(f"  Resource file path: `bundle/{minecraft_version}-{loader}/{resource_file}`")
    lines.append("")

    # DIF known issues
    dif_entries = _search_dif(repo_root, minecraft_version, loader, max_results=4)
    if dif_entries:
        lines.append("## KNOWN ISSUES FOR THIS TARGET")
        lines.append("")
        for entry in dif_entries:
            lines.append(f"  ⚠ [{entry['id']}] {entry['title']}")
        lines.append("")
        lines.append("Full details in PART 2 above.")
        lines.append("")

    lines.append("=" * 70)
    lines.append("END OF PROMPT")
    return "\n".join(lines)


# ─────────────────────────────────────────────────────────────────────────────
# Main — iterate over bundle dir and add prompts to each target folder
# ─────────────────────────────────────────────────────────────────────────────

def add_prompts_to_bundle(
    bundle_dir: Path,
    info_dir: Optional[Path],
    manifest: Dict,
    repo_root: Path,
) -> int:
    """
    For each per-target folder in bundle_dir, add Background Info.txt and prompt.txt.

    Returns the number of target folders processed.
    """
    if not bundle_dir.exists():
        print(f"ERROR: bundle dir not found: {bundle_dir}", file=sys.stderr)
        return 0

    # Find all target directories (skip .cache and root files)
    target_dirs = sorted([
        d for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])

    if not target_dirs:
        print("ERROR: No target directories found in bundle.", file=sys.stderr)
        return 0

    print(f"Found {len(target_dirs)} target directories to process.")

    processed = 0
    for target_dir in target_dirs:
        folder_name = target_dir.name  # e.g. "1.12-forge"
        projectinfo_path = target_dir / "projectinfo.txt"

        if not projectinfo_path.exists():
            print(f"  ⚠ Skipping {folder_name}: no projectinfo.txt found")
            continue

        # Parse folder name into MC version and loader
        # Folder names like "1.12-forge", "1.21.9-fabric", "26.1-neoforge"
        parts = folder_name.rsplit("-", 1)
        if len(parts) != 2:
            print(f"  ⚠ Skipping {folder_name}: cannot parse version-loader")
            continue
        minecraft_version, loader = parts

        projectinfo_text = projectinfo_path.read_text(encoding="utf-8")

        # Find template directory from manifest
        template_dir = _find_template_dir(manifest, minecraft_version, loader, repo_root)

        # ── Generate Background Info.txt ──
        bg_content = generate_background_info(
            minecraft_version=minecraft_version,
            loader=loader,
            template_dir=template_dir,
            repo_root=repo_root,
        )
        bg_path = target_dir / "Background Info.txt"
        bg_path.write_text(bg_content, encoding="utf-8")

        # ── Generate prompt.txt ──
        prompt_content = generate_prompt(
            minecraft_version=minecraft_version,
            loader=loader,
            projectinfo_text=projectinfo_text,
            background_info=bg_content,
            template_dir=template_dir,
            repo_root=repo_root,
            manifest=manifest,
        )
        prompt_path = target_dir / "prompt.txt"
        prompt_path.write_text(prompt_content, encoding="utf-8")

        print(f"  ✅ {folder_name}: Background Info.txt + prompt.txt")
        processed += 1

    print(f"\nDone! {processed}/{len(target_dirs)} target(s) processed.")
    return processed


def _find_template_dir(manifest: Dict, minecraft_version: str, loader: str, repo_root: Path) -> Optional[Path]:
    """Find the template directory for a version+loader combo from the manifest."""
    try:
        from packaging.version import Version, InvalidVersion
        def ver(v):
            try:
                return Version(v)
            except InvalidVersion:
                return Version("0")
        target = ver(minecraft_version)
        for rng in manifest.get("ranges", []):
            if loader not in rng.get("loaders", {}):
                continue
            ldr_cfg = rng["loaders"][loader]
            supported = ldr_cfg.get("supported_versions", [])
            if supported and minecraft_version in supported:
                return repo_root / ldr_cfg["template_dir"]
            min_v = ver(rng.get("min_version", "0"))
            max_v = ver(rng.get("max_version", "99999"))
            if min_v <= target <= max_v:
                return repo_root / ldr_cfg["template_dir"]
    except ImportError:
        # Fallback without packaging
        for rng in manifest.get("ranges", []):
            if loader not in rng.get("loaders", {}):
                continue
            ldr_cfg = rng["loaders"][loader]
            supported = ldr_cfg.get("supported_versions", [])
            if supported and minecraft_version in supported:
                return repo_root / ldr_cfg["template_dir"]
    return None


# ─────────────────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────────────────

def main() -> int:
    import argparse
    parser = argparse.ArgumentParser(
        description="Add Background Info.txt and prompt.txt to each per-target folder in a diagnosis bundle."
    )
    parser.add_argument("--bundle-dir", required=True,
                        help="Path to the assembled bundle (output from assemble_diagnosis_bundle.py)")
    parser.add_argument("--manifest", required=True,
                        help="Path to version-manifest.json")
    parser.add_argument("--project-info", default=None,
                        help="Optional path to project info directory (for decompiled source etc.)")
    args = parser.parse_args()

    bundle_dir = Path(args.bundle_dir)
    manifest_path = Path(args.manifest)
    info_dir = Path(args.project_info) if args.project_info else None

    if not manifest_path.exists():
        print(f"ERROR: manifest not found: {manifest_path}", file=sys.stderr)
        return 1

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    processed = add_prompts_to_bundle(
        bundle_dir=bundle_dir,
        info_dir=info_dir,
        manifest=manifest,
        repo_root=_REPO_ROOT,
    )

    return 0 if processed > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
