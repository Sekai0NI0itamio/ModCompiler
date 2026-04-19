#!/usr/bin/env python3
"""
Generate the allowofflinetojoinlan zip bundle from the examples/ source tree.

Usage:
  python3 scripts/_generate_allowoffline_bundle.py
  python3 scripts/_generate_allowoffline_bundle.py --failed-only
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES_DIR = ROOT / "examples" / "allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge"
INCOMING_DIR = ROOT / "incoming"
ZIP_ALL = INCOMING_DIR / "allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge.zip"
ZIP_FAILED = INCOMING_DIR / "allowofflinetojoinlan-failed-only.zip"
RUNS_DIR = ROOT / "ModCompileRuns"


def get_all_mod_dirs() -> list[Path]:
    """Return all mod source directories sorted."""
    return sorted(
        d for d in EXAMPLES_DIR.iterdir()
        if d.is_dir() and (d / "mod.txt").exists() and (d / "version.txt").exists()
    )


def get_failed_slugs() -> set[str]:
    """Read the latest ModCompileRuns folder that contains allowofflinetojoinlan mods."""
    runs = sorted(RUNS_DIR.iterdir()) if RUNS_DIR.exists() else []
    if not runs:
        print("No ModCompileRuns found — cannot determine failed targets.", file=sys.stderr)
        sys.exit(1)

    # Find the most recent run that has allowofflinetojoinlan mods
    run_dir = None
    for r in reversed(runs):
        mods_dir = r / "artifacts" / "all-mod-builds" / "mods"
        if mods_dir.exists():
            mods = list(mods_dir.iterdir())
            if any("allowofflinetojoinlan" in m.name for m in mods):
                run_dir = r
                break

    if run_dir is None:
        print("No run with allowofflinetojoinlan mods found.", file=sys.stderr)
        sys.exit(1)

    mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
    if not mods_dir.exists():
        print(f"No mods dir in {run_dir}", file=sys.stderr)
        sys.exit(1)

    failed = set()
    for mod_dir in mods_dir.iterdir():
        rf = mod_dir / "result.json"
        if not rf.exists():
            failed.add(mod_dir.name)
            continue
        status = json.loads(rf.read_text()).get("status")
        if status != "success":
            failed.add(mod_dir.name)

    print(f"Latest run: {run_dir.name}")
    print(f"Failed slugs ({len(failed)}): {', '.join(sorted(failed))}")
    return failed


def slug_for_dir(mod_dir: Path) -> str | None:
    """
    Derive the build slug from version.txt + mod.txt.
    Returns e.g. 'allowofflinetojoinlan-forge-1-16-5' or None if unparseable.
    """
    version_txt = mod_dir / "version.txt"
    mod_txt = mod_dir / "mod.txt"
    if not version_txt.exists() or not mod_txt.exists():
        return None

    kv: dict[str, str] = {}
    for path in (version_txt, mod_txt):
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                k, _, v = line.partition("=")
                kv[k.strip()] = v.strip()

    mod_id = kv.get("mod_id", "")
    loader = kv.get("loader", "")
    mc_ver = kv.get("minecraft_version", "")

    if not mod_id or not loader or not mc_ver:
        return None

    # For a range like "1.21.6-1.21.8", expand to individual slugs
    # For a single version like "1.12.2", just one slug
    slugs = []
    if "-" in mc_ver:
        # Could be a range like "1.21.6-1.21.8" or a single like "1.8.9"
        parts = mc_ver.split("-")
        # Detect range: both parts look like version numbers
        if len(parts) == 2 and all(p.replace(".", "").isdigit() for p in parts):
            start = parts[0].split(".")
            end = parts[1].split(".")
            # Same major.minor = same-minor range
            if start[0] == end[0] and start[1] == end[1]:
                # Expand patch range
                start_patch = int(start[2]) if len(start) > 2 else 0
                end_patch = int(end[2]) if len(end) > 2 else 0
                for patch in range(start_patch, end_patch + 1):
                    ver = f"{start[0]}.{start[1]}.{patch}" if patch > 0 else f"{start[0]}.{start[1]}"
                    slug = f"{mod_id}-{loader}-{ver}".replace(".", "-")
                    slugs.append(slug)
            else:
                # Cross-minor range — just use the raw version string as slug base
                slug = f"{mod_id}-{loader}-{mc_ver}".replace(".", "-")
                slugs.append(slug)
        else:
            slug = f"{mod_id}-{loader}-{mc_ver}".replace(".", "-")
            slugs.append(slug)
    else:
        slug = f"{mod_id}-{loader}-{mc_ver}".replace(".", "-")
        slugs.append(slug)

    return slugs


def add_dir_to_zip(zf: zipfile.ZipFile, mod_dir: Path) -> int:
    """Add all files from mod_dir into the zip. Returns file count."""
    count = 0
    for fpath in mod_dir.rglob("*"):
        if fpath.is_file():
            arcname = str(fpath.relative_to(EXAMPLES_DIR))
            zf.write(fpath, arcname)
            count += 1
    return count


def build_zip(mod_dirs: list[Path], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        total = 0
        for mod_dir in mod_dirs:
            n = add_dir_to_zip(zf, mod_dir)
            total += n
            print(f"  + {mod_dir.name}  ({n} files)")
    print(f"\nWrote {out_path}  ({total} files total, {len(mod_dirs)} mod dirs)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true",
                        help="Only include mod dirs that had failures in the latest run")
    args = parser.parse_args()

    all_dirs = get_all_mod_dirs()
    print(f"Found {len(all_dirs)} mod dirs in examples/")

    if args.failed_only:
        failed_slugs = get_failed_slugs()
        selected = []
        for mod_dir in all_dirs:
            slugs = slug_for_dir(mod_dir)
            if slugs is None:
                continue
            # Include this dir if ANY of its expanded slugs failed
            if any(s in failed_slugs for s in slugs):
                selected.append(mod_dir)
                print(f"  INCLUDE (failed): {mod_dir.name}  slugs={slugs}")
            else:
                print(f"  skip (passed):    {mod_dir.name}")
        out_path = ZIP_FAILED
    else:
        selected = all_dirs
        out_path = ZIP_ALL

    print(f"\nBuilding zip with {len(selected)} mod dirs -> {out_path}")
    build_zip(selected, out_path)


if __name__ == "__main__":
    main()
