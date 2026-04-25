#!/usr/bin/env python3
"""
Check which version+loader combinations are present in
DecompiledMinecraftSourceCode/ and which are still missing.

Run from the repo root:
    python3 scripts/_check_decompiled_status.py
"""
import json
from pathlib import Path

DEST = Path("DecompiledMinecraftSourceCode")
MANIFEST = Path("version-manifest.json")

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

expected = {}
for entry in manifest.get("ranges", []):
    for loader, cfg in entry.get("loaders", {}).items():
        versions = cfg.get("supported_versions")
        if not versions:
            versions = [cfg.get("anchor_version", entry.get("min_version", ""))]
        for v in versions:
            slug = f"{v}-{loader}"
            expected[slug] = {"version": v, "loader": loader}

present = {}
missing = []

for slug in sorted(expected):
    folder = DEST / slug
    java_files = list(folder.rglob("*.java")) if folder.is_dir() else []
    count = len(java_files)
    if count > 0:
        present[slug] = count
    else:
        missing.append(slug)

print(f"{'='*55}")
print(f"  DecompiledMinecraftSourceCode status")
print(f"{'='*55}")
print(f"  Present : {len(present)}/{len(expected)}")
print(f"  Missing : {len(missing)}/{len(expected)}")
print()

if present:
    print("✅ Present:")
    for slug, count in sorted(present.items()):
        print(f"   {slug:<30} {count:>5} .java files")

print()

if missing:
    print("❌ Missing (need to decompile):")
    for slug in missing:
        print(f"   {slug}")
else:
    print("✅ All versions present — nothing to decompile!")
