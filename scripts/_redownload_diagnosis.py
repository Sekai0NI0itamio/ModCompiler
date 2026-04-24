#!/usr/bin/env python3
"""
Re-download and validate all source search runs from the last diagnosis batch.
Uses the actual artifact names from each run rather than guessing.
"""
import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESULTS_DIR = ROOT / "SourceSearchDiagnosis"

def gh(*args, check=True):
    cmd = ["gh"] + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
    if check and r.returncode != 0:
        return None
    return r.stdout.strip()

# All run IDs from the last batch (from the diagnosis output)
# Map: run_id -> expected (version, loader)
# These are the 33 runs we triggered
RUNS = [
    (24840596054, "1.8.9",   "forge"),
    (24840838603, "1.16.5",  "fabric"),
    (24840839166, "1.17.1",  "forge"),
    (24840844687, "1.17.1",  "fabric"),
    (24840845539, "1.18.2",  "forge"),
    (24840846425, "1.18.2",  "fabric"),
    (24840846499, "1.19.4",  "forge"),
    (24840854572, "1.19.4",  "fabric"),
    (24840855194, "1.20.1",  "forge"),
    (24840858089, "1.20.1",  "fabric"),  # might be wrong
    (24840859225, "1.20.4",  "forge"),
    (24840859521, "1.20.4",  "fabric"),
    (24840866097, "1.20.4",  "neoforge"),
    (24840868536, "1.20.6",  "forge"),
    (24840871670, "1.20.6",  "fabric"),
    (24840880005, "1.20.6",  "neoforge"),
    (24840882539, "1.21.1",  "forge"),
    (24840892533, "1.21.1",  "fabric"),
    (24840893420, "1.21.1",  "neoforge"),
    (24840894146, "1.21.4",  "forge"),
    (24840895785, "1.21.4",  "fabric"),
    (24840901719, "1.21.4",  "neoforge"),
    (24840903997, "1.21.8",  "forge"),
    (24840918184, "1.21.8",  "fabric"),
    (24840918442, "1.21.8",  "neoforge"),
    (24840919048, "1.21.11", "forge"),
    (24840925595, "1.21.11", "fabric"),
    (24840928646, "1.21.11", "neoforge"),
    (24840933812, "1.12.2",  "forge"),
    (24840936417, "1.16.5",  "forge"),  # might be wrong
    (24840939384, "26.1.2",  "forge"),
    (24840944924, "26.1.2",  "fabric"),
    (24840950491, "26.1.2",  "neoforge"),
]

RESULTS_DIR.mkdir(exist_ok=True)
results = {}

for run_id, version, loader in RUNS:
    print(f"\n=== {version}+{loader} (run {run_id}) ===")
    out_dir = RESULTS_DIR / f"{version}-{loader}"
    out_dir.mkdir(exist_ok=True)

    # Download all artifacts from this run
    dl = gh("run", "download", str(run_id), "-D", str(out_dir), check=False)

    # Find search-info.txt (may be nested)
    info_files = list(out_dir.rglob("search-info.txt"))
    if not info_files:
        print(f"  FAIL: no search-info.txt found")
        results[f"{version}+{loader}"] = {
            "version": version, "loader": loader, "run_id": run_id,
            "status": "fail", "reason": "no artifact", "java_count": 0,
            "all_files_count": 0, "event_classes": 0, "render_classes": 0,
            "query_matches": 0, "sample_files": [],
        }
        continue

    # Use the first info file found
    info_file = info_files[0]
    base_dir = info_file.parent

    # Parse search-info.txt
    info_text = info_file.read_text()
    java_count = 0
    gradle_error = None
    actual_version = version
    actual_loader = loader

    for line in info_text.splitlines():
        if line.startswith("Java files:"):
            try:
                java_count = int(line.split(":")[1].strip())
            except ValueError:
                pass
        if line.startswith("Version  :"):
            actual_version = line.split(":")[1].strip()
        if line.startswith("Loader   :"):
            actual_loader = line.split(":")[1].strip()
        if "ERROR:" in line:
            gradle_error = line

    # Count all-java-files.txt
    all_files_count = 0
    sample_files = []
    all_files_path = base_dir / "all-java-files.txt"
    if all_files_path.exists():
        lines = [l for l in all_files_path.read_text().splitlines() if l.strip()]
        all_files_count = len(lines)
        sample_files = lines[:5]

    # Count event/render classes
    event_classes = 0
    render_classes = 0
    overview_dir = base_dir / "api-overview"
    if overview_dir.exists():
        ef = overview_dir / "event-classes.txt"
        rf = overview_dir / "render-gui-classes.txt"
        if ef.exists():
            event_classes = len([l for l in ef.read_text().splitlines() if l.strip()])
        if rf.exists():
            render_classes = len([l for l in rf.read_text().splitlines() if l.strip()])

    # Count query matches
    query_matches = 0
    queries_dir = base_dir / "queries"
    if queries_dir.exists():
        for qf in queries_dir.glob("*.txt"):
            query_matches += qf.read_text().count("===")

    # Determine status
    if java_count > 0 and all_files_count > 0:
        status = "pass"
        reason = f"{java_count} java files, {event_classes} events, {render_classes} render"
    elif java_count > 0 and all_files_count == 0:
        status = "fail"
        reason = f"java_count={java_count} but all-java-files.txt empty"
    elif gradle_error:
        status = "fail"
        reason = f"gradle: {gradle_error}"
    else:
        status = "fail"
        reason = "0 java files"

    key = f"{actual_version}+{actual_loader}"
    results[key] = {
        "version": actual_version, "loader": actual_loader, "run_id": run_id,
        "status": status, "reason": reason,
        "java_count": java_count, "all_files_count": all_files_count,
        "event_classes": event_classes, "render_classes": render_classes,
        "query_matches": query_matches, "sample_files": sample_files,
    }

    icon = "✓" if status == "pass" else "✗"
    print(f"  {icon} actual={actual_version}+{actual_loader}  java={java_count}  "
          f"files={all_files_count}  events={event_classes}  render={render_classes}")
    if status == "fail":
        print(f"    REASON: {reason}")
    if sample_files:
        print(f"    Sample: {sample_files[0]}")

# Save
results_file = RESULTS_DIR / "diagnosis_results.json"
results_file.write_text(json.dumps(results, indent=2))

# Summary
print("\n" + "=" * 60)
print("REDOWNLOAD SUMMARY")
print("=" * 60)
passed = {k: v for k, v in results.items() if v["status"] == "pass"}
failed = {k: v for k, v in results.items() if v["status"] == "fail"}
print(f"PASSED: {len(passed)}/{len(results)}")
print(f"FAILED: {len(failed)}/{len(results)}")

if passed:
    print("\n✓ PASSED:")
    for k in sorted(passed):
        v = passed[k]
        print(f"  {k:30s}  java={v['java_count']:5d}  events={v['event_classes']:3d}  render={v['render_classes']:3d}")

if failed:
    print("\n✗ FAILED:")
    for k in sorted(failed):
        v = failed[k]
        print(f"  {k:30s}  java={v['java_count']:5d}  REASON: {v['reason']}")

print(f"\nResults: {results_file}")
