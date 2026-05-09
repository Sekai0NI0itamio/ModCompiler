"""
metadata_builder.py — Build the metadata.json required by build_adapter.py
===========================================================================
Extracts mod metadata from the Modrinth project.json and the jar's embedded
descriptor files (mcmod.info for Forge 1.x, fabric.mod.json for Fabric,
META-INF/mods.toml for modern Forge/NeoForge) and produces the exact
metadata.json format that modcompiler/adapters.py expects.

Required fields:
  mod_id, name, mod_version, group, entrypoint_class,
  runtime_side, description, authors, license

Optional fields:
  homepage, sources, issues
"""
from __future__ import annotations

import json
import re
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional


# ---------------------------------------------------------------------------
# Jar descriptor extraction
# ---------------------------------------------------------------------------

def _read_zip_text(jar_path: Path, name: str) -> Optional[str]:
    try:
        with zipfile.ZipFile(jar_path) as zf:
            if name in zf.namelist():
                return zf.read(name).decode("utf-8", errors="replace")
    except Exception:
        pass
    return None


def _extract_mcmod_info(jar_path: Path) -> Optional[Dict]:
    """Forge 1.7–1.12 mcmod.info"""
    text = _read_zip_text(jar_path, "mcmod.info")
    if not text:
        return None
    try:
        data = json.loads(text)
        if isinstance(data, list) and data:
            return data[0]
        if isinstance(data, dict) and "modList" in data:
            mods = data["modList"]
            if mods:
                return mods[0]
    except Exception:
        pass
    return None


def _extract_fabric_mod_json(jar_path: Path) -> Optional[Dict]:
    """Fabric fabric.mod.json"""
    text = _read_zip_text(jar_path, "fabric.mod.json")
    if not text:
        return None
    try:
        return json.loads(text)
    except Exception:
        return None


def _extract_mods_toml(jar_path: Path) -> Optional[Dict]:
    """Forge/NeoForge META-INF/mods.toml or META-INF/neoforge.mods.toml"""
    for name in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
        text = _read_zip_text(jar_path, name)
        if not text:
            continue
        # Simple TOML parser for the fields we need
        result: Dict = {}
        current_section = ""
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("[[") and line.endswith("]]"):
                current_section = line[2:-2].strip()
                continue
            if line.startswith("[") and line.endswith("]"):
                current_section = line[1:-1].strip()
                continue
            if "=" in line:
                key, _, val = line.partition("=")
                key = key.strip()
                val = val.strip().strip('"').strip("'")
                # Only capture top-level and [[mods]] fields
                if current_section in ("", "mods"):
                    result[key] = val
        if result:
            return result
    return None


def _find_entrypoint_class(jar_path: Path, mod_id: str) -> Optional[str]:
    """Scan class files to find the main mod entrypoint class."""
    try:
        with zipfile.ZipFile(jar_path) as zf:
            # Look for @Mod annotation in class names (heuristic)
            class_files = [n for n in zf.namelist() if n.endswith(".class") and not n.startswith("META-INF")]
            # Prefer classes that match the mod_id pattern
            candidates = []
            for cf in class_files:
                class_name = cf.replace("/", ".").removesuffix(".class")
                base = class_name.split(".")[-1].lower()
                if any(kw in base for kw in ("mod", "main", "init", "core", mod_id.lower().replace("-", "").replace("_", ""))):
                    candidates.append(class_name)
            if candidates:
                # Prefer classes ending in "Mod" or "Main" (most likely entrypoints)
                def _rank(c: str) -> int:
                    b = c.split(".")[-1].lower()
                    if b.endswith("mod"):
                        return 0
                    if b.endswith("main"):
                        return 1
                    if b.endswith("init"):
                        return 2
                    return 3
                candidates.sort(key=lambda c: (_rank(c), len(c.split(".")), c))
                return candidates[0]
            # Fall back to first non-inner class
            for cf in sorted(class_files):
                if "$" not in cf:
                    return cf.replace("/", ".").removesuffix(".class")
    except Exception:
        pass
    return None


def _guess_group(entrypoint_class: str, mod_id: str) -> str:
    """Derive the Maven group from the entrypoint class package."""
    parts = entrypoint_class.split(".")
    if len(parts) >= 3:
        return ".".join(parts[:-1])
    if len(parts) == 2:
        return parts[0]
    return f"com.{mod_id.replace('-', '').replace('_', '')}"


# ---------------------------------------------------------------------------
# Main builder
# ---------------------------------------------------------------------------

def build_metadata_json(
    project_json_path: Path,
    jar_path: Optional[Path] = None,
    overrides: Optional[Dict] = None,
) -> Dict[str, Any]:
    """
    Build a metadata dict compatible with modcompiler/adapters.py from:
    - project_info/project.json  (Modrinth project metadata)
    - The first-version jar       (embedded mod descriptors)
    - Optional overrides dict

    Returns a dict with all required fields filled in.
    """
    overrides = overrides or {}

    # Load Modrinth project.json
    project: Dict = {}
    if project_json_path.exists():
        try:
            project = json.loads(project_json_path.read_text(encoding="utf-8"))
        except Exception:
            pass

    slug = project.get("slug", "unknown")
    title = project.get("title", slug)
    description = project.get("description", "")
    license_id = project.get("license", {}).get("id", "ARR") if isinstance(project.get("license"), dict) else str(project.get("license", "ARR"))
    homepage = project.get("source_url") or project.get("wiki_url") or project.get("issues_url") or None
    issues = project.get("issues_url") or None
    sources = project.get("source_url") or None

    # Derive mod_id from slug (slugs use hyphens, mod_ids use underscores or camelCase)
    mod_id = slug.replace("-", "_").lower()

    # Default values
    name = title
    mod_version = "1.0.0"
    authors: List[str] = []
    entrypoint_class = ""
    group = ""
    runtime_side = "both"

    # Try to extract better values from the jar
    if jar_path and jar_path.exists():
        # Try mcmod.info (Forge legacy)
        mcmod = _extract_mcmod_info(jar_path)
        if mcmod:
            mod_id = mcmod.get("modid", mod_id)
            name = mcmod.get("name", name)
            mod_version = mcmod.get("version", mod_version)
            description = mcmod.get("description", description) or description
            raw_authors = mcmod.get("authorList", mcmod.get("authors", []))
            if isinstance(raw_authors, list):
                authors = [str(a) for a in raw_authors if a]
            elif isinstance(raw_authors, str):
                authors = [a.strip() for a in raw_authors.split(",") if a.strip()]

        # Try fabric.mod.json
        fabric = _extract_fabric_mod_json(jar_path)
        if fabric:
            mod_id = fabric.get("id", mod_id)
            name = fabric.get("name", name)
            mod_version = fabric.get("version", mod_version)
            description = fabric.get("description", description) or description
            raw_authors = fabric.get("authors", [])
            if isinstance(raw_authors, list):
                authors = []
                for a in raw_authors:
                    if isinstance(a, str):
                        authors.append(a)
                    elif isinstance(a, dict):
                        authors.append(a.get("name", str(a)))
            env = fabric.get("environment", "*")
            if env == "client":
                runtime_side = "client"
            elif env == "server":
                runtime_side = "server"
            # Extract entrypoint from fabric.mod.json
            eps = fabric.get("entrypoints", {})
            for key in ("main", "client", "server"):
                ep_list = eps.get(key, [])
                if ep_list:
                    ep = ep_list[0]
                    if isinstance(ep, str):
                        entrypoint_class = ep
                    elif isinstance(ep, dict):
                        entrypoint_class = ep.get("value", "")
                    break

        # Try mods.toml (modern Forge/NeoForge)
        mods_toml = _extract_mods_toml(jar_path)
        if mods_toml:
            mod_id = mods_toml.get("modId", mod_id)
            name = mods_toml.get("displayName", name)
            mod_version = mods_toml.get("version", mod_version)
            description = mods_toml.get("description", description) or description
            raw_authors = mods_toml.get("authors", "")
            if raw_authors:
                authors = [a.strip() for a in raw_authors.split(",") if a.strip()]

        # Find entrypoint class if not already found
        if not entrypoint_class:
            found = _find_entrypoint_class(jar_path, mod_id)
            if found:
                entrypoint_class = found

    # Derive group from entrypoint class
    if entrypoint_class:
        group = _guess_group(entrypoint_class, mod_id)
    else:
        # Fallback: use asd.itamio standard (not com.{mod_id})
        _mod_name_clean_fb = mod_id.replace("-", "").replace("_", "").lower()
        group = f"asd.itamio.{_mod_name_clean_fb}"
        entrypoint_class = f"{group}.{_to_pascal_case(mod_id)}Mod"

    # ── Apply the asd.itamio package naming standard ──────────────────────
    # All mods in this project use asd.itamio.<modname> as the group.
    # If the extracted group doesn't follow this standard (e.g. it came from
    # the original jar as com.itamio.* or com.example.*), normalise it.
    _STANDARD_PREFIX = "asd.itamio"
    _mod_name_clean = mod_id.replace("-", "").replace("_", "").lower()

    def _needs_normalisation(g: str) -> bool:
        if not g:
            return True
        if g.startswith(_STANDARD_PREFIX + "."):
            return False  # already correct
        # Reject com.example, com.itamio, net.itamio, etc.
        bad_prefixes = ("com.example", "net.example", "org.example",
                        "com.itamio", "net.itamio", "com.mod", "com.minecraft")
        return any(g.startswith(p) for p in bad_prefixes) or "example" in g.lower()

    if _needs_normalisation(group):
        group = f"{_STANDARD_PREFIX}.{_mod_name_clean}"
        # Rebuild entrypoint class to match the corrected group
        ep_class_name = _to_pascal_case(mod_id) + "Mod"
        entrypoint_class = f"{group}.{ep_class_name}"

    # ── Always attribute to Itamio ─────────────────────────────────────────
    # This project's mods are all authored by Itamio. Always force this.
    _AUTHOR = "Itamio"
    _bad_authors = {"", "yourname", "example", "author", "unknown", "mod", "player"}
    # Always set to Itamio — this is a single-author project
    authors = [_AUTHOR]

    # Clean up mod_version (remove ${version} placeholders)
    if "${" in mod_version:
        mod_version = "1.0.0"

    # Apply overrides
    result: Dict[str, Any] = {
        "mod_id": mod_id,
        "name": name,
        "mod_version": mod_version,
        "group": group,
        "entrypoint_class": entrypoint_class,
        "runtime_side": runtime_side,
        "description": description or f"{name} mod",
        "authors": authors,
        "license": license_id or "ARR",
        "homepage": homepage,
        "sources": sources,
        "issues": issues,
    }
    result.update(overrides)

    # ── Post-override normalisation ────────────────────────────────────────
    # Re-apply the asd.itamio standard and Itamio author after overrides,
    # so that values coming from the jar or overrides dict are also corrected.
    final_group = result.get("group", "")
    final_mod_id = result.get("mod_id", mod_id)
    final_mod_name_clean = final_mod_id.replace("-", "").replace("_", "").lower()
    if _needs_normalisation(final_group):
        result["group"] = f"{_STANDARD_PREFIX}.{final_mod_name_clean}"
        # Only rebuild entrypoint if it was also using the wrong package
        final_ep = result.get("entrypoint_class", "")
        if not final_ep or _needs_normalisation(".".join(final_ep.split(".")[:-1])):
            ep_class_name = _to_pascal_case(final_mod_id) + "Mod"
            result["entrypoint_class"] = f"{result['group']}.{ep_class_name}"

    # Always force Itamio as author — this is a single-author project
    result["authors"] = [_AUTHOR]

    # Final validation — ensure no None for required string fields
    for key in ("mod_id", "name", "mod_version", "group", "entrypoint_class", "description", "license"):
        if not result.get(key):
            result[key] = key  # last-resort placeholder

    return result


def _to_pascal_case(s: str) -> str:
    return "".join(word.capitalize() for word in re.split(r"[-_]", s))


def generate_metadata_json(
    project_info_dir: Path,
    output_path: Path,
    overrides: Optional[Dict] = None,
) -> Path:
    """
    Generate metadata.json from project_info/ and write it to output_path.
    Returns the output path.
    """
    project_json = project_info_dir / "project.json"

    # Find the first-version jar
    jar_path: Optional[Path] = None
    first_version_dir = project_info_dir / "first_version"
    if first_version_dir.exists():
        jars = list(first_version_dir.glob("*.jar"))
        if jars:
            jar_path = jars[0]

    metadata = build_metadata_json(project_json, jar_path, overrides)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    return output_path
