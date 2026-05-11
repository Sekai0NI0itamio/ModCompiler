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
AI_MAX_TOKENS = 16384

# Build artifact names
BUILD_ARTIFACT_ALL = "all-mod-builds"
BUILD_ARTIFACT_PUBLISH = "modrinth-publish"


# ── Helpers ──────────────────────────────────────────────────────────────────

class CycleError(Exception):
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
    """Parse AI response into a dict of filepath → content.

    The AI is instructed to output in the format:
      filepath (relative, not full)
      ```language
      code_here_
      ```
    """
    files: dict[str, str] = {}
    # Match pattern: a filepath line (no backticks) followed by a code block
    pattern = re.compile(
        r"^(?!```)(\S+(?:/\S+)*\.[a-zA-Z0-9]+)\s*\n"
        r"```(?:\w*)\n"
        r"(.*?)"
        r"\n```",
        re.MULTILINE | re.DOTALL
    )
    for m in pattern.finditer(airesponse_text):
        filepath = m.group(1).strip()
        content = m.group(2).strip()
        # Clean the filepath: remove "bundle/" prefix if present
        if filepath.startswith("bundle/"):
            filepath = filepath[7:]
        # Remove leading ./ if present
        if filepath.startswith("./"):
            filepath = filepath[2:]
        if filepath and content:
            files[filepath] = content

    # Fallback: also match lines like: filepath: ```...``` on same line
    if not files:
        alt_pattern = re.compile(
            r"`([^`]+)`\s*```(?:\w*)\n(.*?)```",
            re.DOTALL
        )
        for m in alt_pattern.finditer(airesponse_text):
            filepath = m.group(1).strip()
            content = m.group(2).strip()
            if filepath.startswith("bundle/"):
                filepath = filepath[7:]
            if filepath and content:
                files[filepath] = content

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

        target_out = temp_dir / target_name
        for filepath, content in files.items():
            dest = target_out / filepath
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")
            total_files += 1

        _log(f"  ✓ {target_name}: {len(files)} file(s) extracted")

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

    # Download all build artifacts
    for attempt in range(1, 4):
        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", BUILD_ARTIFACT_ALL, "-D", str(artifacts_dir),
            ], token=token)
            break
        except CycleError:
            _log(f"  Download attempt {attempt} failed, retrying...")
            time.sleep(5 * attempt)

    # Also try publish artifacts
    try:
        _gh([
            "run", "download", str(run_id),
            "-R", repo, "-n", BUILD_ARTIFACT_PUBLISH, "-D", str(artifacts_dir),
        ], token=token)
    except CycleError:
        _log("  No publish artifact (versions may not all be published yet)")

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
                # Check for success markers
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


def _get_step_log_for_target(target_name: str, jobs: list[dict], logs_dir: Path) -> str:
    """Find the build log for a specific target from the jobs."""
    for job in jobs:
        job_name = job.get("name", "")
        steps = job.get("steps", [])
        for step in steps:
            step_name = step.get("name", "")
            # Check if this step is for our target
            if target_name in job_name or target_name in step_name:
                # Try to download the specific log
                log_file = logs_dir / f"{target_name}_build.log"
                if log_file.exists():
                    return log_file.read_text(encoding="utf-8")
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
        "max_tokens": AI_MAX_TOKENS,
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

    # ── Phase C: Retry Loop ───────────────────────────────────────────────
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

        for target_name in current_targets:
            active_failed[target_name] = attempt
            mc_ver, loader = target_name.rsplit("-", 1)

            _log(f"\n  Processing failed: {target_name} (attempt {attempt})")

            # Get current source code
            source_code = _get_current_source_code(bundle_dir, target_name)

            # Get build error log
            jobs = _get_build_jobs(build_run_id, repo, token)
            build_log = _get_step_log_for_target(target_name, jobs, Path(results["build_log_dir"]))
            if not build_log:
                build_log = "(build log not available for this target)"

            # Collect DIF entries and MC source
            dif_entries = _collect_dif_for_target(mc_ver, loader)
            mc_source = _collect_mc_source_files(mc_ver, loader)

            # Create failed-N folder with context
            target_dir = bundle_dir / target_name
            _create_failed_context(
                target_dir, attempt, source_code,
                build_log, dif_entries, mc_source,
            )

            # Get mod info text from projectinfo.txt
            projectinfo_path = target_dir / "projectinfo.txt"
            mod_info = projectinfo_path.read_text(encoding="utf-8") if projectinfo_path.exists() else ""

            # Recompose the fix prompt
            fix_prompt = _recompose_fix_prompt(
                target_name, mod_info, source_code,
                build_log, dif_entries, mc_source,
            )

            # Send to AI
            _log(f"  Sending fix prompt to AI ({len(fix_prompt):,} chars)...")
            try:
                ai_response = _send_fix_prompt_to_ai(fix_prompt, nvidia_key, target_name)
            except CycleError as e:
                _log(f"  ✗ AI request failed: {e}")
                still_failed.append(target_name)
                continue

            # Save fix response
            fix_response_path = target_dir / f"airesponse_fix_{attempt}.txt"
            fix_response_path.write_text(ai_response, encoding="utf-8")
            _log(f"  ✓ AI fix response saved ({len(ai_response):,} chars)")

            # Extract fixed files
            fix_files = _extract_ai_files(ai_response)
            if not fix_files:
                _log(f"  ✗ No files extracted from AI fix response")
                still_failed.append(target_name)
                continue

            _log(f"  Extracted {len(fix_files)} fixed file(s)")

            # Merge and update airesponse.txt
            merged_source = _merge_source_files(source_code, fix_files)
            merged_dir = bundle_dir / target_name / ".merged_source"
            _save_source_files(merged_source, merged_dir)
            _save_source_files(merged_source, bundle_dir / target_name)

            # Re-create airesponse.txt with merged code
            merged_text = ""
            for filepath in sorted(merged_source.keys()):
                content = merged_source[filepath]
                lang = "java" if filepath.endswith(".java") else ""
                merged_text += f"`bundle/{target_name}/{filepath}`\n```{lang}\n{content}\n```\n\n"
            (bundle_dir / target_name / "airesponse.txt").write_text(merged_text, encoding="utf-8")

            fixed_this_round.append(target_name)

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
    remaining_failed = [t for t in target_names if t not in success_set]
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
