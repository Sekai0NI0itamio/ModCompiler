#!/usr/bin/env python3
"""Read build errors from the latest ModCompileRuns folder."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
runs = sorted((ROOT / "ModCompileRuns").iterdir())
run_dir = runs[-1]
mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"

print(f"Run: {run_dir.name}\n")

failed = []
succeeded = []
for mod_dir in sorted(mods_dir.iterdir()):
    rf = mod_dir / "result.json"
    if not rf.exists():
        failed.append(mod_dir.name)
        continue
    status = json.loads(rf.read_text()).get("status")
    if status == "success":
        succeeded.append(mod_dir.name)
    else:
        failed.append(mod_dir.name)

print(f"SUCCESS ({len(succeeded)}): {', '.join(succeeded)}\n")
print(f"FAILED ({len(failed)}):")

for slug in failed:
    log = mods_dir / slug / "build.log"
    if not log.exists():
        print(f"\n=== {slug}: no build.log ===")
        continue
    lines = log.read_text().splitlines()
    errors = []
    seen = set()
    for i, line in enumerate(lines):
        if "error:" in line.lower():
            for j in range(max(0, i - 1), min(len(lines), i + 5)):
                if j not in seen:
                    errors.append(lines[j])
                    seen.add(j)
            errors.append("---")
    print(f"\n=== {slug} ===")
    print("\n".join(errors[:30]))
