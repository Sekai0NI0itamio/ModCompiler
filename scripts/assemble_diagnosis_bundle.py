#!/usr/bin/env python3
"""
assemble_diagnosis_bundle.py — Phase 2 assembly: create diagnostic bundles
==========================================================================
Takes the outputs from the info-gather and diagnosis jobs and produces the
final artifact bundle containing:

  Logs.txt                        — Stdout/stderr capture from this script
  Diagnosis.txt                   — Diagnosis summary with working & missing versions
  <mc_version>-<loader>/          — One folder per missing/shell version combo
    projectinfo.txt               — Full mod info + source code from closest versions

Usage:
  python3 scripts/assemble_diagnosis_bundle.py \
    --info-dir .workflow_downloads/info \
    --diagnosis-dir .workflow_downloads/diagnosis \
    --manifest version-manifest.json \
    --output-dir .workflow_artifacts/bundle
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


# ── Helpers ──────────────────────────────────────────────────────────────────

_LOADER_GROUPS = {
    "forge": ("forge", "neoforge"),
    "neoforge": ("forge", "neoforge"),
    "fabric": ("fabric",),
}

_LOG_LINES: List[str] = []


def _log(msg: str) -> None:
    _LOG_LINES.append(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")
    print(msg, file=sys.stderr)


def _loader_group(loader: str) -> Tuple[str, ...]:
    return _LOADER_GROUPS.get(loader, (loader,))


def _load_json(path: Path) -> Any:
    if not path.exists():
        _log(f"  WARNING: {path} not found")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        _log(f"  WARNING: Could not load {path}: {e}")
        return None


def _to_pascal_case(s: str) -> str:
    return "".join(word.capitalize() for word in re.split(r"[-_\s]+", s))


def _decompile_jar(jar_path: Path, version_manifest: Dict, output_dir: Path) -> Optional[Path]:
    """
    Decompile a jar using vineflower.
    Returns the path to the extracted src/main/java directory, or None on failure.
    """
    temp_root = Path(tempfile.mkdtemp(prefix="assemble-decompile-"))
    try:
        expanded_jar = temp_root / "expanded-jar"
        java_output = temp_root / "java-output"
        src_dir = output_dir / "src" / "main" / "java"
        output_dir.mkdir(parents=True, exist_ok=True)
        expanded_jar.mkdir(parents=True, exist_ok=True)
        java_output.mkdir(parents=True, exist_ok=True)

        with zipfile.ZipFile(jar_path) as archive:
            archive.extractall(expanded_jar)

        decompiler_jar = temp_root / "vineflower.jar"
        url = "https://github.com/Vineflower/vineflower/releases/download/1.9.3/vineflower-1.9.3.jar"
        urllib.request.urlretrieve(url, decompiler_jar)

        import subprocess
        result = subprocess.run(
            ["java", "-jar", str(decompiler_jar), str(expanded_jar), str(java_output)],
            capture_output=True, text=True, timeout=120,
        )
        if result.returncode != 0:
            _log(f"  Decompiler failed (exit {result.returncode}): {result.stderr[:200]}")
            return None

        # Copy decompiled sources
        for source_file in sorted(java_output.rglob("*")):
            if not source_file.is_file():
                continue
            relative = source_file.relative_to(java_output)
            dest = src_dir / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(str(source_file), str(dest))

        if list(src_dir.rglob("*.java")):
            _log(f"  Decompiled {jar_path.name}: {len(list(src_dir.rglob('*.java')))} .java files")
            return src_dir
        else:
            _log(f"  Decompiler produced no .java files for {jar_path.name}")
            return None

    except Exception as e:
        _log(f"  Decompilation error for {jar_path.name}: {e}")
        return None
    finally:
        try:
            shutil.rmtree(str(temp_root))
        except Exception:
            pass


def _get_directory_tree(path: Path, prefix: str = "") -> List[str]:
    """
    Get a tree-like listing of a directory structure.
    """
    if not path.exists():
        return [f"{prefix}(directory not found)"]
    lines = []
    entries = sorted(path.iterdir(), key=lambda p: (not p.is_dir(), p.name))
    for i, entry in enumerate(entries):
        is_last = i == len(entries) - 1
        connector = "└── " if is_last else "├── "
        rel = str(entry.relative_to(path))
        if entry.is_dir():
            lines.append(f"{prefix}{connector}{rel}/"
            )
            # Show children (limited depth)
            child_prefix = prefix + ("    " if is_last else "│   ")
            children = sorted(entry.iterdir(), key=lambda p: (not p.is_dir(), p.name))[:30]
            for j, child in enumerate(children):
                child_is_last = j == len(children) - 1
                child_connector = "└── " if child_is_last else "├── "
                child_rel = str(child.relative_to(entry))
                if child.is_dir():
                    lines.append(f"{child_prefix}{child_connector}{child_rel}/")
                else:
                    size = child.stat().st_size
                    lines.append(f"{child_prefix}{child_connector}{child_rel} ({size:,}B)")
            if len(children) < len(list(entry.iterdir())):
                lines.append(f"{child_prefix}└── ... ({len(list(entry.iterdir())) - 30} more)")
        else:
            size = entry.stat().st_size
            lines.append(f"{prefix}{connector}{rel} ({size:,}B)")
    return lines


def _get_source_code_text(src_dir: Path) -> str:
    """
    Get the actual source code contents from a decompiled source directory.
    Returns a concatenation of all .java files with their paths.
    """
    if not src_dir or not src_dir.exists():
        return "  (no source code available)"
    parts = []
    for jf in sorted(src_dir.rglob("*.java")):
        rel = str(jf.relative_to(src_dir))
        try:
            code = jf.read_text(encoding="utf-8")
        except Exception:
            code = "(could not read)"
        parts.append(f"  ── {rel} ──")
        for cline in code.splitlines():
            parts.append(f"  {cline}")
        parts.append("")
    if not parts:
        return "  (no .java files found)"
    return "\n".join(parts)


def _find_closest_version_target(
    target_mc: str,
    target_loader: str,
    working_records: List[Dict],
) -> Optional[str]:
    """
    Find the closest working version whose game_versions includes target_mc.
    Same MC version (any loader) is preferred.
    If not found, try with forge/neoforge loader closeness.
    """
    target_ldr_group = _loader_group(target_loader)

    best: Optional[str] = None
    best_rec: Optional[Dict] = None

    for rec in working_records:
        gvs = set(str(g) for g in rec.get("game_versions", []))
        if target_mc not in gvs:
            continue
        rec_ldrs = set(str(l) for l in rec.get("loaders", []))
        # Prefer same MC version, any loader
        if best_rec is None:
            best = f"{rec.get('version_number', '?')} (MC={', '.join(rec['game_versions'])}, Loader={', '.join(rec['loaders'])})"
            best_rec = rec
            continue
        # Prefer same loader group
        if any(l in target_ldr_group for l in rec_ldrs):
            if not any(l in target_ldr_group for l in best_rec.get("loaders", [])):
                best = f"{rec.get('version_number', '?')} (MC={', '.join(rec['game_versions'])}, Loader={', '.join(rec['loaders'])})"
                best_rec = rec

    return best


def _find_closest_loader_target(
    target_mc: str,
    target_loader: str,
    working_records: List[Dict],
) -> Optional[str]:
    """
    Find the closest working version with the same loader (or forge/neoforge close).
    Any MC version is fine.
    """
    target_ldr_group = _loader_group(target_loader)

    best: Optional[str] = None
    best_rec: Optional[Dict] = None

    for rec in working_records:
        rec_ldrs = set(str(l) for l in rec.get("loaders", []))
        # Check if this record has a loader in the same group
        if not any(l in target_ldr_group for l in rec_ldrs):
            continue
        if best_rec is None:
            best = f"{rec.get('version_number', '?')} (MC={', '.join(rec['game_versions'])}, Loader={', '.join(rec['loaders'])})"
            best_rec = rec
            continue
        # Prefer exact same loader
        if target_loader in rec_ldrs:
            if target_loader not in best_rec.get("loaders", []):
                best = f"{rec.get('version_number', '?')} (MC={', '.join(rec['game_versions'])}, Loader={', '.join(rec['loaders'])})"
                best_rec = rec

    return best


def _find_jar_for_version(version_id: str, diagnosis_dir: Path) -> Optional[Path]:
    """Find the downloaded jar for a specific version ID."""
    downloads_dir = diagnosis_dir / "downloads"
    if not downloads_dir.exists():
        return None
    vdir = downloads_dir / version_id
    if not vdir.exists():
        return None
    jars = list(vdir.glob("*.jar"))
    return jars[0] if jars else None


def _parse_mc_version_sort_key(mc_ver: str) -> Tuple:
    """Parse a MC version string into a sortable tuple."""
    parts = []
    for t in re.split(r"[.\-]", mc_ver):
        if t.isdigit():
            parts.append(int(t))
        else:
            parts.append(t)
    return tuple(parts)


# ── Main assembly logic ──────────────────────────────────────────────────────

def assemble_bundle(
    info_dir: Path,
    diagnosis_dir: Path,
    manifest_path: Path,
    output_dir: Path,
) -> int:
    """
    Main assembly function.
    Returns 0 on success, 1 on error.
    """
    _log("=" * 72)
    _log("ASSEMBLE DIAGNOSIS BUNDLE — Phase 2")
    _log("=" * 72)

    # Clean and create output dir
    if output_dir.exists():
        shutil.rmtree(str(output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    # ── Load inputs ───────────────────────────────────────────────────────────
    diagnosis = _load_json(diagnosis_dir / "diagnosis.json")
    if not diagnosis:
        _log("FATAL: diagnosis.json not found or invalid")
        return 1

    project_info = _load_json(info_dir / "project.json")
    if not project_info:
        _log("FATAL: project.json not found or invalid")
        return 1

    versions_meta = _load_json(info_dir / "versions_meta.json") or []
    manifest = _load_json(manifest_path) or {"ranges": []}

    slug = diagnosis.get("slug", project_info.get("slug", "unknown"))
    title = diagnosis.get("title", project_info.get("title", slug))
    description = project_info.get("description", "")
    loaders = project_info.get("loaders", [])

    working_records = [r for r in diagnosis.get("version_records", []) if not r.get("is_shell")]
    shell_records = [r for r in diagnosis.get("version_records", []) if r.get("is_shell")]
    missing_pairs = diagnosis.get("missing_pairs", [])

    _log(f"Project: {title} ({slug})")
    _log(f"Working versions: {len(working_records)}")
    _log(f"Shell versions: {len(shell_records)}")
    _log(f"Missing pairs (repo can build): {len(missing_pairs)}")

    # ── Build combined needed-targets list ─────────────────────────────────────
    needed_targets: Dict[str, Dict] = {}

    for rec in shell_records:
        for mc_ver in rec.get("game_versions", []):
            for loader in rec.get("loaders", []):
                key = f"{mc_ver}-{loader}"
                if key not in needed_targets:
                    needed_targets[key] = {
                        "minecraft_version": mc_ver,
                        "loader": loader,
                        "reason": "shell",
                        "shell_version_number": rec.get("version_number", "?"),
                    }

    for pair in missing_pairs:
        mc_ver = pair.get("mc_version", "")
        loader = pair.get("loader", "")
        if mc_ver and loader:
            key = f"{mc_ver}-{loader}"
            if key not in needed_targets:
                needed_targets[key] = {
                    "minecraft_version": mc_ver,
                    "loader": loader,
                    "reason": "missing",
                }

    # ── Sort targets by MC version ────────────────────────────────────────────
    def _sort_key(k: str) -> Tuple:
        parts = []
        for t in re.split(r"[.\-]", k.split("-")[0]):
            if t.isdigit():
                parts.append(int(t))
            else:
                parts.append(t)
        return tuple(parts)
    sorted_keys = sorted(needed_targets.keys(), key=_sort_key)

    _log(f"\nNeeded targets: {len(needed_targets)}")
    for key in sorted_keys:
        t = needed_targets[key]
        _log(f"  {key} [{t['reason']}]")

    # ── Find decompiled jars we can reuse ─────────────────────────────────────
    first_version_src_dir = info_dir / "first_version" / "src"
    first_version_decompiled_src = None
    if first_version_src_dir.exists() and list(first_version_src_dir.rglob("*.java")):
        first_version_decompiled_src = first_version_src_dir
        _log(f"Found pre-decompiled source in first_version/src ({len(list(first_version_src_dir.rglob('*.java')))} files)")

    first_version_decompiled_dir = info_dir / "first_version" / "decompiled"
    if first_version_decompiled_dir.exists():
        javadir = first_version_decompiled_dir / "java"
        if javadir.exists() and list(javadir.rglob("*.java")):
            first_version_decompiled_src = javadir
            _log(f"Found pre-decompiled source in first_version/decompiled ({len(list(javadir.rglob('*.java')))} files)")

    decompile_cache: Dict[str, Path] = {}
    decompile_by_key: Dict[str, Path] = {}

    # ── Helper to score how close a working record is to a target ─────────────
    def _mc_ver_to_tuple(v: str) -> Tuple:
        parts = []
        for t in re.split(r"[.\-]", v):
            if t.isdigit():
                parts.append(int(t))
            else:
                parts.append(t)
        return tuple(parts)

    def _versions_are_close(a: str, b: str) -> bool:
        """Check if two MC version strings are close: same, one is prefix of other, or share major.minor prefix."""
        if a == b:
            return True
        a_t = _mc_ver_to_tuple(a)
        b_t = _mc_ver_to_tuple(b)
        # One is prefix of the other (e.g. 1.12 vs 1.12.2)
        if len(a_t) < len(b_t) and a_t == b_t[:len(a_t)]:
            return True
        if len(b_t) < len(a_t) and b_t == a_t[:len(b_t)]:
            return True
        # Share at least first two components
        if len(a_t) >= 2 and len(b_t) >= 2 and a_t[:2] == b_t[:2]:
            return True
        return False

    def _version_distance(a: str, b: str) -> int:
        """Compute a distance score between two MC versions. Lower = closer."""
        if a == b:
            return 0
        a_t = _mc_ver_to_tuple(a)
        b_t = _mc_ver_to_tuple(b)
        # Compare component by component
        dist = 0
        for i in range(max(len(a_t), len(b_t))):
            av = a_t[i] if i < len(a_t) else 0
            bv = b_t[i] if i < len(b_t) else 0
            if isinstance(av, int) and isinstance(bv, int):
                dist += abs(av - bv) * (10 ** (3 - min(i, 3)))
            elif av != bv:
                dist += 1000
        return dist
    def _parse_version_tuple(v: str) -> Tuple:
        parts = []
        for t in re.split(r"[.\-]", v):
            if t.isdigit():
                parts.append(int(t))
            else:
                parts.append(t)
        return tuple(parts)

    def _find_template(
        target_mc: str, target_ldr: str, manifest: Dict
    ) -> Optional[Path]:
        target_v = _parse_version_tuple(target_mc)
        for range_entry in manifest.get("ranges", []):
            min_v = _parse_version_tuple(range_entry["min_version"])
            max_v = _parse_version_tuple(range_entry["max_version"])
            if min_v <= target_v <= max_v:
                ldrs = range_entry.get("loaders", {})
                # Exact loader match first
                if target_ldr in ldrs:
                    return Path(ldrs[target_ldr]["template_dir"])
                # Forge/neoforge close
                tgt_group = _loader_group(target_ldr)
                for ldr_name, ldr_cfg in ldrs.items():
                    if any(l in tgt_group for l in [ldr_name]):
                        return Path(ldr_cfg["template_dir"])
        return None

    def _read_template_files(template_dir: Path) -> List[str]:
        if not template_dir.exists():
            return [f"  Template directory {template_dir} not found"]
        lines = []
        TEXT_EXTENSIONS = {".java", ".py", ".properties", ".gradle", ".json", ".txt", ".md", ".sh", ".bat", ".cfg", ".toml", ".info"}
        for p in sorted(template_dir.rglob("*")):
            if not p.is_file():
                continue
            if p.name == ".gitignore":
                continue
            rel = str(p.relative_to(template_dir))
            if p.suffix in TEXT_EXTENSIONS:
                try:
                    text = p.read_text(encoding="utf-8")
                    lines.append(f"
  ── {rel} ──")
                    for line in text.splitlines():
                        lines.append(f"  {line}")
                except Exception as e:
                    lines.append(f"  ── {rel} (could not read: {e})")
            else:
                size = p.stat().st_size
                if size < 1024:
                    sz = f"{size}B"
                elif size < 1024 * 1024:
                    sz = f"{size/1024:.1f}KB"
                else:
                    sz = f"{size/1024/1024:.1f}MB"
                lines.append(f"  ── {rel} (binary, {sz})")
        if not lines:
            return ["  (no template files found)"]
        return lines


    def _score_closest_version_rec(rec: Dict, target_mc: str, target_ldr: str) -> Optional[Tuple[int, str, Dict]]:
        """Score a working record for 'closest version' matching.
        Returns (score, label, rec) or None if not a match at all.
        Lower score = better match.
        Score breakdown:
          0-99:   exact MC version match
          100-199: MC version close (prefix or major.minor match)
          penalty: +0 for same loader group, +100 for different loader
        """
        rec_gvs = [str(g) for g in rec.get("game_versions", [])]
        target_ldr_group = _loader_group(target_ldr)
        rec_ldrs = set(str(l) for l in rec.get("loaders", []))

        best_gv = None
        best_dist = 99999
        for gv in rec_gvs:
            if _versions_are_close(target_mc, gv):
                d = _version_distance(target_mc, gv)
                if d < best_dist:
                    best_dist = d
                    best_gv = gv

        if best_gv is None:
            return None

        # Score: version distance + loader penalty
        if best_dist == 0:
            base = best_dist  # exact match
        else:
            base = 100 + best_dist  # close match

        # Prefer same loader group
        if any(l in target_ldr_group for l in rec_ldrs):
            pass  # no penalty
        else:
            base += 100  # different loader group penalty

        label = f"{rec.get('version_number', '?')} (MC={','.join(rec_gvs)}, Loader={','.join(rec.get('loaders', []))})"
        return (base, label, rec)

    def _score_closest_loader_rec(rec: Dict, target_mc: str, target_ldr: str) -> Optional[Tuple[int, str, Dict]]:
        """Score a working record for 'closest loader' matching.
        Score breakdown:
          0-9:    exact same loader
          10-19:   same loader group (forge/neoforge)
          penalty: +0 for same MC version, +100 for different MC
        """
        rec_ldrs = set(str(l) for l in rec.get("loaders", []))
        rec_gvs = [str(g) for g in rec.get("game_versions", [])]
        target_ldr_group = _loader_group(target_ldr)

        if target_ldr in rec_ldrs:
            base = 5  # exact same loader
        elif any(l in target_ldr_group for l in rec_ldrs):
            base = 15  # same loader group
        else:
            return None

        # Prefer same or close MC version
        best_mc_dist = 99999
        for gv in rec_gvs:
            if _versions_are_close(target_mc, gv):
                d = _version_distance(target_mc, gv)
                if d < best_mc_dist:
                    best_mc_dist = d
        if best_mc_dist == 99999:
            base += 100  # different MC version penalty
        elif best_mc_dist == 0:
            pass  # same MC version — best
        else:
            base += 50  # close but not exact

        label = f"{rec.get('version_number', '?')} (MC={','.join(rec_gvs)}, Loader={','.join(rec.get('loaders', []))})"
        return (base, label, rec)

    # ── Create per-target folders ─────────────────────────────────────────────
    projectinfo_texts: Dict[str, str] = {}

    for key in sorted_keys:
        target = needed_targets[key]
        mc_ver = target["minecraft_version"]
        loader = target["loader"]
        target_dir = output_dir / key
        target_dir.mkdir(parents=True, exist_ok=True)

        _log(f"\n{'─' * 60}")
        _log(f"Processing target: {key} [{target['reason']}]")

        closest_version_label = None
        closest_version_src = None
        closest_loader_label = None
        closest_loader_src = None

        # ── Find closest version: same MC version (any loader), with proper scoring ──
        best_score = 99999
        best_rec = None
        for rec in working_records:
            scored = _score_closest_version_rec(rec, mc_ver, loader)
            if scored is not None and scored[0] < best_score:
                best_score = scored[0]
                best_rec = scored[2]
                closest_version_label = scored[1]

        if best_rec:
            vid = best_rec["id"]
            _log(f"  Closest version (score={best_score}): {closest_version_label}")

            src = decompile_cache.get(vid)
            if src is None:
                jar = _find_jar_for_version(vid, diagnosis_dir)
                if jar and jar.exists():
                    _log(f"  Decompiling jar: {jar.name}...")
                    src_out = output_dir / ".cache" / f"decompile-{vid}"
                    src = _decompile_jar(jar, manifest, src_out)
                    if src:
                        decompile_cache[vid] = src
                        for gv in best_rec.get("game_versions", []):
                            for ldr in best_rec.get("loaders", []):
                                decompile_by_key[f"{gv}-{ldr}"] = src
            if src:
                closest_version_src = src
            elif first_version_decompiled_src:
                _log(f"  Using fallback source for closest version")
                closest_version_src = first_version_decompiled_src
        else:
            _log(f"  No close version found for MC {mc_ver}")
            if first_version_decompiled_src:
                _log(f"  Using first_version/src as fallback")
                closest_version_src = first_version_decompiled_src

        # ── Find closest loader: same loader (any MC version), with proper scoring ──
        best_loader_score = 99999
        best_loader_rec = None
        for rec in working_records:
            scored = _score_closest_loader_rec(rec, mc_ver, loader)
            if scored is not None and scored[0] < best_loader_score:
                best_loader_score = scored[0]
                best_loader_rec = scored[2]
                closest_loader_label = scored[1]

        if best_loader_rec:
            lid = best_loader_rec["id"]
            _log(f"  Closest loader (score={best_loader_score}): {closest_loader_label}")

            src = decompile_cache.get(lid)
            if src is None:
                jar = _find_jar_for_version(lid, diagnosis_dir)
                if jar and jar.exists():
                    _log(f"  Decompiling jar: {jar.name}...")
                    src_out = output_dir / ".cache" / f"decompile-{lid}"
                    src = _decompile_jar(jar, manifest, src_out)
                    if src:
                        decompile_cache[lid] = src
                        for gv in best_loader_rec.get("game_versions", []):
                            for ldr in best_loader_rec.get("loaders", []):
                                decompile_by_key[f"{gv}-{ldr}"] = src
            if src:
                closest_loader_src = src
            elif first_version_decompiled_src:
                _log(f"  Using fallback source for closest loader")
                closest_loader_src = first_version_decompiled_src
        else:
            _log(f"  No close loader found for {loader}")
            if first_version_decompiled_src:
                _log(f"  Using first_version/src as fallback")
                closest_loader_src = first_version_decompiled_src

        # ── Build projectinfo.txt ─────────────────────────────────────────────
        lines = []
        lines.append(f"Mod Name: {title}")
        lines.append(f"Mod Author: Itamio")
        mod_id_clean = slug.replace("-", "").replace("_", "").lower()
        lines.append(f"Mod Path: asd.itamio.{mod_id_clean}")
        lines.append(f"")
        lines.append(f"Mod Summary:")
        lines.append(f"  {description[:500]}" if description else "  (no description available)")
        lines.append(f"")
        lines.append(f"Mod Description (what it intends to do):")
        lines.append(f"  {description[:1000]}" if description else "  (no description available)")
        lines.append(f"")

        # Determine if version and loader sources are the same record
        same_source = (closest_version_label is not None and closest_version_label == closest_loader_label)

        if same_source:
            lines.append(f"{'=' * 72}")
            lines.append(f"Mod Source code from closest version and loader:")
            lines.append(f"  Source: {closest_version_label}")
            lines.append(f"")
            lines.append(f"  Directory tree:")
            if closest_version_src:
                tree_lines = _get_directory_tree(closest_version_src, prefix="  ")
                lines.extend(tree_lines)
            else:
                lines.append("    (no source code available)")
            lines.append(f"")
            lines.append(f"  Source code:")
            if closest_version_src:
                source_text = _get_source_code_text(closest_version_src)
                lines.append(f"{source_text}" if source_text else "    (no .java files)")
            else:
                lines.append("    (no source code available)")
        else:
            lines.append(f"{'=' * 72}")
            lines.append(f"Mod Source code from closest version:")
            if closest_version_label:
                lines.append(f"  Source: {closest_version_label}")
            else:
                lines.append(f"  Source: (no closest working version found)")
            lines.append(f"")
            lines.append(f"  Directory tree:")
            if closest_version_src:
                tree_lines = _get_directory_tree(closest_version_src, prefix="  ")
                lines.extend(tree_lines)
            else:
                lines.append("    (no source code available)")
            lines.append(f"")
            lines.append(f"  Source code:")
            if closest_version_src:
                source_text = _get_source_code_text(closest_version_src)
                lines.append(f"{source_text}" if source_text else "    (no .java files)")
            else:
                lines.append("    (no source code available)")

            lines.append(f"")
            lines.append(f"{'=' * 72}")
            lines.append(f"Mod Source code from closest loader:")
            if closest_loader_label:
                lines.append(f"  Source: {closest_loader_label}")
            else:
                lines.append(f"  Source: (no closest working version found)")
            lines.append(f"")
            lines.append(f"  Directory tree:")
            if closest_loader_src:
                tree_lines = _get_directory_tree(closest_loader_src, prefix="  ")
                lines.extend(tree_lines)
            else:
                lines.append("    (no source code available)")
            lines.append(f"")
            lines.append(f"  Source code:")
            if closest_loader_src:
                source_text = _get_source_code_text(closest_loader_src)
                lines.append(f"{source_text}" if source_text else "    (no .java files)")
            else:
                lines.append("    (no source code available)")

        lines.append(f"")
        lines.append(f"{'=' * 72}")
        lines.append(f"End of projectinfo.txt for {key}")

        projectinfo = "\n".join(lines)
        (target_dir / "projectinfo.txt").write_text(projectinfo, encoding="utf-8")
        projectinfo_texts[key] = projectinfo
        _log(f"  -> Wrote {key}/projectinfo.txt ({len(lines)} lines)")

    # ── Write Diagnosis.txt ───────────────────────────────────────────────────
    _log(f"\n{'─' * 60}")
    _log("Writing Diagnosis.txt...")

    diagnosis_lines = []
    SEP = "=" * 72
    diagnosis_lines.append(SEP)
    diagnosis_lines.append(f"DIAGNOSIS: {title}")
    diagnosis_lines.append(f"Slug: {slug}")
    diagnosis_lines.append(f"URL: https://modrinth.com/mod/{slug}")
    diagnosis_lines.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}")
    diagnosis_lines.append(SEP)
    diagnosis_lines.append("")

    diagnosis_lines.append("── SUMMARY ───────────────────────────────────────────────────────")
    diagnosis_lines.append(f"  Total versions on Modrinth : {diagnosis.get('total_versions', '?')}")
    diagnosis_lines.append(f"  Analysed (latest per slot) : {diagnosis.get('analysed_versions', '?')}")
    diagnosis_lines.append(f"  Working versions           : {diagnosis.get('working_count', 0)}")
    diagnosis_lines.append(f"  Shell/malformed versions   : {diagnosis.get('shell_count', 0)}")
    diagnosis_lines.append(f"  Missing (repo can build)   : {diagnosis.get('missing_count', 0)}")
    diagnosis_lines.append(f"  Total needed targets       : {len(needed_targets)}")
    diagnosis_lines.append("")

    diagnosis_lines.append("── WORKING VERSIONS ───────────────────────────────────────────────")
    for rec in working_records:
        ldrs = ",".join(rec.get("loaders", []))
        gvs = ",".join(rec.get("game_versions", []))
        status = "SHELL" if rec.get("is_shell") else "OK"
        diagnosis_lines.append(f"  {rec['version_number']:<20} [{status}] MC={gvs:<20} Loader={ldrs:<20} classes={rec.get('class_count', '?'):>6}")
    diagnosis_lines.append("")

    diagnosis_lines.append("── SHELL / MALFORMED VERSIONS ──────────────────────────────────────")
    for rec in shell_records:
        ldrs = ",".join(rec.get("loaders", []))
        gvs = ",".join(rec.get("game_versions", []))
        reason = rec.get("shell_reason", "unknown")
        diagnosis_lines.append(f"  {rec['version_number']:<20} MC={gvs:<20} Loader={ldrs:<20} reason={reason}")
    if not shell_records:
        diagnosis_lines.append("  None")
    diagnosis_lines.append("")

    diagnosis_lines.append("── MISSING VERSIONS (repo can build, not on Modrinth) ───────────────")
    if missing_pairs:
        missing_by_loader: Dict[str, List[str]] = {}
        for pair in missing_pairs:
            missing_by_loader.setdefault(pair["loader"], []).append(pair["mc_version"])
        for ldr in sorted(missing_by_loader.keys()):
            diagnosis_lines.append(f"  [{ldr}]  {', '.join(sorted(missing_by_loader[ldr]))}")
    else:
        diagnosis_lines.append("  None")
    diagnosis_lines.append("")

    diagnosis_lines.append("── NEEDED TARGETS (projectinfo.txt created for each) ───────────────")
    for key in sorted_keys:
        t = needed_targets[key]
        diagnosis_lines.append(f"  {key:<30} [{t['reason']}]")
    diagnosis_lines.append("")

    diagnosis_lines.append(SEP)

    (output_dir / "Diagnosis.txt").write_text("\n".join(diagnosis_lines) + "\n", encoding="utf-8")
    _log(f"Wrote Diagnosis.txt ({len(diagnosis_lines)} lines)")

    # ── Write Logs.txt ───────────────────────────────────────────────────────
    log_content = "\n".join(_LOG_LINES) + "\n"
    (output_dir / "Logs.txt").write_text(log_content, encoding="utf-8")
    _log(f"Wrote Logs.txt ({len(_LOG_LINES)} lines)")

    # ── Clean up cache ───────────────────────────────────────────────────────
    cache_dir = output_dir / ".cache"
    if cache_dir.exists():
        try:
            shutil.rmtree(str(cache_dir))
        except Exception:
            pass

    # ── Summary ───────────────────────────────────────────────────────────────
    _log(f"\n{'=' * 72}")
    _log(f"ASSEMBLY COMPLETE")
    _log(f"  Output: {output_dir}")
    _log(f"  Needed targets processed: {len(needed_targets)}")
    _log(f"  Diagnosis.txt: {'✓' if (output_dir / 'Diagnosis.txt').exists() else '✗'}")
    _log(f"  Logs.txt: {'✓' if (output_dir / 'Logs.txt').exists() else '✗'}")
    for key in sorted_keys:
        pi = output_dir / key / "projectinfo.txt"
        _log(f"  {key}/projectinfo.txt: {'✓' if pi.exists() else '✗'}")
    _log(f"{'=' * 72}")

    return 0


# ── CLI entry point ──────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Assemble diagnosis bundle: create projectinfo.txt for each missing version/loader combo."
    )
    parser.add_argument("--info-dir", required=True,
        help="Path to info-gather output directory (contains project.json, first_version/, etc.)")
    parser.add_argument("--diagnosis-dir", required=True,
        help="Path to diagnosis output directory (contains diagnosis.json, diagnosis.txt, downloads/)")
    parser.add_argument("--manifest", required=True,
        help="Path to version-manifest.json")
    parser.add_argument("--output-dir", required=True,
        help="Path for the final bundle output")
    args = parser.parse_args()

    sys.exit(assemble_bundle(
        info_dir=Path(args.info_dir),
        diagnosis_dir=Path(args.diagnosis_dir),
        manifest_path=Path(args.manifest),
        output_dir=Path(args.output_dir),
    ))


if __name__ == "__main__":
    main()
