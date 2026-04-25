#!/usr/bin/env python3
"""
Decompile All Minecraft Versions and Add Missing to Repository
==============================================================

Triggers the `decompile-all-minecraft-versions-and-add-missing-to-repository`
GitHub Actions workflow, waits for it to finish, and prints a summary.

The workflow decompiles Minecraft sources for every version+loader combination
in version-manifest.json and commits the extracted .java files into
  decompiled-sources/<version>-<loader>/
in the repository.  Already-committed folders are skipped automatically, so
re-runs are cheap.

Usage
-----
  # Decompile everything that is missing:
  python3 scripts/decompile_all_and_commit.py

  # Force re-decompile even if already committed:
  python3 scripts/decompile_all_and_commit.py --force

  # Only a specific version+loader:
  python3 scripts/decompile_all_and_commit.py --version 1.21.5 --loader forge

  # Custom commit message prefix:
  python3 scripts/decompile_all_and_commit.py --prefix "feat(sources)"

Requirements
------------
  - gh (GitHub CLI) installed and authenticated: https://cli.github.com/
  - The repository must be pushed to GitHub (the workflow runs there).
"""

import argparse
import json
import sys
import time
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_FILE = "decompile-all-minecraft-versions-and-add-missing-to-repository.yml"
POLL_INTERVAL = 30   # seconds between status checks
MAX_WAIT_SECS = 7200  # 2 hours — large matrix can take a while


# ---------------------------------------------------------------------------
# gh CLI helpers (same pattern as ai_source_search.py)
# ---------------------------------------------------------------------------

def _gh(*args, capture: bool = True, check: bool = True) -> str:
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
        return ""


def _repo() -> str:
    return _gh("repo", "view", "--json=nameWithOwner", "-q", ".nameWithOwner")


# ---------------------------------------------------------------------------
# Trigger
# ---------------------------------------------------------------------------

def trigger(
    force: bool,
    version: str,
    loader: str,
    prefix: str,
) -> None:
    print("Triggering decompile-all workflow...")
    print(f"  Force regenerate : {force}")
    if version:
        print(f"  Specific version : {version}")
    if loader:
        print(f"  Specific loader  : {loader}")
    print(f"  Commit prefix    : {prefix}")
    print()

    args = [
        "workflow", "run", WORKFLOW_FILE,
        "-f", f"force_regenerate={'yes' if force else 'no'}",
        "-f", f"specific_version={version}",
        "-f", f"specific_loader={loader}",
        "-f", f"commit_message_prefix={prefix}",
    ]
    _gh(*args, capture=False)


# ---------------------------------------------------------------------------
# Wait for the run to complete
# ---------------------------------------------------------------------------

def wait_for_run(repo: str) -> dict:
    """Poll until the most-recently-triggered run of the workflow finishes."""
    print("Waiting for workflow run to start...", end="", flush=True)
    run_id = None
    for _ in range(20):
        time.sleep(5)
        raw = _gh(
            "run", "list",
            "--workflow", WORKFLOW_FILE,
            "--limit", "1",
            "--json", "databaseId,status,conclusion,url",
            check=False,
        )
        try:
            runs = json.loads(raw)
        except json.JSONDecodeError:
            runs = []
        if runs and runs[0].get("status") != "completed":
            run_id = runs[0]["databaseId"]
            print(f"\nRun started: {runs[0]['url']}")
            break
        if runs and runs[0].get("status") == "completed":
            # Already finished (very fast matrix with all skipped)
            return runs[0]
        print(".", end="", flush=True)

    if run_id is None:
        print("\nERROR: Could not find a running workflow. Did the trigger succeed?", file=sys.stderr)
        sys.exit(1)

    # Poll until done
    elapsed = 0
    while elapsed < MAX_WAIT_SECS:
        time.sleep(POLL_INTERVAL)
        elapsed += POLL_INTERVAL
        raw = _gh(
            "run", "view", str(run_id),
            "--json", "status,conclusion,url",
            check=False,
        )
        try:
            info = json.loads(raw)
        except json.JSONDecodeError:
            info = {}
        status = info.get("status", "unknown")
        conclusion = info.get("conclusion", "")
        print(f"  [{elapsed:4d}s] status={status} conclusion={conclusion}")
        if status == "completed":
            return info

    print(f"\nERROR: Workflow did not finish within {MAX_WAIT_SECS}s.", file=sys.stderr)
    sys.exit(1)


# ---------------------------------------------------------------------------
# Print result summary
# ---------------------------------------------------------------------------

def print_summary(run_info: dict, repo: str) -> None:
    conclusion = run_info.get("conclusion", "unknown")
    url = run_info.get("url", "")
    print()
    print("=" * 60)
    print(f"Workflow finished — conclusion: {conclusion.upper()}")
    print(f"Run URL: {url}")
    print()

    if conclusion in ("success", "neutral"):
        print("✅ Decompiled sources have been committed to the repository.")
        print()
        print("Browse them at:")
        print(f"  https://github.com/{repo}/tree/HEAD/decompiled-sources/")
        print()
        print("Or clone/pull and search locally:")
        print("  git pull")
        print("  grep -r 'YourClassName' decompiled-sources/")
    elif conclusion == "failure":
        print("❌ Some jobs failed. Check the run URL above for details.")
        print("   Partial results may still have been committed.")
    else:
        print(f"⚠️  Unexpected conclusion: {conclusion}")
        print("   Check the run URL above for details.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Trigger the decompile-all-minecraft-versions workflow and wait for it.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force re-decompile even if the folder already exists in the repo.",
    )
    parser.add_argument(
        "--version",
        default="",
        metavar="MC_VERSION",
        help="Only process this Minecraft version (e.g. 1.21.5). Default: all.",
    )
    parser.add_argument(
        "--loader",
        default="",
        choices=["", "forge", "fabric", "neoforge"],
        help="Only process this loader. Default: all.",
    )
    parser.add_argument(
        "--prefix",
        default="chore(sources)",
        metavar="PREFIX",
        help="Git commit message prefix. Default: 'chore(sources)'.",
    )
    parser.add_argument(
        "--no-wait",
        dest="no_wait",
        action="store_true",
        help="Trigger the workflow and exit immediately without waiting.",
    )
    args = parser.parse_args()

    repo = _repo()
    print(f"Repository: {repo}")
    print()

    trigger(
        force=args.force,
        version=args.version,
        loader=args.loader,
        prefix=args.prefix,
    )

    if args.no_wait:
        print("Workflow triggered. Use 'gh run list' to monitor progress.")
        print(f"  gh run list --workflow {WORKFLOW_FILE} --limit 5")
        return

    run_info = wait_for_run(repo)
    print_summary(run_info, repo)


if __name__ == "__main__":
    main()
