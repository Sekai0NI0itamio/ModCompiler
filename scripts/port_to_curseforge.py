#!/usr/bin/env python3
"""
port_to_curseforge.py
---------------------
Dispatches the "Port Mod to CurseForge" GitHub Actions workflow with the two
provided URLs, waits for it to finish, then downloads and prints all logs and
output artifacts locally.

Usage
-----
  python3 scripts/port_to_curseforge.py \
      https://modrinth.com/mod/world-auto-feeder \
      https://www.curseforge.com/minecraft/mc-mods/world-auto-feeder

  # Dry run (shows diff, no uploads):
  python3 scripts/port_to_curseforge.py \
      https://modrinth.com/mod/world-auto-feeder \
      https://www.curseforge.com/minecraft/mc-mods/world-auto-feeder \
      --dry-run

Requirements
------------
  - `gh` CLI installed and authenticated  (gh auth login)
  - The workflow file .github/workflows/port-to-curseforge.yml must be on the
    default branch of the repository.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

WORKFLOW_ID       = "port-to-curseforge.yml"
OUTPUT_DIR        = "CurseForgePortRuns"
POLL_INTERVAL     = 10   # seconds between status checks
DISPATCH_TIMEOUT  = 120  # seconds to wait for the run to appear after dispatch
RUN_TIMEOUT       = 1800 # seconds to wait for the run to complete (30 min)

REPO_ROOT = Path(__file__).resolve().parents[1]


# ---------------------------------------------------------------------------
# Subprocess / gh CLI helpers  (mirrors modcompiler/mod_compile.py patterns)
# ---------------------------------------------------------------------------

def _run(args: list[str], *, env: dict | None = None, cwd: Path | None = None) -> str:
    try:
        result = subprocess.run(
            args,
            cwd=str(cwd) if cwd else None,
            env=env,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return result.stdout
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or exc.stdout or f"exit {exc.returncode}").strip()
        raise RuntimeError(f"Command failed: {' '.join(args)}\n{detail}") from None


def _gh_env() -> dict[str, str]:
    """Environment with GH_TOKEN set from the current gh auth token."""
    env = os.environ.copy()
    try:
        token = _run(["gh", "auth", "token"]).strip()
        if token:
            env["GH_TOKEN"] = token
            env["GITHUB_TOKEN"] = token
    except RuntimeError:
        pass  # already set in env, or gh will use its own keychain
    return env


def _gh(args: list[str]) -> str:
    return _run(["gh"] + args, env=_gh_env())


def _discover_repo() -> str:
    """Derive owner/repo from the git origin remote."""
    raw = _run(["git", "remote", "get-url", "origin"], cwd=REPO_ROOT).strip()
    for pattern in (
        r"git@github\.com:([^/]+/[^/]+?)(?:\.git)?$",
        r"https?://github\.com/([^/]+/[^/]+?)(?:\.git)?$",
    ):
        m = re.search(pattern, raw)
        if m:
            return m.group(1)
    raise RuntimeError(f"Could not parse owner/repo from origin remote: {raw!r}")


def _default_branch(repo: str) -> str:
    out = _gh(["repo", "view", repo, "--json", "defaultBranchRef", "--jq", ".defaultBranchRef.name"])
    return out.strip() or "main"


# ---------------------------------------------------------------------------
# Workflow dispatch + polling
# ---------------------------------------------------------------------------

def _list_runs(repo: str, branch: str) -> list[dict]:
    out = _gh([
        "run", "list",
        "-R", repo,
        "-w", WORKFLOW_ID,
        "-b", branch,
        "-e", "workflow_dispatch",
        "--json", "databaseId,status,conclusion,createdAt,url,workflowName",
        "-L", "20",
    ])
    parsed = json.loads(out or "[]")
    return parsed if isinstance(parsed, list) else []


def _dispatch(repo: str, branch: str, modrinth_url: str, curseforge_url: str, dry_run: bool) -> int:
    """Dispatch the workflow and return the new run ID."""
    before_ids = {int(r["databaseId"]) for r in _list_runs(repo, branch)}

    _gh([
        "workflow", "run", WORKFLOW_ID,
        "-R", repo,
        "--ref", branch,
        "-f", f"modrinth_url={modrinth_url}",
        "-f", f"curseforge_url={curseforge_url}",
        "-f", f"dry_run={'true' if dry_run else 'false'}",
    ])
    print("  Workflow dispatched. Waiting for run to appear...", flush=True)

    deadline = time.time() + DISPATCH_TIMEOUT
    while time.time() < deadline:
        for run in _list_runs(repo, branch):
            run_id = int(run["databaseId"])
            if run_id not in before_ids:
                return run_id
        time.sleep(4)

    raise RuntimeError(
        f"Workflow was dispatched but no new run appeared within {DISPATCH_TIMEOUT}s.\n"
        "Check the Actions tab on GitHub to see if it queued."
    )


def _wait_for_completion(repo: str, run_id: int) -> dict:
    """Poll until the run reaches 'completed' status. Returns the final run JSON."""
    deadline = time.time() + RUN_TIMEOUT
    last_status = ""
    while time.time() < deadline:
        out = _gh([
            "run", "view", str(run_id),
            "-R", repo,
            "--json", "status,conclusion,url,workflowName",
        ])
        info = json.loads(out or "{}")
        status = info.get("status", "")
        if status != last_status:
            ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
            print(f"  [{ts}] status: {status}", flush=True)
            last_status = status
        if status == "completed":
            return info
        time.sleep(POLL_INTERVAL)

    raise RuntimeError(f"Timed out after {RUN_TIMEOUT}s waiting for run {run_id}.")


# ---------------------------------------------------------------------------
# Log + artifact download
# ---------------------------------------------------------------------------

def _download_logs(repo: str, run_id: int, run_dir: Path) -> Path:
    """Download the full run log zip into run_dir."""
    log_zip = run_dir / "run-logs.zip"
    _gh([
        "run", "view", str(run_id),
        "-R", repo,
        "--log",
    ])
    # gh run view --log prints to stdout; capture it as a text file instead
    log_text = _gh([
        "run", "view", str(run_id),
        "-R", repo,
        "--log",
    ])
    log_file = run_dir / "run.log"
    log_file.write_text(log_text, encoding="utf-8")
    return log_file


def _download_artifacts(repo: str, run_id: int, run_dir: Path) -> list[Path]:
    """Download all artifacts for the run. Returns list of artifact directories."""
    # List available artifacts
    out = _gh([
        "run", "view", str(run_id),
        "-R", repo,
        "--json", "jobs",
    ])
    # Use gh run download --all to grab everything
    artifacts_dir = run_dir / "artifacts"
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    try:
        _gh([
            "run", "download", str(run_id),
            "-R", repo,
            "-D", str(artifacts_dir),
        ])
    except RuntimeError as exc:
        # No artifacts is fine (dry run produces none)
        if "no artifact" in str(exc).lower() or "no valid" in str(exc).lower():
            print("  No artifacts to download (expected for dry runs).")
            return []
        raise
    downloaded = [p for p in artifacts_dir.iterdir() if p.is_dir()]
    return downloaded


# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

def _print_separator(char: str = "-", width: int = 70) -> None:
    print(char * width)


def _session_dir() -> Path:
    ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    return REPO_ROOT / OUTPUT_DIR / f"port-{ts}"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Dispatch the CurseForge port workflow and stream results locally.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("modrinth_url",   help="Modrinth project URL or slug")
    parser.add_argument("curseforge_url", help="CurseForge numeric project ID (find it in the right sidebar of your project page under 'Project ID')")
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Pass dry_run=true to the workflow (shows diff, no uploads).",
    )
    parser.add_argument(
        "--repo", default="",
        help="GitHub owner/repo override (default: auto-detected from git remote).",
    )
    parser.add_argument(
        "--output-dir", default=OUTPUT_DIR,
        help=f"Local directory for logs and artifacts (default: {OUTPUT_DIR}).",
    )
    args = parser.parse_args(argv)

    # Sanity-check gh is available
    try:
        _run(["gh", "--version"])
    except (RuntimeError, FileNotFoundError):
        print("[ERROR] 'gh' CLI not found. Install it from https://cli.github.com/", file=sys.stderr)
        return 1

    repo = args.repo.strip() or _discover_repo()
    branch = _default_branch(repo)
    run_dir = (REPO_ROOT / args.output_dir / f"port-{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')}")
    run_dir.mkdir(parents=True, exist_ok=True)

    _print_separator("=")
    print("  Modrinth -> CurseForge Port")
    _print_separator()
    print(f"  Modrinth  : {args.modrinth_url}")
    print(f"  CurseForge: {args.curseforge_url}")
    print(f"  Dry run   : {args.dry_run}")
    print(f"  Repo      : {repo}  (branch: {branch})")
    print(f"  Output    : {run_dir}")
    _print_separator("=")
    print()

    # --- Dispatch ---
    print("[1/4] Dispatching workflow...")
    run_id = _dispatch(repo, branch, args.modrinth_url, args.curseforge_url, args.dry_run)
    run_url = f"https://github.com/{repo}/actions/runs/{run_id}"
    print(f"  Run ID : {run_id}")
    print(f"  URL    : {run_url}")
    print()

    # --- Wait ---
    print("[2/4] Waiting for workflow to complete...")
    run_info = _wait_for_completion(repo, run_id)
    conclusion = run_info.get("conclusion", "unknown")
    print(f"  Conclusion: {conclusion}")
    print()

    # --- Logs ---
    print("[3/4] Downloading logs...")
    try:
        log_file = _download_logs(repo, run_id, run_dir)
        print(f"  Saved to: {log_file}")
        print()
        # Print the log inline so you can see it right in the terminal
        _print_separator()
        print("  === WORKFLOW LOG ===")
        _print_separator()
        log_text = log_file.read_text(encoding="utf-8", errors="replace")
        # Trim very long logs to last 300 lines for terminal readability
        lines = log_text.splitlines()
        if len(lines) > 300:
            print(f"  (showing last 300 of {len(lines)} lines — full log in {log_file.name})")
            lines = lines[-300:]
        print("\n".join(lines))
        _print_separator()
        print()
    except RuntimeError as exc:
        print(f"  [WARN] Could not download logs: {exc}")
        print()

    # --- Artifacts ---
    print("[4/4] Downloading artifacts...")
    try:
        artifact_dirs = _download_artifacts(repo, run_id, run_dir)
        if artifact_dirs:
            for d in artifact_dirs:
                print(f"  {d.name}/")
                for f in sorted(d.rglob("*")):
                    if f.is_file():
                        print(f"    {f.relative_to(d)}  ({f.stat().st_size:,} bytes)")
        else:
            print("  No artifacts.")
    except RuntimeError as exc:
        print(f"  [WARN] Artifact download error: {exc}")
    print()

    # --- Summary ---
    _print_separator("=")
    status_icon = "✓" if conclusion == "success" else "✗"
    print(f"  {status_icon}  Workflow {conclusion.upper()}")
    print(f"  Run    : {run_url}")
    print(f"  Output : {run_dir}")
    _print_separator("=")
    print()

    return 0 if conclusion == "success" else 1


if __name__ == "__main__":
    raise SystemExit(main())
