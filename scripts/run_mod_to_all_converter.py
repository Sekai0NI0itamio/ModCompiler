#!/usr/bin/env python3
"""
run_mod_to_all_converter.py
----------------------------
Triggers the "Automated Mod to ALL Version Converter" workflow on GitHub
Actions, waits for completion, and downloads all artifacts and logs.

After the workflow completes, the script **always** enters the second stage
(mode="prompt-and-code" by default) that sends each generated
prompt to an AI (via C05LocalAI / Groq) and saves the response as
airesponse.txt in each version folder.

Pass --mode prompt-creation to skip the AI coding stage.

Usage
-----
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-and-code
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-creation
  python3 scripts/run_mod_to_all_converter.py --continuefrom lifesteal-parrot-mod-20260513-104548

Modes
-----
  prompt-and-code (default) — Run workflow, download prompts, THEN execute
                              prompts locally via AI and save airesponse.txt.
  prompt-creation            — Run the workflow and download the prompt bundle only.

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
        <mc_version>-<loader>/
          projectinfo.txt
          Background Info.txt
          prompt.txt
          airesponse.txt    ← only in prompt-and-code mode
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
POLL_INTERVAL = 15
LOG_POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


class RunError(Exception):
    pass


class RetryableHTTPError(RuntimeError):
    """An HTTP error that can be retried (429, 503)."""
    pass


class ResponseTooLarge(RuntimeError):
    """Response exceeded the maximum allowed size."""
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


# ─────────────────────────────────────────────────────────────────────────────
# Colors / ANSI helpers for the AI coding stage status display
# ─────────────────────────────────────────────────────────────────────────────

class Colors:
    GREY = "\033[90m"
    BLUE = "[94m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    GREEN = "\033[92m"
    BLACK_BOLD = "\033[1;30m"
    RESET = "\033[0m"
    BOLD = "\033[1m"


_STATUS_LABELS = {
    "queued": ("Queued", Colors.GREY),
    "request_sent": ("Request Sent", Colors.BLUE),
    "waiting": ("Waiting for response", Colors.RED),
    "streaming": ("Streaming", Colors.YELLOW),
    "complete": ("Complete", Colors.GREEN),
    "retrying": ("Retrying", Colors.BLACK_BOLD),
    "failed": ("Failed", Colors.BLACK_BOLD),
}


# ─────────────────────────────────────────────────────────────────────────────
# AI Coding Stage — sends prompts to C05LocalAI / Groq
# ─────────────────────────────────────────────────────────────────────────────

# --- AI Provider Config ---
AI_C05_BASE = "http://localhost:8129"
AI_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

# Maximum retries when the AI response contains no Java source files
AI_JAVA_RETRIES = 3


def _response_has_java_files(response_text: str) -> bool:
    """Quick check whether an AI response contains any .java source paths."""
    # Look for .java in code-block context
    import re
    # Pattern A: inline path before a code block  (e.g. `src/main/java/Foo.java`)
    if re.search(r'\.java[`\s]', response_text):
        return True
    # Pattern B: filepath-only code block  (e.g. ```path/to/Foo.java```)
    if re.search(r'```[^\n]*\.java\s*\n\s*```', response_text):
        return True
    # Pattern C: .java inside a ```codeblock```
    for m in re.finditer(r'```[a-zA-Z0-9_+\-]*\n.*?\n```', response_text, re.DOTALL):
        if '.java' in m.group():
            return True
    return False


def _read_author_info(target_dir: Path) -> tuple[str, str]:
    """Read author name and mod path from projectinfo.txt.
    Returns (author, mod_path)."""
    projectinfo = target_dir / "projectinfo.txt"
    author = ""
    mod_path = ""
    if projectinfo.exists():
        for line in projectinfo.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if s.startswith("Mod Author:"):
                author = s.split(":", 1)[1].strip()
            elif s.startswith("Mod Path:"):
                mod_path = s.split(":", 1)[1].strip()
    return author, mod_path


def _read_mod_description(target_dir: Path) -> tuple[str, str, str, str]:
    """Read mod name, summary, description, and H1 title from projectinfo.txt.

    Returns (mod_name, summary, description, h1_title).
    h1_title is extracted from the first '# Title' line in the description.
    """
    projectinfo_path = target_dir / "projectinfo.txt"
    if not projectinfo_path.exists():
        return "", "", "", ""

    mod_name = ""
    summary = ""
    description = ""
    h1_title = ""

    content = projectinfo_path.read_text(encoding="utf-8")

    for line in content.split("\n"):
        s = line.strip()
        if s.startswith("Mod Name:"):
            mod_name = s.split(":", 1)[1].strip()

    summary_match = re.search(r"Mod Summary:\s*\n(.*?)(?:\n\n|\n(?:Mod Description|Source|$))", content, re.DOTALL)
    if summary_match:
        summary = summary_match.group(1).strip()

    desc_match = re.search(r"Mod Description[^:]*:\s*\n(.*?)(?:\n\n|\n(?:Source|$))", content, re.DOTALL)
    if desc_match:
        description = desc_match.group(1).strip()

    # Extract H1 title from description: first line starting with "# " (single #)
    if description:
        for line in description.split("\n"):
            s = line.strip()
            if re.match(r"^# [^#]", s):
                h1_title = s[2:].strip()
                break

    return mod_name, summary, description, h1_title


def _generate_metadata_with_ai(
    raw_name: str,
    status_callback: callable | None = None,
) -> dict[str, str]:
    """Send a raw mod name to Groq AI to format into proper metadata fields.

    The AI formats the name WITHOUT changing, renaming, or inventing anything.
    It only converts to the required field formats (mod_id, mod_class, etc.).

    Returns dict with keys: mod_id, mod_class, mod_client_class,
    mod_display_name, package_path, author_name.
    """
    import urllib.request
    import urllib.error

    url = f"{AI_C05_BASE}/chat"

    prompt_dir = Path(__file__).resolve().parent.parent / "prompts"
    prompt_path = prompt_dir / "metadata_generation.txt"
    if prompt_path.exists():
        system_prompt = prompt_path.read_text(encoding="utf-8")
    else:
        system_prompt = (
            "You are a Minecraft mod metadata formatter. "
            "Format the given mod name into metadata fields WITHOUT changing it. "
            "author_name is always 'Itamio'. package_path is always 'asd.itamio.{mod_id}'. "
            "Return ONLY a JSON object."
        )

    user_prompt = f"Mod name: {raw_name}\n\nFormat this name into the metadata JSON. Do NOT change the name."

    payload: dict[str, Any] = {
        "hoster": "groq",
        "model": AI_MODEL,
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
        "extra_body": {
            "temperature": 0.1,
            "max_tokens": 1024,
        },
    }

    body_bytes = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method="POST")

    full_response = ""
    buffer = ""

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
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
                    try:
                        data = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if "error" in data:
                        raise RuntimeError(str(data["error"]))
                    if data.get("event") == "content":
                        full_response += data.get("content", "")
    except urllib.error.HTTPError as e:
        error_body = ""
        try:
            error_body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(
            f"Metadata AI request failed: HTTP {e.code} - {error_body}"
        ) from e
    except Exception:
        raise

    full_response = full_response.strip()

    json_match = re.search(r"\{[\s\S]*\}", full_response)
    if json_match:
        full_response = json_match.group(0)

    try:
        metadata = json.loads(full_response)
    except json.JSONDecodeError:
        print(f"  WARNING: Could not parse metadata JSON from AI response: {full_response[:200]}")
        return {}

    required_keys = {"mod_id", "mod_class", "mod_client_class", "mod_display_name", "package_path", "author_name"}
    if not required_keys.issubset(metadata.keys()):
        missing = required_keys - metadata.keys()
        print(f"  WARNING: Metadata response missing keys: {missing}")
        return {}

    return metadata


def _substitute_prompt_variables(prompt_text: str, metadata: dict[str, str]) -> str:
    """Replace ${...} template variables in prompt with AI-generated metadata."""
    var_map = {
        "${MOD_ID}": metadata.get("mod_id", ""),
        "${MOD_CLASS}": metadata.get("mod_class", ""),
        "${MOD_CLIENT_CLASS}": metadata.get("mod_client_class", ""),
        "${MOD_DISPLAY_NAME}": metadata.get("mod_display_name", ""),
        "${PACKAGE_PATH}": metadata.get("package_path", ""),
        "${AUTHOR_NAME}": metadata.get("author_name", ""),
    }
    for var, value in var_map.items():
        if value:
            prompt_text = prompt_text.replace(var, value)
    return prompt_text


def _run_ai_coding_stage(bundle_dir: Path, previous_progress: dict[str, str] | None = None) -> int:
    """Execute the AI coding stage: send prompts to AI and save responses.

    Args:
        bundle_dir: Path to the analysis bundle directory.
        previous_progress: Dict mapping target_name -> status ("complete" or "failed")
                           from a previous partial run. Targets already marked complete
                           will be skipped.
    """
    import threading

    print()
    print("=" * 72)
    print("  PHASE 2 — AI MOD CODING (Local)")
    print(f"  Model: {AI_MODEL} (reasoning=high)")
    print(f"  Provider: C05LocalAI / Groq")
    print(f"  Auto-retry on missing Java code: up to {AI_JAVA_RETRIES} retries per target")
    print("=" * 72)
    print()

    # Find all version folders with prompt.txt
    target_dirs = sorted([
        d for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])

    if not target_dirs:
        print("ERROR: No target directories found in bundle.", file=sys.stderr)
        return 1

    # Filter to those with prompt.txt (skip if already completed from previous run)
    previous_progress = previous_progress or {}
    prompt_targets = []
    skipped_targets = []
    for td in target_dirs:
        prompt_path = td / "prompt.txt"
        if not prompt_path.exists():
            continue
        # Skip targets that already have airesponse.txt AND are marked complete/failed in progress
        prev_status = previous_progress.get(td.name, "")
        if prev_status in ("complete", "failed") and (td / "airesponse.txt").exists():
            skipped_targets.append(td.name)
            _log(f"  Skipping already-processed target: {td.name} (status: {prev_status})")
            continue
        prompt_targets.append(td)

    if skipped_targets:
        print(f"\n  Skipping {len(skipped_targets)} already-processed target(s): {', '.join(skipped_targets)}")
        print()

    if not prompt_targets:
        print("All targets already processed. Skipping AI coding stage.")
        return 0

    # Also drop any target that has airesponse.txt even without progress file
    already_done = [td for td in prompt_targets if (td / "airesponse.txt").exists()]
    if already_done:
        for td in already_done:
            _log(f"  Target already has airesponse.txt: {td.name} — skipping")
        prompt_targets = [td for td in prompt_targets if not (td / "airesponse.txt").exists()]

    if not prompt_targets:
        print("All targets already processed. Skipping AI coding stage.")
        return 0

    target_names = sorted(td.name for td in prompt_targets)
    print(f"Found {len(target_names)} target(s) with prompts to execute.")
    print()

    # ── Build and print the status table ───────────────────────────────────
    # We print it once, then update individual lines in-place.
    print_lock = threading.Lock()

    # Table layout:
    #   ──── separator  (line 1)
    #   Target ... Status  (line 2)
    #   ──── separator  (line 3)
    #   target_1           (line 4)
    #   target_2           (line 5)
    #   ...
    #   target_n           (line 4 + n - 1)
    #   (blank line)       (line 4 + n)
    #
    # All later status updates overwrite lines 4 through 4+n-1.

    HEADER_LINES = 3  # separator + header + separator
    TABLE_ROWS = len(target_names)

    print(f"{'─' * 72}")
    print(f"  {'Target':<30}  Status")
    print(f"{'─' * 72}")
    for name in target_names:
        label, color = _STATUS_LABELS["queued"]
        print(f"  {name:<30}  {color}{label}{Colors.RESET}")
    print()  # blank line after table

    # Cursor is now at line (HEADER_LINES + TABLE_ROWS) 0-based from table top.
    # After "Processing..." and its blank, cursor will be 2 more lines down.
    TABLE_BOTTOM = HEADER_LINES + TABLE_ROWS  # 0-based row of the blank line after table

    # Process with ThreadPoolExecutor for parallel AI requests
    max_workers = min(10, len(target_names))
    print(f"  Processing with up to {max_workers} parallel workers...")
    print()
    # Cursor now at TABLE_BOTTOM + 2 (0-based) = HEADER_LINES + TABLE_ROWS + 2
    CURSOR_HOME = TABLE_BOTTOM + 2  # reference line where our cursor stays

    # ── Helper: update a single line in the table in-place ─────────────────
    def _update_line(name: str, status: str, detail: str = "") -> None:
        """Overwrite the status line for `name` in-place using ANSI codes."""
        idx = target_names.index(name)
        # Target row is HEADER_LINES + idx (0-based).
        # From CURSOR_HOME, go up: CURSOR_HOME - (HEADER_LINES + idx)
        # = (HEADER_LINES + TABLE_ROWS + 2) - (HEADER_LINES + idx)
        # = TABLE_ROWS - idx + 2
        lines_up = TABLE_ROWS - idx + 3
        label, color = _STATUS_LABELS.get(status, (status, Colors.RESET))
        text = f"{color}{label}{Colors.RESET}"
        if detail:
            text += f"  ({detail})"
        with print_lock:
            # Go up to the target line, clear it, write new content
            print(f"\033[{lines_up}A\r\033[K  {name:<30}  {text}", flush=True)
            # Return cursor to home position
            print(f"\033[{lines_up}B", end="", flush=True)

    # ── Execute ────────────────────────────────────────────────────────────
    success_count = 0
    fail_count = 0

    # Pre-read author info for every target so we can pass it with each request
    target_author_info: dict[str, tuple[str, str]] = {}
    for td in prompt_targets:
        target_author_info[td.name] = _read_author_info(td)

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        fut_to_target = {}
        for td in prompt_targets:
            prompt_path = td / "prompt.txt"
            author_info = target_author_info.get(td.name, ("", ""))
            fut = pool.submit(
                _send_prompt_to_c05,
                td.name,
                prompt_path.read_text(encoding="utf-8"),
                _update_line,
                author_info,
                "",
            )
            fut_to_target[fut] = td
            _update_line(td.name, "request_sent")
            time.sleep(2)  # Stagger requests 2s apart to avoid API rate limits

        def _do_send(
            td: Path,
            prompt_text: str,
            retry_context: str,
            author_info: tuple[str, str],
        ) -> str:
            """Helper: send prompt and return response, handling HTTP errors."""
            return _send_prompt_to_c05(
                td.name, prompt_text, _update_line,
                author_info=author_info, retry_context=retry_context,
            )

        for fut in as_completed(fut_to_target):
            td = fut_to_target[fut]
            author_info = target_author_info.get(td.name, ("", ""))
            prompt_text = (td / "prompt.txt").read_text(encoding="utf-8")

            try:
                response_text = fut.result()
            except RetryableHTTPError:
                # Retry on 429/503 with backoff
                _update_line(td.name, "retrying", "server busy, retrying in 30s...")
                _log(f"  Retrying {td.name} after 30s (503 ResourceExhausted)...")
                time.sleep(30)
                try:
                    response_text = _do_send(td, prompt_text, "", author_info)
                except Exception as exc2:
                    error_msg = str(exc2)
                    _update_line(td.name, "failed", error_msg[:80])
                    fail_count += 1
                    fail_path = td / "airesponse.txt"
                    fail_path.write_text(f"AI CODING FAILED\n\nError: {error_msg}\n", encoding="utf-8")
                    continue
            except Exception as exc:
                error_msg = str(exc)
                _update_line(td.name, "failed", error_msg[:80])
                fail_count += 1
                fail_path = td / "airesponse.txt"
                fail_path.write_text(f"AI CODING FAILED\n\nError: {error_msg}\n", encoding="utf-8")
                continue

            # ── Save initial response ──────────────────────────────────
            resp_path = td / "airesponse.txt"
            resp_path.write_text(response_text, encoding="utf-8")

            # ── Retry loop: re-prompt AI if response lacks Java code ───
            final_response = response_text
            _, mod_path = author_info
            for ai_attempt in range(AI_JAVA_RETRIES + 1):  # 0=initial, 1..N=retries
                if _response_has_java_files(final_response):
                    break  # Good — response contains Java code

                if ai_attempt >= AI_JAVA_RETRIES:
                    _log(f"  {td.name}: No Java code after {AI_JAVA_RETRIES} AI retries")
                    break  # Exhausted all retries

                attempt_num = ai_attempt + 1
                _update_line(
                    td.name, "retrying",
                    f"no Java files, retry {attempt_num}/{AI_JAVA_RETRIES}",
                )
                _log(f"  {td.name}: No Java files in response, retry {attempt_num}/{AI_JAVA_RETRIES}")

                # Build a retry context that tells the AI what went wrong
                if attempt_num == 1:
                    retry_context = (
                        "YOUR PREVIOUS RESPONSE HAD NO JAVA SOURCE FILES.\n"
                        "This means the build will produce empty jars with no .class files.\n"
                        "You MUST generate COMPLETE Java source files for this mod, "
                        "each one starting with the correct `package` declaration.\n"
                        "Output each file under `src/main/java/` with its full package path.\n"
                        "Do NOT output the same JSON/config files again — they are already present."
                    )
                else:
                    pkg = mod_path if mod_path else "com.example"
                    retry_context = (
                        f"RETRY ATTEMPT {attempt_num}: Your previous responses STILL lack Java files.\n"
                        "You MUST write the Java source code for this mod.\n"
                        f"Package path: {pkg}\n"
                        "Output each file under `src/main/java/` with the correct package path."
                    )

                try:
                    final_response = _do_send(
                        td, prompt_text, retry_context, author_info,
                    )
                except Exception as exc2:
                    error_msg = str(exc2)
                    _update_line(td.name, "failed", error_msg[:80])
                    fail_count += 1
                    fail_path = td / "airesponse.txt"
                    fail_path.write_text(f"AI CODING FAILED\n\nError: {error_msg}\n", encoding="utf-8")
                    continue

            # ── Save final response ────────────────────────────────────
            try:
                resp_path = td / "airesponse.txt"
                resp_path.write_text(final_response, encoding="utf-8")

                # ── Save conversation for fix cycle reuse ──────────────
                author, mod_path = author_info
                system_parts = [
                    "You are an excellent and professional Minecraft mod developer.",
                    "You are expert at reading and following technical instructions precisely.",
                    "You write clean, correct, and well-structured Java code.",
                    "Provide ALL files requested with complete implementations — no stubs, no TODOs, no placeholders.",
                ]
                if author:
                    system_parts.append(f"The mod author is: {author}")
                    system_parts.append("You should keep the package path and style consistent with this author's work.")
                if mod_path:
                    system_parts.append(f"The preferred package path for the mod is: {mod_path}")
                    system_parts.append("Use this Java package path for all source files.")
                system_prompt = "\n".join(system_parts)
                conversation = [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": prompt_text},
                    {"role": "assistant", "content": final_response},
                ]
                conv_path = td / "conversation.json"
                conv_path.write_text(json.dumps(conversation, indent=2), encoding="utf-8")

                if _response_has_java_files(final_response):
                    _update_line(td.name, "complete", f"{len(final_response):,} chars")
                    success_count += 1
                else:
                    _update_line(td.name, "failed", "no Java code after retries")
                    _log(f"  {td.name}: FAILED — no Java code after {AI_JAVA_RETRIES} retries")
                    fail_count += 1
            except Exception as exc:
                _log(f"  {td.name}: Error saving response: {exc}")
                fail_count += 1

    # ── Save progress to ai_stage_progress.json ────────────────────────
    # Determine the run output root: bundle_dir = out_root/artifacts/ANALYSIS_ARTIFACT
    ai_progress: dict[str, str] = {}
    for td in prompt_targets:
        resp_path = td / "airesponse.txt"
        if resp_path.exists():
            content = resp_path.read_text(encoding="utf-8")
            if _response_has_java_files(content):
                ai_progress[td.name] = "complete"
            else:
                ai_progress[td.name] = "failed"
        else:
            ai_progress[td.name] = "failed"
    # Also include skipped targets from previous progress
    for tname in skipped_targets:
        if tname not in ai_progress:
            ai_progress[tname] = previous_progress.get(tname, "skipped")
    # Add targets that were already done (found airesponse.txt without progress file)
    for td in already_done:
        if td.name not in ai_progress:
            resp_path = td / "airesponse.txt"
            if resp_path.exists():
                content = resp_path.read_text(encoding="utf-8")
                ai_progress[td.name] = "complete" if _response_has_java_files(content) else "failed"
            else:
                ai_progress[td.name] = "failed"

    out_root = bundle_dir.parent.parent
    ai_progress_path = out_root / "ai_stage_progress.json"
    try:
        ai_progress_path.write_text(json.dumps(ai_progress, indent=2), encoding="utf-8")
        _log(f"  Saved AI stage progress to {ai_progress_path}")
    except Exception as exc:
        _log(f"  ⚠ Failed to save AI stage progress: {exc}")

    print()
    print(f"{'─' * 72}")
    print(f"  AI Coding Stage Complete: {success_count} success, {fail_count} failed")
    print(f"{'─' * 72}")

    return 0 if fail_count == 0 else 1


def _send_prompt_to_c05(
    target_name: str,
    prompt_text: str,
    status_callback: callable,
    author_info: tuple[str, str] = ("", ""),
    retry_context: str = "",
) -> str:
    """Send a single prompt to C05LocalAI / Groq and return the full response.

    Args:
        target_name: The name of the target (e.g. "1.20.5-fabric").
        prompt_text: The prompt to send.
        status_callback: Callback for status updates.
        author_info: (author_name, mod_path) tuple from projectinfo.txt.
        retry_context: If non-empty, this is a retry — the text explains what
                       was wrong with the previous response.
    """
    import urllib.request
    import urllib.error

    url = f"{AI_C05_BASE}/chat"

    author, mod_path = author_info

    system_parts = [
        "You are an excellent and professional Minecraft mod developer.",
        "You are expert at reading and following technical instructions precisely.",
        "You write clean, correct, and well-structured Java code.",
        "Provide ALL files requested with complete implementations — no stubs, no TODOs, no placeholders.",
    ]
    if author:
        system_parts.append(f"")
        system_parts.append(f"The mod author is: {author}")
        system_parts.append(f"You should keep the package path and style consistent with this author's work.")
    if mod_path:
        system_parts.append(f"")
        system_parts.append(f"The preferred package path for the mod is: {mod_path}")
        system_parts.append(f"Use this Java package path for all source files.")

    user_content = prompt_text
    if retry_context:
        user_content = f"{retry_context}\n\n---\n\n{prompt_text}"

    payload: dict[str, Any] = {
        "hoster": "groq",
        "model": AI_MODEL,
        "system_prompt": "\n".join(system_parts),
        "user_prompt": user_content,
        "extra_body": {
            "temperature": 0.2,
        },
        "reasoning_effort": "high",
    }

    body_bytes = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    status_callback(target_name, "waiting")

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

                # Process NDJSON lines (each line is a complete JSON object)
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        data = json.loads(line)
                    except json.JSONDecodeError:
                        continue

                    # Error event
                    if "error" in data:
                        raise RuntimeError(str(data["error"]))

                    # Content event
                    if data.get("event") == "content":
                        content = data.get("content", "") or ""
                        if content:
                            full_response += content
                            accumulated += len(content)
                            if accumulated > 100000:
                                raise ResponseTooLarge(f"Response exceeded 100KB ({accumulated:,} bytes)")
                            if accumulated % 500 < 100:
                                status_callback(
                                    target_name, "streaming",
                                    f"{accumulated:,} bytes received"
                                )

                    # End event — contains the full accumulated content
                    if data.get("status") == "end":
                        end_content = data.get("content", "") or ""
                        if end_content and not full_response:
                            full_response = end_content
                            accumulated = len(end_content)
                        break
    except urllib.error.HTTPError as e:
        error_code = e.code
        error_detail = e.read().decode("utf-8", errors="replace")[:500]
        if error_code in (429, 503):
            status_callback(target_name, "retrying", f"HTTP {error_code}: {error_detail}")
            import time as _time
            _time.sleep(30)
            raise RetryableHTTPError(f"HTTP {error_code}: {error_detail}")
        raise RuntimeError(f"HTTP {error_code}: {error_detail}")
    except Exception as e:
        raise RuntimeError(str(e))

    if not full_response:
        raise RuntimeError("Empty response from AI — no content generated.")

    # Final streaming update
    status_callback(target_name, "streaming", f"{accumulated:,} bytes received")
    return full_response


# ─────────────────────────────────────────────────────────────────────────────
# Main Runner
# ─────────────────────────────────────────────────────────────────────────────

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Trigger Automated Mod to ALL Version Converter workflow, wait, and download results."
    )
    parser.add_argument("modrinth_url", nargs="?", default="",
        help="Modrinth project URL or slug (e.g. https://modrinth.com/mod/sort-chest)")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR,
        help=f"Root folder for run outputs (default: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--continuefrom",
        help="Folder name (from ModToAllRuns/) to resume from. Skips workflow dispatch and re-downloads if artifacts exist.")
    parser.add_argument("--repo", default="",
        help="owner/repo override (default: auto-detect from git remote)")
    parser.add_argument("--mode", default="prompt-and-code",
        choices=["prompt-creation", "prompt-and-code"],
        help=(
            "Operation mode:\n"
            "  prompt-and-code (default) — Run workflow, download prompts, THEN\n"
            "                              execute prompts locally via AI and\n"
            "                              save airesponse.txt files.\n"
            "  prompt-creation           — Run workflow + download prompts only."
        ))
    args = parser.parse_args(argv)

    if args.continuefrom and args.modrinth_url:
        parser.error("Cannot specify both a modrinth_url and --continuefrom. Use one or the other.")
        return 1

    if not args.continuefrom and not args.modrinth_url:
        parser.error("Either a modrinth_url or --continuefrom must be specified.")
        return 1

    try:
        return Runner(args).run()
    except RunError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


class Runner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.repo = (args.repo or _detect_repo()).strip()
        self.token = _detect_token()
        self.mode = args.mode

        if args.continuefrom:
            self.out_root = Path(args.output_dir).resolve() / args.continuefrom
            self.resuming = True
            self._load_existing_run(args.continuefrom)
        else:
            self.modrinth_url = args.modrinth_url.strip()
            self.resuming = False
            m = re.search(r"modrinth\.com/(?:mod|plugin|resourcepack|shader|datapack|modpack)/([^/?#]+)", self.modrinth_url)
            self.slug = m.group(1) if m else self.modrinth_url.replace("https://", "").replace("/", "-")
            ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
            self.out_root = Path(args.output_dir).resolve() / f"{self.slug}-{ts}"
            self.run_id = 0

    def _load_existing_run(self, folder_name: str) -> None:
        result_json = self.out_root / "result.json"
        if result_json.exists():
            data = json.loads(result_json.read_text(encoding="utf-8"))
            self.modrinth_url = data.get("modrinth_url", "")
            self.slug = data.get("slug", folder_name.rsplit("-", 2)[0] if "-" in folder_name else folder_name)
            self.run_id = data.get("run_id", 0)
            self.mode = data.get("mode", self.mode)
        else:
            self.modrinth_url = ""
            parts = folder_name.rsplit("-", 2)
            self.slug = parts[0] if parts else folder_name
            self.run_id = 0

    def _detect_progress(self) -> dict[str, bool]:
        artifacts_dir = self.out_root / "artifacts"
        bundle_dir = artifacts_dir / ANALYSIS_ARTIFACT if artifacts_dir.exists() else None
        build_results_dir = artifacts_dir / ".build_results" if artifacts_dir.exists() else None
        incoming_dir = self.out_root / "incoming"

        # Load AI stage progress file if it exists
        ai_progress_path = self.out_root / "ai_stage_progress.json"
        ai_stage_progress: dict[str, str] = {}
        if ai_progress_path.exists():
            try:
                ai_stage_progress = json.loads(ai_progress_path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, Exception):
                pass

        progress = {
            "workflow_completed": (self.out_root / "result.json").exists(),
            "artifacts_downloaded": bundle_dir is not None and bundle_dir.exists(),
            "has_prompt_txt": False,
            "has_airesponse_txt": False,
            "airesponse_count": 0,
            "prompt_target_count": 0,
            "has_build_zip": False,
            "has_build_results": False,
            "compile_started": False,
            "compile_complete": False,
            "ai_stage_progress": ai_stage_progress,
        }
        if bundle_dir and bundle_dir.exists():
            prompt_targets = []
            airesponse_targets = []
            for target_dir in bundle_dir.iterdir():
                if not target_dir.is_dir() or target_dir.name.startswith("."):
                    continue
                if (target_dir / "prompt.txt").exists():
                    progress["has_prompt_txt"] = True
                    prompt_targets.append(target_dir.name)
                if (target_dir / "airesponse.txt").exists():
                    airesponse_targets.append(target_dir.name)
            progress["airesponse_count"] = len(airesponse_targets)
            progress["prompt_target_count"] = len(prompt_targets)
            # has_airesponse_txt is True only when ALL targets with prompts have responses
            if prompt_targets and len(airesponse_targets) >= len(prompt_targets):
                progress["has_airesponse_txt"] = True
            elif airesponse_targets:
                progress["has_airesponse_txt"] = False  # partial

        if incoming_dir.exists():
            for f in incoming_dir.iterdir():
                if f.is_file() and f.suffix == ".zip" and "all-versions" in f.name:
                    progress["has_build_zip"] = True
                    break
        if build_results_dir and build_results_dir.exists():
            results_json = build_results_dir / "results.json"
            if results_json.exists():
                progress["has_build_results"] = True
        return progress

    def run(self) -> int:
        self.out_root.mkdir(parents=True, exist_ok=True)
        _ensure_gh()

        print(f"Mode:        {self.mode}")
        print(f"Repo:        {self.repo}")
        print(f"Output dir:  {self.out_root}")
        print()

        progress = self._detect_progress()

        if self.resuming:
            print("=" * 72)
            print("  RESUME MODE — continuing from previous run")
            print("=" * 72)
            print()
            if self.modrinth_url:
                print(f"Modrinth:    {self.modrinth_url}")
            if self.run_id:
                print(f"Run ID:      #{self.run_id}")
            print()
            print("Progress detected:")
            print(f"  Workflow:        {'✓ completed' if progress['workflow_completed'] else '✗ not done'}")
            print(f"  Artifacts:       {'✓ downloaded' if progress['artifacts_downloaded'] else '✗ not downloaded'}")
            print(f"  Prompt files:    {'✓ present' if progress['has_prompt_txt'] else '✗ missing'}")
            if progress['prompt_target_count'] > 0 and progress['airesponse_count'] < progress['prompt_target_count']:
                print(f"  AI responses:    ⏳ partial ({progress['airesponse_count']}/{progress['prompt_target_count']} targets)")
            else:
                print(f"  AI responses:    {'✓ present' if progress['has_airesponse_txt'] else '✗ missing'}")
            print(f"  Build zip:       {'✓ present' if progress['has_build_zip'] else '✗ missing'}")
            print(f"  Build results:   {'✓ present' if progress['has_build_results'] else '✗ missing'}")
            print()
        else:
            print(f"Modrinth:    {self.modrinth_url}")
            print()

        conclusion = ""

        if not self.resuming or not progress["artifacts_downloaded"]:
            if not self.resuming:
                self.run_id = self._dispatch()
                run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
                print(f"Dispatched run #{self.run_id}")
                print(f"URL: {run_url}")
                print()
                conclusion = self._wait()
            else:
                run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
                print(f"Run URL: {run_url}")

            print("\nDownloading artifacts and logs...")
            artifacts_dir = self.out_root / "artifacts"
            self._download_all(artifacts_dir)
            progress = self._detect_progress()

            conclusion = "success"
            self._write_summary(conclusion, run_url, artifacts_dir)
            print(f"\nWorkflow conclusion: {conclusion.upper()}")
        else:
            print("Skipping workflow dispatch and artifact download (already present)")
            print()
            run_url = f"https://github.com/{self.repo}/actions/runs/{self.run_id}"
            conclusion = "success"

        summary_path = self.out_root / "SUMMARY.md"
        print(f"\nRun folder:  {self.out_root}")
        print(f"Summary:     {summary_path}")
        print()

        if self.mode not in ("prompt-and-code",):
            return 0

        bundle_dir = self.out_root / "artifacts" / ANALYSIS_ARTIFACT
        if not bundle_dir.exists():
            print("ERROR: Analysis bundle directory not found. Cannot run AI coding stage.",
                  file=sys.stderr)
            return 1

        # ── Generate mod metadata with AI before coding ──────────────────
        print()
        print("=" * 72)
        print("  METADATA — Generating mod metadata via AI")
        print("=" * 72)
        print()

        target_dirs = sorted([
            d for d in bundle_dir.iterdir()
            if d.is_dir() and not d.name.startswith(".")
            and (d / "prompt.txt").exists()
        ])

        # Determine mod name for metadata generation
        # Priority: H1 title from description > Modrinth title from projectinfo
        mod_name_for_metadata = ""
        h1_title = ""
        for td in target_dirs:
            _, _, _, h1 = _read_mod_description(td)
            if h1:
                h1_title = h1
                break

        if h1_title:
            mod_name_for_metadata = h1_title
            print(f"  Using H1 title from description: \"{h1_title}\"")
        else:
            for td in target_dirs:
                mn, _, _, _ = _read_mod_description(td)
                if mn:
                    mod_name_for_metadata = mn
                    break
            if mod_name_for_metadata:
                print(f"  Using Modrinth title: \"{mod_name_for_metadata}\"")
            else:
                print(f"  WARNING: No mod name found in description or projectinfo")

        if mod_name_for_metadata:
            from generate_prompts_in_bundle import _get_fallback_mod_values
            metadata = _get_fallback_mod_values({"mod_name": mod_name_for_metadata})
            print(f"  Metadata:")
            for k, v in metadata.items():
                print(f"    {k}: {v}")

            for td in target_dirs:
                prompt_path = td / "prompt.txt"
                if prompt_path.exists():
                    prompt_text = prompt_path.read_text(encoding="utf-8")
                    updated = _substitute_prompt_variables(prompt_text, metadata)
                    prompt_path.write_text(updated, encoding="utf-8")
            print(f"  ✓ Substituted variables in {len(target_dirs)} prompt files")
        else:
            print(f"  WARNING: No mod name available, prompts will have raw variable names")
        print()

        # Run AI coding stage if not ALL targets have responses yet
        if not progress["has_airesponse_txt"]:
            ai_result = _run_ai_coding_stage(bundle_dir, progress.get("ai_stage_progress", {}))

            self._append_ai_summary(self.out_root / "artifacts")

            if ai_result != 0:
                print("\n⚠ AI coding stage had failures. Proceeding to compile cycle anyway...")
        else:
            print(f"Skipping AI coding stage (all {progress['prompt_target_count']} target(s) already have airesponse.txt)")

        _log("Validating AI responses before compile cycle...")
        targets_no_java: list[str] = []
        for td in sorted(bundle_dir.iterdir()):
            if not td.is_dir() or td.name.startswith("."):
                continue
            ai_path = td / "airesponse.txt"
            if not ai_path.exists():
                targets_no_java.append(td.name)
                continue
            text = ai_path.read_text(encoding="utf-8")
            has_java = bool(re.search(r'\.java[`\s]', text)) or bool(
                re.search(r'```[a-zA-Z]*\n', text)
            )
            if not has_java:
                targets_no_java.append(td.name)

        if targets_no_java:
            print()
            print(f"{'─' * 72}")
            print(f"  ⚠ {len(targets_no_java)} target(s) have no Java source code in AI response:")
            for t in targets_no_java:
                print(f"    • {t}")
            print(f"  The build will produce empty jars for these targets.")
            print(f"  The compile cycle's retry logic will attempt to fix them.")
            print(f"{'─' * 72}")
            print()
        else:
            _log("All targets contain Java source code. Proceeding to compile cycle.")

        print()
        print("=" * 72)
        print("  PHASE 3 — COMPILE CYCLE")
        print("  (Extract sources, create build zip, dispatch build, retry failures)")
        print("=" * 72)
        print()

        compile_result = subprocess.run(
            [sys.executable, "scripts/ai_compile_cycle.py",
             "--bundle-dir", str(bundle_dir),
             "--slug", self.slug,
             "--modrinth-url", self.modrinth_url,
             "--repo", self.repo,
             "--max-retries", "5"]
            + (["--continuefrom"] if self.resuming else [])
            + (["--intelligent"] if False else []),
            capture_output=False,
        )

        if compile_result.returncode != 0:
            print("\n⚠ Compile cycle completed with some failures.")
            print("  Check the bundle for failed-* folders with error context.")
            return 1

        print("\n✓ Compile cycle complete — all versions built and published!")

        return 0

    def _dispatch(self) -> int:
        before = {r["databaseId"] for r in self._list_runs()}
        fields = ["-f", f"modrinth_project_url={self.modrinth_url}"]
        # Add the mode as a workflow input (the workflow can ignore it or use it)
        # For now we always dispatch the full prompt-creation workflow
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
        last_status = ""
        last_jobs_print = 0.0
        print("Waiting for workflow to complete...")
        print(f"  (polling every {POLL_INTERVAL}s)\n")

        while True:
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
            f"- Mode: {self.mode}",
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
                    # Check for airesponse.txt
                    ai = bundle_dir / folder / "airesponse.txt"
                    if ai.exists():
                        ai_size = ai.stat().st_size
                        lines[-1] += f"  [✓ airesponse.txt ({ai_size:,}B)]"

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

            # AI coding stage summary
            ai_files = list(bundle_dir.rglob("airesponse.txt"))
            if ai_files:
                lines += ["", "## AI Coding Stage Results"]
                lines.append(f"\n{len(ai_files)} target(s) processed via AI.")
                for af in sorted(ai_files):
                    rel = af.relative_to(bundle_dir)
                    size = af.stat().st_size
                    lines.append(f"- `{rel}` ({size:,}B)")
        else:
            lines.append("Artifact bundle was not created (workflow may have failed early).")

        (self.out_root / "SUMMARY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

        # Write result.json
        result = {
            "run_id": self.run_id,
            "repo": self.repo,
            "modrinth_url": self.modrinth_url,
            "slug": self.slug,
            "mode": self.mode,
            "conclusion": conclusion,
            "output_dir": str(self.out_root),
        }
        (self.out_root / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")

    def _append_ai_summary(self, artifacts_dir: Path) -> None:
        """Append AI coding stage results to SUMMARY.md."""
        bundle_dir = artifacts_dir / ANALYSIS_ARTIFACT
        summary_path = self.out_root / "SUMMARY.md"
        if not summary_path.exists():
            return

        ai_files = list(bundle_dir.rglob("airesponse.txt"))
        if not ai_files:
            return

        ai_lines = ["", "## AI Coding Stage Results", ""]
        ai_lines.append(f"{len(ai_files)} target(s) processed via AI model `{AI_MODEL}`.")
        ai_lines.append("")

        # Count success/fail
        success = 0
        failed = 0
        for af in ai_files:
            content = af.read_text(encoding="utf-8")
            if content.startswith("AI CODING FAILED"):
                failed += 1
            else:
                success += 1

        ai_lines.append(f"- **Success:** {success}")
        ai_lines.append(f"- **Failed:** {failed}")
        ai_lines.append("")

        for af in sorted(ai_files):
            rel = af.relative_to(bundle_dir)
            size = af.stat().st_size
            ai_lines.append(f"- `{rel}` ({size:,}B)")

        existing = summary_path.read_text(encoding="utf-8")
        summary_path.write_text(existing + "\n".join(ai_lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
