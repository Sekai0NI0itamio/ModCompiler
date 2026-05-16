#!/usr/bin/env python3
"""
Trigger the ModLauncherRunDiagnosis GitHub Actions workflow.

Usage
-----
  python3 scripts/run_launcher_diagnosis.py https://modrinth.com/mod/fabric-api
  python3 scripts/run_launcher_diagnosis.py sort-chest
  python3 scripts/run_launcher_diagnosis.py https://modrinth.com/mod/sort-chest --wait
  python3 scripts/run_launcher_diagnosis.py https://modrinth.com/mod/sort-chest --download-crash-logs

Requirements
------------
  - gh (GitHub CLI) must be installed and authenticated
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

WORKFLOW_FILE = "ModLauncherRunDiagnosis.yml"
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0
POLL_INTERVAL = 15
DEFAULT_TIMEOUT = 7200


class RunError(Exception):
    pass


def _detect_repo() -> str:
    try:
        url = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            stderr=subprocess.DEVNULL, text=True).strip()
    except subprocess.CalledProcessError:
        raise RunError("Could not detect GitHub repo from git remote. Use --repo owner/repo.")
    m = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    if not m:
        raise RunError(f"Could not parse owner/repo from remote URL: {url}")
    return m.group(1)


def _detect_token() -> str:
    for var in ("GH_TOKEN", "GITHUB_TOKEN"):
        t = os.environ.get(var, "").strip()
        if t:
            return t
    try:
        t = subprocess.check_output(
            ["gh", "auth", "token"], stderr=subprocess.DEVNULL, text=True).strip()
        if t:
            return t
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    return ""


def _ensure_gh() -> None:
    try:
        subprocess.check_output(["gh", "--version"], stderr=subprocess.DEVNULL)
    except (FileNotFoundError, subprocess.CalledProcessError):
        raise RunError(
            "GitHub CLI (gh) is not installed or not on PATH.\n"
            "Install it from https://cli.github.com/ and run `gh auth login`."
        )


def _gh(args: list[str], *, token: str, retries: int = MAX_GH_RETRIES) -> str:
    env = os.environ.copy()
    if token:
        env["GH_TOKEN"] = token
        env["GITHUB_TOKEN"] = token

    last_err = ""
    for attempt in range(1, retries + 1):
        try:
            result = subprocess.run(
                ["gh"] + args,
                env=env,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            return result.stdout
        except subprocess.CalledProcessError as exc:
            stderr = (exc.stderr or "").strip()
            stdout = (exc.stdout or "").strip()
            last_err = stderr or stdout or f"exit {exc.returncode}"
            transient = any(m in last_err.lower() for m in (
                "connection reset", "tls handshake", "i/o timeout",
                "timeout", "unexpected eof", "connection refused",
                "temporary failure", "no such host",
            ))
            if attempt < retries and transient:
                time.sleep(GH_RETRY_DELAY * attempt)
                continue
            break

    raise RunError(f"gh {' '.join(args[:3])}... failed: {last_err}")


def _normalize_modrinth_link(raw: str) -> str:
    raw = raw.strip().strip("`").strip("'").strip('"').rstrip("/")
    if not raw:
        raise RunError("Modrinth link or slug is required.")
    if "modrinth.com" in raw:
        return raw
    return f"https://modrinth.com/mod/{raw}"


def dispatch(repo: str, token: str, modrinth_link: str) -> int:
    before = {r["databaseId"] for r in _list_runs(repo, token)}

    _gh([
        "workflow", "run", WORKFLOW_FILE, "-R", repo,
        "-f", f"modrinth_link={modrinth_link}",
    ], token=token)

    deadline = time.time() + 120
    while time.time() < deadline:
        for run in _list_runs(repo, token):
            rid = run["databaseId"]
            if rid not in before:
                return rid
        time.sleep(4)
    raise RunError("Workflow was dispatched but no new run appeared within 120 s.")


def _list_runs(repo: str, token: str) -> list[dict]:
    out = _gh([
        "run", "list", "-R", repo,
        "-w", WORKFLOW_FILE,
        "-e", "workflow_dispatch",
        "--json", "databaseId,status,conclusion,createdAt",
        "-L", "20",
    ], token=token)
    return json.loads(out or "[]")


def _run_view(run_id: int, repo: str, token: str) -> dict:
    out = _gh([
        "run", "view", str(run_id), "-R", repo,
        "--json", "status,conclusion,url,workflowName",
    ], token=token)
    return json.loads(out or "{}")


def _get_jobs(run_id: int, repo: str, token: str) -> list[dict]:
    out = _gh([
        "run", "view", str(run_id), "-R", repo,
        "--json", "jobs",
    ], token=token)
    data = json.loads(out or "{}")
    return data.get("jobs", [])


def wait_for_run(run_id: int, repo: str, token: str, timeout: int = DEFAULT_TIMEOUT) -> str:
    deadline = time.time() + timeout
    last_status = ""
    last_jobs_print = 0.0

    print(f"Waiting for workflow run #{run_id} to complete...")
    print(f"  (polling every {POLL_INTERVAL}s, timeout {timeout}s)\n")

    while time.time() < deadline:
        info = _run_view(run_id, repo, token)
        status = info.get("status", "")
        conclusion = info.get("conclusion") or ""

        if status != last_status:
            _log(f"Status: {status}")
            last_status = status

        if time.time() - last_jobs_print >= 30:
            _print_job_progress(run_id, repo, token)
            last_jobs_print = time.time()

        if status == "completed":
            _log(f"Completed — conclusion: {conclusion}")
            return conclusion

        time.sleep(POLL_INTERVAL)

    raise RunError(f"Timed out after {timeout}s waiting for run {run_id}.")


def _print_job_progress(run_id: int, repo: str, token: str) -> None:
    try:
        jobs = _get_jobs(run_id, repo, token)
    except RunError:
        return
    lines = []
    for job in jobs:
        name = job.get("name", "?")
        status = job.get("status", "?")
        conc = job.get("conclusion") or ""
        icon = {"success": "✓", "failure": "✗", "skipped": "–"}.get(conc, "…")
        lines.append(f"  {icon} {name}  [{status}{' / ' + conc if conc else ''}]")
    if lines:
        print(f"\nJob progress ({len(lines)} jobs):")
        print("\n".join(lines))
        print()


def download_crash_artifacts(run_id: int, repo: str, token: str, dest: Path) -> list[str]:
    dest.mkdir(parents=True, exist_ok=True)
    jobs = _get_jobs(run_id, repo, token)
    downloaded = []

    for job in jobs:
        if (job.get("conclusion") or "") != "failure":
            continue
        job_name = job.get("name", "unknown")
        safe_name = re.sub(r"[^A-Za-z0-9._-]+", "_", job_name).strip("_")

        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo,
                "-n", f"crash-{safe_name}",
                "-D", str(dest / safe_name),
            ], token=token)
            downloaded.append(safe_name)
            print(f"  ✓ crash logs for {safe_name}")
        except RunError:
            pass

    return downloaded


def _log(msg: str) -> None:
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] {msg}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Trigger ModLauncherRunDiagnosis workflow on GitHub Actions."
    )
    parser.add_argument(
        "modrinth_link",
        help="Modrinth project link (e.g. https://modrinth.com/mod/fabric-api) or slug (e.g. fabric-api)",
    )
    parser.add_argument("--repo", default="",
        help="owner/repo override (default: auto-detect from git remote)")
    parser.add_argument("--wait", action="store_true",
        help="Wait for the workflow to complete before exiting")
    parser.add_argument("--download-crash-logs", action="store_true",
        help="Download crash log artifacts for any failed test jobs (implies --wait)")
    parser.add_argument("--output-dir", default="LauncherDiagnosisRuns",
        help="Root folder for downloaded artifacts (default: LauncherDiagnosisRuns)")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
        help=f"Max seconds to wait for workflow (default: {DEFAULT_TIMEOUT})")

    args = parser.parse_args(argv)

    if args.download_crash_logs:
        args.wait = True

    try:
        _ensure_gh()

        repo = (args.repo or _detect_repo()).strip()
        token = _detect_token()
        modrinth_link = _normalize_modrinth_link(args.modrinth_link)

        print("=" * 60)
        print("ModLauncherRunDiagnosis — Workflow Trigger")
        print("=" * 60)
        print(f"  Repo:          {repo}")
        print(f"  Modrinth link: {modrinth_link}")
        print()

        run_id = dispatch(repo, token, modrinth_link)
        run_url = f"https://github.com/{repo}/actions/runs/{run_id}"

        print(f"\n✓ Workflow triggered successfully!")
        print(f"  Run ID:  {run_id}")
        print(f"  URL:     {run_url}")

        if not args.wait:
            print(f"\nCheck progress at:")
            print(f"  {run_url}")
            print("=" * 60)
            return 0

        conclusion = wait_for_run(run_id, repo, token, args.timeout)

        print()
        if args.download_crash_logs:
            print("Downloading crash log artifacts...")
            ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
            dest = Path(args.output_dir).resolve() / f"run-{run_id}-{ts}"
            downloaded = download_crash_artifacts(run_id, repo, token, dest)
            if downloaded:
                print(f"\n  Crash logs saved to: {dest}")
            else:
                print("\n  No crash log artifacts found (all tests may have passed).")

        print()
        print("=" * 60)
        if conclusion == "success":
            print("✓ All launcher tests passed!")
        else:
            print("✗ Some launcher tests failed.")
            if not args.download_crash_logs:
                print("  Tip: re-run with --download-crash-logs to get crash reports.")
        print(f"  Run URL: {run_url}")
        print("=" * 60)

        return 0 if conclusion == "success" else 1

    except RunError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
