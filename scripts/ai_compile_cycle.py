#!/usr/bin/env python3
"""
ai_compile_cycle.py — Build, analyze, retry loop for AI-coded mod versions.
=======================================================================
Takes an analysis bundle (with airesponse.txt files), extracts the AI-written
source files, creates a build-ready zip, dispatches the Build workflow,
analyzes results, and for failed versions enters a retry cycle.

Phases:
  Phase A — Extract & Bundle:  Parse airesponse.txt files into source trees,
                                create a build zip, commit to incoming/.
  Phase B — Build & Analyze:   Dispatch the Build workflow, wait, download,
                                classify versions as success/failed.
  Phase C — Retry (loop):      For each failed version, create failed-N folder
                                with full context, recompose prompt, re-send to
                                AI, extract fixed code, re-zip, re-dispatch
                                (only the failed versions), repeat until all pass
                                or max retries reached.

Usage:
  python3 scripts/ai_compile_cycle.py \\
    --bundle-dir <path> \\
    --slug <mod-slug> \\
    --modrinth-url <url>
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional


# ── Constants ────────────────────────────────────────────────────────────────

BUILD_WORKFLOW_FILE = "build.yml"
INCOMING_DIR = "incoming"
MAX_RETRIES = 5
POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0

# NVIDIA AI config
AI_NVIDIA_BASE = "https://integrate.api.nvidia.com/v1"
AI_MODEL = "stepfun-ai/step-3.5-flash"

# Build artifact names
BUILD_ARTIFACT_ALL = "all-mod-builds"
BUILD_ARTIFACT_PUBLISH = "modrinth-publish"


# ── Helpers ──────────────────────────────────────────────────────────────────

class CycleError(Exception):
    pass


def _log(msg: str) -> None:
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] {msg}")


def _is_infrastructure_failure(build_log: str) -> bool:
    """Detect if a build failure is an infrastructure/transient issue, not a code bug.

    Returns True if the failure is infrastructure-related and should just be retried
    without sending the AI to fix code.
    """
    infra_patterns = [
        r"Read timed out",
        r"Connection reset",
        r"Could not download.*timeout",
        r"Could not get resource.*timeout",
        r"SocketTimeoutException",
        r"Could not GET.*timed out",
        r"Response 304.*has no content",
        r"Could not resolve all artifacts",
        r"Could not download.*\.jar",
        r"Unable to resolve dependency",
        r"Could not transfer artifact",
        r"peer not authenticated",
        r"unable to find valid certification",
        r"transport error",
        r"Connection refused",
        r"No route to host",
        r"Network is unreachable",
        r"503.*Service Unavailable",
        r"502.*Bad Gateway",
        r"504.*Gateway Timeout",
        r"Gradle.*download.*timeout",
        r"Could not install Gradle distribution",
    ]
    if not build_log:
        return False
    for pattern in infra_patterns:
        if re.search(pattern, build_log, re.IGNORECASE):
            return True

    # Check for "BUILD SUCCESSFUL" markers indicating code compiled fine
    if re.search(r"BUILD SUCCESSFUL", build_log):
        return False

    # If there are Java compilation errors AND infra errors, it's a code bug too
    has_compile_errors = re.search(r"error: cannot find symbol|error: incompatible types|compilation failed", build_log, re.IGNORECASE)
    has_infra_errors = re.search(r"timeout|Could not download|Could not resolve", build_log, re.IGNORECASE)
    if has_compile_errors and has_infra_errors:
        return False

    return len(re.findall(r"Could not download|Read timed out|Connection reset|timeout", build_log, re.IGNORECASE)) >= 1


def _map_slug_to_target_name(slug: str, target_names: list[str]) -> str | None:
    """Map a build workflow slug (autofastxp-forge-1-12) to the bundle
    target folder name (1.12-forge).

    Uses heuristic matching since we don't store the mapping explicitly.
    """
    # Extract loader and version from slug
    # Slug format: {mod_id}-{loader}-{version}  e.g. "autofastxp-forge-1-12"
    # Target format: {version}-{loader}         e.g. "1.12-forge"
    parts = slug.rsplit("-", 1)
    if len(parts) == 2:
        loader = parts[0].rsplit("-", 1)[-1] if "-" in parts[0] else ""
        version_part = parts[1]

        # Try exact match: {version}-{loader}
        for tn in target_names:
            if tn == f"{version_part}-{parts[0].rsplit('-', 1)[-1]}" if parts[0].rsplit('-', 1) else False:
                pass

        # Heuristic: find target names containing both the loader and version
        if loader:
            for tn in target_names:
                tn_parts = tn.rsplit("-", 1)
                if len(tn_parts) == 2:
                    if tn_parts[0] == version_part and tn_parts[1] == loader:
                        return tn
                    if tn_parts[1] == loader and version_part in tn_parts[0]:
                        return tn

    # Fallback: just return first matching target or None
    # This handles simple cases like 1.12-forge matching autofastxp-forge-1-12
    # by extracting the last two tokens from slug
    slug_last_parts = "-".join(slug.rsplit("-", 2)[-2:]) if "-" in slug else slug
    for tn in target_names:
        if tn == slug_last_parts:
            return tn
        if tn in slug or slug in tn:
            return tn

    return target_names[0] if len(target_names) == 1 else None


def _detect_repo() -> str:
    try:
        url = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            stderr=subprocess.DEVNULL, text=True).strip()
    except subprocess.CalledProcessError:
        raise CycleError("Could not detect GitHub repo from git remote.")
    m = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    if not m:
        raise CycleError(f"Could not parse owner/repo from remote URL: {url}")
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
    raise CycleError(f"gh {' '.join(args)} failed: {last_err}")


def _load_nvidia_key() -> str:
    """Load the first NVIDIA API key."""
    key_path = Path("C05LocalAi/keys/nvidia.txt")
    if key_path.exists():
        for line in key_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                return line
    for var in ("NVIDIA_API_KEY", "NVAPI_KEY"):
        val = os.environ.get(var, "").strip()
        if val:
            return val
    raise CycleError("No NVIDIA API key found.")


# ── Phase A: Extract files from AI responses and create build zip ───────────

def _extract_ai_files(airesponse_text: str) -> dict[str, str]:
    """Parse AI response into a dict of filepath -> content.

    Finds all ```(...)\n(content)\n``` code blocks.
    For each block, only looks at the text segment between the previous
    block's closing ``` and this block's opening ``` to find the filepath.
    This prevents mispairing filepaths with wrong code blocks.
    """
    files: dict[str, str] = {}
    pattern = re.compile(r"```([a-zA-Z0-9_+\-]*)\n(.*?)\n```", re.DOTALL)
    prev_end = None

    for m in pattern.finditer(airesponse_text):
        lang_spec = m.group(1)
        raw_content = m.group(2)

        # Only scan text between previous block's close and this block's open
        if prev_end is not None:
            text_before = airesponse_text[prev_end:m.start()]
        else:
            text_before = airesponse_text[:m.start()]

        # Find filepath: last line that looks like a path
        before_lines = text_before.splitlines()
        filepath = ""
        for line in reversed(before_lines):
            s = line.strip().strip("`*[]'\"")
            if "/" in s:
                last_part = s.split("/")[-1]
                if "." in last_part and not last_part.startswith("."):
                    filepath = s
                    break
            if "bundle/" in s:
                filepath = s
                break
        if not filepath:
            prev_end = m.end()
            continue

        for prefix in ("bundle/", "./"):
            if filepath.startswith(prefix):
                filepath = filepath[len(prefix):]

        clean_lines = [
            ln for ln in raw_content.splitlines()
            if not ln.strip().startswith("```")
        ]
        clean_content = "\n".join(clean_lines).strip()

        if filepath and clean_content and filepath not in files:
            files[filepath] = clean_content

        prev_end = m.end()

    return files
def _get_expected_build_dir(target_name: str) -> str:
    """Get the expected build source directory for a target.

    The build workflow expects a structure like:
      <mc_version>-<loader>/
        src/main/java/...
        src/main/resources/...
        build.gradle
        ...
    """
    return target_name  # The folder name IS the target name (e.g., "1.12-forge")


def _create_build_bundle(
    bundle_dir: Path,
    output_zip: Path,
) -> tuple[int, list[str]]:
    """Extract airesponse.txt files, build a source tree, and create a zip.

    The build workflow expects each target directory to contain:
      - src/        (source tree, e.g. src/main/java/...)
      - mod.txt     (mod metadata: mod_id, name, version, etc.)
      - version.txt (minecraft_version=X, loader=Y)

    Returns (file_count, target_names).
    """
    temp_dir = Path(output_zip).parent / f".build_bundle_{output_zip.stem}"
    if temp_dir.exists():
        shutil.rmtree(str(temp_dir))
    temp_dir.mkdir(parents=True, exist_ok=True)

    target_dirs = sorted([
        d for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])

    target_names: list[str] = []
    total_files = 0

    for td in target_dirs:
        ai_path = td / "airesponse.txt"
        if not ai_path.exists():
            continue

        target_name = td.name
        target_names.append(target_name)
        ai_text = ai_path.read_text(encoding="utf-8")

        files = _extract_ai_files(ai_text)
        if not files:
            _log(f"  ⚠ No files extracted from {target_name}/airesponse.txt")
            continue

        # Parse mc_version and loader from target_name (e.g. "1.12-forge")
        parts = target_name.rsplit("-", 1)
        if len(parts) == 2:
            mc_version, loader = parts
        else:
            mc_version, loader = target_name, "forge"

        target_out = temp_dir / target_name

        for filepath, content in files.items():
            # Strip the target_name prefix if present (AI often writes
            # "1.12-forge/src/main/java/..." which would double-nest)
            cleaned = filepath
            prefix = target_name + "/"
            if cleaned.startswith(prefix):
                cleaned = cleaned[len(prefix):]
            # Also strip leading "src/" duplicates if present
            if cleaned.startswith("src/"):
                # Keep as-is – src/ is expected
                pass

            dest = target_out / cleaned
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")
            total_files += 1

        # ── Generate mod.txt from projectinfo.txt ──────────────────────
        projectinfo = td / "projectinfo.txt"
        mod_id = "unknown"
        mod_name = target_name
        mod_version = "1.0.0"
        mod_description = ""
        mod_authors = ""
        mod_group = ""
        if projectinfo.exists():
            info_text = projectinfo.read_text(encoding="utf-8")
            for line in info_text.splitlines():
                line = line.strip()
                if "=" in line and not line.startswith("#"):
                    key, _, val = line.partition("=")
                    key = key.strip().lower().replace(" ", "_")
                    val = val.strip().strip('"').strip("'").rstrip(',').strip().strip('"').strip("'")
                    if key in ("mod_id", "modid", "id"):
                        mod_id = val
                    elif key in ("name", "mod_name", "display_name"):
                        mod_name = val
                    elif key == "version":
                        mod_version = val
                    elif key == "description":
                        mod_description = val
                    elif key in ("authors", "author"):
                        mod_authors = val
                    elif key in ("group", "package", "mod_group"):
                        mod_group = val
            # Fallback: parse from header lines (Mod Name:, Mod Author:, Mod Path:)
            if mod_name == target_name or not mod_authors:
                for line in info_text.splitlines():
                    if line.startswith("Mod Name:"):
                        mod_name = line.split(":", 1)[1].strip()
                    elif line.startswith("Mod Author:"):
                        mod_authors = line.split(":", 1)[1].strip()
                    elif line.startswith("Mod Path:"):
                        mod_group = line.split(":", 1)[1].strip()

        mod_txt_lines = []
        mod_txt_lines.append(f"mod_id={mod_id}")
        mod_txt_lines.append(f"name={mod_name}")
        mod_txt_lines.append(f"mod_version={mod_version}")
        if mod_group:
            mod_txt_lines.append(f"group={mod_group}")
        else:
            mod_txt_lines.append("group=com.example")
        mod_txt_lines.append("entrypoint_class=Main")
        if mod_description:
            mod_txt_lines.append(f"description={mod_description}")
        else:
            mod_txt_lines.append("description=A Minecraft mod.")
        if mod_authors:
            mod_txt_lines.append(f"authors={mod_authors}")
        else:
            mod_txt_lines.append("authors=Unknown")
        mod_txt_lines.append("license=MIT")
        (target_out / "mod.txt").write_text("\n".join(mod_txt_lines), encoding="utf-8")
        total_files += 1

        # ── Generate version.txt ───────────────────────────────────────
        (target_out / "version.txt").write_text(
            f"minecraft_version={mc_version}\nloader={loader}\n",
            encoding="utf-8",
        )
        total_files += 1

        _log(f"  ✓ {target_name}: {len(files)} file(s) extracted + mod.txt/version.txt")

    if total_files == 0:
        raise CycleError("No files extracted from any airesponse.txt — cannot create build zip.")

    # Create the zip
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for p in temp_dir.rglob("*"):
            if p.is_file():
                arcname = str(p.relative_to(temp_dir))
                zf.write(p, arcname)

    # Cleanup
    shutil.rmtree(str(temp_dir))

    _log(f"  Created build zip: {output_zip} ({output_zip.stat().st_size:,} bytes)")
    return total_files, target_names


# ── Git commit & push ────────────────────────────────────────────────────────

def _git_run(args: list[str], check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(["git"] + args, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise CycleError(f"git {' '.join(args)} failed: {result.stderr[:300]}")
    return result


def _commit_and_push_zip(zip_path: Path, slug: str, token: str) -> None:
    """Commit the build zip to the incoming/ folder and push."""
    repo = _detect_repo()
    branch = _git_run(["rev-parse", "--abbrev-ref", "HEAD"]).stdout.strip()

    # Stage the zip
    _log(f"  Staging {zip_path}...")
    _git_run(["add", str(zip_path)])

    # Check if there's anything to commit
    status = _git_run(["status", "--porcelain"]).stdout.strip()
    if not status:
        _log("  No changes to commit (zip already up to date).")
        return

    # Commit
    message = f"ai: add {slug} AI-coded build bundle"
    _log(f"  Committing: {message}")
    _git_run(["commit", "-m", message])

    # Push
    _log(f"  Pushing to origin/{branch}...")
    push_result = _git_run(["push", "origin", branch], check=False)
    if push_result.returncode != 0:
        _log("  Remote has new commits, rebasing...")
        _git_run(["pull", "--rebase", "origin", branch], check=False)
        push_result = _git_run(["push", "--set-upstream", "origin", branch], check=False)
        if push_result.returncode != 0:
            raise CycleError(f"Push failed: {push_result.stderr[:300]}")

    _log("  ✓ Pushed successfully")
    _log(f"  Zip URL: in repo at {zip_path}")


# ── Phase B: Dispatch build workflow, wait, analyze ─────────────────────────

def _dispatch_build(zip_path: str, slug: str, modrinth_url: str, repo: str, token: str) -> int:
    """Dispatch the Build workflow and return the run ID."""
    before = {r["databaseId"] for r in _list_build_runs(repo, token)}
    fields = [
        "-f", f"zip_path={zip_path}",
        "-f", f"max_parallel=all",
        "-f", f"modrinth_project_url={modrinth_url}",
    ]
    _gh(["workflow", "run", BUILD_WORKFLOW_FILE, "-R", repo] + fields, token=token)
    deadline = time.time() + 120
    while time.time() < deadline:
        for run in _list_build_runs(repo, token):
            rid = run["databaseId"]
            if rid not in before:
                _log(f"  Dispatched build run #{rid}")
                return rid
        time.sleep(4)
    raise CycleError("Build workflow was dispatched but no new run appeared within 120s.")


def _list_build_runs(repo: str, token: str) -> list[dict]:
    out = _gh([
        "run", "list", "-R", repo,
        "-w", BUILD_WORKFLOW_FILE,
        "-e", "workflow_dispatch",
        "--json", "databaseId,status,conclusion,createdAt",
        "-L", "20",
    ], token=token)
    return json.loads(out or "[]")


def _wait_for_build(run_id: int, repo: str, token: str, timeout: int = 7200) -> str:
    """Wait for a build run to complete. Returns the conclusion."""
    deadline = time.time() + timeout
    last_status = ""
    print(f"\n  Waiting for build run #{run_id}...")
    print(f"  (polling every {POLL_INTERVAL}s, timeout {timeout}s)")

    while time.time() < deadline:
        out = _gh([
            "run", "view", str(run_id), "-R", repo,
            "--json", "status,conclusion",
        ], token=token)
        info = json.loads(out or "{}")
        status = info.get("status", "")
        conclusion = info.get("conclusion") or ""

        if status != last_status:
            _log(f"  Build status: {status} / {conclusion}")
            last_status = status

        if status == "completed":
            _log(f"  Build completed — conclusion: {conclusion}")
            return conclusion

        time.sleep(POLL_INTERVAL)

    raise CycleError(f"Build run #{run_id} timed out after {timeout}s.")


def _download_build_results(run_id: int, repo: str, token: str, dest_dir: Path) -> dict[str, Any]:
    """Download build artifacts and return structured results.

    Returns dict with keys:
      success_versions: list of version names that built successfully
      failed_versions: list of version names that failed
      published_versions: list of version names that were published to Modrinth
      all_artifacts_dir: Path to downloaded artifacts
      build_log_dir: Path to build logs
    """
    dest_dir.mkdir(parents=True, exist_ok=True)
    artifacts_dir = dest_dir / "artifacts"
    logs_dir = dest_dir / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    # Wait a few seconds for GitHub to finalize artifacts
    time.sleep(5)

    # Download all build artifacts with exponential backoff
    artifact_downloaded = False
    for attempt in range(1, 7):
        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", BUILD_ARTIFACT_ALL, "-D", str(artifacts_dir),
            ], token=token)
            artifact_downloaded = True
            break
        except CycleError:
            delay = min(5 * (2 ** (attempt - 1)), 60)
            _log(f"  Download attempt {attempt} failed, retrying in {delay}s...")
            time.sleep(delay)

    # Also try publish artifacts
    for attempt in range(1, 5):
        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", BUILD_ARTIFACT_PUBLISH, "-D", str(artifacts_dir),
            ], token=token)
            break
        except CycleError:
            if attempt == 4:
                _log("  No publish artifact (versions may not all be published yet)")
            time.sleep(3 * attempt)

    # Download logs
    try:
        raw_log = _gh(["run", "view", str(run_id), "-R", repo, "--log"], token=token)
        (logs_dir / "build_overview.txt").write_text(raw_log, encoding="utf-8")
    except CycleError:
        pass

    # Analyze results
    success_versions: list[str] = []
    failed_versions: list[str] = []
    published_versions: list[str] = []

    # Read SUMMARY.md from artifacts if available
    summary_file = artifacts_dir / BUILD_ARTIFACT_ALL / "SUMMARY.md"
    if summary_file.exists():
        summary_text = summary_file.read_text(encoding="utf-8")
        # Look for build results markers
        for line in summary_text.splitlines():
            # Example: "| 1.12-forge | ✅ | ..."
            if "|" in line and "✅" in line:
                parts = [p.strip() for p in line.split("|") if p.strip()]
                if parts:
                    success_versions.append(parts[0])
            if "|" in line and "❌" in line:
                parts = [p.strip() for p in line.split("|") if p.strip()]
                if parts:
                    failed_versions.append(parts[0])

    # Check for published versions
    publish_summary = artifacts_dir / BUILD_ARTIFACT_PUBLISH / "SUMMARY.md"
    if publish_summary.exists():
        pub_text = publish_summary.read_text(encoding="utf-8")
        for line in pub_text.splitlines():
            if "✅" in line or "Published" in line:
                # Extract version name from markdown
                m = re.search(r"\*\*([^*]+)\*\*", line)
                if m:
                    published_versions.append(m.group(1).strip())

    # Fallback: scan per-mod build artifacts
    build_artifact_dir = artifacts_dir / BUILD_ARTIFACT_ALL
    if build_artifact_dir.exists():
        for item in build_artifact_dir.iterdir():
            if item.is_dir() and not item.name.startswith("."):
                # Check for per-mod result.json files
                result_json = item / "result.json"
                if result_json.exists():
                    try:
                        rj = json.loads(result_json.read_text(encoding="utf-8"))
                        slug = rj.get("slug", "")
                        status = rj.get("status", "")
                        if status == "success" and slug not in success_versions:
                            success_versions.append(slug)
                        elif status == "failed" and slug not in failed_versions:
                            failed_versions.append(slug)
                    except (json.JSONDecodeError, KeyError):
                        pass
                # Check for legacy marker files
                success_marker = item / ".build_success"
                fail_marker = item / ".build_failed"
                if success_marker.exists():
                    vname = item.name
                    if vname not in success_versions:
                        success_versions.append(vname)
                elif fail_marker.exists():
                    vname = item.name
                    if vname not in failed_versions:
                        failed_versions.append(vname)

    # Fallback: use job statuses if no artifacts were parsed
    if not success_versions and not failed_versions:
        if artifact_downloaded:
            _log("  No per-target results in artifact, using job statuses...")
        else:
            _log("  Could not download artifact, using job statuses...")
        try:
            jobs = _get_build_jobs(run_id, repo, token)
            for job in jobs:
                job_name = job.get("name", "")
                m = re.match(r"build\s*\(([^)]+)\)", job_name)
                if m:
                    slug = m.group(1).strip()
                    job_conclusion = job.get("conclusion", "")
                    if job_conclusion == "success":
                        if slug not in success_versions:
                            success_versions.append(slug)
                    elif job_conclusion in ("failure", "cancelled", "skipped"):
                        if slug not in failed_versions:
                            failed_versions.append(slug)
            if success_versions or failed_versions:
                _log(f"  Job results: {len(success_versions)} success, {len(failed_versions)} failed")
        except CycleError:
            _log("  Could not fetch job list.")

    return {
        "success_versions": success_versions,
        "failed_versions": failed_versions,
        "published_versions": published_versions,
        "all_artifacts_dir": str(build_artifact_dir) if build_artifact_dir.exists() else "",
        "build_log_dir": str(logs_dir),
    }


def _get_all_target_names(bundle_dir: Path) -> list[str]:
    """Get all target names from the bundle."""
    return sorted([
        d.name for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])


def _get_build_jobs(run_id: int, repo: str, token: str) -> list[dict]:
    """Get the list of jobs from the build workflow run."""
    out = _gh([
        "run", "view", str(run_id), "-R", repo,
        "--json", "jobs",
    ], token=token)
    data = json.loads(out or "{}")
    return data.get("jobs", [])


def _get_step_log_for_target(target_name: str, jobs: list[dict], logs_dir: Path, repo: str, token: str, run_id: int) -> str:
    """Get the actual build.log from the per-mod artifact (fastest way).

    Downloads the per-mod artifact mod-{slug} which contains build.log
    (actual compiler error output) and is tiny compared to all-mod-builds.
    """
    slug = None
    job_id = None
    for job in jobs:
        job_name = job.get("name", "")
        m = re.match(r"build\s*\(([^)]+)\)", job_name)
        if m:
            matched_slug = m.group(1).strip()
            if target_name in matched_slug or target_name.split("-", 1)[-1] in matched_slug:
                slug = matched_slug
                job_id = job.get("databaseId") or job.get("id")
                break

    if slug:
        per_mod_dir = logs_dir.parent / "per_mod_artifacts" / slug
        per_mod_dir.mkdir(parents=True, exist_ok=True)

        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", f"mod-{slug}", "-D", str(per_mod_dir),
            ], token=token, retries=2)

            for p in per_mod_dir.rglob("build.log"):
                return p.read_text(encoding="utf-8")

            result_json = per_mod_dir / "result.json"
            if result_json.exists():
                rj = json.loads(result_json.read_text(encoding="utf-8"))
                log_rel = rj.get("log_relpath", "build.log")
                lp = per_mod_dir / log_rel
                if lp.exists():
                    return lp.read_text(encoding="utf-8")
        except (CycleError, json.JSONDecodeError, OSError):
            pass

    if job_id:
        try:
            job_log = _gh([
                "run", "view", str(run_id), "-R", repo,
                "--log", "--job", str(job_id),
            ], token=token, retries=2)
            if job_log.strip():
                (logs_dir / f"{target_name}_build.log").write_text(job_log, encoding="utf-8")
                return job_log
        except CycleError:
            pass

    for p in [logs_dir / f"{target_name}_build.log", logs_dir / "build_overview.txt"]:
        if p.exists():
            text = p.read_text(encoding="utf-8", errors="replace")
            if "error" in text.lower() or "compile" in text.lower():
                return text

    return "(no specific build log found for this target)"


# ── Phase C: Retry cycle for failed versions ────────────────────────────────

FIX_PROMPT_TEMPLATE = """You are an excellent and professional Minecraft mod developer.
You are expert at reading and following technical instructions precisely.
You write clean, correct, and well-structured Java code.
You are tasked with FIXING a mod version that failed to build.

## Mod Information

{mod_info}

## Current Source Code

Below is the current source code that failed to build:

{source_code}

## Build Error Log

The build failed with the following error(s):

{build_log}

## Context Information

### DIF (Documented Issue Fix) Entries for This Target

{dif_entries}

### Minecraft Source Code (relevant portions)

{mc_source}

## Instructions

1. Analyze the build errors and identify what needs to be fixed.
2. Output ONLY the files that need to be changed/replaced.
3. For each file, use this format:

`bundle/{target_name}/filepath`
```language
code_here_
```

4. Provide COMPLETE file contents — no stubs, no TODOs, no placeholders.
5. If the build error is about missing methods/classes, check the provided
   Minecraft source code and DIF entries for the correct API to use.
6. Output ALL files that need changes, even if only small changes are needed.
"""


def _create_failed_context(
    target_dir: Path,
    attempt_num: int,
    version_source_code: dict[str, str],
    build_error_log: str,
    dif_entries: list[dict],
    mc_source_files: dict[str, str],
) -> Path:
    """Create a failed-N folder with full context for AI retry.

    Returns the path to the failed folder.
    """
    failed_dir = target_dir / f"failed-{attempt_num}"
    failed_dir.mkdir(parents=True, exist_ok=True)

    # Save build error log
    (failed_dir / "build_error.log").write_text(build_error_log, encoding="utf-8")

    # Save current source code
    src_dir = failed_dir / "source"
    for filepath, content in version_source_code.items():
        dest = src_dir / filepath
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")

    # Save DIF entries
    if dif_entries:
        dif_text = ""
        for entry in dif_entries:
            dif_text += f"## [{entry.get('id', '?')}] {entry.get('title', '')}\n\n"
            dif_text += entry.get("fix", entry.get("body", ""))
            dif_text += "\n\n---\n\n"
        (failed_dir / "dif_entries.txt").write_text(dif_text, encoding="utf-8")

    # Save MC source files
    if mc_source_files:
        mc_dir = failed_dir / "minecraft_source"
        for filepath, content in mc_source_files.items():
            dest = mc_dir / filepath
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")

    _log(f"  ✓ Created {failed_dir.relative_to(target_dir.parent.parent)}")
    return failed_dir


def _collect_dif_for_target(minecraft_version: str, loader: str, max_results: int = 4) -> list[dict]:
    """Search the DIF knowledge base for this target."""
    dif_dir = Path("dif")
    if not dif_dir.exists():
        return []
    results = []
    query_terms = {loader.lower(), minecraft_version}
    version_parts = minecraft_version.split(".")
    if len(version_parts) >= 2:
        query_terms.add(".".join(version_parts[:2]))

    for f in sorted(dif_dir.glob("*.md")):
        try:
            content = f.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        meta, body = _parse_dif_frontmatter(content)
        entry_versions = [v.lower() for v in meta.get("versions", [])]
        entry_loaders = [l.lower() for l in meta.get("loaders", [])]
        score = 0
        if minecraft_version.lower() in entry_versions:
            score += 10
        else:
            for v in entry_versions:
                ev_parts = v.split(".")
                if len(ev_parts) >= 2 and len(version_parts) >= 2 and ev_parts[:2] == version_parts[:2]:
                    score += 5
                    break
        if loader.lower() in entry_loaders:
            score += 8
        if score > 0:
            fix = _extract_fix_section(body)
            results.append({
                "id": meta.get("id", f.stem),
                "title": meta.get("title", f.stem),
                "score": score,
                "body": body,
                "fix": fix,
            })
    results.sort(key=lambda x: (x["score"],), reverse=True)
    return results[:max_results]


def _parse_dif_frontmatter(content: str) -> tuple[dict, str]:
    if not content.startswith("---"):
        return {}, content
    end = content.find("\n---", 3)
    if end == -1:
        return {}, content
    fm_text = content[3:end].strip()
    body = content[end + 4:].strip()
    meta: dict = {}
    for line in fm_text.splitlines():
        if ":" not in line:
            continue
        key, _, val = line.partition(":")
        key = key.strip()
        val = val.strip()
        if val.startswith("[") and val.endswith("]"):
            items = [x.strip().strip('"').strip("'") for x in val[1:-1].split(",") if x.strip()]
            meta[key] = items
        else:
            try:
                meta[key] = int(val)
            except ValueError:
                meta[key] = val
    return meta, body


def _extract_fix_section(body: str) -> str:
    fix_match = re.search(r"##\s*Fix\s*\n(.*?)(?=\n##\s|\Z)", body, re.DOTALL | re.IGNORECASE)
    if fix_match:
        fix_text = fix_match.group(1).strip()
        if len(fix_text) > 2000:
            fix_text = fix_text[:2000] + "\n... (truncated)"
        return fix_text
    return "(No fix section found)"


def _collect_mc_source_files(minecraft_version: str, loader: str) -> dict[str, str]:
    """Try to find relevant Minecraft source files for this version."""
    src_files: dict[str, str] = {}
    # Check DecompiledMinecraftSourceCode directory
    decomp_root = Path("DecompiledMinecraftSourceCode")
    if not decomp_root.exists():
        return src_files

    # Look for matching version folder
    for vdir in decomp_root.iterdir():
        if not vdir.is_dir():
            continue
        vname = vdir.name
        if minecraft_version.startswith(vname) or vname.startswith(minecraft_version.split(".")[0]):
            # Found a matching version directory
            # Collect .java files from the mod's package area (asd/itamio/...)
            for p in vdir.rglob("*.java"):
                try:
                    text = p.read_text(encoding="utf-8", errors="replace")
                    rel = str(p.relative_to(vdir))
                    # Only include files relevant to mod development
                    if len(text) < 200000:  # Skip huge files
                        src_files[rel] = text
                except Exception:
                    pass
            if src_files:
                break  # Use first matching directory

    return src_files


def _get_current_source_code(bundle_dir: Path, target_name: str) -> dict[str, str]:
    """Get the current source code for a target from its airesponse.txt."""
    ai_path = bundle_dir / target_name / "airesponse.txt"
    if not ai_path.exists():
        return {}
    text = ai_path.read_text(encoding="utf-8")
    return _extract_ai_files(text)


def _recompose_fix_prompt(
    target_name: str,
    mod_info_text: str,
    source_code: dict[str, str],
    build_error_log: str,
    dif_entries: list[dict],
    mc_source_files: dict[str, str],
) -> str:
    """Create a fix prompt for a failed version."""
    mc_ver, loader = target_name.rsplit("-", 1)

    # Format source code
    src_lines = []
    for filepath in sorted(source_code.keys()):
        content = source_code[filepath]
        src_lines.append(f"`{filepath}`")
        src_lines.append("```java" if filepath.endswith(".java") else "```")
        src_lines.append(content)
        src_lines.append("```")
        src_lines.append("")

    src_text = "\n".join(src_lines) if src_lines else "(no source code available)"

    # Format DIF entries
    dif_text = ""
    for entry in dif_entries:
        dif_text += f"### [{entry['id']}] {entry['title']}\n{entry['fix']}\n\n"

    if not dif_text:
        dif_text = "(no DIF entries found for this target)"

    # Format MC source
    mc_text = ""
    for filepath in sorted(mc_source_files.keys())[:10]:  # Limit to 10 files
        content = mc_source_files[filepath]
        mc_text += f"### DecompiledMinecraftSourceCode/{filepath}\n```java\n{content[:2000]}\n```\n\n"

    if not mc_text:
        mc_text = "(no Minecraft source files available)"

    prompt = FIX_PROMPT_TEMPLATE.format(
        mod_info=mod_info_text[:2000] if mod_info_text else "(no mod info)",
        source_code=src_text,
        mc_source=mc_text,
        build_log=build_error_log,
        dif_entries=dif_text,
        target_name=target_name,
    )

    return prompt


def _send_fix_prompt_to_ai(prompt: str, api_key: str, target_name: str) -> str:
    """Send a fix prompt to the NVIDIA API and return the full response."""
    import urllib.request
    import urllib.error

    url = f"{AI_NVIDIA_BASE}/chat/completions"

    messages = [
        {
            "role": "system",
            "content": (
                "You are an excellent and professional Minecraft mod developer. "
                "You are expert at reading build error logs and fixing compilation issues. "
                "You write clean, correct, and well-structured Java code. "
                "Provide ONLY the files that need to be changed, with complete implementations."
            )
        },
        {"role": "user", "content": prompt},
    ]

    payload = {
        "model": AI_MODEL,
        "messages": messages,
        "temperature": 0.2,
        "stream": True,
    }

    payload["extra_body"] = {
        "chat_template_kwargs": {
            "enable_thinking": True,
            "reasoning_effort": "high",
        }
    }

    body_bytes = json.dumps(payload).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method="POST")
    full_response = ""
    accumulated = 0
    buffer = ""

    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            while True:
                chunk = resp.read(4096)
                if not chunk:
                    break
                buffer += chunk.decode("utf-8", errors="replace")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("data: "):
                        data_str = line[6:]
                        if data_str.strip() == "[DONE]":
                            break
                        try:
                            data = json.loads(data_str)
                            choices = data.get("choices", [])
                            if choices:
                                delta = choices[0].get("delta", {})
                                content = delta.get("content", "") or ""
                                if content:
                                    full_response += content
                                    accumulated += len(content)
                        except json.JSONDecodeError:
                            pass
    except urllib.error.HTTPError as e:
        error_detail = e.read().decode("utf-8", errors="replace")[:500]
        raise CycleError(f"AI HTTP {e.code}: {error_detail}")
    except Exception as e:
        raise CycleError(f"AI request failed: {e}")

    if not full_response:
        raise CycleError("Empty response from AI — no content generated.")

    return full_response


def _merge_source_files(
    existing_files: dict[str, str],
    fix_files: dict[str, str],
) -> dict[str, str]:
    """Merge fixed files into existing source, replacing changed files."""
    merged = dict(existing_files)
    for filepath, content in fix_files.items():
        merged[filepath] = content
    return merged


def _save_source_files(files: dict[str, str], target_dir: Path) -> None:
    """Save source files into a target directory structure."""
    for filepath, content in files.items():
        dest = target_dir / filepath
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")


# ── Main orchestrator ────────────────────────────────────────────────────────

def run_compile_cycle(
    bundle_dir: Path,
    slug: str,
    modrinth_url: str,
    token: str,
    nvidia_key: str,
    repo: str,
    max_retries: int = MAX_RETRIES,
) -> int:
    """Run the full compile cycle: build, analyze, retry.

    Returns 0 if all versions eventually succeed, 1 if some remain failed.
    """
    print()
    print("=" * 72)
    print("  AI COMPILE CYCLE — Build, Analyze, Retry")
    print(f"  Slug: {slug}")
    print(f"  Max retries per version: {max_retries}")
    print("=" * 72)
    print()

    # ── Get all target names ──────────────────────────────────────────────
    target_names = _get_all_target_names(bundle_dir)
    _log(f"Found {len(target_names)} target(s) in bundle")

    # ── Phase A: Extract & Bundle ─────────────────────────────────────────
    print()
    print(f"{'─' * 72}")
    print("  PHASE A — Extract AI Files & Create Build Bundle")
    print(f"{'─' * 72}")
    print()

    zip_filename = f"{slug}-all-versions.zip"
    zip_path = Path(INCOMING_DIR) / zip_filename

    file_count, _ = _create_build_bundle(bundle_dir, zip_path)
    _log(f"Extracted {file_count} file(s) into {zip_path}")

    # Commit and push
    print()
    _log("Committing build bundle to repository...")
    _commit_and_push_zip(zip_path, slug, token)

    # ── Phase B: Build & Analyze ──────────────────────────────────────────
    print()
    print(f"{'─' * 72}")
    print("  PHASE B — Build & Analyze (Build Workflow)")
    print(f"{'─' * 72}")
    print()

    build_run_id = _dispatch_build(
        zip_path=str(zip_path),
        slug=slug,
        modrinth_url=modrinth_url,
        repo=repo,
        token=token,
    )
    build_conclusion = _wait_for_build(build_run_id, repo, token)

    _log(f"Downloading build results...")
    build_results_dir = bundle_dir.parent / ".build_results"
    results = _download_build_results(build_run_id, repo, token, build_results_dir)

    success_set = set(results["success_versions"])
    failed_set = set(results["failed_versions"])
    published_set = set(results["published_versions"])

    _log(f"Results: {len(success_set)} success, {len(failed_set)} failed")

    # Map any slug names from the build workflow back to target folder names
    mapped_failed: set[str] = set()
    for slug in failed_set:
        mapped = _map_slug_to_target_name(slug, target_names)
        if mapped:
            mapped_failed.add(mapped)
        else:
            mapped_failed.add(slug)
    failed_set = mapped_failed

    mapped_success: set[str] = set()
    for slug in success_set:
        mapped = _map_slug_to_target_name(slug, target_names)
        if mapped:
            mapped_success.add(mapped)
        else:
            mapped_success.add(slug)
    success_set = mapped_success

    # If no results could be determined (artifact download failed, etc.),
    # treat all targets as failed so the retry cycle can attempt to fix them.
    if not success_set and not failed_set:
        _log("  No results available — treating all targets as failed for retry.")
        failed_set = set(target_names)

    # Phase C: Retry Loop ───────────────────────────────────────────────
    current_targets = list(failed_set)
    active_failed: dict[str, int] = {t: 0 for t in failed_set}
    all_targets_set = set(target_names)

    for attempt in range(1, max_retries + 1):
        if not current_targets:
            _log("All versions built successfully!")
            break

        print()
        print(f"{'─' * 72}")
        print(f"  PHASE C — Retry Cycle (Attempt {attempt}/{max_retries})")
        print(f"  Remaining failed: {len(current_targets)}")
        print(f"{'─' * 72}")
        print()

        # For each failed version, create context and recompose prompt
        fixed_this_round: list[str] = []
        still_failed: list[str] = []

        # Fetch jobs once for this entire retry round
        jobs = _get_build_jobs(build_run_id, repo, token)

        # Pre-collect source code for all targets
        target_data = []
        for target_name in current_targets:
            active_failed[target_name] = attempt
            mc_ver, loader = target_name.rsplit("-", 1)
            source_code = _get_current_source_code(bundle_dir, target_name)
            target_data.append((target_name, mc_ver, loader, source_code))

        # Phase 1: Download per-mod artifacts + collect context IN PARALLEL
        _log(f"  Collecting context for {len(target_data)} failed target(s)...")
        context_list = [None] * len(target_data)

        def _collect_context(idx, tname, mcver, loader, scode):
            try:
                build_log = _get_step_log_for_target(
                    tname, jobs, Path(results["build_log_dir"]), repo, token, build_run_id
                )
                if not build_log:
                    build_log = "(build log not available)"
                dif_entries = _collect_dif_for_target(mcver, loader)
                mc_source = _collect_mc_source_files(mcver, loader)
                return (idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source)
            except Exception as exc:
                return (idx, tname, mcver, loader, scode, str(exc), [], {})

        with ThreadPoolExecutor(max_workers=min(4, len(target_data))) as pool:
            futures = []
            for idx, (tname, mcver, loader, scode) in enumerate(target_data):
                futures.append(pool.submit(_collect_context, idx, tname, mcver, loader, scode))
            for fut in as_completed(futures):
                result = fut.result()
                context_list[result[0]] = result

        # Phase 2: Create failed-N folders
        for result in context_list:
            if result is None:
                continue
            idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source = result
            target_dir = bundle_dir / tname
            _create_failed_context(target_dir, attempt, scode, build_log, dif_entries, mc_source)

        # Check for infrastructure failures (network timeouts, etc.) that
        # don't need AI fixes — just re-dispatch without sending to AI
        infra_failures = []
        for i, result in enumerate(context_list):
            if result is None:
                continue
            idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source = result
            if _is_infrastructure_failure(build_log):
                _log(f"    {tname}: infrastructure failure (network timeout), skipping AI fix")
                infra_failures.append(tname)
                # Mark as fixed by re-dispatched — no AI needed
                target_dir = bundle_dir / tname
                (target_dir / ".infra_retry").write_text("", encoding="utf-8")
                fixed_this_round.append(tname)

        # Remove infra failures from AI queue
        context_list_ai = [r for r in context_list if r is not None and r[1] not in infra_failures]
        if not context_list_ai:
            _log("  All failures are infrastructure-related, no AI fixes needed — re-dispatching...")
        else:
            # Phase 3: Send AI prompts IN PARALLEL
            _log(f"  Sending {len(context_list_ai)} AI fix prompt(s) in parallel...")
            ai_responses = [None] * len(target_data)

        def _send_ai(idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source):
            try:
                target_dir = bundle_dir / tname
                projectinfo_path = target_dir / "projectinfo.txt"
                mod_info = projectinfo_path.read_text(encoding="utf-8") if projectinfo_path.exists() else ""
                fix_prompt = _recompose_fix_prompt(tname, mod_info, scode, build_log, dif_entries, mc_source)
                _log(f"    AI: {tname} ({len(fix_prompt):,} chars)...")
                ai_response = _send_fix_prompt_to_ai(fix_prompt, nvidia_key, tname)
                return (idx, tname, ai_response, None)
            except Exception as exc:
                return (idx, tname, None, str(exc))

        with ThreadPoolExecutor(max_workers=min(3, len(context_list_ai))) as pool:
            futures = []
            for result in context_list_ai:
                if result is None:
                    continue
                idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source = result
                futures.append(pool.submit(_send_ai, idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source))
            for fut in as_completed(futures):
                result = fut.result()
                ai_responses[result[0]] = result

        # Phase 4: Process AI responses
        for result in ai_responses:
            if result is None:
                continue
            idx, tname, ai_response, error = result
            target_dir = bundle_dir / tname

            if error or not ai_response:
                _log(f"  \u2717 {tname}: AI request failed: {error}")
                still_failed.append(tname)
                continue

            fix_response_path = target_dir / f"airesponse_fix_{attempt}.txt"
            fix_response_path.write_text(ai_response, encoding="utf-8")
            _log(f"  \u2713 {tname}: AI response saved ({len(ai_response):,} chars)")

            fix_files = _extract_ai_files(ai_response)
            if not fix_files:
                _log(f"  \u2717 {tname}: No files extracted")
                still_failed.append(tname)
                continue

            _log(f"  \u2713 {tname}: {len(fix_files)} fixed file(s)")

            source_code = _get_current_source_code(bundle_dir, tname)
            merged_source = _merge_source_files(source_code, fix_files)
            merged_dir = bundle_dir / tname / ".merged_source"
            _save_source_files(merged_source, merged_dir)
            _save_source_files(merged_source, bundle_dir / tname)

            merged_text = ""
            for fp in sorted(merged_source.keys()):
                c = merged_source[fp]
                lang = "java" if fp.endswith(".java") else ""
                merged_text += f"`bundle/{tname}/{fp}`\n```{lang}\n{c}\n```\n\n"
            (bundle_dir / tname / "airesponse.txt").write_text(merged_text, encoding="utf-8")

            fixed_this_round.append(tname)

        if not fixed_this_round:
            _log("No versions could be fixed this round. Aborting retry cycle.")
            break

        # Rebuild the zip with fixed versions
        print()
        _log(f"Rebuilding bundle zip with {len(fixed_this_round)} fixed version(s)...")
        file_count, _ = _create_build_bundle(bundle_dir, zip_path)
        _log(f"Updated zip with {file_count} file(s) (including fixes)")

        # Commit the updated zip (only failed versions changed)
        _log("Committing updated build bundle...")
        _commit_and_push_zip(zip_path, slug, token)

        # Dispatch a new build with only the failed versions
        # We use the same zip, but the build will try all versions
        # The successful versions will just be re-verified
        print()
        _log(f"Dispatching new build (attempt {attempt + 1})...")
        build_run_id = _dispatch_build(
            zip_path=str(zip_path),
            slug=slug,
            modrinth_url=modrinth_url,
            repo=repo,
            token=token,
        )
        build_conclusion = _wait_for_build(build_run_id, repo, token)

        _log("Downloading updated build results...")
        results = _download_build_results(build_run_id, repo, token, build_results_dir)

        new_success = set(results["success_versions"])
        new_failed = set(results["failed_versions"])
        published_set.update(results["published_versions"])

        _log(f"Updated results: {len(new_success)} success, {len(new_failed)} failed")

        # Update the current targets for next iteration
        current_targets = [t for t in current_targets if t in new_failed]

    # ── Final Summary ─────────────────────────────────────────────────────
    print()
    print("=" * 72)
    print("  COMPILE CYCLE COMPLETE")
    print("=" * 72)
    print(f"  Published to Modrinth: {len(published_set)} version(s)")
    if published_set:
        for v in sorted(published_set):
            print(f"    ✓ {v}")
    print(f"  Built but not published: {len(success_set - published_set)} version(s)")
    # Compute remaining failed from the retry loop final state
    all_resolved = success_set | set(t for t in target_names if t not in current_targets and active_failed.get(t) is not None)
    remaining_failed = [t for t in target_names if t not in all_resolved]
    if remaining_failed:
        print(f"  FAILED after {max_retries} retries: {len(remaining_failed)} version(s)")
        for v in sorted(remaining_failed):
            print(f"    ✗ {v}")

    return 0 if not remaining_failed else 1


# ── CLI ──────────────────────────────────────────────────────────────────────

def main() -> int:
    import argparse
    parser = argparse.ArgumentParser(
        description="AI Compile Cycle: extract, build, analyze, retry."
    )
    parser.add_argument("--bundle-dir", required=True,
                        help="Path to the analysis bundle directory")
    parser.add_argument("--slug", required=True,
                        help="Mod slug (e.g. sort-chest)")
    parser.add_argument("--modrinth-url", required=True,
                        help="Full Modrinth project URL")
    parser.add_argument("--repo", default="",
                        help="owner/repo (auto-detected from git remote)")
    parser.add_argument("--max-retries", type=int, default=MAX_RETRIES,
                        help=f"Max retry attempts per version (default: {MAX_RETRIES})")
    args = parser.parse_args()

    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.exists():
        print(f"ERROR: bundle dir not found: {bundle_dir}", file=sys.stderr)
        return 1

    repo = args.repo or _detect_repo()
    token = _detect_token()
    nvidia_key = _load_nvidia_key()

    try:
        return run_compile_cycle(
            bundle_dir=bundle_dir,
            slug=args.slug,
            modrinth_url=args.modrinth_url,
            token=token,
            nvidia_key=nvidia_key,
            repo=repo,
            max_retries=args.max_retries,
        )
    except CycleError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
