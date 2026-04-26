#!/usr/bin/env python3
"""
AI Source Search — trigger the ai-source-search GitHub Actions workflow,
wait for it, download the results, and print them to stdout so the IDE
agent can read the actual Minecraft/Forge/NeoForge API signatures.

Usage:
  python3 scripts/ai_source_search.py \\
      --version 1.21.5 \\
      --loader forge \\
      --queries "computeIfAbsent" "SavedDataType" "DimensionDataStorage" \\
      --files "*DimensionDataStorage*.java" "*SavedData*.java"

  python3 scripts/ai_source_search.py \\
      --version 1.21.9 \\
      --loader neoforge \\
      --queries "computeIfAbsent,SavedData.Factory,SavedDataType"

Requirements:
  - gh (GitHub CLI) installed and authenticated: https://cli.github.com/
  - The repository must be pushed to GitHub (the workflow runs there)
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

# DIF core — unified source search + issue matching
try:
    from dif_core import (
        SourceSearchEngine,
        extract_missing_symbols,
        match_errors_to_dif,
        print_search_results,
        _detect_repo,
        _detect_token,
        _gh as _dif_gh,
    )
    _DIF_AVAILABLE = True
except ImportError:
    _DIF_AVAILABLE = False


# ---------------------------------------------------------------------------
# gh CLI helpers
# ---------------------------------------------------------------------------

def _gh(*args, capture=True, check=True):
    cmd = ["gh"] + list(args)
    if capture:
        r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
        if check and r.returncode != 0:
            print(f"ERROR running: {' '.join(cmd)}", file=sys.stderr)
            print(r.stderr, file=sys.stderr)
            sys.exit(1)
        return r.stdout.strip()
    else:
        r = subprocess.run(cmd, cwd=ROOT)
        if check and r.returncode != 0:
            sys.exit(1)


def _repo():
    return _gh("repo", "view", "--json=nameWithOwner", "-q", ".nameWithOwner")


# ---------------------------------------------------------------------------
# Workflow trigger + wait
# ---------------------------------------------------------------------------

def trigger(version: str, loader: str, queries: str, file_patterns: str,
            context: int, dump_full: str) -> int:
    print(f"Triggering ai-source-search workflow...")
    print(f"  Version  : {version}")
    print(f"  Loader   : {loader}")
    print(f"  Queries  : {queries}")
    print(f"  Patterns : {file_patterns}")
    print(f"  Context  : {context} lines")
    print()

    _gh(
        "workflow", "run", "ai-source-search.yml",
        "-f", f"minecraft_version={version}",
        "-f", f"loader={loader}",
        "-f", f"queries={queries}",
        "-f", f"file_patterns={file_patterns}",
        "-f", f"context_lines={context}",
        "-f", f"dump_full_class={dump_full}",
        capture=False,
    )

    # Give GitHub a moment to register the run
    time.sleep(6)

    runs_json = _gh(
        "run", "list",
        "--workflow=ai-source-search.yml",
        "--limit=1",
        "--json=databaseId,status,conclusion,createdAt",
    )
    runs = json.loads(runs_json)
    if not runs:
        print("ERROR: Could not find workflow run", file=sys.stderr)
        sys.exit(1)

    run_id = runs[0]["databaseId"]
    repo = _repo()
    print(f"Run ID : {run_id}")
    print(f"URL    : https://github.com/{repo}/actions/runs/{run_id}")
    return run_id


def wait(run_id: int, timeout: int = 3600) -> bool:
    print("\nWaiting for workflow to complete...")
    start = time.time()
    while True:
        elapsed = int(time.time() - start)
        if elapsed > timeout:
            print(f"\nTimeout after {timeout}s", file=sys.stderr)
            sys.exit(1)

        data = json.loads(_gh("run", "view", str(run_id), "--json=status,conclusion"))
        status = data["status"]
        conclusion = data.get("conclusion") or ""

        if status == "completed":
            print(f"\nWorkflow finished: {conclusion}")
            return conclusion == "success"

        print(f"\r[{elapsed:04d}s] {status}...", end="", flush=True)
        time.sleep(12)


# ---------------------------------------------------------------------------
# Download + display results
# ---------------------------------------------------------------------------

def download(run_id: int, version: str, loader: str, output_dir: Path) -> bool:
    artifact_name = f"ai-source-search-{version}-{loader}"
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\nDownloading artifact '{artifact_name}' → {output_dir}")
    _gh(
        "run", "download", str(run_id),
        "-n", artifact_name,
        "-D", str(output_dir),
        capture=False,
        check=False,
    )

    info_file = output_dir / "search-info.txt"
    if info_file.exists():
        print("\n" + "=" * 60)
        print("SEARCH INFO")
        print("=" * 60)
        print(info_file.read_text())

    queries_dir = output_dir / "queries"
    if queries_dir.exists():
        for qfile in sorted(queries_dir.iterdir()):
            if qfile.suffix == ".txt":
                print("\n" + "=" * 60)
                print(f"QUERY: {qfile.stem}")
                print("=" * 60)
                content = qfile.read_text()
                # Limit output to avoid flooding the terminal
                lines = content.splitlines()
                if len(lines) > 200:
                    print("\n".join(lines[:200]))
                    print(f"\n... ({len(lines) - 200} more lines — read {qfile} for full output)")
                else:
                    print(content)

    classes_dir = output_dir / "full-classes"
    if classes_dir.exists():
        class_files = list(classes_dir.iterdir())
        if class_files:
            print("\n" + "=" * 60)
            print(f"FULL CLASS FILES ({len(class_files)} files)")
            print("=" * 60)
            for cf in sorted(class_files):
                print(f"\n--- {cf.name} ---")
                content = cf.read_text()
                lines = content.splitlines()
                if len(lines) > 300:
                    print("\n".join(lines[:300]))
                    print(f"\n... ({len(lines) - 300} more lines — read {cf} for full content)")
                else:
                    print(content)

    # API overview — broad discovery even when queries find nothing
    overview_dir = output_dir / "api-overview"
    if overview_dir.exists():
        print(f"\n{'=' * 60}")
        print("API OVERVIEW (broad discovery)")
        print("=" * 60)

        for overview_file in ["event-classes.txt", "render-gui-classes.txt",
                               "client-classes.txt", "modloader-api-classes.txt"]:
            fp = overview_dir / overview_file
            if fp.exists():
                lines = fp.read_text().splitlines()
                label = overview_file.replace(".txt", "").replace("-", " ").title()
                print(f"\n--- {label} ({len(lines)} files) ---")
                print("\n".join(lines[:60]))
                if len(lines) > 60:
                    print(f"... ({len(lines) - 60} more — read {fp})")

        # Print full content of key API files from the overview
        full_api_files = sorted(overview_dir.glob("full_*.java"))
        if full_api_files:
            print(f"\n--- Full API Source Files ({len(full_api_files)} files) ---")
            for cf in full_api_files[:20]:  # show up to 20
                print(f"\n{'─' * 50}")
                print(f"FILE: {cf.name}")
                print('─' * 50)
                content = cf.read_text()
                lines = content.splitlines()
                if len(lines) > 200:
                    print("\n".join(lines[:200]))
                    print(f"\n... ({len(lines) - 200} more lines — read {cf})")
                else:
                    print(content)
            if len(full_api_files) > 20:
                print(f"\n... ({len(full_api_files) - 20} more API files in {overview_dir})")

    all_files = output_dir / "all-java-files.txt"
    if all_files.exists():
        lines = all_files.read_text().splitlines()
        print(f"\n{'=' * 60}")
        print(f"ALL JAVA FILES ({len(lines)} total) — first 100:")
        print("=" * 60)
        print("\n".join(lines[:100]))
        if len(lines) > 100:
            print(f"... ({len(lines) - 100} more — read {all_files})")

    print(f"\nFull results saved to: {output_dir}")
    return info_file.exists()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(
        description="Search Minecraft source code via GitHub Actions (AI IDE helper)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Find the SavedDataType / computeIfAbsent API in Forge 1.21.5
  python3 scripts/ai_source_search.py \\
      --version 1.21.5 --loader forge \\
      --queries "computeIfAbsent" "SavedDataType" \\
      --files "*DimensionDataStorage*.java" "*SavedData*.java"

  # Find NeoForge 1.21.9 SavedData API
  python3 scripts/ai_source_search.py \\
      --version 1.21.9 --loader neoforge \\
      --queries "computeIfAbsent,SavedData.Factory,SavedDataType"

  # Broad search — all .java files
  python3 scripts/ai_source_search.py \\
      --version 1.21.2 --loader forge \\
      --queries "class DimensionDataStorage" --context 20
        """,
    )
    ap.add_argument("--version", required=True,
                    help="Minecraft version, e.g. 1.21.5")
    ap.add_argument("--loader", required=True,
                    choices=["forge", "neoforge", "fabric"],
                    help="Mod loader")
    ap.add_argument("--queries", nargs="+", required=True,
                    help="One or more search queries (regex). Can also be comma-separated in a single arg.")
    ap.add_argument("--files", nargs="*", default=["*.java"],
                    help="File patterns to restrict search (default: *.java)")
    ap.add_argument("--context", type=int, default=15,
                    help="Lines of context around matches (default: 15)")
    ap.add_argument("--no-dump-full", action="store_true",
                    help="Skip dumping full class file content")
    ap.add_argument("--output-dir", default=None,
                    help="Where to save results (default: MinecraftSourceSearch/run-TIMESTAMP/)")
    ap.add_argument("--no-wait", action="store_true",
                    help="Trigger workflow and exit without waiting")
    ap.add_argument("--timeout", type=int, default=3600,
                    help="Max seconds to wait (default: 3600)")
    ap.add_argument("--local-only", action="store_true",
                    help="Only search DecompiledMinecraftSourceCode/ locally, never trigger workflow")
    ap.add_argument("--dif-search", metavar="QUERY",
                    help="Also search the DIF database with this natural language query")

    args = ap.parse_args()

    # Check gh CLI
    try:
        subprocess.run(["gh", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("ERROR: GitHub CLI (gh) not found. Install from https://cli.github.com/",
              file=sys.stderr)
        sys.exit(1)

    # Normalise queries and file patterns to comma-separated strings
    queries_str = ",".join(q.strip() for q in args.queries if q.strip())
    files_str = ",".join(f.strip() for f in args.files if f.strip()) or "*.java"
    dump_full = "no" if args.no_dump_full else "yes"

    # ── DIF search (optional) ─────────────────────────────────────────
    if getattr(args, "dif_search", None) and _DIF_AVAILABLE:
        from dif_core import search_dif
        results = search_dif(args.dif_search, top_n=5)
        print_search_results(results, query=args.dif_search)

    # ── Local search via dif_core (instant if sources are in repo) ────
    ts = time.strftime("%Y%m%d-%H%M%S")
    out = Path(args.output_dir) if args.output_dir else (
        ROOT / "MinecraftSourceSearch" / f"run-{ts}"
    )

    if _DIF_AVAILABLE:
        from dif_core import DECOMPILED_DIR
        slug = f"{args.version}-{args.loader}"
        sources_folder = DECOMPILED_DIR / slug
        if sources_folder.is_dir():
            print(f"\nLocal sources found: {sources_folder}")
            print("Searching locally (instant)...\n")
            engine = SourceSearchEngine()
            symbols = [q.strip() for q in queries_str.split(",") if q.strip()]
            result = engine.search(
                version=args.version, loader=args.loader,
                symbols=symbols, out_dir=out,
                context_lines=args.context,
                dump_full_class=(dump_full == "yes"),
            )
            java_count = result.get("java_count", 0)
            qr = result.get("query_results", {})
            fc = result.get("full_classes", [])
            print(f"✓ Local search complete: {java_count:,} files, "
                  f"{len(qr)} queries matched, {len(fc)} full classes captured")
            print(f"  Results: {out}")

            # Print query results
            queries_dir = out / "queries"
            if queries_dir.exists():
                for qfile in sorted(queries_dir.glob("*.txt")):
                    content = qfile.read_text(encoding="utf-8")
                    lines = content.splitlines()
                    print(f"\n{'=' * 60}")
                    print(f"QUERY: {qfile.stem}")
                    print("=" * 60)
                    if len(lines) > 200:
                        print("\n".join(lines[:200]))
                        print(f"\n... ({len(lines) - 200} more lines — read {qfile})")
                    else:
                        print(content)

            if getattr(args, "local_only", False):
                return

            if java_count > 0:
                print("\n✓ Local search found results — skipping workflow trigger.")
                return

    run_id = trigger(
        version=args.version,
        loader=args.loader,
        queries=queries_str,
        file_patterns=files_str,
        context=args.context,
        dump_full=dump_full,
    )

    if args.no_wait:
        print(f"\nWorkflow triggered (run {run_id}). Download later with:")
        print(f"  gh run download {run_id} -n ai-source-search-{args.version}-{args.loader}")
        return

    success = wait(run_id, timeout=args.timeout)

    if not success:
        print("\nWorkflow failed. Check logs:")
        print(f"  gh run view {run_id} --log")
        sys.exit(1)

    ok = download(run_id, args.version, args.loader, out)
    if ok:
        print("\n✓ Source search complete!")
        print(f"  Results: {out}")
    else:
        print("\n✗ Download failed or no results", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
