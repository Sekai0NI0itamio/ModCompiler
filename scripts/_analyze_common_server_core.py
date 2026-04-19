#!/usr/bin/env python3
"""Analyze the common-server-core bundle to find ghost shells and missing versions."""
import json
from pathlib import Path

BUNDLE = Path("/tmp/common-server-core-bundle")

# Load index
index = json.loads((BUNDLE / "index.json").read_text())
versions = index["versions"]

print(f"Total versions on Modrinth: {len(versions)}\n")

# Check jar sizes and decompile results
print("=== JAR SIZE ANALYSIS ===")
shells = []
real = []
for v in versions:
    vid = v["id"]
    vdir = BUNDLE / "versions" / vid
    jar_name = v.get("primary_jar", "")
    jar_path = vdir / jar_name if jar_name else None
    size = jar_path.stat().st_size if jar_path and jar_path.exists() else 0

    # Check decompile result
    result_file = vdir / "decompiled" / "result.json"
    classes = 0
    if result_file.exists():
        try:
            r = json.loads(result_file.read_text())
            classes = r.get("class_count", 0)
        except:
            pass

    mc = v.get("game_versions", [])
    loader = v.get("loaders", [])
    status = "SHELL" if (size < 5000 or classes == 0) else "OK"
    if status == "SHELL":
        shells.append(v)
    else:
        real.append(v)

    print(f"  [{v['id']}] mc={','.join(mc)} loader={','.join(loader)} size={size:,}B classes={classes} → {status}")

print(f"\nShells: {len(shells)}")
print(f"Real:   {len(real)}")

# What versions/loaders exist on Modrinth
print("\n=== EXISTING COVERAGE ===")
existing = set()
for v in versions:
    for mc in v.get("game_versions", []):
        for loader in v.get("loaders", []):
            existing.add((mc, loader))

# What the repo supports
SUPPORTED = [
    ("1.8.9",  ["forge"]),
    ("1.12.2", ["forge"]),
    ("1.16.5", ["forge", "fabric"]),
    ("1.17.1", ["forge", "fabric"]),
    ("1.18",   ["forge", "fabric"]),
    ("1.18.1", ["forge", "fabric"]),
    ("1.18.2", ["forge", "fabric"]),
    ("1.19",   ["forge", "fabric"]),
    ("1.19.1", ["forge", "fabric"]),
    ("1.19.2", ["forge", "fabric"]),
    ("1.19.3", ["forge", "fabric"]),
    ("1.19.4", ["forge", "fabric"]),
    ("1.20.1", ["forge", "fabric"]),
    ("1.20.2", ["forge", "fabric"]),
    ("1.20.3", ["forge", "fabric"]),
    ("1.20.4", ["forge", "fabric"]),
    ("1.20.5", ["forge", "fabric"]),
    ("1.20.6", ["forge", "fabric"]),
    ("1.21",   ["forge", "fabric"]),
    ("1.21.1", ["forge", "fabric"]),
    ("1.21.2", ["forge", "fabric"]),
    ("1.21.3", ["forge", "fabric"]),
    ("1.21.4", ["forge", "fabric"]),
    ("1.21.5", ["forge", "fabric"]),
    ("1.21.6", ["forge", "fabric"]),
    ("1.21.7", ["forge", "fabric"]),
    ("1.21.8", ["forge", "fabric"]),
    ("1.21.9", ["forge", "fabric"]),
    ("1.21.10",["forge", "fabric"]),
    ("1.21.11",["forge", "fabric"]),
]

print("\n=== MISSING VERSIONS (in repo manifest but not on Modrinth) ===")
missing = []
for mc, loaders in SUPPORTED:
    for loader in loaders:
        if (mc, loader) not in existing:
            missing.append((mc, loader))
            print(f"  MISSING: {mc} {loader}")

print(f"\nTotal missing: {len(missing)}")
print(f"Total shells:  {len(shells)}")
print(f"Total to fix:  {len(missing) + len(shells)}")
