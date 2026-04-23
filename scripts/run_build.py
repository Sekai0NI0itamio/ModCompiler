#!/usr/bin/env python3
"""
run_build.py
------------
Fully autonomous GitHub Actions build runner for the IDE agent.

Triggers the "Build Mods" workflow on GitHub Actions, streams live progress,
downloads all artifacts and per-job logs when done, and writes everything into
a structured local run folder.

Usage
-----
  python3 scripts/run_build.py incoming/my-bundle.zip
  python3 scripts/run_build.py incoming/my-bundle.zip --modrinth https://modrinth.com/mod/sort-chest
  python3 scripts/run_build.py incoming/my-bundle.zip --output-dir MyRuns --max-parallel 4

The script exits 0 on workflow success, 1 on failure or error.

Output folder layout
--------------------
  <output-dir>/<run-id>/
    result.json          - machine-readable run summary
    SUMMARY.md           - human-readable summary (read this first)
    artifacts/
      all-mod-builds/    - extracted build artifact (SUMMARY.md, per-mod jars, logs)
      modrinth-publish/  - extracted publish artifact (if modrinth URL was given)
    logs/
      run_overview.txt   - top-level workflow run log
      <job-name>.txt     - full log for each job
      jobs.json          - raw job list from GitHub API

Requirements
------------
  - gh (GitHub CLI) must be installed and authenticated
  - git must be installed
  - The zip must already be committed and pushed to the repo
    (this script does NOT commit or push anything)
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

WORKFLOW_FILE = "build.yml"
BUILD_ARTIFACT = "all-mod-builds"
PUBLISH_ARTIFACT = "modrinth-publish"
DEFAULT_OUTPUT_DIR = "ModCompileRuns"
DEFAULT_TIMEOUT = 7200          # 2 hours
POLL_INTERVAL = 15              # seconds between status checks
LOG_POLL_INTERVAL = 30          # seconds between log-streaming checks
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Trigger Build Mods workflow, wait, download artifacts + logs."
    )
    parser.add_argument("zip_path",
        help="Repo-relative path to the committed zip (e.g. incoming/my-bundle.zip)")
    parser.add_argument("--modrinth", "--modrinth-project-url", default="",
        dest="modrinth", metavar="URL",
        help="Modrinth project URL or slug for auto-publish")
    parser.add_argument("--max-parallel", default="all",
        help="Max parallel build jobs (default: all)")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR,
        help=f"Root folder for run outputs (default: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
        help=f"Max seconds to wait for workflow (default: {DEFAULT_TIMEOUT})")
    parser.add_argument("--repo", default="",
        help="owner/repo override (default: auto-detect from git remote)")
    args = parser.parse_args(argv)

    try:
        return Runner(args).run()
    except RunError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

class Runner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.zip_path   = args.zip_path.strip()
        self.modrinth   = args.modrinth.strip()
        self.parallel   = _normalize_parallel(args.max_parallel)
        self.timeout    = int(args.timeout)
        self.repo       = (args.repo or _detect_repo()).strip()
        self.token      = _detect_token()
        self.run_id: int = 0

        ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        self.out_root = Path(args.output_dir).resolve() / f"run-{ts}"

    # ------------------------------------------------------------------
    def run(self) -> int:
        self.out_root.mkdir(parents=True, exist_ok=True)
        _ensure_gh()

        print(f"Repo:        {self.repo}")
        print(f"Zip:         {self.zip_path}")
        print(f"Modrinth:    {self.modrinth or '(none)'}")
        print(f"Output dir:  {self.out_root}")
        print()

        # 1. Dispatch
        self.run_id = self._dispatch()
        run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
        print(f"Dispatched run #{self.run_id}")
        print(f"URL: {run_url}")
        print()

        # 2. Wait + stream progress
        conclusion = self._wait()

        # 3. Download artifacts + logs concurrently
        print("\nDownloading artifacts and logs concurrently...")
        artifacts_dir = self.out_root / "artifacts"
        self._download_all(artifacts_dir)

        # 4. Write summary
        self._write_summary(conclusion, run_url, artifacts_dir)

        # 5. Print final status
        summary_path = self.out_root / "SUMMARY.md"
        print(f"\nRun folder:  {self.out_root}")
        print(f"Summary:     {summary_path}")
        build_summary = artifacts_dir / BUILD_ARTIFACT / "SUMMARY.md"
        if build_summary.exists():
            print(f"Build result:{build_summary}")
        print(f"\nWorkflow conclusion: {conclusion.upper()}")

        return 0 if conclusion == "success" else 1

    # ------------------------------------------------------------------
    def _dispatch(self) -> int:
        """Dispatch the workflow and return the new run ID."""
        # Snapshot existing run IDs so we can identify the new one
        before = {r["databaseId"] for r in self._list_runs()}

        fields = [
            "-f", f"zip_path={self.zip_path}",
            "-f", f"max_parallel={self.parallel}",
            "-f", f"modrinth_project_url={self.modrinth}",
        ]
        _gh(["workflow", "run", WORKFLOW_FILE, "-R", self.repo] + fields,
            token=self.token)

        # Poll until a new run appears
        deadline = time.time() + 120
        while time.time() < deadline:
            for run in self._list_runs():
                rid = run["databaseId"]
                if rid not in before:
                    return rid
            time.sleep(4)
        raise RunError("Workflow was dispatched but no new run appeared within 120 s.")

    # ------------------------------------------------------------------
    def _list_runs(self) -> list[dict]:
        out = _gh([
            "run", "list", "-R", self.repo,
            "-w", WORKFLOW_FILE,
            "-e", "workflow_dispatch",
            "--json", "databaseId,status,conclusion,createdAt",
            "-L", "20",
        ], token=self.token)
        return json.loads(out or "[]")

    # ------------------------------------------------------------------
    def _wait(self) -> str:
        """Poll until the run completes. Returns the conclusion string."""
        deadline = time.time() + self.timeout
        last_status = ""
        last_jobs_print = 0.0

        print("Waiting for workflow to complete...")
        print(f"  (polling every {POLL_INTERVAL}s, timeout {self.timeout}s)\n")

        while time.time() < deadline:
            info = self._run_view()
            status     = info.get("status", "")
            conclusion = info.get("conclusion") or ""

            if status != last_status:
                _log(f"Status: {status}")
                last_status = status

            # Print per-job progress periodically
            if time.time() - last_jobs_print >= LOG_POLL_INTERVAL:
                self._print_job_progress()
                last_jobs_print = time.time()

            if status == "completed":
                _log(f"Completed — conclusion: {conclusion}")
                return conclusion

            time.sleep(POLL_INTERVAL)

        raise RunError(f"Timed out after {self.timeout}s waiting for run {self.run_id}.")

    # ------------------------------------------------------------------
    def _run_view(self) -> dict:
        out = _gh([
            "run", "view", str(self.run_id), "-R", self.repo,
            "--json", "status,conclusion,url,workflowName",
        ], token=self.token)
        return json.loads(out or "{}")

    # ------------------------------------------------------------------
    def _print_job_progress(self) -> None:
        try:
            jobs = self._get_jobs()
        except RunError:
            return
        lines = []
        for job in jobs:
            name   = job.get("name", "?")
            status = job.get("status", "?")
            conc   = job.get("conclusion") or ""
            icon   = {"success": "✓", "failure": "✗", "skipped": "–"}.get(conc, "…")
            lines.append(f"  {icon} {name}  [{status}{' / ' + conc if conc else ''}]")
        if lines:
            print(f"\nJob progress ({len(lines)} jobs):")
            print("\n".join(lines))
            print()

    # ------------------------------------------------------------------
    def _get_jobs(self) -> list[dict]:
        out = _gh([
            "run", "view", str(self.run_id), "-R", self.repo,
            "--json", "jobs",
        ], token=self.token)
        data = json.loads(out or "{}")
        return data.get("jobs", [])

    # ------------------------------------------------------------------
    # ------------------------------------------------------------------
    def _download_all(self, artifacts_dir: Path) -> None:
        """Download all artifacts and per-job logs concurrently (max 30 threads)."""
        MAX_WORKERS = 30
        logs_dir = self.out_root / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)

        # Collect jobs first (needed to build the per-job log task list)
        try:
            jobs = self._get_jobs()
            (logs_dir / "jobs.json").write_text(
                json.dumps(jobs, indent=2), encoding="utf-8")
        except RunError:
            jobs = []

        # Build task list: (label, callable)
        tasks: list[tuple[str, Any]] = []

        # Artifact: all-mod-builds
        tasks.append((
            f"artifact:{BUILD_ARTIFACT}",
            lambda: self._download_artifact(BUILD_ARTIFACT, artifacts_dir / BUILD_ARTIFACT),
        ))

        # Artifact: modrinth-publish (optional)
        if self.modrinth:
            def _dl_publish():
                try:
                    self._download_artifact(PUBLISH_ARTIFACT, artifacts_dir / PUBLISH_ARTIFACT)
                except RunError as exc:
                    print(f"  (modrinth-publish artifact not available: {exc})")
            tasks.append((f"artifact:{PUBLISH_ARTIFACT}", _dl_publish))

        # Log: full run overview
        def _dl_run_log():
            try:
                raw_log = _gh([
                    "run", "view", str(self.run_id), "-R", self.repo, "--log",
                ], token=self.token)
                (logs_dir / "run_overview.txt").write_text(raw_log, encoding="utf-8")
                print(f"  ✓ run_overview.txt  ({len(raw_log):,} chars)")
            except RunError as exc:
                (logs_dir / "run_overview.txt").write_text(
                    f"Could not fetch run log: {exc}\n", encoding="utf-8")
        tasks.append(("log:run_overview", _dl_run_log))

        # Logs: per-job
        for job in jobs:
            job_id   = job.get("databaseId") or job.get("id")
            job_name = job.get("name", f"job-{job_id}")
            safe_name = re.sub(r"[^A-Za-z0-9._-]+", "_", job_name).strip("_")
            log_file = logs_dir / f"{safe_name}.txt"

            if not job_id:
                continue

            def _make_job_log_task(jid=job_id, lf=log_file, sn=safe_name):
                def _dl():
                    try:
                        job_log = _gh([
                            "run", "view", str(self.run_id), "-R", self.repo,
                            "--log", "--job", str(jid),
                        ], token=self.token)
                        lf.write_text(job_log, encoding="utf-8")
                        print(f"  ✓ {sn}.txt  ({len(job_log):,} chars)")
                    except RunError as exc:
                        lf.write_text(f"Could not fetch log for job {jid}: {exc}\n",
                                      encoding="utf-8")
                return _dl

            tasks.append((f"log:{safe_name}", _make_job_log_task()))

        print(f"  Queuing {len(tasks)} download tasks (max {MAX_WORKERS} concurrent)...")
        with ThreadPoolExecutor(max_workers=min(MAX_WORKERS, len(tasks))) as pool:
            futures = {pool.submit(fn): label for label, fn in tasks}
            for fut in as_completed(futures):
                label = futures[fut]
                exc = fut.exception()
                if exc:
                    print(f"  ✗ {label}: {exc}")

    # ------------------------------------------------------------------
    def _download_artifact(self, name: str, dest: Path) -> None:
        dest.mkdir(parents=True, exist_ok=True)
        last_err = ""
        for attempt in range(1, 6):
            try:
                _gh([
                    "run", "download", str(self.run_id),
                    "-R", self.repo,
                    "-n", name,
                    "-D", str(dest),
                ], token=self.token)
                print(f"  ✓ {name}  →  {dest}")
                return
            except RunError as exc:
                last_err = str(exc)
                time.sleep(3 * attempt)
        raise RunError(f"Could not download artifact '{name}': {last_err}")

    # ------------------------------------------------------------------
    def _write_summary(self, conclusion: str, run_url: str, artifacts_dir: Path) -> None:
        build_summary_path = artifacts_dir / BUILD_ARTIFACT / "SUMMARY.md"
        build_summary_text = ""
        if build_summary_path.exists():
            build_summary_text = build_summary_path.read_text(encoding="utf-8")

        md = _render_summary(
            run_id=self.run_id,
            run_url=run_url,
            repo=self.repo,
            zip_path=self.zip_path,
            modrinth=self.modrinth,
            conclusion=conclusion,
            out_root=self.out_root,
            artifacts_dir=artifacts_dir,
            build_summary_text=build_summary_text,
        )
        (self.out_root / "SUMMARY.md").write_text(md, encoding="utf-8")

        result = {
            "run_id":       self.run_id,
            "run_url":      run_url,
            "repo":         self.repo,
            "zip_path":     self.zip_path,
            "modrinth":     self.modrinth,
            "conclusion":   conclusion,
            "status":       "success" if conclusion == "success" else "failed",
            "out_root":     str(self.out_root),
            "artifacts_dir":str(artifacts_dir),
            "logs_dir":     str(self.out_root / "logs"),
            "timestamp":    datetime.now(timezone.utc).isoformat(),
        }
        (self.out_root / "result.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class RunError(Exception):
    pass


def _log(msg: str) -> None:
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] {msg}")


def _normalize_parallel(raw: str) -> str:
    v = str(raw or "").strip().lower()
    if not v or v in {"all", "max", "unlimited"}:
        return "all"
    if not v.isdigit() or int(v) < 1:
        raise RunError(f"--max-parallel must be 'all' or a positive integer, got: {raw!r}")
    return v


def _detect_repo() -> str:
    try:
        url = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            stderr=subprocess.DEVNULL, text=True).strip()
    except subprocess.CalledProcessError:
        raise RunError("Could not detect GitHub repo from git remote. Use --repo owner/repo.")
    # Parse https://github.com/owner/repo or git@github.com:owner/repo
    m = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    if not m:
        raise RunError(f"Could not parse owner/repo from remote URL: {url}")
    return m.group(1)


def _detect_token() -> str:
    for var in ("GH_TOKEN", "GITHUB_TOKEN"):
        t = os.environ.get(var, "").strip()
        if t:
            return t
    # Try gh auth token
    try:
        t = subprocess.check_output(
            ["gh", "auth", "token"], stderr=subprocess.DEVNULL, text=True).strip()
        if t:
            return t
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    return ""   # gh will use its own stored credentials


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
            # Retry on transient errors
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


def _render_summary(
    *,
    run_id: int,
    run_url: str,
    repo: str,
    zip_path: str,
    modrinth: str,
    conclusion: str,
    out_root: Path,
    artifacts_dir: Path,
    build_summary_text: str,
) -> str:
    status_icon = "✓" if conclusion == "success" else "✗"
    lines = [
        "# Build Run Summary",
        "",
        f"- Status:      {status_icon} {conclusion.upper()}",
        f"- Run ID:      {run_id}",
        f"- Run URL:     {run_url}",
        f"- Repo:        {repo}",
        f"- Zip:         {zip_path}",
        f"- Modrinth:    {modrinth or '(none)'}",
        f"- Output dir:  {out_root}",
        "",
        "## Folder layout",
        "",
        "```",
        f"{out_root.name}/",
        "  SUMMARY.md              ← this file",
        "  result.json             ← machine-readable summary",
        "  artifacts/",
        "    all-mod-builds/       ← build outputs (jars, per-mod logs, SUMMARY.md)",
        "    modrinth-publish/     ← publish results (if modrinth URL was given)",
        "  logs/",
        "    run_overview.txt      ← full workflow run log",
        "    <job-name>.txt        ← per-job log",
        "    jobs.json             ← raw job list",
        "```",
        "",
    ]

    if build_summary_text:
        lines += [
            "## Build artifact SUMMARY.md",
            "",
            build_summary_text.strip(),
            "",
        ]

    # List per-job log files
    logs_dir = out_root / "logs"
    job_logs = sorted(logs_dir.glob("*.txt")) if logs_dir.exists() else []
    if job_logs:
        lines += ["## Job logs", ""]
        for lf in job_logs:
            lines.append(f"- `logs/{lf.name}`")
        lines.append("")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    raise SystemExit(main())
