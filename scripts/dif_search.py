#!/usr/bin/env python3
"""
dif_search.py — Documentary of Issues and Fixes (DIF) Search Tool
==================================================================
Local NLP search tool for the DIF database.

Usage
-----
  # Natural language search
  python3 scripts/dif_search.py "farmland trample event neoforge 26.1"
  python3 scripts/dif_search.py "modrinth project not found publish failed"
  python3 scripts/dif_search.py "cannot find symbol EventBusSubscriber"

  # Search with more results
  python3 scripts/dif_search.py "forge 1.21 overlay api" --top 10

  # Match a build log file against DIF
  python3 scripts/dif_search.py --match-log path/to/build.log

  # List all DIF entries
  python3 scripts/dif_search.py --list

  # Create a new DIF entry interactively
  python3 scripts/dif_search.py --create

  # Scan a ModCompileRuns directory for DIF matches
  python3 scripts/dif_search.py --scan-run ModCompileRuns/run-20260426-005930
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from dif_core import (
    DIF_DIR,
    DifEngine,
    extract_missing_symbols,
    match_errors_to_dif,
    print_search_results,
    search_dif,
    _render_dif_file,
)


def cmd_search(query: str, top_n: int) -> None:
    results = search_dif(query, top_n=top_n)
    print_search_results(results, query=query)


def cmd_match_log(log_path: Path) -> None:
    log_text = log_path.read_text(encoding="utf-8", errors="replace")
    symbols = extract_missing_symbols(log_text)
    print(f"\nExtracted symbols from log: {', '.join(sorted(symbols)) or '(none)'}")

    results = match_errors_to_dif(log_text)
    if not results:
        print("\nNo DIF matches found for this build log.")
        return

    print(f"\nDIF matches for {log_path.name}:")
    print_search_results(results, query=f"build log: {log_path.name}")


def cmd_scan_run(run_dir: Path) -> None:
    mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
    if not mods_dir.exists():
        print(f"No mods directory found at {mods_dir}")
        return

    print(f"\nScanning {run_dir.name} for DIF matches...\n")
    found_any = False

    for mod_dir in sorted(mods_dir.iterdir()):
        result_file = mod_dir / "result.json"
        build_log = mod_dir / "build.log"
        if not result_file.exists() or not build_log.exists():
            continue
        try:
            result = json.loads(result_file.read_text(encoding="utf-8"))
        except Exception:
            continue
        if result.get("status") == "success":
            continue

        log_text = build_log.read_text(encoding="utf-8", errors="replace")
        matches = match_errors_to_dif(log_text)
        if not matches:
            continue

        found_any = True
        print(f"{'─' * 60}")
        print(f"FAILED: {mod_dir.name}")
        symbols = extract_missing_symbols(log_text)
        if symbols:
            print(f"  Missing symbols: {', '.join(sorted(symbols))}")
        print(f"  DIF matches ({len(matches)}):")
        for score, entry in matches[:3]:
            pct = int(score * 100)
            print(f"    {pct}%  [{entry.id}] {entry.title}")
            print(f"         → {entry.path.relative_to(ROOT)}")
        print()

    if not found_any:
        print("No failed builds with DIF matches found.")


def cmd_list() -> None:
    engine = DifEngine()
    entries = engine.entries()
    if not entries:
        print(f"No DIF entries found in {DIF_DIR}")
        return
    print(f"\nDIF Database — {len(entries)} entries in {DIF_DIR.relative_to(ROOT)}\n")
    for e in entries:
        tags = f"  [{', '.join(e.tags[:4])}]" if e.tags else ""
        vers = f"  versions: {', '.join(e.versions[:3])}" if e.versions else ""
        print(f"  {e.id:<40} {e.title[:50]}{tags}{vers}")
    print()


def cmd_create() -> None:
    print("\nCreate a new DIF entry")
    print("=" * 40)

    entry_id = input("ID (e.g. FORGE-26-EVENTBUSSUBSCRIBER): ").strip()
    if not entry_id:
        print("ID is required.")
        return

    title = input("Title (short description): ").strip()
    tags_raw = input("Tags (comma-separated, e.g. forge,compile-error,26.1): ").strip()
    tags = [t.strip() for t in tags_raw.split(",") if t.strip()]
    versions_raw = input("Versions (comma-separated, e.g. 26.1,26.1.1,26.1.2): ").strip()
    versions = [v.strip() for v in versions_raw.split(",") if v.strip()]
    loaders_raw = input("Loaders (comma-separated, e.g. forge,neoforge): ").strip()
    loaders = [l.strip() for l in loaders_raw.split(",") if l.strip()]
    symbols_raw = input("Symbols (comma-separated class names that were missing): ").strip()
    symbols = [s.strip() for s in symbols_raw.split(",") if s.strip()]
    error_patterns_raw = input("Error patterns (comma-separated regex, optional): ").strip()
    error_patterns = [p.strip() for p in error_patterns_raw.split(",") if p.strip()]

    print("\nNow enter the body (Issue/Error/Root Cause/Fix sections).")
    print("Type END on a line by itself when done.\n")
    body_lines = []
    while True:
        line = input()
        if line.strip() == "END":
            break
        body_lines.append(line)
    body = "\n".join(body_lines)

    front = {
        "id": entry_id,
        "title": title,
        "tags": tags,
        "versions": versions,
        "loaders": loaders,
        "symbols": symbols,
        "error_patterns": error_patterns,
    }

    DIF_DIR.mkdir(exist_ok=True)
    path = DIF_DIR / f"{entry_id.lower().replace(' ', '-')}.md"
    engine = DifEngine()
    entry = engine.create_entry(path, front, body)
    print(f"\n✓ Created: {path.relative_to(ROOT)}")
    print(f"  ID: {entry.id}")
    print(f"  Title: {entry.title}")


def main() -> None:
    ap = argparse.ArgumentParser(
        description="DIF — Documentary of Issues and Fixes search tool",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("query", nargs="?", default="",
                    help="Natural language search query")
    ap.add_argument("--top", type=int, default=10,
                    help="Number of results to return (default: 10, always shows top 3 in full)")
    ap.add_argument("--match-log", type=Path, metavar="LOG",
                    help="Match a build.log file against DIF entries")
    ap.add_argument("--scan-run", type=Path, metavar="RUN_DIR",
                    help="Scan a ModCompileRuns directory for DIF matches")
    ap.add_argument("--list", action="store_true",
                    help="List all DIF entries")
    ap.add_argument("--create", action="store_true",
                    help="Create a new DIF entry interactively")

    args = ap.parse_args()

    if args.create:
        cmd_create()
    elif args.list:
        cmd_list()
    elif args.match_log:
        cmd_match_log(args.match_log)
    elif args.scan_run:
        cmd_scan_run(args.scan_run)
    elif args.query:
        cmd_search(args.query, top_n=args.top)
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
