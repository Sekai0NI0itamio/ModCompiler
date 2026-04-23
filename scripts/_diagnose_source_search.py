#!/usr/bin/env python3
"""
Full diagnosis of AI Source Search across all supported version+loader combos.

Triggers one search per version+loader, waits for each, records pass/fail,
then writes a summary. Run this once to find which combos are broken.

Usage:
    python3 scripts/_diagnose_source_search.py
    python3 scripts/_diagnose_source_search.py --only-failed   # re-run only previously failed
    python3 scripts/_diagnose_source_search.py --loader forge  # only one loader
    python3 scripts/_diagnose_source_search.py --version 1.21.1 --loader forge  # single combo
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESULTS_DIR = ROOT / "SourceSearchDiagnosis"

# All version+loader combos from version-manifest.json
ALL_COMBOS = [
    # (version, loader)
    ("1.8.9",   "forge"),
    ("1.12.2",  "forge"),
    ("1.16.5",  "forge"),
    ("1.16.5",  "fabric"),
    ("1.17.1",  "forge"),
    ("1.17.1",  "fabric"),
    ("1.18.2",  "forge"),
    ("1.18.2",  "fabric"),
    ("1.19.4",  "forge"),
    ("1.19.4",  "fabric"),
    ("1.20.1",  "forge"),
    ("1.20.1",  "fabric"),
    ("1.20.4",  "forge"),
    ("1.20.4",  "fabric"),
    ("1.20.4",  "neoforge"),
    ("1.20.6",  "forge"),
    ("1.20.6",  "fabric"),
    ("1.20.6",  "neoforge"),
    ("1.21.1",  "forge"),
    ("1.21.1",  "fabric"),
    ("1.21.1",  "neoforge"),
    ("1.21.4",  "forge"),
    ("1.21.4",  "fabric"),
    ("1.21.4",  "neoforge"),
    ("1.21.8",  "forge"),
    ("1.21.8",  "fabric"),
    ("1.21.8",  "neoforge"),
    ("1.21.11", "forge"),
    ("1.21.11", "fabric"),
    ("1.21.11", "neoforge"),
    ("26.1.2",  "forge"),
    ("26.1.2",  "fabric"),
    ("26.1.2",  "neoforge"),
]

# A query that should match in any Minecraft version
PROBE_QUERY = "class Minecraft,class MinecraftClient,class MinecraftServer"


def gh(*args, capture=True, check=True):
    cmd = ["gh"] + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
    if check and r.returncode != 0:
        return None
    return r.stdout.strip() if capture else r.returncode == 0


def trigger_search(version, loader):
    """Trigger one search workflow run. Returns run_id or None."""
    result = gh(
        "workflow", "run", "ai-source-search.yml",
        "-f", f"minecraft_version={version}",
        "-f", f"loader={loader}",
        "-f", f"queries={PROBE_QUERY}",
        "-f", "file_patterns=*.java",
        "-f", "context_lines=3",
        "-f", "dump_full_class=no",
        check=False,
    )
    if result is None:
        return None
    time.sleep(5)
    runs = gh("run", "list", "--workflow=ai-source-search.yml",
              "--limit=1", "--json=databaseId,createdAt")
    if not runs:
        return None
    data = json.loads(runs)
    return data[0]["databaseId"] if data else None


def wait_for_run(run_id, timeout=3600):
    """Wait for a run to complete. Returns (status, conclusion, java_count)."""
    start = time.time()
    while time.time() - start < timeout:
        data = gh("run", "view", str(run_id),
                  "--json=status,conclusion", check=False)
        if not data:
            time.sleep(15)
            continue
        d = json.loads(data)
        if d["status"] == "completed":
            return d["status"], d.get("conclusion", ""), None
        time.sleep(20)
    return "timeout", "", None


def download_and_check(run_id, version, loader, out_dir):
    """Download artifact and check java_count from search-info.txt."""
    artifact = f"ai-source-search-{version}-{loader}"
    out_dir.mkdir(parents=True, exist_ok=True)
    gh("run", "download", str(run_id), "-n", artifact,
       "-D", str(out_dir), check=False)
    info = out_dir / "search-info.txt"
    if not info.exists():
        return 0, "no artifact"
    text = info.read_text()
    for line in text.splitlines():
        if line.startswith("Java files:"):
            try:
                count = int(line.split(":")[1].strip())
                return count, text
            except ValueError:
                pass
    return 0, text


def main():
    ap = argparse.ArgumentParser(description="Diagnose AI Source Search for all version+loader combos")
    ap.add_argument("--only-failed", action="store_true",
                    help="Only re-run combos that failed in a previous diagnosis")
    ap.add_argument("--loader", help="Only test this loader")
    ap.add_argument("--version", help="Only test this version")
    ap.add_argument("--parallel", type=int, default=3,
                    help="How many runs to have in-flight at once (default: 3)")
    args = ap.parse_args()

    combos = ALL_COMBOS
    if args.loader:
        combos = [(v, l) for v, l in combos if l == args.loader]
    if args.version:
        combos = [(v, l) for v, l in combos if v == args.version]

    # Load previous results if --only-failed
    prev_results_file = RESULTS_DIR / "diagnosis_results.json"
    if args.only_failed and prev_results_file.exists():
        prev = json.loads(prev_results_file.read_text())
        combos = [(v, l) for v, l in combos
                  if prev.get(f"{v}+{l}", {}).get("status") != "pass"]
        print(f"Re-running {len(combos)} previously failed combos")

    if not combos:
        print("No combos to test.")
        return

    print(f"Testing {len(combos)} version+loader combos (parallel={args.parallel})")
    print("=" * 60)

    RESULTS_DIR.mkdir(exist_ok=True)
    results = {}
    in_flight = {}  # run_id -> (version, loader, start_time)

    combo_iter = iter(combos)
    done = 0
    total = len(combos)

    def fill_queue():
        while len(in_flight) < args.parallel:
            try:
                version, loader = next(combo_iter)
            except StopIteration:
                break
            print(f"  Triggering {version}+{loader}...")
            run_id = trigger_search(version, loader)
            if run_id:
                in_flight[run_id] = (version, loader, time.time())
                print(f"    → run {run_id}")
            else:
                results[f"{version}+{loader}"] = {
                    "version": version, "loader": loader,
                    "status": "fail", "reason": "trigger failed",
                    "java_count": 0,
                }
                print(f"    → TRIGGER FAILED")

    fill_queue()

    while in_flight:
        time.sleep(20)
        completed = []
        for run_id, (version, loader, start) in list(in_flight.items()):
            data = gh("run", "view", str(run_id),
                      "--json=status,conclusion", check=False)
            if not data:
                continue
            d = json.loads(data)
            if d["status"] != "completed":
                elapsed = int(time.time() - start)
                print(f"  [{elapsed:4d}s] {version}+{loader} still running...")
                continue

            conclusion = d.get("conclusion", "")
            elapsed = int(time.time() - start)
            print(f"  [{elapsed:4d}s] {version}+{loader} → {conclusion}")

            # Download and check
            out_dir = RESULTS_DIR / f"{version}-{loader}"
            java_count, info_text = download_and_check(run_id, version, loader, out_dir)

            key = f"{version}+{loader}"
            if conclusion == "success" and java_count > 0:
                status = "pass"
                reason = f"{java_count} java files found"
            elif conclusion == "success" and java_count == 0:
                status = "fail"
                reason = "workflow succeeded but 0 java files found"
            else:
                status = "fail"
                reason = f"workflow conclusion={conclusion}"

            results[key] = {
                "version": version, "loader": loader,
                "status": status, "reason": reason,
                "java_count": java_count,
                "run_id": run_id,
                "elapsed_s": elapsed,
            }
            print(f"    java_count={java_count}  status={status}")
            completed.append(run_id)
            done += 1

        for run_id in completed:
            del in_flight[run_id]

        fill_queue()

    # Save results
    prev_results_file.write_text(json.dumps(results, indent=2))

    # Print summary
    print("\n" + "=" * 60)
    print("DIAGNOSIS SUMMARY")
    print("=" * 60)
    passed = [k for k, v in results.items() if v["status"] == "pass"]
    failed = [k for k, v in results.items() if v["status"] == "fail"]
    print(f"PASSED: {len(passed)}/{total}")
    print(f"FAILED: {len(failed)}/{total}")

    if passed:
        print("\n✓ PASSED:")
        for k in sorted(passed):
            v = results[k]
            print(f"  {k:30s}  {v['java_count']:5d} java files")

    if failed:
        print("\n✗ FAILED:")
        for k in sorted(failed):
            v = results[k]
            print(f"  {k:30s}  {v['reason']}")

    print(f"\nFull results: {prev_results_file}")
    print(f"Artifacts:    {RESULTS_DIR}/")


if __name__ == "__main__":
    main()
