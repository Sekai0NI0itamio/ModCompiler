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
    parser.add_argument("--no-auto-search", action="store_true",
        help="Skip automatic source search on build failure")
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
        self.auto_search = not getattr(args, "no_auto_search", False)
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

        # 5. Auto source search for failed targets
        if conclusion != "success" and self.auto_search:
            self._auto_source_search(artifacts_dir)

        # 6. Print final status
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
    def _auto_source_search(self, artifacts_dir: Path) -> None:
        """
        After a failed build, automatically:
        1. Scan all failed build.log files for 'cannot find symbol' / 'does not exist' errors
        2. Extract the missing class/package names
        3. Group by (version, loader)
        4. Trigger one AI Source Search per unique failing combo
        5. Download results and write them to auto-source-search/ in the run folder
        6. Print a summary of what was found so the IDE agent can use it immediately

        This saves the agent from having to manually run source searches after every
        failed build.
        """
        mods_dir = artifacts_dir / BUILD_ARTIFACT / "mods"
        if not mods_dir.exists():
            return

        print("\n" + "=" * 60)
        print("AUTO SOURCE SEARCH — analyzing failed builds")
        print("=" * 60)

        # ----------------------------------------------------------------
        # Step 1: Parse all failed build logs and extract error info
        # ----------------------------------------------------------------
        # Map: (version, loader) -> set of missing symbols
        failing: dict[tuple[str, str], set[str]] = {}

        for mod_dir in sorted(mods_dir.iterdir()):
            result_file = mod_dir / "result.json"
            build_log   = mod_dir / "build.log"
            if not result_file.exists() or not build_log.exists():
                continue

            try:
                result = json.loads(result_file.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                continue

            if result.get("status") == "success":
                continue

            # Extract version and loader from result.json
            version = result.get("minecraft_version") or result.get("version", "")
            loader  = result.get("loader", "")
            if not version or not loader:
                # Try to parse from folder name: daycounter-forge-1-21-1
                name = mod_dir.name
                parts = name.split("-")
                # Find loader
                for l in ("forge", "fabric", "neoforge"):
                    if l in parts:
                        loader = l
                        # Version is everything after the loader
                        idx = parts.index(l)
                        version = ".".join(parts[idx+1:])
                        break

            if not version or not loader:
                continue

            # Parse build.log for missing symbols
            log_text = build_log.read_text(encoding="utf-8", errors="replace")
            symbols = _extract_missing_symbols(log_text)

            if symbols:
                key = (version, loader)
                if key not in failing:
                    failing[key] = set()
                failing[key].update(symbols)

        if not failing:
            print("  No symbol errors found in failed builds — skipping source search.")
            return

        print(f"  Found {len(failing)} unique version+loader combos with symbol errors:")
        for (ver, ldr), syms in sorted(failing.items()):
            print(f"    {ver}+{ldr}: {', '.join(sorted(syms)[:5])}"
                  + (f" +{len(syms)-5} more" if len(syms) > 5 else ""))

        # ----------------------------------------------------------------
        # Step 2: Trigger source searches for all failing combos in parallel
        # ----------------------------------------------------------------
        search_dir = self.out_root / "auto-source-search"
        search_dir.mkdir(exist_ok=True)

        # Trigger all searches simultaneously
        print(f"\n  Triggering {len(failing)} source searches simultaneously...")
        search_runs: dict[tuple[str, str], int] = {}  # (ver, ldr) -> run_id

        for (version, loader), symbols in sorted(failing.items()):
            # Build query from the missing symbols (max 5 most useful ones)
            queries = _symbols_to_queries(symbols)
            query_str = ",".join(queries[:6])

            print(f"    Triggering search for {version}+{loader}: {query_str[:60]}...")
            run_id = _trigger_source_search(
                version=version,
                loader=loader,
                queries=query_str,
                repo=self.repo,
                token=self.token,
            )
            if run_id:
                search_runs[(version, loader)] = run_id
                print(f"      → run {run_id}")
            else:
                print(f"      → TRIGGER FAILED")
            time.sleep(2)  # avoid rate limiting + ensure unique timestamps

        if not search_runs:
            print("  All source search triggers failed.")
            return

        # ----------------------------------------------------------------
        # Step 3: Wait for all searches to complete
        # ----------------------------------------------------------------
        print(f"\n  Waiting for {len(search_runs)} source searches to complete...")
        in_flight = dict(search_runs)  # (ver, ldr) -> run_id
        results: dict[tuple[str, str], dict] = {}

        deadline = time.time() + 1800  # 30 min max
        while in_flight and time.time() < deadline:
            time.sleep(20)
            completed = []
            for (ver, ldr), run_id in list(in_flight.items()):
                try:
                    data_str = _gh([
                        "run", "view", str(run_id), "-R", self.repo,
                        "--json", "status,conclusion",
                    ], token=self.token)
                    data = json.loads(data_str)
                except (RunError, json.JSONDecodeError):
                    continue

                if data.get("status") != "completed":
                    continue

                conclusion = data.get("conclusion", "")
                print(f"    {ver}+{ldr} → {conclusion}")

                # Download the artifact
                artifact_name = f"ai-source-search-{ver}-{ldr}"
                out_dir = search_dir / f"{ver}-{ldr}"
                out_dir.mkdir(exist_ok=True)

                try:
                    _gh([
                        "run", "download", str(run_id), "-R", self.repo,
                        "-D", str(out_dir),
                    ], token=self.token)
                except RunError:
                    pass

                # Parse results
                info_file = out_dir / "search-info.txt"
                # Also check nested (artifact may be in a subdirectory)
                if not info_file.exists():
                    for nested in out_dir.rglob("search-info.txt"):
                        info_file = nested
                        break

                java_count = 0
                if info_file.exists():
                    for line in info_file.read_text(encoding="utf-8").splitlines():
                        if line.startswith("Java files:"):
                            try:
                                java_count = int(line.split(":")[1].strip())
                            except ValueError:
                                pass

                results[(ver, ldr)] = {
                    "run_id": run_id,
                    "conclusion": conclusion,
                    "java_count": java_count,
                    "out_dir": str(out_dir),
                    "symbols": sorted(failing.get((ver, ldr), set())),
                }
                completed.append((ver, ldr))

            for key in completed:
                del in_flight[key]

            if in_flight:
                still = [f"{v}+{l}" for v, l in in_flight]
                print(f"    Still waiting: {', '.join(still[:3])}"
                      + (f" +{len(still)-3} more" if len(still) > 3 else ""))

        # ----------------------------------------------------------------
        # Step 4: Print summary and write report
        # ----------------------------------------------------------------
        print("\n" + "=" * 60)
        print("AUTO SOURCE SEARCH RESULTS")
        print("=" * 60)

        report_lines = [
            "# Auto Source Search Report",
            "",
            "Generated automatically after build failure.",
            "Each section shows what was found for a failing version+loader combo.",
            "",
        ]

        for (ver, ldr), info in sorted(results.items()):
            java_count = info["java_count"]
            out_dir = Path(info["out_dir"])
            symbols = info["symbols"]

            status = "✓" if java_count > 0 else "✗"
            print(f"\n  {status} {ver}+{ldr}  (java_count={java_count})")
            print(f"    Missing symbols: {', '.join(symbols[:5])}")

            report_lines += [
                f"## {ver} + {ldr}",
                "",
                f"**Missing symbols**: `{'`, `'.join(symbols)}`",
                f"**Java files found**: {java_count}",
                f"**Artifact**: `{out_dir}`",
                "",
            ]

            if java_count == 0:
                report_lines.append("*Source search found no files — check gradle-output.log*\n")
                continue

            # Print key findings from the search
            # 1. Show matching query results
            queries_dir = out_dir / "queries"
            if not queries_dir.exists():
                # Check nested
                for nested in out_dir.rglob("queries"):
                    if nested.is_dir():
                        queries_dir = nested
                        break

            if queries_dir.exists():
                for qf in sorted(queries_dir.glob("*.txt")):
                    content = qf.read_text(encoding="utf-8", errors="replace")
                    match_count = content.count("===")
                    if match_count > 0:
                        print(f"    Query '{qf.stem}': {match_count} matches")
                        report_lines.append(f"### Query: `{qf.stem}` ({match_count} matches)\n")
                        # Show first 30 lines of matches
                        lines = content.splitlines()
                        snippet = "\n".join(lines[:40])
                        report_lines.append(f"```\n{snippet}\n```\n")

            # 2. Show full class files found
            classes_dir = out_dir / "full-classes"
            if not classes_dir.exists():
                for nested in out_dir.rglob("full-classes"):
                    if nested.is_dir():
                        classes_dir = nested
                        break

            if classes_dir.exists():
                class_files = list(classes_dir.glob("*.java"))
                if class_files:
                    print(f"    Full class files: {[f.name for f in class_files[:5]]}")
                    report_lines.append(f"### Full class files found\n")
                    for cf in class_files[:10]:
                        content = cf.read_text(encoding="utf-8", errors="replace")
                        lines = content.splitlines()
                        snippet = "\n".join(lines[:60])
                        report_lines.append(f"**{cf.name}**:\n```java\n{snippet}\n```\n")

            # 3. Show render/event overview
            overview_dir = out_dir / "api-overview"
            if not overview_dir.exists():
                for nested in out_dir.rglob("api-overview"):
                    if nested.is_dir():
                        overview_dir = nested
                        break

            if overview_dir.exists():
                for fname in ["render-gui-classes.txt", "event-classes.txt",
                               "modloader-api-classes.txt"]:
                    fp = overview_dir / fname
                    if fp.exists():
                        lines = [l for l in fp.read_text(encoding="utf-8").splitlines() if l.strip()]
                        if lines:
                            label = fname.replace(".txt", "").replace("-", " ").title()
                            print(f"    {label}: {len(lines)} files")
                            report_lines.append(f"### {label} ({len(lines)} files)\n")
                            report_lines.append("```\n" + "\n".join(lines[:30]) + "\n```\n")

                # Full API source files
                full_api = list(overview_dir.glob("full_*.java"))
                if full_api:
                    print(f"    Full API source files: {len(full_api)}")
                    report_lines.append(f"### Full API Source Files ({len(full_api)} files)\n")
                    for cf in full_api[:5]:
                        content = cf.read_text(encoding="utf-8", errors="replace")
                        lines = content.splitlines()
                        snippet = "\n".join(lines[:80])
                        report_lines.append(f"**{cf.name}**:\n```java\n{snippet}\n```\n")

        # Write the report
        report_path = self.out_root / "AUTO_SOURCE_SEARCH_REPORT.md"
        report_path.write_text("\n".join(report_lines), encoding="utf-8")
        print(f"\n  Full report: {report_path}")
        print(f"  Artifacts:   {search_dir}")
        print("=" * 60)

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
# Auto source search helpers
# ---------------------------------------------------------------------------

# Patterns that indicate a missing class/package in Java compile errors
_SYMBOL_PATTERNS = [
    re.compile(r"symbol:\s+class\s+(\w+)"),
    re.compile(r"package\s+([\w.]+)\s+does not exist"),
    re.compile(r"cannot find symbol.*?class\s+(\w+)", re.DOTALL),
    re.compile(r"error: cannot find symbol\s+symbol:\s+class\s+(\w+)", re.DOTALL),
]

# Classes that are too generic to be useful search queries
_SKIP_SYMBOLS = {
    "String", "int", "long", "boolean", "void", "Object", "List", "Map",
    "Set", "Optional", "File", "Path", "IOException", "Exception",
    "Override", "Nullable", "NotNull", "ApiStatus", "Deprecated",
    "Cancelable", "Event", "IModBusEvent", "SubscribeEvent",
}


def _extract_missing_symbols(log_text: str) -> set[str]:
    """Extract missing class/package names from a Java compile error log."""
    symbols: set[str] = set()

    for line in log_text.splitlines():
        # "symbol:   class Foo"
        m = re.search(r"symbol:\s+class\s+(\w+)", line)
        if m:
            sym = m.group(1)
            if sym not in _SKIP_SYMBOLS and len(sym) > 3:
                symbols.add(sym)
            continue

        # "package net.minecraftforge.client.event does not exist"
        m = re.search(r"package\s+([\w.]+)\s+does not exist", line)
        if m:
            pkg = m.group(1)
            # Extract the last component as the class name
            parts = pkg.split(".")
            if len(parts) >= 2:
                # Add the full package and the last component
                symbols.add(parts[-1])
            continue

    return symbols


def _symbols_to_queries(symbols: set[str]) -> list[str]:
    """
    Convert a set of missing symbol names to useful source search queries.
    Prioritizes render/event/GUI classes since those are most commonly wrong.
    """
    # Priority order: render/event/GUI classes first
    priority_keywords = [
        "Render", "Gui", "Hud", "Overlay", "Layer", "Event",
        "Register", "Callback", "Draw", "Graphics",
    ]

    prioritized = []
    others = []

    for sym in sorted(symbols):
        if any(kw.lower() in sym.lower() for kw in priority_keywords):
            prioritized.append(sym)
        else:
            others.append(sym)

    # Return prioritized first, then others, max 8 total
    result = prioritized[:5] + others[:3]
    return result[:8] if result else list(symbols)[:6]


def _trigger_source_search(
    version: str,
    loader: str,
    queries: str,
    repo: str,
    token: str,
) -> int | None:
    """
    Trigger the AI Source Search workflow for a specific version+loader.
    Returns the run_id or None if trigger failed.
    Records timestamp before trigger to find the correct run ID.
    """
    before_trigger = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    try:
        _gh([
            "workflow", "run", "ai-source-search.yml",
            "-R", repo,
            "-f", f"minecraft_version={version}",
            "-f", f"loader={loader}",
            "-f", f"queries={queries}",
            "-f", "file_patterns=*.java",
            "-f", "context_lines=8",
            "-f", "dump_full_class=yes",
        ], token=token)
    except RunError:
        return None

    # Poll for the new run (created after our trigger time)
    for _ in range(15):
        time.sleep(4)
        try:
            runs_str = _gh([
                "run", "list", "-R", repo,
                "--workflow=ai-source-search.yml",
                "--limit=10",
                "--json=databaseId,createdAt,status",
            ], token=token)
            runs = json.loads(runs_str or "[]")
            for run in runs:
                if run.get("createdAt", "") >= before_trigger:
                    return run["databaseId"]
        except (RunError, json.JSONDecodeError):
            pass

    return None


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    raise SystemExit(main())
