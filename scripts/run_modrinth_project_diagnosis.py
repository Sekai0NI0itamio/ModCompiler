#!/usr/bin/env python3
"""
run_modrinth_project_diagnosis.py
----------------------------------
Trigger the ModrinthProjectDiagnosis workflow on GitHub Actions,
stream live progress, download all artifacts (diagnosis report,
launcher results, crash logs), and write a structured local run folder.

Usage
-----
  python3 scripts/run_modrinth_project_diagnosis.py https://modrinth.com/mod/pingfix
  python3 scripts/run_modrinth_project_diagnosis.py fabric-api
  python3 scripts/run_modrinth_project_diagnosis.py sort-chest --output-dir MyRuns
  python3 scripts/run_modrinth_project_diagnosis.py sort-chest --no-wait

The script exits 0 if all launcher tests pass, 1 on failure or error.

Output folder layout
--------------------
  <output-dir>/<run-id>/
    result.json               - machine-readable run summary
    SUMMARY.md                - human-readable summary (read this first)
    artifacts/
      diagnosis-output/       - diagnosis.json, diagnosis.txt, launcher-matrix.json
      DiagnosisLogs/          - per-jar health report bundle (from finalize job)
      launcher-results/       - per-test result.json files
      crash-logs/             - crash logs for each failed test
    logs/
      run_overview.txt        - top-level workflow run log
      <job-name>.txt          - full log for each job
      jobs.json               - raw job list from GitHub API

Requirements
------------
  - gh (GitHub CLI) must be installed and authenticated
  - git must be installed
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

WORKFLOW_FILE = "ModrinthProjectDiagnosis.yml"
DEFAULT_OUTPUT_DIR = "ProjectDiagnosisRuns"
DEFAULT_TIMEOUT = 7200
POLL_INTERVAL = 15
LOG_POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Trigger ModrinthProjectDiagnosis workflow, wait, download artifacts + logs."
    )
    parser.add_argument(
        "modrinth_project_url",
        help="Modrinth project URL, slug, or ID (e.g. https://modrinth.com/mod/fabric-api or fabric-api)",
    )
    parser.add_argument("--repo", default="",
        help="owner/repo override (default: auto-detect from git remote)")
    parser.add_argument("--no-wait", action="store_true",
        help="Don't wait for the workflow to complete (just dispatch)")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR,
        help=f"Root folder for run outputs (default: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
        help=f"Max seconds to wait for workflow (default: {DEFAULT_TIMEOUT})")
    args = parser.parse_args(argv)

    try:
        return Runner(args).run()
    except RunError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nInterrupted by user.")
        return 130


class Runner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.modrinth_ref = _normalize_modrinth_link(args.modrinth_project_url)
        self.no_wait = args.no_wait
        self.timeout = int(args.timeout)
        self.repo = (args.repo or _detect_repo()).strip()
        self.token = _detect_token()
        self.run_id: int = 0

        ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        self.out_root = Path(args.output_dir).resolve() / f"run-{ts}"

    def run(self) -> int:
        self.out_root.mkdir(parents=True, exist_ok=True)
        _ensure_gh()
        _ensure_gh_auth(self.token)

        print("=" * 60)
        print("ModrinthProjectDiagnosis — Workflow Trigger")
        print("=" * 60)
        print(f"  Repo:           {self.repo}")
        print(f"  Modrinth ref:   {self.modrinth_ref}")
        print(f"  Output dir:     {self.out_root}")
        print()

        self.run_id = self._dispatch()
        run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
        print(f"\nDispatched run #{self.run_id}")
        print(f"URL: {run_url}")

        if self.no_wait:
            print(f"\nCheck progress at:")
            print(f"  {run_url}")
            print("=" * 60)
            return 0

        print()

        conclusion = self._wait()

        print("\nDownloading artifacts and logs concurrently...")
        artifacts_dir = self.out_root / "artifacts"
        self._download_all(artifacts_dir)

        self._write_summary(conclusion, run_url, artifacts_dir)

        summary_path = self.out_root / "SUMMARY.md"
        print(f"\nRun folder:  {self.out_root}")
        print(f"Summary:     {summary_path}")
        print()
        print("=" * 60)

        # Print diagnosis summary if available
        diag_json = artifacts_dir / "diagnosis-output" / "diagnosis.json"
        if diag_json.exists():
            try:
                diag = json.loads(diag_json.read_text(encoding="utf-8"))
                print(f"  Project:    {diag.get('title', '?')}")
                print(f"  Slug:       {diag.get('slug', '?')}")
                print(f"  Versions:   {diag.get('total_versions', 0)} total, "
                      f"{diag.get('analysed_slots', 0)} slots")
                print(f"  Working:    {diag.get('working_count', 0)}")
                print(f"  Shell:      {diag.get('shell_count', 0)}")
                print(f"  Missing:    {diag.get('missing_count', 0)}")
            except Exception:
                pass

        if conclusion == "success":
            print("All launcher tests passed!")
        else:
            print("Some launcher tests failed.")
            crash_dir = artifacts_dir / "crash-logs"
            if crash_dir.exists() and any(crash_dir.iterdir()):
                print(f"  Crash logs downloaded to:")
                for item in sorted(crash_dir.iterdir()):
                    print(f"    {item}")
        print(f"  Run URL: {run_url}")
        print("=" * 60)

        return 0 if conclusion == "success" else 1

    def _dispatch(self) -> int:
        before = {r["databaseId"] for r in self._list_runs()}

        _gh([
            "workflow", "run", WORKFLOW_FILE, "-R", self.repo,
            "-f", f"modrinth_project_url={self.modrinth_ref}",
        ], token=self.token)

        deadline = time.time() + 120
        while time.time() < deadline:
            for run in self._list_runs():
                rid = run["databaseId"]
                if rid not in before:
                    return rid
            time.sleep(4)
        raise RunError("Workflow was dispatched but no new run appeared within 120 s.")

    def _list_runs(self) -> list[dict]:
        out = _gh([
            "run", "list", "-R", self.repo,
            "-w", WORKFLOW_FILE,
            "-e", "workflow_dispatch",
            "--json", "databaseId,status,conclusion,createdAt",
            "-L", "20",
        ], token=self.token)
        return json.loads(out or "[]")

    def _wait(self) -> str:
        deadline = time.time() + self.timeout
        last_status = ""
        last_jobs_print = 0.0

        print(f"Waiting for workflow run #{self.run_id} to complete...")
        print(f"  (polling every {POLL_INTERVAL}s, timeout {self.timeout}s)\n")

        while time.time() < deadline:
            info = self._run_view()
            status = info.get("status", "")
            conclusion = info.get("conclusion") or ""

            if status != last_status:
                _log(f"Status: {status}")
                last_status = status

            if time.time() - last_jobs_print >= LOG_POLL_INTERVAL:
                self._print_job_progress()
                last_jobs_print = time.time()

            if status == "completed":
                _log(f"Completed — conclusion: {conclusion}")
                return conclusion

            time.sleep(POLL_INTERVAL)

        raise RunError(f"Timed out after {self.timeout}s waiting for run {self.run_id}.")

    def _run_view(self) -> dict:
        out = _gh([
            "run", "view", str(self.run_id), "-R", self.repo,
            "--json", "status,conclusion,url,workflowName",
        ], token=self.token)
        return json.loads(out or "{}")

    def _print_job_progress(self) -> None:
        try:
            jobs = self._get_jobs()
        except RunError:
            return
        lines = []
        for job in jobs:
            name = job.get("name", "?")
            status = job.get("status", "?")
            conc = job.get("conclusion") or ""
            icon = {"success": "OK", "failure": "FAIL", "skipped": "SKIP"}.get(conc, "...")
            lines.append(f"  [{icon}] {name}  ({status}{' / ' + conc if conc else ''})")
        if lines:
            print(f"\nJob progress ({len(lines)} jobs):")
            print("\n".join(lines))
            print()

    def _get_jobs(self) -> list[dict]:
        out = _gh([
            "run", "view", str(self.run_id), "-R", self.repo,
            "--json", "jobs",
        ], token=self.token)
        data = json.loads(out or "{}")
        return data.get("jobs", [])

    def _list_artifacts(self) -> list[dict]:
        out = _gh([
            "api", f"repos/{self.repo}/actions/runs/{self.run_id}/artifacts",
        ], token=self.token)
        data = json.loads(out or "{}")
        return data.get("artifacts", [])

    def _download_all(self, artifacts_dir: Path) -> None:
        MAX_WORKERS = 30
        logs_dir = self.out_root / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)

        try:
            jobs = self._get_jobs()
            (logs_dir / "jobs.json").write_text(
                json.dumps(jobs, indent=2), encoding="utf-8")
        except RunError:
            jobs = []

        tasks: list[tuple[str, Any]] = []

        # Collect artifacts to download
        artifact_names = set()
        try:
            all_artifacts = self._list_artifacts()
            for a in all_artifacts:
                artifact_names.add(a.get("name", ""))
            print(f"  Found {len(all_artifacts)} artifact(s) total")
        except RunError as exc:
            print(f"  Could not list artifacts: {exc}")

        for aname in sorted(artifact_names):
            dest = artifacts_dir / aname
            def _make_dl(an=aname, d=dest):
                def _dl():
                    try:
                        self._download_artifact(an, d)
                    except RunError as exc:
                        print(f"  (artifact {an} not available: {exc})")
                return _dl
            tasks.append((f"artifact:{aname}", _make_dl()))

        def _dl_run_log():
            try:
                raw_log = _gh([
                    "run", "view", str(self.run_id), "-R", self.repo, "--log",
                ], token=self.token)
                (logs_dir / "run_overview.txt").write_text(raw_log, encoding="utf-8")
                print(f"  + run_overview.txt  ({len(raw_log):,} chars)")
            except RunError as exc:
                (logs_dir / "run_overview.txt").write_text(
                    f"Could not fetch run log: {exc}\n", encoding="utf-8")
        tasks.append(("log:run_overview", _dl_run_log))

        for job in jobs:
            job_id = job.get("databaseId") or job.get("id")
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
                        print(f"  + {sn}.txt  ({len(job_log):,} chars)")
                    except RunError as exc:
                        lf.write_text(f"Could not fetch log for job {jid}: {exc}\n",
                                      encoding="utf-8")
                return _dl

            tasks.append((f"log:{safe_name}", _make_job_log_task()))

        if not tasks:
            print("  Nothing to download.")
            return

        print(f"  Queuing {len(tasks)} download tasks (max {MAX_WORKERS} concurrent)...")
        with ThreadPoolExecutor(max_workers=min(MAX_WORKERS, max(len(tasks), 1))) as pool:
            futures = {pool.submit(fn): label for label, fn in tasks}
            for fut in as_completed(futures):
                label = futures[fut]
                exc = fut.exception()
                if exc:
                    print(f"  x {label}: {exc}")

    def _download_artifact(self, name: str, dest: Path) -> None:
        dest.mkdir(parents=True, exist_ok=True)
        last_err = ""
        for attempt in range(1, 4):
            try:
                _gh([
                    "run", "download", str(self.run_id),
                    "-R", self.repo,
                    "-n", name,
                    "-D", str(dest),
                ], token=self.token)
                print(f"  + {name}  ->  {dest}")
                return
            except RunError as exc:
                last_err = str(exc)
                time.sleep(3 * attempt)
        raise RunError(f"Could not download artifact '{name}': {last_err}")

    def _write_summary(self, conclusion: str, run_url: str, artifacts_dir: Path) -> None:
        jobs = []
        try:
            jobs = self._get_jobs()
        except RunError:
            pass

        passed = 0
        failed = 0
        skipped = 0
        total = 0

        launcher_results_dir = artifacts_dir / "launcher-results"
        if launcher_results_dir.exists():
            for result_file in sorted(launcher_results_dir.rglob("result.json")):
                try:
                    data = json.loads(result_file.read_text(encoding="utf-8"))
                    status = data.get("status", "")
                    if status == "passed":
                        passed += 1
                    elif status == "launcher_failed":
                        failed += 1
                    else:
                        skipped += 1
                    total += 1
                except Exception:
                    skipped += 1
                    total += 1

        if total == 0:
            passed = sum(1 for j in jobs if (j.get("conclusion") or "") == "success")
            failed = sum(1 for j in jobs if (j.get("conclusion") or "") == "failure")
            skipped = sum(1 for j in jobs if (j.get("conclusion") or "") == "skipped")
            total = len(jobs)

        status_icon = "PASS" if conclusion == "success" else "FAIL"

        # Load diagnosis overview
        diag_summary_lines = []
        diag_json = artifacts_dir / "diagnosis-output" / "diagnosis.json"
        if diag_json.exists():
            try:
                diag = json.loads(diag_json.read_text(encoding="utf-8"))
                diag_summary_lines = [
                    "",
                    "## Diagnosis Overview",
                    "",
                    f"| Metric | Value |",
                    f"|--------|-------|",
                    f"| Project | {diag.get('title', '?')} ({diag.get('slug', '?')}) |",
                    f"| Total versions | {diag.get('total_versions', 0)} |",
                    f"| Analysed slots | {diag.get('analysed_slots', 0)} |",
                    f"| Working jars | {diag.get('working_count', 0)} |",
                    f"| Shell/corrupted | {diag.get('shell_count', 0)} |",
                    f"| Missing combos | {diag.get('missing_count', 0)} |",
                    "",
                ]
            except Exception:
                pass

        lines = [
            "# ModrinthProjectDiagnosis Summary",
            "",
            f"- Status:      {status_icon} {conclusion.upper()}",
            f"- Run ID:      {self.run_id}",
            f"- Run URL:     {run_url}",
            f"- Repo:        {self.repo}",
            f"- Modrinth:    {self.modrinth_ref}",
            f"- Output dir:  {self.out_root}",
            f"- Tests:       {passed} passed, {failed} failed, {skipped} skipped (of {total})",
        ] + diag_summary_lines + [
            "## Launcher test results",
            "",
            "| Status | Loader | MC Version | Test MC | Details |",
            "|--------|--------|------------|---------|---------|",
        ]

        if launcher_results_dir.exists():
            for result_file in sorted(launcher_results_dir.rglob("result.json")):
                try:
                    data = json.loads(result_file.read_text(encoding="utf-8"))
                    s = "OK" if data.get("status") == "passed" else "FAIL"
                    loader = data.get("loader", data.get("original_loader", "?"))
                    gv = data.get("game_version", "?")
                    test_mc = data.get("test_mc", gv)
                    summary = data.get("crash_summary", "")[:60].replace("\n", " ") if s == "FAIL" else ""
                    lines.append(f"| {s} | {loader} | {gv} | {test_mc} | {summary} |")
                except Exception:
                    lines.append("| ERR | ? | ? | ? | parse error |")

        lines += [
            "",
            "## Folder layout",
            "",
            "```",
            f"{self.out_root.name}/",
            "  SUMMARY.md              <- this file",
            "  result.json             <- machine-readable summary",
            "  artifacts/",
            "    diagnosis-output/     <- diagnosis.json, diagnosis.txt, launcher-matrix.json",
            "    DiagnosisLogs/        <- per-jar health report bundle (from finalize job)",
            "    launcher-results/     <- per-test result.json files",
            "    crash-logs/           <- crash logs for failed tests",
            "  logs/",
            "    run_overview.txt      <- full workflow run log",
            "    <job-name>.txt        <- per-job log",
            "    jobs.json             <- raw job list",
            "```",
            "",
        ]

        crash_artifacts = list(artifacts_dir.glob("crash-logs"))
        if crash_artifacts:
            lines += ["## Crash log artifacts", ""]
            for d in sorted(crash_artifacts):
                files = list(d.rglob("*"))
                file_names = [f.name for f in files if f.is_file()]
                lines.append(f"- `{d.name}/` ({len(file_names)} files)")
                for fn in file_names[:10]:
                    lines.append(f"  - `{fn}`")
                if len(file_names) > 10:
                    lines.append(f"  - ... and {len(file_names) - 10} more")
            lines.append("")

        (self.out_root / "SUMMARY.md").write_text("\n".join(lines), encoding="utf-8")

        result = {
            "run_id": self.run_id,
            "run_url": run_url,
            "repo": self.repo,
            "modrinth_ref": self.modrinth_ref,
            "conclusion": conclusion,
            "status": "success" if conclusion == "success" else "failed",
            "total_jobs": total,
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
            "out_root": str(self.out_root),
            "artifacts_dir": str(artifacts_dir),
            "logs_dir": str(self.out_root / "logs"),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        (self.out_root / "result.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8")


class RunError(Exception):
    pass


def _log(msg: str) -> None:
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] {msg}")


def _normalize_modrinth_link(raw: str) -> str:
    raw = raw.strip().strip("`").strip("'").strip('"').rstrip("/")
    if not raw:
        raise RunError("Modrinth project URL or slug is required.")
    if "modrinth.com" in raw:
        return raw
    return f"https://modrinth.com/mod/{raw}"


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


def _ensure_gh_auth(token: str) -> None:
    env = os.environ.copy()
    if token:
        env["GH_TOKEN"] = token
        env["GITHUB_TOKEN"] = token
    try:
        subprocess.run(
            ["gh", "auth", "status"],
            env=env, check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        raise RunError(
            "GitHub CLI is not authenticated.\n"
            "Run `gh auth login` first, or set the GH_TOKEN environment variable."
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


if __name__ == "__main__":
    raise SystemExit(main())
