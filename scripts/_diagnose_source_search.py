#!/usr/bin/env python3
"""
Full diagnosis of AI Source Search across all supported version+loader combos.

Triggers ALL combos simultaneously (max parallel), waits for each, validates
that actual .java source files were found (not just workflow success), then
writes a detailed summary.

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
# One representative version per range (the anchor version)
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

# Queries that should match in any Minecraft version
# Using broad terms that exist in every MC version
PROBE_QUERY = "class Minecraft,class MinecraftClient,class MinecraftServer,public class"


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
        "-f", "context_lines=2",
        "-f", "dump_full_class=no",
        check=False,
    )
    if result is None:
        return None
    time.sleep(3)
    runs = gh("run", "list", "--workflow=ai-source-search.yml",
              "--limit=1", "--json=databaseId,createdAt")
    if not runs:
        return None
    data = json.loads(runs)
    return data[0]["databaseId"] if data else None


def download_and_validate(run_id, version, loader, out_dir):
    """
    Download artifact and do deep validation:
    - Check java_count from search-info.txt
    - Check all-java-files.txt actually has entries
    - Check api-overview/ has render/event class lists
    - Check queries/ has actual matches
    Returns dict with full validation details.
    """
    artifact = f"ai-source-search-{version}-{loader}"
    out_dir.mkdir(parents=True, exist_ok=True)
    gh("run", "download", str(run_id), "-n", artifact,
       "-D", str(out_dir), check=False)

    result = {
        "version": version,
        "loader": loader,
        "run_id": run_id,
        "java_count": 0,
        "all_files_count": 0,
        "query_matches": 0,
        "event_classes": 0,
        "render_classes": 0,
        "api_overview_files": 0,
        "gradle_error": None,
        "status": "fail",
        "reason": "",
        "sample_files": [],
    }

    # 1. Check search-info.txt
    info = out_dir / "search-info.txt"
    if not info.exists():
        result["reason"] = "no artifact downloaded"
        return result

    info_text = info.read_text()
    for line in info_text.splitlines():
        if line.startswith("Java files:"):
            try:
                result["java_count"] = int(line.split(":")[1].strip())
            except ValueError:
                pass
        if "ERROR:" in line:
            result["gradle_error"] = line

    # 2. Check all-java-files.txt
    all_files = out_dir / "all-java-files.txt"
    if all_files.exists():
        lines = [l for l in all_files.read_text().splitlines() if l.strip()]
        result["all_files_count"] = len(lines)
        result["sample_files"] = lines[:10]  # first 10 for inspection

    # 3. Check query matches
    queries_dir = out_dir / "queries"
    if queries_dir.exists():
        total_matches = 0
        for qf in queries_dir.glob("*.txt"):
            content = qf.read_text()
            matches = content.count("===")
            total_matches += matches
        result["query_matches"] = total_matches

    # 4. Check api-overview
    overview_dir = out_dir / "api-overview"
    if overview_dir.exists():
        event_f = overview_dir / "event-classes.txt"
        render_f = overview_dir / "render-gui-classes.txt"
        if event_f.exists():
            result["event_classes"] = len([l for l in event_f.read_text().splitlines() if l.strip()])
        if render_f.exists():
            result["render_classes"] = len([l for l in render_f.read_text().splitlines() if l.strip()])
        result["api_overview_files"] = len(list(overview_dir.glob("full_*.java")))

    # 5. Check gradle log for errors
    gradle_log = out_dir / "gradle-output.log"
    if gradle_log.exists():
        log_text = gradle_log.read_text()
        if "BUILD FAILED" in log_text and not result["gradle_error"]:
            # Extract the failure reason
            for line in log_text.splitlines():
                if "Task '" in line and "not found" in line:
                    result["gradle_error"] = line.strip()
                    break
                if "FAILURE:" in line:
                    result["gradle_error"] = line.strip()
                    break

    # Determine pass/fail
    # PASS requires: java_count > 0 AND all_files_count > 0
    # A workflow can "succeed" but find 0 files — that's still a fail
    if result["java_count"] > 0 and result["all_files_count"] > 0:
        result["status"] = "pass"
        result["reason"] = (
            f"{result['java_count']} java files, "
            f"{result['query_matches']} query matches, "
            f"{result['event_classes']} event classes, "
            f"{result['render_classes']} render classes"
        )
    elif result["java_count"] > 0 and result["all_files_count"] == 0:
        result["status"] = "fail"
        result["reason"] = f"java_count={result['java_count']} but all-java-files.txt is empty (extraction bug)"
    elif result["gradle_error"]:
        result["status"] = "fail"
        result["reason"] = f"gradle error: {result['gradle_error']}"
    else:
        result["status"] = "fail"
        result["reason"] = "0 java files found — sources not extracted"

    return result


def main():
    ap = argparse.ArgumentParser(
        description="Diagnose AI Source Search for all version+loader combos (all parallel)"
    )
    ap.add_argument("--only-failed", action="store_true",
                    help="Only re-run combos that failed in a previous diagnosis")
    ap.add_argument("--loader", help="Only test this loader")
    ap.add_argument("--version", help="Only test this version")
    args = ap.parse_args()

    combos = list(ALL_COMBOS)
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

    RESULTS_DIR.mkdir(exist_ok=True)
    total = len(combos)
    print(f"Triggering ALL {total} version+loader combos simultaneously...")
    print("=" * 60)

    # Trigger all at once
    in_flight = {}  # run_id -> (version, loader, trigger_time)
    failed_triggers = []

    for version, loader in combos:
        print(f"  Triggering {version}+{loader}...", end=" ", flush=True)
        run_id = trigger_search(version, loader)
        if run_id:
            in_flight[run_id] = (version, loader, time.time())
            print(f"run {run_id}")
        else:
            failed_triggers.append((version, loader))
            print("TRIGGER FAILED")
        time.sleep(1)  # small delay to avoid rate limiting

    print(f"\nAll {len(in_flight)} runs triggered. Waiting for completion...")
    print("=" * 60)

    results = {}

    # Add trigger failures immediately
    for version, loader in failed_triggers:
        key = f"{version}+{loader}"
        results[key] = {
            "version": version, "loader": loader,
            "status": "fail", "reason": "workflow trigger failed",
            "java_count": 0, "all_files_count": 0,
            "query_matches": 0, "event_classes": 0, "render_classes": 0,
        }

    # Poll until all complete
    while in_flight:
        time.sleep(30)
        completed_ids = []

        for run_id, (version, loader, start) in list(in_flight.items()):
            data = gh("run", "view", str(run_id),
                      "--json=status,conclusion", check=False)
            if not data:
                continue
            d = json.loads(data)
            if d["status"] != "completed":
                continue

            elapsed = int(time.time() - start)
            conclusion = d.get("conclusion", "")
            print(f"  [{elapsed:4d}s] {version}+{loader} workflow={conclusion} — downloading...")

            out_dir = RESULTS_DIR / f"{version}-{loader}"
            result = download_and_validate(run_id, version, loader, out_dir)
            result["elapsed_s"] = elapsed
            result["workflow_conclusion"] = conclusion

            key = f"{version}+{loader}"
            results[key] = result

            status_icon = "✓" if result["status"] == "pass" else "✗"
            print(f"    {status_icon} java={result['java_count']} "
                  f"files={result['all_files_count']} "
                  f"matches={result['query_matches']} "
                  f"events={result['event_classes']} "
                  f"render={result['render_classes']}")
            if result["status"] == "fail":
                print(f"    REASON: {result['reason']}")

            completed_ids.append(run_id)

        for run_id in completed_ids:
            del in_flight[run_id]

        if in_flight:
            still_running = [f"{v}+{l}" for _, (v, l, _) in in_flight.items()]
            print(f"  Still waiting: {', '.join(still_running[:5])}"
                  + (f" +{len(still_running)-5} more" if len(still_running) > 5 else ""))

    # Save results
    prev_results_file.write_text(json.dumps(results, indent=2))

    # Print final summary
    print("\n" + "=" * 60)
    print("DIAGNOSIS SUMMARY")
    print("=" * 60)
    passed = {k: v for k, v in results.items() if v["status"] == "pass"}
    failed = {k: v for k, v in results.items() if v["status"] == "fail"}

    print(f"\nPASSED: {len(passed)}/{total}")
    print(f"FAILED: {len(failed)}/{total}")

    if passed:
        print("\n✓ PASSED (sources found):")
        for k in sorted(passed):
            v = passed[k]
            print(f"  {k:30s}  java={v['java_count']:5d}  "
                  f"events={v['event_classes']:3d}  "
                  f"render={v['render_classes']:3d}  "
                  f"matches={v['query_matches']:3d}")

    if failed:
        print("\n✗ FAILED (sources NOT found or broken):")
        for k in sorted(failed):
            v = failed[k]
            print(f"  {k:30s}  java={v['java_count']:5d}  "
                  f"REASON: {v['reason']}")

    # Sample files from a passing combo to show what was found
    if passed:
        sample_key = next(iter(sorted(passed)))
        sample = passed[sample_key]
        if sample.get("sample_files"):
            print(f"\nSample files from {sample_key}:")
            for f in sample["sample_files"][:5]:
                print(f"  {f}")

    print(f"\nFull results: {prev_results_file}")
    print(f"Artifacts:    {RESULTS_DIR}/")

    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
