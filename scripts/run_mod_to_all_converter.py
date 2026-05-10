#!/usr/bin/env python3
"""
run_mod_to_all_converter.py
----------------------------
Triggers the "Automated Mod to ALL Version Converter" workflow on GitHub
Actions, waits for completion, and downloads all artifacts and logs.

Usage
-----
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --output-dir MyRuns

The script exits 0 on workflow success, 1 on failure or error.

Output folder layout
--------------------
  <output-dir>/<slug>-<timestamp>/
    result.json         - machine-readable run summary
    SUMMARY.md          - human-readable summary
    artifacts/
      mod-to-all-analysis-bundle/
        projectinfo.txt
        diagnosis.txt
        diagnosis.json
        first_version/...
        jars/...
    logs/
      run_overview.txt
      <job-name>.txt
      jobs.json
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


WORKFLOW_FILE = "auto-mod-to-all-version-converter.yml"
ANALYSIS_ARTIFACT = "mod-to-all-analysis-bundle"
DEFAULT_OUTPUT_DIR = "ModToAllRuns"
DEFAULT_TIMEOUT = 7200
POLL_INTERVAL = 15
LOG_POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


class RunError(Exception):
    pass


def _log(msg: str) -> None:
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] {msg}")


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
                capture_output=True, text=True, check=True,
                env=env, timeout=120,
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            last_err = e.stderr[:300] if e.stderr else str(e)
            if attempt < retries:
                time.sleep(GH_RETRY_DELAY * attempt)
        except subprocess.TimeoutExpired as e:
            last_err = str(e)
            if attempt < retries:
                time.sleep(GH_RETRY_DELAY * attempt)
    raise RunError(f"gh {' '.join(args)} failed: {last_err}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Trigger Automated Mod to ALL Version Converter workflow, wait, and download results."
    )
    parser.add_argument("modrinth_url",
        help="Modrinth project URL or slug (e.g. https://modrinth.com/mod/sort-chest)")
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


class Runner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.modrinth_url = args.modrinth_url.strip()
        self.timeout = int(args.timeout)
        self.repo = (args.repo or _detect_repo()).strip()
        self.token = _detect_token()
        self.run_id: int = 0

        # Derive slug from URL
        m = re.search(r"modrinth\.com/(?:mod|plugin|resourcepack|shader|datapack|modpack)/([^/?#]+)", self.modrinth_url)
        self.slug = m.group(1) if m else self.modrinth_url.replace("https://", "").replace("/", "-")

        ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        self.out_root = Path(args.output_dir).resolve() / f"{self.slug}-{ts}"

    def run(self) -> int:
        self.out_root.mkdir(parents=True, exist_ok=True)
        _ensure_gh()

        print(f"Repo:        {self.repo}")
        print(f"Modrinth:    {self.modrinth_url}")
        print(f"Ouput dir:   {self.out_root}")
        print()

        # 1. Dispatch workflow
        self.run_id = self._dispatch()
        run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
        print(f"Dispatched run #{self.run_id}")
        print(f"URL: {run_url}")
        print()

        # 2. Wait for completion
        conclusion = self._wait()

        # 3. Download artifacts and logs
        print("\nDownloading artifacts and logs...")
        artifacts_dir = self.out_root / "artifacts"
        self._download_all(artifacts_dir)

        # 4. Write summary
        self._write_summary(conclusion, run_url, artifacts_dir)

        summary_path = self.out_root / "SUMMARY.md"
        print(f"\nRun folder:  {self.out_root}")
        print(f"Summary:     {summary_path}")
        print(f"Workflow conclusion: {conclusion.upper()}")
        return 0 if conclusion == "success" else 1

    def _dispatch(self) -> int:
        before = {r["databaseId"] for r in self._list_runs()}
        fields = ["-f", f"modrinth_project_url={self.modrinth_url}"]
        _gh(["workflow", "run", WORKFLOW_FILE, "-R", self.repo] + fields, token=self.token)
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
        print("Waiting for workflow to complete...")
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
            icon = {"success": "\u2713", "failure": "\u2717", "skipped": "\u2013"}.get(conc, "\u2026")
            lines.append(f"  {icon} {name}  [{status}{' / ' + conc if conc else ''}]")
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

    def _download_all(self, artifacts_dir: Path) -> None:
        MAX_WORKERS = 20
        logs_dir = self.out_root / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)

        try:
            jobs = self._get_jobs()
            (logs_dir / "jobs.json").write_text(json.dumps(jobs, indent=2), encoding="utf-8")
        except RunError:
            jobs = []

        tasks: list[tuple[str, Any]] = []

        # Analysis bundle artifact
        tasks.append((
            f"artifact:{ANALYSIS_ARTIFACT}",
            lambda: self._download_artifact(ANALYSIS_ARTIFACT, artifacts_dir / ANALYSIS_ARTIFACT),
        ))

        # Run log
        def _dl_run_log():
            try:
                raw_log = _gh(["run", "view", str(self.run_id), "-R", self.repo, "--log"], token=self.token)
                (logs_dir / "run_overview.txt").write_text(raw_log, encoding="utf-8")
                print(f"  \u2713 run_overview.txt  ({len(raw_log):,} chars)")
            except RunError as exc:
                (logs_dir / "run_overview.txt").write_text(f"Could not fetch run log: {exc}\n", encoding="utf-8")
        tasks.append(("log:run_overview", _dl_run_log))

        # Per-job logs
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
                        print(f"  \u2713 {sn}.txt  ({len(job_log):,} chars)")
                    except RunError as exc:
                        lf.write_text(f"Could not fetch log for job {jid}: {exc}\n", encoding="utf-8")
                return _dl
            tasks.append((f"log:{safe_name}", _make_job_log_task()))

        print(f"  Queuing {len(tasks)} download tasks...")
        with ThreadPoolExecutor(max_workers=min(MAX_WORKERS, len(tasks))) as pool:
            futures = {pool.submit(fn): label for label, fn in tasks}
            for fut in as_completed(futures):
                label = futures[fut]
                exc = fut.exception()
                if exc:
                    print(f"  \u2717 {label}: {exc}")

    def _download_artifact(self, name: str, dest: Path) -> None:
        dest.mkdir(parents=True, exist_ok=True)
        last_err = ""
        for attempt in range(1, 6):
            try:
                _gh([
                    "run", "download", str(self.run_id),
                    "-R", self.repo, "-n", name, "-D", str(dest),
                ], token=self.token)
                print(f"  \u2713 {name}  \u2192  {dest}")
                return
            except RunError as exc:
                last_err = str(exc)
                time.sleep(3 * attempt)
        raise RunError(f"Could not download artifact '{name}': {last_err}")

    def _write_summary(self, conclusion: str, run_url: str, artifacts_dir: Path) -> None:
        bundle_dir = artifacts_dir / ANALYSIS_ARTIFACT
        lines = [
            f"# Mod to ALL Version Converter — {self.slug}",
            "",
            f"- Workflow run: [{self.run_id}]({run_url})",
            f"- Conclusion: {conclusion}",
            f"- Modrinth URL: {self.modrinth_url}",
            "",
        ]

        if bundle_dir.exists():
            # List all files in the bundle
            files = []
            for p in bundle_dir.rglob("*"):
                if p.is_file():
                    rel = p.relative_to(bundle_dir)
                    files.append((str(rel), p.stat().st_size))
            files.sort()

            lines.append("## Artifact Contents\n")
            lines.append("| File | Size |")
            lines.append("| --- | --- |")
            for name, size in files:
                if size < 1024:
                    sz = f"{size}B"
                elif size < 1024 * 1024:
                    sz = f"{size/1024:.1f}KB"
                else:
                    sz = f"{size/1024/1024:.1f}MB"
                lines.append(f"| {name} | {sz} |")

            # Count version/loader target folders
            target_dirs = sorted(d.name for d in bundle_dir.iterdir()
                                 if d.is_dir() and not d.name.startswith("."))
            non_target = {"Diagnosis.txt", "Logs.txt"}
            target_folders = [d for d in target_dirs if d not in non_target]
            if target_folders:
                lines += ["", "## Missing Version/Loader Targets"]
                for folder in target_folders:
                    pi = bundle_dir / folder / "projectinfo.txt"
                    status = "✓" if pi.exists() else "✗"
                    lines.append(f"- {folder}  [{status} projectinfo.txt]")

            # Include Diagnosis.txt
            diag = bundle_dir / "Diagnosis.txt"
            if diag.exists():
                text = diag.read_text(encoding="utf-8")
                lines += ["", "## Diagnosis.txt (first 60 lines)", "", "```"]
                for line in text.splitlines()[:60]:
                    lines.append(line)
                lines.append("```")

            # Include a sample projectinfo.txt from the first target
            if target_folders:
                first_pi = bundle_dir / target_folders[0] / "projectinfo.txt"
                if first_pi.exists():
                    text = first_pi.read_text(encoding="utf-8")
                    lines += ["", f"## {target_folders[0]}/projectinfo.txt (first 40 lines)", "", "```"]
                    for line in text.splitlines()[:40]:
                        lines.append(line)
                    lines.append("```")
        else:
            lines.append("Artifact bundle was not created (workflow may have failed early).")

        (self.out_root / "SUMMARY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

        # Write result.json
        result = {
            "run_id": self.run_id,
            "repo": self.repo,
            "modrinth_url": self.modrinth_url,
            "slug": self.slug,
            "conclusion": conclusion,
            "output_dir": str(self.out_root),
        }
        (self.out_root / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
