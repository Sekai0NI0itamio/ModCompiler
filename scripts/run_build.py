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

# DIF core — unified source search + issue matching
_SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(_SCRIPT_DIR))
try:
    from dif_core import (
        SourceSearchEngine,
        extract_missing_symbols,
        match_errors_to_dif,
        print_search_results,
        _symbols_to_queries,
        is_infrastructure_failure,
        _symbols_are_generic,
        _trigger_source_search_workflow as _trigger_source_search,
        _gh as _dif_gh,
    )
    _DIF_AVAILABLE = True
except ImportError:
    _DIF_AVAILABLE = False

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

        # 7. Always print the failed-only reminder so the agent never
        #    wastes GitHub Actions minutes rebuilding already-green targets.
        print()
        print("=" * 60)
        if conclusion == "success":
            print("✓ All targets passed.")
        else:
            print("✗ Some targets failed.")
        print()
        print("IMPORTANT — next run tip:")
        print("  If you fix only the failing targets and re-run, use")
        print("  --failed-only when regenerating your bundle zip so that")
        print("  already-green targets are NOT included in the new zip.")
        print()
        print("  Example:")
        print("    python3 scripts/generate_<mod>_bundle.py --failed-only")
        print("    git add incoming/ && git commit -m 'Fix failing targets'")
        print("    git push")
        print("    python3 scripts/run_build.py incoming/<bundle>.zip")
        print()
        print("  Rebuilding targets that already succeeded wastes GitHub")
        print("  Actions minutes, slows the overall build, and can cause")
        print("  the Modrinth publish step to skip already-uploaded versions.")
        print("  Only include targets that actually need to be fixed.")
        print("=" * 60)

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
        After a failed build:
        1. Scan all failed build.log files for compile errors
        2. Extract missing symbols
        3. Search DecompiledMinecraftSourceCode/ locally (instant)
        4. Fall back to GitHub Actions workflow if folder is missing
        5. Match errors against DIF (Documentary of Issues and Fixes) database
        6. Write AUTO_SOURCE_SEARCH_REPORT.md with full findings

        All source search logic is delegated to dif_core.py.
        """
        if not _DIF_AVAILABLE:
            print("\n  (dif_core not available — skipping auto source search)")
            return

        mods_dir = artifacts_dir / BUILD_ARTIFACT / "mods"
        if not mods_dir.exists():
            return

        print("\n" + "=" * 60)
        print("AUTO SOURCE SEARCH — analyzing failed builds")
        print("=" * 60)

        # ── Step 1: Parse failed build logs ──────────────────────────
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

            version = result.get("minecraft_version") or result.get("version", "")
            loader  = result.get("loader", "")
            if not version or not loader:
                name = mod_dir.name
                parts = name.split("-")
                for l in ("forge", "fabric", "neoforge"):
                    if l in parts:
                        loader = l
                        idx = parts.index(l)
                        version = ".".join(parts[idx+1:])
                        break

            if not version or not loader:
                continue

            log_text = build_log.read_text(encoding="utf-8", errors="replace")
            symbols = extract_missing_symbols(log_text)

            # ── Infrastructure failure detection ──────────────────────
            # If the build failed due to Gradle/dependency/config issues
            # (not a missing Minecraft API), source search won't help.
            is_infra, infra_reason = is_infrastructure_failure(log_text)
            if is_infra:
                print(f"\n  ⚠️  {mod_dir.name}: skipping source search")
                print(f"       Reason: {infra_reason}")
                # Still run DIF matching — it may have a relevant entry
                dif_matches = match_errors_to_dif(log_text)
                if dif_matches:
                    print(f"  💡 DIF matches for {mod_dir.name}:")
                    for score, entry in dif_matches[:3]:
                        pct = int(score * 100)
                        print(f"     {pct}%  [{entry.id}] {entry.title}")
                        print(f"          → dif/{entry.path.name}")
                continue

            # ── DIF matching for this failed build ────────────────────
            dif_matches = match_errors_to_dif(log_text)
            if dif_matches:
                print(f"\n  💡 DIF matches for {mod_dir.name}:")
                for score, entry in dif_matches[:3]:
                    pct = int(score * 100)
                    print(f"     {pct}%  [{entry.id}] {entry.title}")
                    print(f"          → dif/{entry.path.name}")

            if symbols:
                # Skip if all extracted symbols are generic package segments —
                # this indicates a broken classpath, not a targeted API issue.
                if _symbols_are_generic(symbols):
                    print(f"\n  ⚠️  {mod_dir.name}: skipping source search")
                    print(f"       Reason: only generic package segments found "
                          f"({', '.join(sorted(symbols)[:5])}) — likely broken classpath")
                    continue

                key = (version, loader)
                if key not in failing:
                    failing[key] = set()
                failing[key].update(symbols)

        if not failing:
            print("  No symbol errors found in failed builds — skipping source search.")
            return

        print(f"\n  Found {len(failing)} unique version+loader combos with symbol errors:")
        for (ver, ldr), syms in sorted(failing.items()):
            print(f"    {ver}+{ldr}: {', '.join(sorted(syms)[:5])}"
                  + (f" +{len(syms)-5} more" if len(syms) > 5 else ""))

        # ── Step 2: Source search via dif_core ────────────────────────
        search_dir = self.out_root / "auto-source-search"
        search_dir.mkdir(exist_ok=True)

        engine = SourceSearchEngine(repo=self.repo, token=self.token)
        results: dict[tuple[str, str], dict] = {}

        print()
        for (version, loader), symbols in sorted(failing.items()):
            slug = f"{version}-{loader}"
            out_dir = search_dir / slug
            print(f"  🔍 {slug}: searching...")

            result = engine.search(
                version=version, loader=loader,
                symbols=list(symbols), out_dir=out_dir,
            )
            results[(version, loader)] = result

            java_count = result.get("java_count", 0)
            qr = result.get("query_results", {})
            fc = result.get("full_classes", [])
            total_matches = sum(qr.values()) if isinstance(list(qr.values())[:1] and list(qr.values())[0], int) else 0
            if isinstance(qr, dict):
                total_matches = sum(v if isinstance(v, int) else len(v) for v in qr.values())
            print(f"      → {len(qr)}/{len(_symbols_to_queries(symbols))} queries matched, "
                  f"{len(fc)} class files captured, "
                  f"{java_count:,} java files searched")

        # ── Step 3: Print summary and write report ────────────────────
        print("\n" + "=" * 60)
        print("AUTO SOURCE SEARCH RESULTS")
        print("=" * 60)

        report_lines = [
            "# Auto Source Search Report",
            "",
            "Generated automatically after build failure.",
            "Source search via dif_core.py (local repo first, workflow fallback).",
            "DIF matches shown where applicable.",
            "",
        ]

        for (ver, ldr), info in sorted(results.items()):
            java_count = info.get("java_count", 0)
            out_dir    = Path(info["out_dir"])
            symbols    = info.get("symbols", [])
            source_tag = f"[{info.get('source', '?')}]"

            status = "✓" if java_count > 0 else "✗"
            print(f"\n  {status} {ver}+{ldr} {source_tag}  ({java_count:,} java files)")
            print(f"    Missing symbols: {', '.join(symbols[:5])}"
                  + (f" +{len(symbols)-5} more" if len(symbols) > 5 else ""))

            report_lines += [
                f"## {ver} + {ldr}  {source_tag}",
                "",
                f"**Missing symbols**: `{'`, `'.join(symbols)}`",
                f"**Java files searched**: {java_count:,}",
                f"**Results folder**: `{out_dir}`",
                "",
            ]

            if java_count == 0:
                report_lines.append(
                    "*Source search found no files — version may not be in "
                    "DecompiledMinecraftSourceCode/ and workflow fallback failed.*\n")
                continue

            qr = info.get("query_results", {})
            if isinstance(qr, dict):
                for q, count in qr.items():
                    cnt = count if isinstance(count, int) else len(count)
                    if cnt:
                        print(f"    Query '{q}': {cnt} match lines")
                        report_lines.append(f"### Query: `{q}` ({cnt} match lines)\n")
                        qfile = out_dir / "queries" / f"{re.sub(r'[^A-Za-z0-9._-]+', '_', q)}.txt"
                        if qfile.exists():
                            snippet = "\n".join(qfile.read_text(encoding="utf-8").splitlines()[:50])
                            report_lines.append(f"```\n{snippet}\n```\n")

            fc = info.get("full_classes", [])
            if fc:
                print(f"    Full class files captured: {fc[:5]}")
                report_lines.append(f"### Full class files ({len(fc)} captured)\n")
                classes_dir = out_dir / "full-classes"
                if classes_dir.exists():
                    for cf in list(classes_dir.glob("*.java"))[:8]:
                        content = cf.read_text(encoding="utf-8", errors="replace")
                        snippet = "\n".join(content.splitlines()[:60])
                        report_lines.append(f"**{cf.name}**:\n```java\n{snippet}\n```\n")

            overview_dir = out_dir / "api-overview"
            if overview_dir.exists():
                for fname in ["render-gui-classes.txt", "event-classes.txt",
                              "modloader-api-classes.txt"]:
                    fp = overview_dir / fname
                    if fp.exists():
                        ov_lines = [l for l in fp.read_text(encoding="utf-8").splitlines() if l.strip()]
                        if ov_lines:
                            label = fname.replace(".txt", "").replace("-", " ").title()
                            print(f"    {label}: {len(ov_lines)} files")
                            report_lines.append(f"### {label} ({len(ov_lines)} files)\n")
                            report_lines.append("```\n" + "\n".join(ov_lines[:30]) + "\n```\n")

        report_path = self.out_root / "AUTO_SOURCE_SEARCH_REPORT.md"
        report_path.write_text("\n".join(report_lines), encoding="utf-8")
        print(f"\n  Full report: {report_path}")
        print(f"  Artifacts:   {search_dir}")
        print("=" * 60)
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
        # Step 2: For each failing combo, search the pre-committed repo
        #         sources first. Only fall back to the workflow if the
        #         DecompiledMinecraftSourceCode/<version>-<loader>/ folder
        #         is missing from the repository.
        # ----------------------------------------------------------------
        # Locate the repo root (two levels up from scripts/)
        repo_root = Path(__file__).resolve().parents[1]
        sources_root = repo_root / "DecompiledMinecraftSourceCode"

        search_dir = self.out_root / "auto-source-search"
        search_dir.mkdir(exist_ok=True)

        results: dict[tuple[str, str], dict] = {}
        workflow_needed: list[tuple[str, str, set]] = []  # (ver, ldr, symbols)

        print()
        for (version, loader), symbols in sorted(failing.items()):
            slug = f"{version}-{loader}"
            sources_folder = sources_root / slug

            java_files = list(sources_folder.rglob("*.java")) if sources_folder.is_dir() else []

            if not java_files:
                # Folder missing — queue for workflow fallback
                print(f"  ⚠  {slug}: not in DecompiledMinecraftSourceCode/ — will trigger workflow")
                workflow_needed.append((version, loader, symbols))
                continue

            # ── Repo search (instant) ────────────────────────────────
            print(f"  🔍 {slug}: searching {len(java_files):,} local files...")
            out_dir = search_dir / slug
            out_dir.mkdir(exist_ok=True)

            queries = _symbols_to_queries(symbols)
            query_results: dict[str, list[str]] = {}   # query -> matching lines
            full_classes: dict[str, str] = {}           # filename -> content

            for query in queries:
                matches = []
                for jf in java_files:
                    try:
                        text = jf.read_text(encoding="utf-8", errors="replace")
                    except OSError:
                        continue
                    if query.lower() in text.lower():
                        # Collect matching lines with 3 lines of context
                        lines = text.splitlines()
                        for i, line in enumerate(lines):
                            if query.lower() in line.lower():
                                start = max(0, i - 3)
                                end   = min(len(lines), i + 4)
                                ctx   = lines[start:end]
                                rel   = str(jf.relative_to(sources_folder))
                                matches.append(f"=== {rel} (line {i+1}) ===")
                                matches.extend(ctx)
                                matches.append("")
                                # Capture the full class file (once per file)
                                fname = jf.name
                                if fname not in full_classes:
                                    full_classes[fname] = text
                if matches:
                    query_results[query] = matches

            # Write query result files
            queries_dir = out_dir / "queries"
            queries_dir.mkdir(exist_ok=True)
            for q, lines in query_results.items():
                safe_q = re.sub(r"[^A-Za-z0-9._-]+", "_", q)
                (queries_dir / f"{safe_q}.txt").write_text(
                    "\n".join(lines), encoding="utf-8")

            # Write full class files
            classes_dir = out_dir / "full-classes"
            classes_dir.mkdir(exist_ok=True)
            for fname, content in list(full_classes.items())[:20]:
                safe_fname = re.sub(r"[^A-Za-z0-9._-]+", "_", fname)
                (classes_dir / safe_fname).write_text(content, encoding="utf-8")

            # Build API overview lists
            overview_dir = out_dir / "api-overview"
            overview_dir.mkdir(exist_ok=True)
            event_files   = [str(f.relative_to(sources_folder))
                             for f in java_files if "event" in f.name.lower()]
            render_files  = [str(f.relative_to(sources_folder))
                             for f in java_files
                             if any(k in f.name.lower()
                                    for k in ("render", "gui", "hud", "overlay", "layer"))]
            loader_files  = [str(f.relative_to(sources_folder))
                             for f in java_files
                             if any(k in str(f).lower()
                                    for k in ("minecraftforge", "neoforged", "fabricmc"))]
            if event_files:
                (overview_dir / "event-classes.txt").write_text(
                    "\n".join(event_files[:200]), encoding="utf-8")
            if render_files:
                (overview_dir / "render-gui-classes.txt").write_text(
                    "\n".join(render_files[:200]), encoding="utf-8")
            if loader_files:
                (overview_dir / "modloader-api-classes.txt").write_text(
                    "\n".join(loader_files[:200]), encoding="utf-8")

            # Write all-java-files.txt
            (out_dir / "all-java-files.txt").write_text(
                "\n".join(str(f.relative_to(sources_folder)) for f in sorted(java_files)),
                encoding="utf-8")

            total_matches = sum(len(v) for v in query_results.values())
            print(f"      → {len(query_results)}/{len(queries)} queries matched, "
                  f"{len(full_classes)} class files captured, "
                  f"{total_matches} match lines")

            results[(version, loader)] = {
                "source": "repo",
                "java_count": len(java_files),
                "query_results": {q: len(v) for q, v in query_results.items()},
                "full_classes": list(full_classes.keys()),
                "out_dir": str(out_dir),
                "symbols": sorted(symbols),
            }

        # ----------------------------------------------------------------
        # Step 3: Workflow fallback for any versions not in the repo
        # ----------------------------------------------------------------
        if workflow_needed:
            print(f"\n  Triggering {len(workflow_needed)} workflow searches "
                  f"(missing from DecompiledMinecraftSourceCode/)...")
            search_runs: dict[tuple[str, str], int] = {}

            for (version, loader, symbols) in workflow_needed:
                queries = _symbols_to_queries(symbols)
                query_str = ",".join(queries[:6])
                print(f"    Triggering {version}+{loader}: {query_str[:60]}...")
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
                time.sleep(2)

            # Wait for workflow runs to complete
            if search_runs:
                print(f"\n  Waiting for {len(search_runs)} workflow searches to complete...")
                in_flight = dict(search_runs)
                deadline = time.time() + 1800
                while in_flight and time.time() < deadline:
                    time.sleep(20)
                    completed_keys = []
                    for (ver, ldr), run_id in list(in_flight.items()):
                        try:
                            data = json.loads(_gh([
                                "run", "view", str(run_id), "-R", self.repo,
                                "--json", "status,conclusion",
                            ], token=self.token))
                        except (RunError, json.JSONDecodeError):
                            continue
                        if data.get("status") != "completed":
                            continue
                        conclusion = data.get("conclusion", "")
                        print(f"    {ver}+{ldr} → {conclusion}")
                        out_dir = search_dir / f"{ver}-{ldr}"
                        out_dir.mkdir(exist_ok=True)
                        try:
                            _gh(["run", "download", str(run_id), "-R", self.repo,
                                 "-D", str(out_dir)], token=self.token)
                        except RunError:
                            pass
                        # Count java files from search-info.txt
                        java_count = 0
                        for info_file in out_dir.rglob("search-info.txt"):
                            for line in info_file.read_text(encoding="utf-8").splitlines():
                                if line.startswith("Java files:"):
                                    try:
                                        java_count = int(line.split(":")[1].strip())
                                    except ValueError:
                                        pass
                            break
                        results[(ver, ldr)] = {
                            "source": "workflow",
                            "run_id": run_id,
                            "conclusion": conclusion,
                            "java_count": java_count,
                            "out_dir": str(out_dir),
                            "symbols": sorted(failing.get((ver, ldr), set())),
                        }
                        completed_keys.append((ver, ldr))
                    for key in completed_keys:
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
            "Repo sources searched directly from DecompiledMinecraftSourceCode/ (instant).",
            "Workflow fallback used only for versions not yet in the repository.",
            "",
        ]

        for (ver, ldr), info in sorted(results.items()):
            java_count  = info["java_count"]
            out_dir     = Path(info["out_dir"])
            symbols     = info["symbols"]
            source_tag  = f"[{info['source']}]"

            status = "✓" if java_count > 0 else "✗"
            print(f"\n  {status} {ver}+{ldr} {source_tag}  ({java_count:,} java files)")
            print(f"    Missing symbols: {', '.join(symbols[:5])}"
                  + (f" +{len(symbols)-5} more" if len(symbols) > 5 else ""))

            report_lines += [
                f"## {ver} + {ldr}  {source_tag}",
                "",
                f"**Missing symbols**: `{'`, `'.join(symbols)}`",
                f"**Java files searched**: {java_count:,}",
                f"**Results folder**: `{out_dir}`",
                "",
            ]

            if java_count == 0:
                report_lines.append(
                    "*Source search found no files — version may not be in "
                    "DecompiledMinecraftSourceCode/ and workflow fallback failed.*\n")
                continue

            # Query match summary
            if info["source"] == "repo":
                qr = info.get("query_results", {})
                for q, count in qr.items():
                    if count:
                        print(f"    Query '{q}': {count} match lines")
                        report_lines.append(f"### Query: `{q}` ({count} match lines)\n")
                        qfile = out_dir / "queries" / f"{re.sub(r'[^A-Za-z0-9._-]+', '_', q)}.txt"
                        if qfile.exists():
                            snippet = "\n".join(
                                qfile.read_text(encoding="utf-8").splitlines()[:50])
                            report_lines.append(f"```\n{snippet}\n```\n")
            else:
                # Workflow artifact — read queries dir
                queries_dir = out_dir / "queries"
                if not queries_dir.exists():
                    for nested in out_dir.rglob("queries"):
                        if nested.is_dir():
                            queries_dir = nested
                            break
                if queries_dir.exists():
                    for qf in sorted(queries_dir.glob("*.txt")):
                        content = qf.read_text(encoding="utf-8", errors="replace")
                        mc = content.count("===")
                        if mc:
                            print(f"    Query '{qf.stem}': {mc} matches")
                            report_lines.append(f"### Query: `{qf.stem}` ({mc} matches)\n")
                            snippet = "\n".join(content.splitlines()[:40])
                            report_lines.append(f"```\n{snippet}\n```\n")

            # Full class files
            fc = info.get("full_classes", []) if info["source"] == "repo" else []
            if not fc:
                classes_dir = out_dir / "full-classes"
                if not classes_dir.exists():
                    for nested in out_dir.rglob("full-classes"):
                        if nested.is_dir():
                            classes_dir = nested
                            break
                if classes_dir.exists():
                    fc = [f.name for f in classes_dir.glob("*.java")]
            if fc:
                print(f"    Full class files captured: {fc[:5]}")
                report_lines.append(f"### Full class files ({len(fc)} captured)\n")
                classes_dir = out_dir / "full-classes"
                if not classes_dir.exists():
                    for nested in out_dir.rglob("full-classes"):
                        if nested.is_dir():
                            classes_dir = nested
                            break
                if classes_dir.exists():
                    for cf in list(classes_dir.glob("*.java"))[:8]:
                        content = cf.read_text(encoding="utf-8", errors="replace")
                        snippet = "\n".join(content.splitlines()[:60])
                        report_lines.append(f"**{cf.name}**:\n```java\n{snippet}\n```\n")

            # API overview
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
                        ov_lines = [l for l in fp.read_text(encoding="utf-8").splitlines()
                                    if l.strip()]
                        if ov_lines:
                            label = fname.replace(".txt", "").replace("-", " ").title()
                            print(f"    {label}: {len(ov_lines)} files")
                            report_lines.append(f"### {label} ({len(ov_lines)} files)\n")
                            report_lines.append(
                                "```\n" + "\n".join(ov_lines[:30]) + "\n```\n")

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
# Auto source search helpers — now delegated to dif_core.py
# (kept here as thin wrappers for backward compatibility)
# ---------------------------------------------------------------------------

def _extract_missing_symbols(log_text: str) -> set[str]:
    if _DIF_AVAILABLE:
        return extract_missing_symbols(log_text)
    # Fallback inline implementation
    symbols: set[str] = set()
    _SKIP = {"String","int","long","boolean","void","Object","List","Map","Set",
              "Optional","File","Path","IOException","Exception","Override",
              "Nullable","NotNull","ApiStatus","Deprecated","Cancelable","Event",
              "IModBusEvent","SubscribeEvent"}
    for line in log_text.splitlines():
        m = re.search(r"symbol:\s+class\s+(\w+)", line)
        if m and m.group(1) not in _SKIP and len(m.group(1)) > 3:
            symbols.add(m.group(1))
            continue
        m = re.search(r"package\s+([\w.]+)\s+does not exist", line)
        if m:
            parts = m.group(1).split(".")
            if len(parts) >= 2:
                symbols.add(parts[-1])
    return symbols


def _symbols_to_queries(symbols: set[str]) -> list[str]:
    if _DIF_AVAILABLE:
        from dif_core import _symbols_to_queries as _sq
        return _sq(symbols)
    priority = ["Render","Gui","Hud","Overlay","Layer","Event","Register","Callback","Draw","Graphics"]
    p = [s for s in sorted(symbols) if any(k.lower() in s.lower() for k in priority)]
    o = [s for s in sorted(symbols) if s not in p]
    return (p[:5] + o[:3])[:8] or list(symbols)[:6]


def _trigger_source_search(version, loader, queries, repo, token):
    if _DIF_AVAILABLE:
        from dif_core import _trigger_source_search_workflow
        return _trigger_source_search_workflow(version, loader, queries, repo, token)
    return None


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    raise SystemExit(main())
