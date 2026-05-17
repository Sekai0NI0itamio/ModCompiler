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
                                with full context and build a fix prompt.
                                The fix prompt is sent to Grok via Safari
                                browser automation. The fixed code is
                                extracted, re-zipped, and re-built. Repeats
                                until all pass or max retries reached.

Usage:
  python3 scripts/ai_compile_cycle.py \
    --bundle-dir <path> \
    --slug <mod-slug> \
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

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from run_mod_to_all_converter import (
    _ai_select_fix_source_files,
    _read_source_file_contents,
    _trim_prompt_to_limit,
)

_DECOMPILED_ROOT = _REPO_ROOT / "DecompiledMinecraftSourceCode"


# ── Constants ────────────────────────────────────────────────────────────────

BUILD_WORKFLOW_FILE = "build.yml"
INCOMING_DIR = "incoming"
MAX_RETRIES = 5

# Artifact names from the Build workflow (build.yml)
BUILD_ARTIFACT_ALL = "all-mod-builds"
BUILD_ARTIFACT_PUBLISH = "modrinth-publish"
POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0

# --- AI Provider Config ---
#
#   HOSTER: Grok (Browser-Based)  |  hoster: "grok"
#
# This uses Safari automation on macOS to interact with the Grok web
# interface at grok.com. The user must be authenticated to Grok in Safari.
# The system never steals focus or disrupts the user's current work.
# Fresh Safari tabs are created for each request and closed afterward.
#
# SETUP (one-time):
#   1. Open Safari and authenticate to https://grok.com
#   2. Safari > Preferences > Advanced > Check "Show Develop menu in menu bar"
#   3. Safari > Develop menu > Check "Allow JavaScript from Apple Events"
#   4. Keep Safari running (can be minimized)
#   5. Key file at keys/grok.txt can be empty — "browser-auth" fallback is automatic
#   6. Run: python scripts/check_safari_permissions.py
#   7. Test:  python scripts/test_browser_grok.py
#
#   IMPORTANT: This hoster ONLY works on macOS. It uses AppleScript/JXA.

AI_C05_BASE = "http://localhost:8129"
AI_MODEL = "Fast"
AI_HOSTER = "grok"

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
            last_err = e.stderr[:500] if e.stderr else str(e)
            _log(f"  gh command failed (attempt {attempt}/{retries}): {last_err}")
            if attempt < retries:
                time.sleep(GH_RETRY_DELAY * attempt)
        except subprocess.TimeoutExpired as e:
            last_err = str(e)
            _log(f"  gh command timed out (attempt {attempt}/{retries}): {last_err}")
            if attempt < retries:
                time.sleep(GH_RETRY_DELAY * attempt)
    raise CycleError(f"gh {' '.join(args)} failed: {last_err}")


# ── Phase A: Extract files from AI responses and create build zip ───────────


def _is_infrastructure_failure(build_log: str) -> bool:
    if not build_log:
        return False
    infra_patterns = [
        r"Read timed out", r"Connection reset", r"Could not download.*timeout",
        r"Could not get resource.*timeout", r"SocketTimeoutException",
        r"Could not GET.*timed out", r"Response 304.*has no content",
        r"Could not resolve all artifacts", r"Could not download.*\\.jar",
        r"Unable to resolve dependency", r"503.*Service Unavailable",
        r"502.*Bad Gateway", r"504.*Gateway Timeout",
    ]
    for p in infra_patterns:
        if re.search(p, build_log, re.IGNORECASE):
            return True
    return False


def _map_slug_to_target_name(slug: str, target_names: list[str]) -> str | None:
    """Map a build workflow slug (e.g. 'lifestealparrotmod-fabric-1-20-6')
    back to a bundle target folder name (e.g. '1.20.6-fabric')."""
    # Slug format: <modid>-<loader>-<mc-version>   (dashes in version)
    # Target format: <mc-version>-<loader>          (dots in version)
    #
    slug_lower = slug.lower()
    for loader_key in ("neoforge", "forge", "fabric"):
        if loader_key in slug_lower:
            idx = slug_lower.rfind(loader_key)
            version_slug_part = slug[idx + len(loader_key):].lstrip("-")  # "1-20-6"
            # Convert dashes to dots for matching: "1-20-6" → "1.20.6"
            version_dotted = version_slug_part.replace("-", ".")
            for tn in target_names:
                tn_parts = tn.rsplit("-", 1)
                if len(tn_parts) == 2:
                    tn_loader = tn_parts[1].lower()
                    tn_version = tn_parts[0]  # "1.20.6"
                    if tn_loader == loader_key:
                        # Exact match on dotted version
                        if tn_version == version_dotted:
                            return tn
                        # Fuzzy: dotted version contains slug version
                        if version_dotted in tn_version or tn_version in version_dotted:
                            return tn

    # 2. Fallback: search by substring (reversed: tn="1.20.6-fabric")
    #    "1.20.6-fabric" in "lifestealparrotmod-fabric-1-20-6" → YES
    for tn in target_names:
        if tn in slug:
            return tn
        # Also try with version dots → dashes
        parts = tn.rsplit("-", 1)
        if len(parts) == 2:
            dotted_version = parts[0].replace(".", "-")
            slug_pattern = f"{parts[1]}-{dotted_version}"
            if slug_pattern in slug:
                return tn
    return target_names[0] if len(target_names) == 1 else None



def _is_valid_filepath(candidate: str) -> bool:
    """Check if a string looks like a valid filepath (not code)."""
    candidate = candidate.strip("`*[]'\"")
    if not candidate:
        return False
    # Must contain a path separator or at least look like a filename
    if "/" not in candidate and "." not in candidate:
        return False
    # Reject if it looks like code
    code_starts = ("package ", "import ", "public ", "private ",
                   "protected ", "class ", "interface ", "@", "{",
                   "//", "/*")
    if any(candidate.startswith(p) for p in code_starts):
        return False
    # Reject if it has multiple lines (a real path is a single line)
    if "\n" in candidate.rstrip():
        return False
    # The last segment should have an extension (e.g. file.java, file.json)
    last_part = candidate.split("/")[-1] if "/" in candidate else candidate
    if "." not in last_part:
        return False
    # Segment length sanity
    for seg in candidate.split("/"):
        if len(seg) > 120:
            return False
    return True


def _extract_ai_files(airesponse_text: str) -> dict[str, str]:
    """Parse AI response into a dict of filepath -> content.

    Uses a multi-strategy approach to handle various AI output formats:

    Strategy 1 (Primary): Pair consecutive ``` blocks.
      ```filepath
      path/to/file.java
      ```
      ```java
      code here
      ```

    Strategy 2 (Fallback): Backtick-wrapped filepath on its own line,
    followed by a code block:
      `path/to/file.java`
      ```java
      code here
      ```

    Strategy 3 (Regex): Find any filepath-like string near a code block
    and pair them intelligently.
    """
    all_lines = airesponse_text.splitlines()

    # ── Strategy 1: Pair consecutive ``` blocks ────────────────────────
    markers: list[tuple[int, str]] = []
    for idx, line in enumerate(all_lines):
        if "```" in line:
            lang = line[line.index("```") + 3:].strip()
            markers.append((idx, lang))

    blocks: list[tuple[str, str]] = []
    if len(markers) >= 2:
        for i in range(len(markers) - 1):
            start = markers[i][0] + 1
            end = markers[i + 1][0]
            if start >= end:
                continue
            content_lines = all_lines[start:end]
            content = "\n".join(content_lines).strip()
            if content:
                blocks.append((content, markers[i][1]))

    files: dict[str, str] = {}

    # Scan forward through blocks: find path → next block is its code/status
    i = 0
    while i < len(blocks) - 1:
        raw_path = blocks[i][0].strip()
        raw_code = blocks[i + 1][0].strip()
        code_lang = blocks[i + 1][1]

        if not raw_path or not raw_code:
            i += 1
            continue

        filepath = raw_path.strip("`*[]'\"")
        if not _is_valid_filepath(filepath):
            i += 1
            continue

        cleaned = _normalize_filepath(filepath)
        if _is_build_file(cleaned):
            i += 2
            continue

        if code_lang == "status" and raw_code.lower() == "unchanged":
            i += 2
            continue

        if cleaned not in files:
            files[cleaned] = raw_code
        i += 2

    # ── Strategy 2: Backtick-wrapped filepaths ────────────────────────
    # Look for lines like: `path/to/file.java` followed by a ``` code block
    if not files or not any(fp.endswith(".java") for fp in files):
        i = 0
        while i < len(all_lines):
            line = all_lines[i].strip()
            # Check if this line is a backtick-wrapped filepath
            if line.startswith("`") and line.endswith("`") and not line.startswith("```"):
                candidate = line.strip("`").strip()
                if _is_valid_filepath(candidate):
                    # Look ahead for the next ``` code block
                    for j in range(i + 1, min(i + 10, len(all_lines))):
                        if all_lines[j].strip().startswith("```"):
                            lang = all_lines[j].strip()[3:].strip()
                            if lang and lang not in ("filepath", "status", ""):
                                # Found a code block — extract its content
                                code_start = j + 1
                                code_end = len(all_lines)
                                for k in range(j + 1, len(all_lines)):
                                    if all_lines[k].strip().startswith("```"):
                                        code_end = k
                                        break
                                code_content = "\n".join(all_lines[code_start:code_end]).strip()
                                if code_content:
                                    cleaned = _normalize_filepath(candidate)
                                    if not _is_build_file(cleaned) and cleaned not in files:
                                        files[cleaned] = code_content
                                break
            i += 1

    # ── Strategy 3: Regex-based fallback ──────────────────────────────
    # Find any filepath-like pattern near a code block, even if the
    # filepath is embedded in text (e.g. "File: path/to/file.java")
    if not files or not any(fp.endswith(".java") for fp in files):
        # Find all code blocks (```...```) and their surrounding context
        code_blocks: list[tuple[int, int, str]] = []
        for idx, line in enumerate(all_lines):
            if line.strip().startswith("```"):
                lang = line.strip()[3:].strip()
                end_idx = -1
                for j in range(idx + 1, len(all_lines)):
                    if all_lines[j].strip().startswith("```"):
                        end_idx = j
                        break
                if end_idx > idx:
                    code_blocks.append((idx, end_idx, lang))

        for cb_start, cb_end, lang in code_blocks:
            if lang in ("filepath", "status", ""):
                continue
            code_content = "\n".join(all_lines[cb_start + 1:cb_end]).strip()
            if not code_content:
                continue

            # Look backwards from the code block for a filepath
            search_start = max(0, cb_start - 5)
            for j in range(cb_start - 1, search_start - 1, -1):
                line = all_lines[j].strip()
                # Try various formats
                for prefix_to_strip in ("`", "File:", "file:", "-", "*"):
                    candidate = line
                    if candidate.startswith(prefix_to_strip):
                        candidate = candidate[len(prefix_to_strip):].strip()
                    if candidate.endswith("`"):
                        candidate = candidate.rstrip("`").strip()
                    if _is_valid_filepath(candidate):
                        cleaned = _normalize_filepath(candidate)
                        if not _is_build_file(cleaned) and cleaned not in files:
                            files[cleaned] = code_content
                        break

    return files


def _normalize_filepath(filepath: str) -> str:
    """Normalize a filepath by stripping prefixes and ensuring proper structure."""
    result = filepath.strip()

    # Strip known prefixes
    for prefix in ("bundle/", "./"):
        if result.startswith(prefix):
            result = result[len(prefix):]

    # Strip leading target-name prefix ("1.12-forge/src/..." → "src/...")
    if "/" in result and not result.startswith("src/"):
        parts = result.split("/", 1)
        if len(parts) == 2:
            result = parts[1]

    # Ensure .java files are under src/main/java/
    if result.endswith(".java") and not result.startswith("src/"):
        result = "src/main/java/" + result

    return result


def _is_build_file(filepath: str) -> bool:
    """Check if a filepath is a build system file that should be skipped."""
    build_files = {
        "build.gradle", "build.gradle.kts",
        "settings.gradle", "settings.gradle.kts",
        "gradle.properties", "gradlew", "gradlew.bat",
    }
    return filepath.lower() in build_files or "/gradle/" in filepath.lower()
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
    targets_filter: set[str] | None = None,
) -> tuple[int, list[str]]:
    """Extract airesponse.txt files, build a source tree, and create a zip.

    The build workflow expects each target directory to contain:
      - src/        (source tree, e.g. src/main/java/...)
      - mod.txt     (mod metadata: mod_id, name, version, etc.)
      - version.txt (minecraft_version=X, loader=Y)

    Args:
        bundle_dir: The analysis bundle directory.
        output_zip: Path where the build zip will be created.
        targets_filter: Optional set of target names to include.  If provided,
                        only these targets are added to the zip.  This is used
                        during retry cycles to avoid rebuilding already-successful
                        versions.

    Returns (file_count, target_names).
    """
    temp_dir = Path(output_zip).parent / f".build_bundle_{output_zip.stem}"
    if temp_dir.exists():
        shutil.rmtree(str(temp_dir))
    temp_dir.mkdir(parents=True, exist_ok=True)

    target_dirs = sorted([
        d for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
        and (targets_filter is None or d.name in targets_filter)
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
            # Ensure paths with "bundle/" prefix are stripped
            if cleaned.startswith("bundle/"):
                cleaned = cleaned[len("bundle/"):]
                if cleaned.startswith(prefix):
                    cleaned = cleaned[len(prefix):]

            # Ensure .java files are placed under src/main/java/ if they
            # don't already have a proper prefix.  The AI sometimes outputs
            # bare paths like "com/example/Mod.java" without src/main/java/.
            if cleaned.endswith(".java") and not cleaned.startswith("src/"):
                cleaned = "src/main/java/" + cleaned

            dest = target_out / cleaned
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")
            total_files += 1

        # ── Generate mod.txt from projectinfo.txt and AI source ──────
        projectinfo = td / "projectinfo.txt"
        ai_text = ai_path.read_text(encoding="utf-8") if ai_path.exists() else ""
        mod_id = "unknown"
        mod_name = target_name
        mod_version = "1.0.0"
        mod_description = ""
        mod_authors = ""
        mod_group = ""

        # Scan both projectinfo.txt and AI response for mod identifiers
        for source_text, source_name in [(ai_text, "AI"),
                                          (projectinfo.read_text(encoding="utf-8") if projectinfo.exists() else "", "info")]:
            # FIRST: Header-based parsing (reliable, from projectinfo headers)
            for line in source_text.splitlines():
                s = line.strip()
                if s.startswith("Mod Name:"):
                    mod_name = s.split(":", 1)[1].strip()
                elif s.startswith("Mod Author:"):
                    mod_authors = s.split(":", 1)[1].strip()
                elif s.startswith("Mod Path:"):
                    mod_group = s.split(":", 1)[1].strip()
                    if mod_id == "unknown":
                        path_parts = mod_group.rsplit(".", 1)
                        if len(path_parts) > 1:
                            mod_id = path_parts[-1]

            # SECOND: @Mod, modid, MOD_ID patterns
            for line in source_text.splitlines():
                s = line.strip()
                m = re.search(r'@Mod\s*\(\s*(?:modid\s*=\s*)?["\x27]([^"\x27]+)["\x27]', s)
                if m and mod_id == "unknown":
                    mod_id = m.group(1)
                m = re.search(r'["\x27](?:modId|modid|id)["\x27]\s*:\s*["\x27]([^"\x27]+)["\x27]', s)
                if m and mod_id == "unknown":
                    mod_id = m.group(1)
                m = re.search(r'MOD_ID\s*=\s*["\x27]([^"\x27]+)["\x27]', s)
                if m and mod_id == "unknown":
                    mod_id = m.group(1)

            # THIRD: key = value pairs (for mod_id and version only, NOT name)
            if mod_id == "unknown" or mod_version == "1.0.0" or not mod_authors or not mod_group:
                for line in source_text.splitlines():
                    s = line.strip()
                    if "=" in s and not s.startswith("#"):
                        key, _, val = s.partition("=")
                        key = key.strip().lower().replace(" ", "_")
                        val = val.strip().strip('"').strip("'").rstrip(',').strip().strip('"').strip("'")
                        if key in ("mod_id", "modid", "id") and mod_id == "unknown":
                            mod_id = val
                        elif key in ("version", "mod_version") and mod_version == "1.0.0":
                            mod_version = val
                        elif key in ("authors", "author") and not mod_authors:
                            mod_authors = val
                        elif key in ("description") and not mod_description:
                            mod_description = val
                        elif key in ("group", "package", "mod_group") and not mod_group:
                            mod_group = val

        mod_txt_lines = []
        mod_txt_lines.append(f"mod_id={mod_id}")
        mod_txt_lines.append(f"name={mod_name}")
        mod_txt_lines.append(f"mod_version={mod_version}")
        if mod_group:
            mod_txt_lines.append(f"group={mod_group}")
        else:
            mod_txt_lines.append("group=net.itamio.skypvp")
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

        # Validate: the target MUST contain at least one .java file, otherwise
        # Gradle will report 'compileJava NO-SOURCE' and produce empty jars.
        java_files = list(target_out.rglob("*.java"))
        if not java_files:
            _log(f"  ⚠ {target_name}: NO .JAVA FILES in AI response — build will produce empty jars")
            _log(f"      Expected at least one file under src/main/java/ (got {len(files)} misc files)")
            _log(f"      The retry cycle will attempt to fix this via a targeted AI prompt.")
        else:
            _log(f"  ✓ {target_name}: {len(files)} file(s) ({len(java_files)} .java) + mod.txt/version.txt")

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
    # Clean any stale artifacts from a previous run to avoid
    # "file exists" errors — gh run download refuses to overwrite.
    if dest_dir.exists():
        shutil.rmtree(str(dest_dir))
    dest_dir.mkdir(parents=True, exist_ok=True)
    artifacts_dir = dest_dir / "artifacts"
    logs_dir = dest_dir / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    # Wait a few seconds for GitHub to finalize artifacts
    time.sleep(5)

    # ── Download per-mod build artifacts ───────────────────────────
    # The individual per-mod artifacts (mod-{slug}) are uploaded by the
    # `build` job in build.yml with `if: always()`, making them much more
    # reliable than the combined `all-mod-builds` bundle.
    #
    # First list ALL artifacts to discover what's available
    artifact_names: list[str] = []
    try:
        api_out = _gh([
            "api", f"repos/{repo}/actions/runs/{run_id}/artifacts",
        ], token=token, retries=2)
        api_data = json.loads(api_out)
        for art in api_data.get("artifacts", []):
            artifact_names.append(art["name"])
        _log(f"  Found {len(artifact_names)} artifact(s) via API")
    except (CycleError, json.JSONDecodeError) as e:
        _log(f"  Could not list artifacts: {e}")

    # Download each per-mod artifact individually
    artifact_downloaded = False
    per_mod_artifact_dir = artifacts_dir / "per_mod"
    per_mod_artifact_dir.mkdir(parents=True, exist_ok=True)
    for art_name in artifact_names:
        if not art_name.startswith("mod-"):
            continue
        # Each per-mod artifact goes into its own subdirectory
        slug = art_name[len("mod-"):]
        slug_dir = per_mod_artifact_dir / slug
        # Clean and recreate directory to avoid "file exists" errors
        if slug_dir.exists():
            shutil.rmtree(str(slug_dir))
        slug_dir.mkdir(parents=True, exist_ok=True)
        for attempt in range(1, 4):
            try:
                _gh([
                    "run", "download", str(run_id),
                    "-R", repo, "-n", art_name, "-D", str(slug_dir),
                ], token=token, retries=2)
                artifact_downloaded = True
                _log(f"    ✓ Downloaded {art_name}")
                break
            except CycleError as e:
                err_preview = str(e)[:200]
                if attempt < 3:
                    _log(f"    Retry {attempt} for {art_name}: {err_preview}")
                    time.sleep(5)
                else:
                    _log(f"    ⚠ Could not download {art_name}: {err_preview}")

    # Fallback: try the combined bundle if no per-mod artifacts were available
    all_artifact_dir = artifacts_dir / BUILD_ARTIFACT_ALL
    if not artifact_downloaded:
        _log("  No per-mod artifacts found, trying combined bundle...")
        for attempt in range(1, 4):
            # Clean and recreate to avoid "file exists" errors from stale extractions
            if all_artifact_dir.exists():
                shutil.rmtree(str(all_artifact_dir))
            all_artifact_dir.mkdir(parents=True, exist_ok=True)
            try:
                _gh([
                    "run", "download", str(run_id),
                    "-R", repo, "-n", BUILD_ARTIFACT_ALL, "-D", str(all_artifact_dir),
                ], token=token)
                artifact_downloaded = True
                break
            except CycleError as e:
                err_preview = str(e)[:200]
                _log(f"  Combined bundle attempt {attempt}: {err_preview}")
                if attempt < 3:
                    time.sleep(10)

    # ── Download publish artifact ────────────────────────────────────
    publish_artifact_dir = artifacts_dir / BUILD_ARTIFACT_PUBLISH
    if publish_artifact_dir.exists():
        shutil.rmtree(str(publish_artifact_dir))
    publish_artifact_dir.mkdir(parents=True, exist_ok=True)
    publish_available = BUILD_ARTIFACT_PUBLISH in artifact_names

    if publish_available:
        _log("  Publish artifact found via API, downloading...")
        for attempt in range(1, 6):
            try:
                _gh([
                    "run", "download", str(run_id),
                    "-R", repo, "-n", BUILD_ARTIFACT_PUBLISH, "-D", str(publish_artifact_dir),
                ], token=token, retries=2)
                break
            except CycleError as e:
                delay = min(10 * attempt, 60)
                err_preview = str(e)[:200]
                _log(f"  Publish download attempt {attempt} failed: {err_preview}")
                if attempt < 6:
                    time.sleep(delay)
    else:
        _log("  No publish artifact found via API")

    # Download logs
    try:
        raw_log = _gh(["run", "view", str(run_id), "-R", repo, "--log"], token=token)
        (logs_dir / "build_overview.txt").write_text(raw_log, encoding="utf-8")
    except CycleError:
        pass

    # Analyze results from per-mod artifacts
    success_versions: list[str] = []
    failed_versions: list[str] = []
    published_versions: list[str] = []

    # Scan per-mod build artifacts (mod-{slug}) — these are the most reliable
    # source of build results since each is uploaded independently.
    per_mod_dir = artifacts_dir / "per_mod"
    if per_mod_dir.exists():
        for slug_dir in sorted(per_mod_dir.iterdir()):
            if not slug_dir.is_dir() or slug_dir.name.startswith("."):
                continue
            result_json = slug_dir / "result.json"
            if result_json.exists():
                try:
                    rj = json.loads(result_json.read_text(encoding="utf-8"))
                    slug = rj.get("slug", "")
                    status = rj.get("status", "")
                    if status == "success":
                        if slug not in success_versions:
                            success_versions.append(slug)
                    elif status == "failed":
                        if slug not in failed_versions:
                            failed_versions.append(slug)
                except (json.JSONDecodeError, KeyError):
                    pass

    # Also check the combined bundle as fallback
    all_artifact_dir = artifacts_dir / BUILD_ARTIFACT_ALL
    if all_artifact_dir.exists():
        summary_file = all_artifact_dir / "SUMMARY.md"
        if summary_file.exists():
            summary_text = summary_file.read_text(encoding="utf-8")
            for line in summary_text.splitlines():
                if "|" in line and "✅" in line:
                    parts = [p.strip() for p in line.split("|") if p.strip()]
                    if parts and parts[0] not in success_versions:
                        success_versions.append(parts[0])
                if "|" in line and "❌" in line:
                    parts = [p.strip() for p in line.split("|") if p.strip()]
                    if parts and parts[0] not in failed_versions:
                        failed_versions.append(parts[0])

    # Check for published versions via the structured result.json (most reliable)
    publish_artifact_dir = artifacts_dir / BUILD_ARTIFACT_PUBLISH
    publish_result_json = publish_artifact_dir / "result.json"
    if publish_result_json.exists():
        try:
            pub_result = json.loads(publish_result_json.read_text(encoding="utf-8"))
            for upload in pub_result.get("uploads", []):
                slug = upload.get("slug", "").strip()
                pub_status = upload.get("publish_status", "").strip()
                if slug and pub_status == "uploaded":
                    if slug not in published_versions:
                        published_versions.append(slug)
        except (json.JSONDecodeError, KeyError) as e:
            _log(f"  Warning: Could not parse publish result.json: {e}")

    # Fallback: parse SUMMARY.md table format
    if not published_versions:
        publish_summary = publish_artifact_dir / "SUMMARY.md"
        if publish_summary.exists():
            pub_text = publish_summary.read_text(encoding="utf-8")
            in_table = False
            for line in pub_text.splitlines():
                stripped = line.strip()
                # Detect markdown table rows (pipe-delimited, not header/separator)
                if stripped.startswith("|") and stripped.endswith("|"):
                    if "---" in stripped or "Slug" in stripped:
                        in_table = True
                        continue
                    if in_table:
                        parts = [p.strip() for p in stripped.split("|") if p.strip()]
                        if len(parts) >= 5:
                            slug = parts[0]
                            pub_status = parts[4]  # Publish Status column
                            if pub_status == "uploaded" and slug not in published_versions:
                                published_versions.append(slug)

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
        "all_artifacts_dir": str(per_mod_dir) if per_mod_dir.exists() else str(all_artifact_dir) if all_artifact_dir.exists() else "",
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


def _extract_build_errors(build_log: str) -> str:
    """Extract only the actual compilation errors from a Gradle build log.

    The build log structure is:
      1. Gradle download progress / welcome banner
      2. Config messages
      3. > Task :compileJava         ← errors start HERE
      4. ... error messages ...
      5. > Task :compileJava FAILED  ← failure marker

    Everything before ':compileJava' is Gradle boilerplate (download progress,
    version banner, config), not useful for the AI.  We return everything from
    the first line containing ':compileJava' onward.
    """
    if not build_log:
        return build_log

    lines = build_log.splitlines()
    for i, line in enumerate(lines):
        if ":compileJava" in line:
            # Found the marker — return from this line to end
            return "\n".join(lines[i:])

    # Fallback: if no marker found, just return the last 50 lines
    last_lines = lines[-50:] if len(lines) > 50 else lines
    return "\n".join(last_lines)


def _get_step_log_for_target(
    target_name: str,
    jobs: list[dict],
    logs_dir: Path,
    repo: str,
    token: str,
    run_id: int,
) -> str:
    """Get the build log for a specific target.

    First tries to find the already-downloaded per-mod artifact from the main
    download phase (artifacts/per_mod/…), then falls back to re-downloading.
    The log is automatically trimmed to only the error section.
    """
    def _read_and_trim(path: Path) -> str:
        return _extract_build_errors(path.read_text(encoding="utf-8"))
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
        # 1. Check the already-downloaded per-mod artifact first
        # logs_dir = .../.build_results/logs/  →  artifacts are at .../.build_results/artifacts/
        cached_dir = logs_dir.parent / "artifacts" / "per_mod" / slug
        if cached_dir.exists():
            for p in cached_dir.rglob("build.log"):
                return _read_and_trim(p)
            result_json = cached_dir / "result.json"
            if result_json.exists():
                try:
                    rj = json.loads(result_json.read_text(encoding="utf-8"))
                    log_rel = rj.get("log_relpath", "build.log")
                    lp = cached_dir / log_rel
                    if lp.exists():
                        return _read_and_trim(lp)
                except (json.JSONDecodeError, OSError):
                    pass

        # 2. Fallback: re-download (clean old dir first to avoid file-exists)
        per_mod_dir = logs_dir.parent / "per_mod_artifacts" / slug
        if per_mod_dir.exists():
            shutil.rmtree(str(per_mod_dir))
        per_mod_dir.mkdir(parents=True, exist_ok=True)
        try:
            _gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", f"mod-{slug}", "-D", str(per_mod_dir),
            ], token=token, retries=2)
            for p in per_mod_dir.rglob("build.log"):
                return _read_and_trim(p)
            result_json = per_mod_dir / "result.json"
            if result_json.exists():
                rj = json.loads(result_json.read_text(encoding="utf-8"))
                log_rel = rj.get("log_relpath", "build.log")
                lp = per_mod_dir / log_rel
                if lp.exists():
                    return _read_and_trim(lp)
        except (CycleError, json.JSONDecodeError, OSError):
            pass

    if job_id:
        try:
            job_log = _gh([
                "run", "view", str(run_id), "-R", repo,
                "--log", "--job", str(job_id),
            ], token=token, retries=2)
            if job_log.strip():
                trimmed = _extract_build_errors(job_log)
                (logs_dir / f"{target_name}_build.log").write_text(trimmed, encoding="utf-8")
                return trimmed
        except CycleError:
            pass

    for p in [logs_dir / f"{target_name}_build.log", logs_dir / "build_overview.txt"]:
        if p.exists():
            text = _extract_build_errors(p.read_text(encoding="utf-8", errors="replace"))
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

## Original Coding Prompt (Full Context)

Below is the original prompt that was used to create this mod version.
It contains the full specification: mod name, mod ID, author, package, main class,
required files, and loader-specific API patterns. Read it carefully to understand
what the mod is supposed to do.

{original_prompt}

## Previous AI Response (Code That Failed)

Below is the AI's previous response — the code that was generated and failed to build.
Review it to understand what was attempted and what needs to be fixed.

{previous_ai_response}

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
2. You MUST list ALL source files in the project. For each file, indicate
   whether it is MODIFIED (provide full code) or UNCHANGED (provide status only).
3. For each file, you MUST follow this EXACT format — two code blocks per file.
   This is the ONLY accepted format. The parser strictly reads blocks in pairs:

   ### FOR MODIFIED FILES (full code required) — CORRECT EXAMPLE:
   ```filepath
   bundle/{target_name}/src/main/java/net/itamio/skypvp/SkypvpMod.java
   ```
   ```java
   package net.itamio.skypvp;

   public class SkypvpMod {{
       // ...
   }}
   ```

   ### FOR UNCHANGED FILES (status only) — CORRECT EXAMPLE:
   ```filepath
   bundle/{target_name}/src/main/resources/pack.mcmeta
   ```
   ```status
   unchanged
   ```

   The FIRST backtick block always has the language `filepath` and contains ONLY the path.
   The SECOND backtick block contains either:
     - The full source code with the correct language (e.g. `java`, `json`, `toml`)
       for MODIFIED files, OR
     - `status` with content `unchanged` for UNCHANGED files.
   The parser reads both blocks in pairs.

   ### ❌ COMMON MISTAKES — DO NOT do any of these:
   - DO NOT wrap the filepath in single backticks: `path/to/file.java` ← WRONG
   - DO NOT put the filepath on a line without a ```filepath block: path/to/file.java ← WRONG
   - DO NOT add extra text between the filepath block and the code block
   - DO NOT use `filepath` as the language for the code block — use `java`, `json`, `toml`, etc.
   - DO NOT forget to include ALL files — every file must be listed (modified or unchanged)
   - DO NOT create build files (build.gradle, settings.gradle, etc.)
{source_code_note}

### IMPORTANT FILEPATH RULES

- **Java source files MUST be placed under `src/main/java/`**.  For example:
  ```filepath
  bundle/{target_name}/src/main/java/net/itamio/skypvp/SkypvpMod.java
  ```
  ```java
  package com.example;
  public class MyMod {{ ... }}
  ```
- Resource files should go under `src/main/resources/`.
- Every filepath MUST contain an extension (`.java`, `.json`, `.toml`, etc.).

4. Provide COMPLETE file contents — no stubs, no TODOs, no placeholders.
5. If the build error is about missing methods/classes, check the provided
   Minecraft source code and DIF entries for the correct API to use.
6. You MUST include EVERY source file in the project output. Files that do not
   need changes must still be listed with a `status: unchanged` marker.
7. **CRITICAL: DO NOT create build files** — The build system already provides:
   - build.gradle / build.gradle.kts
   - settings.gradle / settings.gradle.kts
   - gradle.properties
   - gradlew / gradlew.bat
   - gradle/wrapper/*
   Creating these files will cause build failures. Only create source files.
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


def _find_decompiled_source_dir(minecraft_version: str, loader: str) -> Path | None:
    target_name = f"{minecraft_version}-{loader}"
    direct = _DECOMPILED_ROOT / target_name
    if direct.exists() and direct.is_dir():
        return direct
    if not _DECOMPILED_ROOT.exists():
        return None
    for d in _DECOMPILED_ROOT.iterdir():
        if not d.is_dir():
            continue
        if d.name.startswith(f"{minecraft_version}-{loader}"):
            return d
        if minecraft_version.startswith(d.name.split("-")[0]):
            return d
    return None


def _collect_decompiled_source_paths(source_dir: Path) -> list[str]:
    paths = []
    for p in sorted(source_dir.rglob("*.java")):
        rel = str(p.relative_to(source_dir))
        if rel.endswith("package-info.java"):
            continue
        paths.append(rel)
    return paths


def _collect_mc_source_files(
    minecraft_version: str,
    loader: str,
    build_error: str = "",
    current_code: str = "",
) -> dict[str, str]:
    src_files: dict[str, str] = {}
    decomp_dir = _find_decompiled_source_dir(minecraft_version, loader)
    if not decomp_dir:
        decomp_root = Path("DecompiledMinecraftSourceCode")
        if decomp_root.exists():
            for vdir in decomp_root.iterdir():
                if not vdir.is_dir():
                    continue
                vname = vdir.name
                if minecraft_version.startswith(vname) or vname.startswith(minecraft_version.split(".")[0]):
                    decomp_dir = vdir
                    break
    if not decomp_dir:
        return src_files

    source_paths = _collect_decompiled_source_paths(decomp_dir)
    if not source_paths:
        return src_files

    selected_paths: list[str] = []
    if build_error and current_code:
        try:
            ai_selected = _ai_select_fix_source_files(
                source_paths, build_error, current_code,
            )
            if ai_selected:
                selected_paths = ai_selected
                _log(f"    AI selected {len(selected_paths)} relevant source file(s) for {minecraft_version}-{loader}")
        except Exception as exc:
            _log(f"    AI file selection failed for {minecraft_version}-{loader}: {exc}")

    if not selected_paths:
        for p in decomp_dir.rglob("*.java"):
            try:
                text = p.read_text(encoding="utf-8", errors="replace")
                rel = str(p.relative_to(decomp_dir))
                if len(text) < 200000:
                    src_files[rel] = text
            except Exception:
                pass
        return src_files

    for path in selected_paths:
        file_path = decomp_dir / path
        if not file_path.exists() or not file_path.is_file():
            continue
        try:
            text = file_path.read_text(encoding="utf-8", errors="replace")
            if len(text) < 200000:
                src_files[path] = text
        except Exception:
            pass

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
    original_prompt_text: str = "",
    previous_ai_response: str = "",
) -> str:
    """Create a fix prompt for a failed version."""
    mc_ver, loader = target_name.rsplit("-", 1)

    # Format original prompt — cap at 8KB
    MAX_ORIGINAL_PROMPT = 8000
    original_prompt_trimmed = original_prompt_text[:MAX_ORIGINAL_PROMPT] if original_prompt_text else "(no original prompt available)"
    if original_prompt_text and len(original_prompt_text) > MAX_ORIGINAL_PROMPT:
        original_prompt_trimmed += "\n\n(... original prompt truncated ...)"

    # Format previous AI response — cap at 8KB
    MAX_PREV_RESPONSE = 8000
    prev_response_trimmed = previous_ai_response[:MAX_PREV_RESPONSE] if previous_ai_response else "(no previous AI response available)"
    if previous_ai_response and len(previous_ai_response) > MAX_PREV_RESPONSE:
        prev_response_trimmed += "\n\n(... previous AI response truncated ...)"

    # Format source code — cap each file to 3KB and total to 15KB
    MAX_SRC_PER_FILE = 3000
    MAX_SRC_TOTAL = 15000
    src_lines = []
    total_src_size = 0
    for filepath in sorted(source_code.keys()):
        if total_src_size >= MAX_SRC_TOTAL:
            src_lines.append(f"  (… remaining {len(source_code) - len(src_lines)} files truncated)")
            break
        content = source_code[filepath][:MAX_SRC_PER_FILE]
        src_lines.append(f"`{filepath}`")
        src_lines.append("```java" if filepath.endswith(".java") else "```")
        src_lines.append(content)
        src_lines.append("```")
        src_lines.append("")
        total_src_size += len(content)

    src_text = "\n".join(src_lines) if src_lines else "(no source code available)"

    # Format DIF entries — cap at 4 to keep prompt size manageable
    dif_text = ""
    for entry in dif_entries[:4]:
        fix_text = entry.get("fix", "")[:1500]  # Truncate each fix to 1.5KB
        dif_text += f"### [{entry['id']}] {entry['title']}\n{fix_text}\n\n"

    if not dif_text:
        dif_text = "(no DIF entries found for this target)"

    # Format MC source — cap at 8 files, 5KB each max
    mc_text = ""
    if mc_source_files:
        mc_text = "=== RELEVANT MINECRAFT SOURCE CODE (for fixing the error) ===\n"
        for filepath in sorted(mc_source_files.keys())[:8]:
            content = mc_source_files[filepath][:5000]
            mc_text += f"--- {filepath} ---\n```java\n{content}\n```\n\n"
        mc_text += "=== END RELEVANT MINECRAFT SOURCE CODE ===\n"

    # Detect if there are no Java source files at all — this indicates the
    # AI response didn't produce valid code, which requires a special note.
    has_java_sources = any(fp.endswith(".java") for fp in source_code)
    if not has_java_sources:
        # Try to extract the main class name from the original prompt
        main_class = _extract_main_class_name(original_prompt_text) or "ModClass"
        source_code_note = (
            "\n### ❌ NO JAVA SOURCE FILES FOUND\n"
            "The current source code contains NO Java files at all. "
            "This is why the build produced empty jars.\n"
            "You MUST create the complete Java source files for this mod. "
            f"Begin by writing the main mod class at:\n"
            f"  `bundle/{target_name}/src/main/java/.../{main_class}.java`\n"
            "Refer to the Mod Information section above for the package path.\n"
            "Create ALL necessary classes (main mod, mixins, config, storage, etc.) "
            "with FULL implementations — no stubs or TODOs."
        )
    else:
        source_code_note = ""

    if not mc_text:
        mc_text = "(no Minecraft source files available)"

    # Truncate build log to 5KB to keep prompt size manageable
    build_log_truncated = build_error_log[:5000] if build_error_log else "(no build log)"
    if build_error_log and len(build_error_log) > 5000:
        build_log_truncated += "\n\n(... build log truncated, last 300 chars shown below ...)\n"
        build_log_truncated += build_error_log[-300:]

    prompt = FIX_PROMPT_TEMPLATE.format(
        mod_info=mod_info_text[:2000] if mod_info_text else "(no mod info)",
        original_prompt=original_prompt_trimmed,
        previous_ai_response=prev_response_trimmed,
        source_code=src_text,
        mc_source=mc_text,
        build_log=build_log_truncated,
        dif_entries=dif_text,
        target_name=target_name,
        source_code_note=source_code_note,
    )

    return prompt


def _extract_main_class_name(prompt_text: str) -> str | None:
    """Extract the main class name from a prompt text.

    Looks for patterns like:
      - "The main class name is `ClassName`"
      - "main class name is ClassName"
      - "`ClassName.java`"
    """
    if not prompt_text:
        return None
    # Pattern: "main class name is `ClassName`" or "main class name is ClassName"
    m = re.search(r'main\s+class\s+name\s+is\s+`?(\w+)`?', prompt_text, re.IGNORECASE)
    if m:
        return m.group(1)
    # Pattern: "`ClassName.java`" near "main class"
    m = re.search(r'main\s+class.*?`(\w+)\.java`', prompt_text, re.IGNORECASE)
    if m:
        return m.group(1)
    return None


DIF_GENERATION_TEMPLATE = """You are a documentation expert for Minecraft mod development.
You analyze build successes and failures to create DIF (Documented Issue Fix)
entries that help other AI agents avoid the same mistakes.

## Context

A mod version **{mc_version}** / **{loader}** failed to build with the errors below.
The AI retry cycle fixed it on attempt {fixed_on_attempt}.  Below is the full
history — source code before the fix, the build errors, and the working source code.

## Build Error Log

{build_log}

## Source Code BEFORE Fix

{source_before}

## Source Code AFTER Fix (working)

{source_after}

## Instructions

Write a DIF document in the following exact format.  Use YAML frontmatter between
`---` markers, then a markdown body.

---
id: <UPPERCASE-ID>
title: A short, descriptive title
tags: [{loader}, compile-error, api-change, {mc_version}]
versions: [{mc_version}]
loaders: [{loader}]
symbols: [<relevant-class-names>]
error_patterns: ["<regex patterns that match the build errors>"]
---

## Issue

1-2 sentence description of the problem.

## Error

Copy the exact error message from the build log.

## Root Cause

Brief explanation of why the error occurred.

## Fix

Show the corrected code snippet(s) from the AFTER version that fixed the issue.
Explain what changed.

## Verified

Confirmed in {slug} build run."

---

Use this information:
- Target: {target_name}
- Slug: {slug}
- Write the id like "{loader.upper()}-{mc_version.replace('.','')}-SHORT-DESC"
- The error_patterns should be regex patterns that would match the build errors
Generate ONLY the DIF document content, nothing else."""


def _generate_dif_entry(
    mc_version: str,
    loader: str,
    target_name: str,
    slug: str,
    build_log: str,
    source_before: dict[str, str],
    source_after: dict[str, str],
    fixed_on_attempt: int,
    dif_dir: Path,
) -> None:
    """Generate a DIF document from build history and save to dif/ directory."""
    # Format source code for the prompt
    def _fmt_source(src: dict[str, str]) -> str:
        lines = []
        for fp in sorted(src.keys()):
            content = src[fp]
            lang = "java" if fp.endswith(".java") else ""
            lines.append(f"`{fp}`")
            lines.append(f"```{lang}")
            lines.append(content[:2000])  # Limit per file for prompt size
            lines.append("```")
            lines.append("")
        return "\n".join(lines) if lines else "(no source code)"

    prompt = DIF_GENERATION_TEMPLATE.format(
        mc_version=mc_version,
        loader=loader,
        target_name=target_name,
        slug=slug,
        build_log=build_log[-3000:],  # Last 3000 chars of build log
        source_before=_fmt_source(source_before),
        source_after=_fmt_source(source_after),
        fixed_on_attempt=fixed_on_attempt,
    )

    messages = [
        {
            "role": "system",
            "content": (
                "You are a documentation expert for Minecraft mod development. "
                "You analyze build successes and failures to create DIF entries."
            )
        },
        {"role": "user", "content": prompt},
    ]

    _log(f"  Generating DIF entry for {target_name}...")
    try:
        dif_content = _send_fix_prompt_to_ai(messages, target_name)
    except CycleError as e:
        _log(f"  ⚠ DIF generation failed for {target_name}: {e}")
        return

    if not dif_content.strip():
        _log(f"  ⚠ DIF generation returned empty content for {target_name}")
        return

    # Extract the DIF content (strip any extra commentary from the AI)
    # The AI might wrap in extra ``` markers — strip those
    clean = dif_content.strip().strip("`").strip()

    # Generate a filename from the first few lines
    first_line = clean.split("\n")[0] if "\n" in clean else clean[:60]
    # Find the id from frontmatter
    id_match = re.search(r"^id:\s*(.+)$", clean, re.MULTILINE)
    dif_id = id_match.group(1).strip() if id_match else slug.upper()
    # Sanitize for filename
    safe_id = re.sub(r"[^A-Za-z0-9_-]", "_", dif_id)[:80]
    dif_path = dif_dir / f"{safe_id}.md"

    # Don't overwrite existing DIF entries
    if dif_path.exists():
        _log(f"  ⚠ DIF entry already exists: {dif_path.name} — skipping")
        return

    dif_path.write_text(clean, encoding="utf-8")
    _log(f"  ✓ DIF entry saved: {dif_path.name}")


def _send_fix_prompt_to_ai(
    messages: list[dict],
    target_name: str,
) -> str:
    """Send messages to Grok via browser automation (Safari) and return the response.

    Args:
        messages: List of message dicts with 'role' and 'content' keys.
                  The first should be the system message.
        target_name: Target name for logging.
    """
    import http.client
    import urllib.parse

    system_prompt = ""
    user_parts: list[str] = []

    for msg in messages:
        role = msg.get("role", "")
        content = msg.get("content", "")
        if role == "system":
            system_prompt = content
        elif role == "user":
            user_parts.append(f"[User]:\n{content}")
        elif role == "assistant":
            user_parts.append(f"[Assistant]:\n{content}")

    user_prompt = "\n\n".join(user_parts)

    user_prompt = _trim_prompt_to_limit(user_prompt)

    payload: dict[str, Any] = {
        "hoster": AI_HOSTER,
        "model": AI_MODEL,
        "request_type": "browser_based",
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
        "response_mode": "direct",
    }

    body_bytes = json.dumps(payload).encode("utf-8")

    parsed = urllib.parse.urlparse(AI_C05_BASE)
    host = parsed.hostname or "localhost"
    port = parsed.port or 8129

    full_response = ""
    accumulated = 0
    buffer = ""
    done = False

    conn = http.client.HTTPConnection(host, port, timeout=600)
    try:
        conn.request(
            "POST",
            "/chat",
            body=body_bytes,
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
        )
        resp = conn.getresponse()

        if resp.status != 200:
            error_detail = resp.read().decode("utf-8", errors="replace")[:500]
            if resp.status in (429, 503):
                print(f"  ⚠ {target_name}: HTTP {resp.status} — waiting 30s before retry...")
                time.sleep(30)
                raise CycleError(f"HTTP {resp.status} (retryable): {error_detail}")
            raise CycleError(f"HTTP {resp.status}: {error_detail}")

        while not done:
            chunk = resp.read(4096)
            if not chunk:
                break
            buffer += chunk.decode("utf-8", errors="replace")

            while "\n" in buffer and not done:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except json.JSONDecodeError:
                    continue

                if "error" in data:
                    raise CycleError(str(data["error"]))

                if data.get("event") == "content":
                    content = data.get("content", "") or ""
                    if content:
                        full_response += content
                        accumulated += len(content)
                        if accumulated > 200000:
                            raise CycleError(f"Response exceeded 200KB ({accumulated:,} bytes)")

                if data.get("status") == "end":
                    end_content = data.get("content", "") or ""
                    if end_content and not full_response:
                        full_response = end_content
                        accumulated = len(end_content)
                    done = True
                    break
    except Exception:
        conn.close()
        raise

    conn.close()

    if not full_response:
        raise CycleError("Empty response from Grok — no content generated.")

    print(f"  {target_name}: ✓ {accumulated:,} bytes received from Grok")
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
    repo: str,
    max_retries: int = MAX_RETRIES,
    continuefrom: bool = False,

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

    if continuefrom and zip_path.exists():
        _log(f"  Skipping Phase A (build zip already exists): {zip_path}")
    else:
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

    build_results_dir = bundle_dir.parent / ".build_results"
    results_json_path = build_results_dir / "results.json"

    if continuefrom and results_json_path.exists():
        _log("  Skipping Phase B (build results already downloaded)")
        saved = json.loads(results_json_path.read_text(encoding="utf-8"))
        build_run_id = saved["build_run_id"]
        success_set = set(saved["success_versions"])
        failed_set = set(saved["failed_versions"])
        published_set = set(saved["published_versions"])
        results = {
            "success_versions": saved["success_versions"],
            "failed_versions": saved["failed_versions"],
            "published_versions": saved["published_versions"],
            "all_artifacts_dir": saved.get("all_artifacts_dir", ""),
            "build_log_dir": saved.get("build_log_dir", ""),
        }
        _log(f"  Loaded results: {len(success_set)} success, {len(failed_set)} failed")
    else:
        build_run_id = _dispatch_build(
            zip_path=str(zip_path),
            slug=slug,
            modrinth_url=modrinth_url,
            repo=repo,
            token=token,
        )
        build_conclusion = _wait_for_build(build_run_id, repo, token)

        _log(f"Downloading build results...")
        results = _download_build_results(build_run_id, repo, token, build_results_dir)

        # Save results for future --continuefrom
        build_results_dir.mkdir(parents=True, exist_ok=True)
        results_json_path.write_text(json.dumps({
            "build_run_id": build_run_id,
            "success_versions": results["success_versions"],
            "failed_versions": results["failed_versions"],
            "published_versions": results["published_versions"],
            "all_artifacts_dir": results.get("all_artifacts_dir", ""),
            "build_log_dir": results.get("build_log_dir", ""),
        }, indent=2), encoding="utf-8")
        _log(f"  Saved build results to {results_json_path}")

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
            _log(f"  ⚠ Could not map slug '{slug}' to target — using as-is")
            mapped_failed.add(slug)
    failed_set = mapped_failed

    mapped_success: set[str] = set()
    for slug in success_set:
        mapped = _map_slug_to_target_name(slug, target_names)
        if mapped:
            mapped_success.add(mapped)
        else:
            _log(f"  ⚠ Could not map slug '{slug}' to target — using as-is")
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

    # Build history tracker for DIF generation
    build_history: dict[str, dict] = {}
    for tname in target_names:
        mc_ver, loader = tname.rsplit("-", 1)
        build_history[tname] = {
            "mc_version": mc_ver,
            "loader": loader,
            "errors": [],         # list of build error logs
            "source_before": {},   # source BEFORE any fix
            "source_after": {},    # source AFTER successful fix
            "fixed_on_attempt": 0, # which retry attempt fixed it
            "fixed": False,
        }

    # Multi-round conversation tracking for AI retries.
    # Each target gets a conversation history:
    #   [system_msg, initial_user_msg, assistant_resp, retry_user_msg, ...]
    # The first request builds the cache prefix (system + initial user).
    # Subsequent requests reuse the cached prefix — only the new
    # assistant + user tokens are charged at full rate (saves money).
    SYSTEM_PROMPT = (
        "You are an excellent and professional Minecraft mod developer. "
        "You are expert at reading build error logs and fixing compilation issues. "
        "You write clean, correct, and well-structured Java code. "
        "Provide ONLY the files that need to be changed, with complete implementations."
    )
    conversations: dict[str, list[dict]] = {}

    # ── Load retry progress for continuefrom ────────────────────────────
    retry_progress_path = bundle_dir / "retry_progress.json"
    saved_retry_progress: dict = {}
    if continuefrom and retry_progress_path.exists():
        try:
            saved_retry_progress = json.loads(retry_progress_path.read_text(encoding="utf-8"))
            _log(f"  Loaded retry progress from {retry_progress_path}")
            _log(f"  Completed attempts: {saved_retry_progress.get('completed_attempts', [])}")
        except (json.JSONDecodeError, Exception) as exc:
            _log(f"  ⚠ Failed to load retry progress: {exc}")
            saved_retry_progress = {}

    completed_attempts: list[int] = saved_retry_progress.get("completed_attempts", [])

    # Restore the remaining failed targets from saved progress (if resuming)
    saved_remaining = saved_retry_progress.get("remaining_failed", [])
    if continuefrom and saved_remaining:
        # Use the saved remaining_failed as the authoritative list
        # (targets that were fixed in earlier attempts are removed)
        old_count = len(current_targets)
        current_targets = [t for t in current_targets if t in saved_remaining]
        _log(f"  Restored {len(current_targets)} remaining failed target(s) from saved progress")
        _log(f"  (was {old_count} before, {old_count - len(current_targets)} already fixed)")
        # Also update active_failed tracking
        for tname in current_targets:
            if tname not in active_failed:
                active_failed[tname] = 0

    def _is_attempt_completed(tname: str, attempt_num: int) -> bool:
        """Check if a specific retry attempt was already completed for this target."""
        fix_resp = bundle_dir / tname / f"airesponse_fix_{attempt_num}.txt"
        fix_prompt = bundle_dir / tname / f"fix_prompt_{attempt_num}.txt"
        return fix_resp.exists() and fix_prompt.exists()

    # Load conversation histories from disk if resuming
    if continuefrom:
        for tname in current_targets:
            conv_path = bundle_dir / tname / "conversation.json"
            if conv_path.exists():
                try:
                    loaded_conv = json.loads(conv_path.read_text(encoding="utf-8"))
                    if loaded_conv:
                        conversations[tname] = loaded_conv
                        _log(f"  Loaded conversation for {tname} ({len(loaded_conv)} messages)")
                except (json.JSONDecodeError, Exception) as exc:
                    _log(f"  ⚠ Failed to load conversation for {tname}: {exc}")

    def _save_retry_progress() -> None:
        """Save the current retry progress to disk."""
        progress_data = {
            "completed_attempts": sorted(completed_attempts),
            "current_targets": current_targets,
            "remaining_failed": [t for t in current_targets],
            "last_updated": datetime.now(timezone.utc).isoformat(),
        }
        try:
            retry_progress_path.write_text(json.dumps(progress_data, indent=2), encoding="utf-8")
        except Exception as exc:
            _log(f"  ⚠ Failed to save retry progress: {exc}")

    for attempt in range(1, max_retries + 1):
        # ── continuefrom: skip fully-completed attempts ─────────────────
        if continuefrom and attempt in completed_attempts:
            _log(f"  Skipping already-completed attempt {attempt}/{max_retries}")
            continue
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
                current_code_str = "\n".join(
                    f"// {fp}\n{content}" for fp, content in sorted(scode.items())
                )
                mc_source = _collect_mc_source_files(
                    mcver, loader,
                    build_error=build_log if build_log != "(build log not available)" else "",
                    current_code=current_code_str,
                )
                # Record build error for DIF history
                if tname in build_history and build_log and build_log != "(build log not available)":
                    build_history[tname]["errors"].append(build_log)
                    # Capture source BEFORE any fix on first failure
                    if not build_history[tname]["source_before"]:
                        build_history[tname]["source_before"] = dict(scode)
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
            # Phase 3: Interactive AI fix prompts (one at a time)
            _log(f"  Interactive fix for {len(context_list_ai)} failed target(s)...")
            ai_responses = [None] * len(target_data)

        def _build_retry_context(
            tname: str,
            build_log: str,
            dif_entries: list[dict],
            mc_source: dict[str, str],
        ) -> str:
            """Build a concise retry context to append to the existing conversation.

            The conversation already has the original prompt and AI's response,
            so we only need to send the build errors and reinforced instructions.
            """
            parts: list[str] = []

            parts.append("## Build Failed — Fix the Errors Below")
            parts.append("")
            parts.append("The build failed with compilation errors. Fix the code and return ALL files")
            parts.append("(both modified and unchanged) using the EXACT format specified below.")
            parts.append("")

            # Build error log
            build_log_truncated = build_log[:5000] if build_log else "(no build log)"
            if build_log and len(build_log) > 5000:
                build_log_truncated += "\n\n(... build log truncated ...)"
            parts.append("### Build Error Log")
            parts.append("```")
            parts.append(build_log_truncated)
            parts.append("```")
            parts.append("")

            # DIF entries
            if dif_entries:
                parts.append("### DIF Entries for This Target")
                for entry in dif_entries[:4]:
                    fix_text = entry.get("fix", "")[:1500]
                    parts.append(f"- [{entry['id']}] {entry['title']}")
                    parts.append(f"  {fix_text}")
                parts.append("")

            # MC source code
            if mc_source:
                parts.append("=== RELEVANT MINECRAFT SOURCE CODE (for fixing the error) ===")
                for filepath in sorted(mc_source.keys())[:8]:
                    content = mc_source[filepath][:5000]
                    parts.append(f"--- {filepath} ---")
                    parts.append(f"```java\n{content}\n```")
                    parts.append("")
                parts.append("=== END RELEVANT MINECRAFT SOURCE CODE ===")
                parts.append("")

            # Reinforced format instructions
            parts.append("## CRITICAL — You MUST Follow This EXACT Output Format")
            parts.append("")
            parts.append("Every file MUST be in this EXACT two-block format. The parser strictly reads blocks in pairs:")
            parts.append("")
            parts.append("### FOR MODIFIED FILES (full code required) — CORRECT EXAMPLE:")
            parts.append("```filepath")
            parts.append(f"bundle/{tname}/src/main/java/net/itamio/skypvp/SkypvpMod.java")
            parts.append("```")
            parts.append("```java")
            parts.append("package net.itamio.skypvp;")
            parts.append("")
            parts.append("public class SkypvpMod {")
            parts.append("    // ...")
            parts.append("}")
            parts.append("```")
            parts.append("")
            parts.append("### FOR UNCHANGED FILES (status only) — CORRECT EXAMPLE:")
            parts.append("```filepath")
            parts.append(f"bundle/{tname}/src/main/resources/pack.mcmeta")
            parts.append("```")
            parts.append("```status")
            parts.append("unchanged")
            parts.append("```")
            parts.append("")
            parts.append("### ❌ COMMON MISTAKES — DO NOT do any of these:")
            parts.append("- DO NOT wrap the filepath in single backticks: `path/to/file.java` ← WRONG")
            parts.append("- DO NOT put the filepath on a bare line without a ```filepath block")
            parts.append("- DO NOT add extra text between the filepath block and the code block")
            parts.append("- DO NOT use `filepath` as the language for the code block — use `java`, `json`, `toml`, etc.")
            parts.append("- DO NOT forget to include ALL files — every file must be listed (modified or unchanged)")
            parts.append("- DO NOT create build files (build.gradle, settings.gradle, etc.)")
            parts.append("")
            parts.append("### FILEPATH RULES")
            parts.append("- Java source files MUST be under `src/main/java/`")
            parts.append("- Resource files go under `src/main/resources/`")
            parts.append("- Every filepath MUST have an extension (.java, .json, .toml, etc.)")
            parts.append("")
            parts.append("Provide COMPLETE file contents — no stubs, no TODOs, no placeholders.")
            parts.append("You MUST include EVERY source file. Unchanged files get `status: unchanged`.")

            return "\n".join(parts)

        print()

        for result in context_list_ai:
            if result is None:
                continue
            idx, tname, mcver, loader, scode, build_log, dif_entries, mc_source = result
            target_dir = bundle_dir / tname
            ai_response: str | None = None
            error: str | None = None

            try:
                # ── Load saved conversation from initial AI coding ─────
                conv_path = target_dir / "conversation.json"
                if conv_path.exists():
                    try:
                        saved_conv = json.loads(conv_path.read_text(encoding="utf-8"))
                        if isinstance(saved_conv, list) and len(saved_conv) >= 2:
                            conversations[tname] = saved_conv
                    except (json.JSONDecodeError, OSError):
                        pass

                if tname in conversations and conversations[tname]:
                    # ── REUSE existing conversation ─────────────────────
                    retry_context = _build_retry_context(
                        tname, build_log, dif_entries, mc_source,
                    )
                    conversations[tname].append({"role": "user", "content": retry_context})
                    msg_count = len(retry_context)
                    prompt_path = target_dir / f"fix_prompt_{attempt}.txt"
                    prompt_path.write_text(retry_context, encoding="utf-8")
                else:
                    # ── FALLBACK: No saved conversation — create fix prompt from scratch
                    projectinfo_path = target_dir / "projectinfo.txt"
                    mod_info = projectinfo_path.read_text(encoding="utf-8") if projectinfo_path.exists() else ""
                    prompt_path = target_dir / "prompt.txt"
                    original_prompt = prompt_path.read_text(encoding="utf-8") if prompt_path.exists() else ""
                    airesponse_path = target_dir / "airesponse.txt"
                    previous_response = airesponse_path.read_text(encoding="utf-8") if airesponse_path.exists() else ""

                    fix_prompt = _recompose_fix_prompt(
                        tname, mod_info, scode, build_log, dif_entries, mc_source,
                        original_prompt_text=original_prompt,
                        previous_ai_response=previous_response,
                    )
                    conversations[tname] = [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": fix_prompt},
                    ]
                    msg_count = len(fix_prompt)
                    prompt_path = target_dir / f"fix_prompt_{attempt}.txt"
                    prompt_path.write_text(fix_prompt, encoding="utf-8")

                # ── Save the conversation to disk ───────────────────────
                try:
                    conv_path.write_text(json.dumps(conversations[tname], indent=2), encoding="utf-8")
                except Exception as conv_exc:
                    _log(f"    Failed to save conversation for {tname}: {conv_exc}")

                # ── Send fix prompt to Grok via browser automation ──────
                conv_text_parts = []
                for m in conversations[tname]:
                    role = m.get("role", "")
                    content = m.get("content", "")
                    if role == "system":
                        conv_text_parts.append(f"[System Instructions]\n{content}")
                    elif role == "user":
                        conv_text_parts.append(f"[User Prompt]\n{content}")
                    elif role == "assistant":
                        conv_text_parts.append(f"[Previous AI Response]\n{content}")
                full_prompt = "\n\n".join(conv_text_parts)

                print(f"  Fix prompt: sending to Grok ({len(full_prompt):,} chars)...", end=" ", flush=True)
                try:
                    ai_response = _send_fix_prompt_to_ai(conversations[tname], tname)
                except CycleError as e:
                    _log(f"  Grok request failed for {tname}: {e}")
                    ai_responses[idx] = (idx, tname, None, str(e))
                    continue

                if not ai_response or not ai_response.strip():
                    error = "Empty response from Grok"
                    _log(f"  No response from Grok for {tname}.")
                    ai_responses[idx] = (idx, tname, None, error)
                    continue

                # ── Save response and update conversation ───────────────
                conversations[tname].append({"role": "assistant", "content": ai_response})
                try:
                    conv_path.write_text(json.dumps(conversations[tname], indent=2), encoding="utf-8")
                except Exception as conv_exc:
                    _log(f"    Failed to save conversation for {tname}: {conv_exc}")

                _log(f"  Response saved for {tname} ({len(ai_response):,} chars)")
                ai_responses[idx] = (idx, tname, ai_response, None)

            except Exception as exc:
                import traceback
                exc_type = type(exc).__name__
                tb = traceback.format_exc()
                ai_responses[idx] = (idx, tname, None, f"[{exc_type}] {exc}\n{tb}")

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

            _log(f"  ✓ {tname}: {len(fix_files)} fixed file(s)")

            source_code = _get_current_source_code(bundle_dir, tname)
            merged_source = _merge_source_files(source_code, fix_files)
            merged_dir = bundle_dir / tname / ".merged_source"
            _save_source_files(merged_source, merged_dir)
            _save_source_files(merged_source, bundle_dir / tname)

            merged_text = ""
            for fp in sorted(merged_source.keys()):
                c = merged_source[fp]
                lang = "java" if fp.endswith(".java") else ""
                merged_text += f"```filepath\nbundle/{tname}/{fp}\n```\n```{lang}\n{c}\n```\n\n"
            (bundle_dir / tname / "airesponse.txt").write_text(merged_text, encoding="utf-8")

            # Track the fix for DIF generation
            if tname in build_history:
                build_history[tname]["source_after"] = dict(merged_source)
                build_history[tname]["fixed_on_attempt"] = attempt

            fixed_this_round.append(tname)

        if not fixed_this_round:
            _log("No versions could be fixed this round. Aborting retry cycle.")
            # Save progress before aborting
            completed_attempts.append(attempt)
            _save_retry_progress()
            break

        # Rebuild the zip with fixed versions
        print()
        _log(f"Rebuilding bundle zip with {len(fixed_this_round)} fixed version(s)...")
        # Only include the failed targets + infra retries to avoid re-building
        # versions that already succeeded.
        retry_targets = set(fixed_this_round) | set(infra_failures)
        file_count, _ = _create_build_bundle(bundle_dir, zip_path, targets_filter=retry_targets)
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

        new_success_raw = set(results["success_versions"])
        new_failed_raw = set(results["failed_versions"])
        published_set.update(results["published_versions"])

        # Map raw slugs back to target folder names (same as Phase B)
        new_success: set[str] = set()
        for slug in new_success_raw:
            mapped = _map_slug_to_target_name(slug, target_names)
            new_success.add(mapped if mapped else slug)
        new_failed: set[str] = set()
        for slug in new_failed_raw:
            mapped = _map_slug_to_target_name(slug, target_names)
            new_failed.add(mapped if mapped else slug)

        _log(f"Updated results: {len(new_success)} success, {len(new_failed)} failed")

        # After a successful retry, generate DIF entries for newly-fixed targets
        newly_fixed = [t for t in new_success if t in build_history and not build_history[t]["fixed"]]
        if newly_fixed:
            _log(f"  Generating DIF entries for {len(newly_fixed)} newly-fixed target(s)...")
            dif_dir = Path("dif")
            dif_dir.mkdir(parents=True, exist_ok=True)
            for tname in newly_fixed:
                hist = build_history[tname]
                hist["fixed"] = True
                if not hist["source_after"] or not hist["errors"]:
                    continue
                combined_log = "\n---\n".join(hist["errors"][-3:])
                _generate_dif_entry(
                    mc_version=hist["mc_version"],
                    loader=hist["loader"],
                    target_name=tname,
                    slug=slug,
                    build_log=combined_log,
                    source_before=hist["source_before"],
                    source_after=hist["source_after"],
                    fixed_on_attempt=hist["fixed_on_attempt"],
                    dif_dir=dif_dir,
                )

        # Update the current targets for next iteration
        current_targets = [t for t in current_targets if t in new_failed]

        # ── Save retry progress to disk ────────────────────────────────
        completed_attempts.append(attempt)
        _save_retry_progress()
        _log(f"  Saved retry progress (attempt {attempt} complete, {len(current_targets)} remaining)")

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
    parser.add_argument("--continuefrom", action="store_true",
                        help="Resume a previous run. Skips phases already completed.")
    args = parser.parse_args()

    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.exists():
        print(f"ERROR: bundle dir not found: {bundle_dir}", file=sys.stderr)
        return 1

    repo = args.repo or _detect_repo()
    token = _detect_token()

    try:

        return run_compile_cycle(
            bundle_dir=bundle_dir,
            slug=args.slug,
            modrinth_url=args.modrinth_url,
            token=token,
            repo=repo,
            max_retries=args.max_retries,
            continuefrom=args.continuefrom,
        )
    except CycleError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
