#!/usr/bin/env python3
"""Check which MC versions/loaders are already covered for Seed Protect."""
import json
from pathlib import Path

bundle_dir = Path("/tmp/seed-protect-bundle")

# Load index
with open(bundle_dir / "index.json") as f:
    index_data = json.load(f)

# index_data is a dict with a "versions" key
versions = index_data.get("versions", [])
covered = {}
for v in versions:
    if not isinstance(v, dict):
        continue
    for mc in v.get("game_versions", []):
        for loader in v.get("loaders", []):
            covered.setdefault(mc, set()).add(loader)

print("=== COVERED (mc_version -> loaders) ===")
for mc in sorted(covered.keys()):
    print(f"  {mc}: {sorted(covered[mc])}")

# Full target matrix from version-manifest.json
targets = {
    "1.8.9":   ["forge"],
    "1.12.2":  ["forge"],
    "1.16.5":  ["forge", "fabric"],
    "1.17.1":  ["forge", "fabric"],
    "1.18":    ["forge", "fabric"],
    "1.18.1":  ["forge", "fabric"],
    "1.18.2":  ["forge", "fabric"],
    "1.19":    ["forge", "fabric"],
    "1.19.1":  ["forge", "fabric"],
    "1.19.2":  ["forge", "fabric"],
    "1.19.3":  ["forge", "fabric"],
    "1.19.4":  ["forge", "fabric"],
    "1.20.1":  ["forge", "fabric"],
    "1.20.2":  ["forge", "fabric", "neoforge"],
    "1.20.3":  ["forge", "fabric"],
    "1.20.4":  ["forge", "fabric", "neoforge"],
    "1.20.5":  ["fabric", "neoforge"],
    "1.20.6":  ["forge", "fabric", "neoforge"],
    "1.21":    ["forge", "neoforge"],
    "1.21.1":  ["forge", "fabric", "neoforge"],
    "1.21.2":  ["neoforge"],
    "1.21.3":  ["forge", "neoforge"],
    "1.21.4":  ["forge", "neoforge"],
    "1.21.5":  ["forge", "neoforge"],
    "1.21.6":  ["forge", "neoforge"],
    "1.21.7":  ["forge", "neoforge"],
    "1.21.8":  ["forge", "fabric", "neoforge"],
    "1.21.9":  ["forge", "neoforge"],
    "1.21.10": ["forge", "neoforge"],
    "1.21.11": ["forge", "fabric", "neoforge"],
}

print("\n=== MISSING (not yet on Modrinth) ===")
missing = []
for mc, loaders in sorted(targets.items()):
    for loader in loaders:
        already = loader in covered.get(mc, set())
        if not already:
            print(f"  MISSING: {mc} {loader}")
            missing.append((mc, loader))

print(f"\nTotal missing: {len(missing)}")
print("\n=== ALREADY COVERED ===")
for mc, loaders in sorted(targets.items()):
    for loader in loaders:
        if loader in covered.get(mc, set()):
            print(f"  OK: {mc} {loader}")
