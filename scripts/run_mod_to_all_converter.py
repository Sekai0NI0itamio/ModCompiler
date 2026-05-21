#!/usr/bin/env python3
"""
run_mod_to_all_converter.py
----------------------------
Triggers the "Automated Mod to ALL Version Converter" workflow on GitHub
Actions, waits for completion, and downloads all artifacts and logs.

After the workflow completes, the script enters the second stage
(mode="prompt-and-code" by default) — an **automated** DeepSeek
AI coding stage. For each target version/loader:

  1. The prompt is sent to DeepSeek via C05 local server
  2. DeepSeek's response is captured programmatically
  3. The response is saved as airesponse.txt
  4. If the response lacks Java source files, retries with enhanced prompt
     (up to AI_JAVA_RETRIES times)
  5. Conversation is saved as conversation.json on success

Requires: C05 local server running on localhost:8129.
See setup instructions in the AI config section below.

Pass --mode prompt-creation to skip the AI coding stage entirely.

Usage
-----
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-and-code
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-creation
  python3 scripts/run_mod_to_all_converter.py --continuefrom lifesteal-parrot-mod-20260513-104548

Modes
-----
  prompt-and-code (default) — Run workflow, download prompts, THEN run the
                              automated DeepSeek AI coding stage.
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
          airesponse.txt    ← created by DeepSeek AI
          conversation.json ← saved on successful responses
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

_DECOMPILED_ROOT = Path(__file__).resolve().parent.parent / "DecompiledMinecraftSourceCode"


def _query_local_ai(user_prompt: str, max_retries: int = 2) -> str:
    import json as _json
    import urllib.error
    import urllib.request

    payload = _json.dumps({
        "hoster": "nvidia",
        "model": "meta/llama-3.1-8b-instruct",
        "user_prompt": user_prompt,
    }).encode("utf-8")

    req = urllib.request.Request(
        "http://localhost:8129/chat",
        data=payload,
        headers={"Content-Type": "application/json"},
    )

    for attempt in range(1, max_retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return resp.read().decode("utf-8", errors="replace").strip()
        except Exception:
            if attempt < max_retries:
                time.sleep(2 * attempt)
                continue
            return ""


def _ai_select_fix_source_files(
    source_paths: list[str],
    error_message: str,
    current_code: str,
    count: int = 5,
) -> list[str]:
    if not source_paths:
        return []

    current_code_truncated = current_code[:8000] if len(current_code) > 8000 else current_code

    file_list = "\n".join(source_paths[:500])
    if len(source_paths) > 500:
        file_list += f"\n... and {len(source_paths) - 500} more files"

    prompt = (
        "You are a Minecraft modding expert. A mod failed to compile with the following error:\n"
        "\n"
        "BUILD ERROR:\n"
        f"{error_message[:5000]}\n"
        "\n"
        "CURRENT MOD CODE:\n"
        f"{current_code_truncated}\n"
        "\n"
        "AVAILABLE MINECRAFT SOURCE FILES:\n"
        f"{file_list}\n"
        "\n"
        f"Select the {count} most likely Minecraft source files that would help fix this build error.\n"
        "Respond with ONLY the selected file paths, one per line, inside a code block. No explanations needed."
    )

    response = _query_local_ai(prompt)
    if not response:
        return []

    code_block_match = re.search(r"```[^\n]*\n(.*?)```", response, re.DOTALL)
    if code_block_match:
        response = code_block_match.group(1)

    selected = []
    path_set = set(source_paths)
    for line in response.strip().splitlines():
        line = line.strip()
        if not line:
            continue
        if line in path_set:
            selected.append(line)
        else:
            for sp in source_paths:
                if sp.endswith("/" + line) or sp.endswith("\\" + line):
                    selected.append(sp)
                    break

    return selected[:count]


def _read_source_file_contents(
    decompiled_root: Path,
    file_paths: list[str],
    max_chars: int = 50000,
) -> str:
    content_parts = []
    total = 0
    for path in file_paths:
        file_path = decompiled_root / path
        if not file_path.exists() or not file_path.is_file():
            continue
        try:
            text = file_path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        if len(text) > 200000:
            text = text[:200000] + "\n// ... (truncated)\n"
        if total + len(text) > max_chars:
            allowed = max_chars - total
            if allowed > 200:
                text = text[:allowed] + "\n// ... (truncated)\n"
                content_parts.append(f"--- {path} ---\n```java\n{text}\n```")
            break
        content_parts.append(f"--- {path} ---\n```java\n{text}\n```")
        total += len(text)
    return "\n".join(content_parts)


def _trim_prompt_to_limit(prompt_text: str, max_chars: int = 300000) -> str:
    if len(prompt_text) <= max_chars:
        return prompt_text

    source_start_marker = "=== RELEVANT MINECRAFT SOURCE CODE (for fixing the error) ==="
    source_end_marker = "=== END RELEVANT MINECRAFT SOURCE CODE ==="

    start_idx = prompt_text.find(source_start_marker)
    end_idx = prompt_text.find(source_end_marker)

    if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
        before = prompt_text[:start_idx]
        after = prompt_text[end_idx + len(source_end_marker):]
        source_section = prompt_text[start_idx:end_idx + len(source_end_marker)]

        available = max_chars - len(before) - len(after)
        if available > 1000:
            trimmed_source = source_section[:available]
            prompt_text = before + trimmed_source + "\n" + source_end_marker + after
        else:
            prompt_text = before + after

    if len(prompt_text) > max_chars:
        prompt_text = prompt_text[:max_chars]

    return prompt_text


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
# AI Coding Stage — sends prompts to Free DeepSeek via C05 local server
# ─────────────────────────────────────────────────────────────────────────────
#
#   HOSTER: Free DeepSeek  |  hoster: "freedeepseek"
#   MODEL:  Expert
#
# Uses the C05 local server at localhost:8129 to access DeepSeek's models.
# No API key required — the server handles authentication.
#
# Example curl:
#   curl -s http://localhost:8129/chat \
#     -H "Content-Type: application/json" \
#     -d '{
#       "hoster": "freedeepseek",
#       "model": "Expert",
#       "user_prompt": "Explain quantum computing",
#       "extra_body": {
#           "thinking": false,
#           "web_search": false
#       }
#     }'

# --- AI Provider Config ---
AI_C05_BASE = "http://localhost:8129"
AI_MODEL = "Expert"
AI_HOSTER = "freedeepseek"

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
    """Send a raw mod name to DeepSeek to format into proper metadata fields.

    The AI formats the name WITHOUT changing, renaming, or inventing anything.
    It only converts to the required field formats (mod_id, mod_class, etc.).

    Returns dict with keys: mod_id, mod_class, mod_client_class,
    mod_display_name, package_path, author_name.
    """
    import http.client
    import urllib.parse

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
        "hoster": AI_HOSTER,
        "model": AI_MODEL,
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
        "extra_body": {
            "thinking": False,
            "web_search": False,
        },
    }

    body_bytes = json.dumps(payload).encode("utf-8")

    parsed = urllib.parse.urlparse(AI_C05_BASE)
    host = parsed.hostname or "localhost"
    port = parsed.port or 8129

    full_response = ""
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
            raise RuntimeError(f"HTTP {resp.status}: {error_detail}")

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
                    raise RuntimeError(str(data["error"]))

                if data.get("event") == "content":
                    full_response += data.get("content", "")

                if data.get("status") == "end":
                    end_content = data.get("content", "") or ""
                    if end_content and not full_response:
                        full_response = end_content
                    done = True
                    break
    finally:
        conn.close()

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


def _send_prompt_to_deepseek(
    target_name: str,
    prompt_text: str,
    retry_context: str = "",
) -> str:
    """Send a prompt to DeepSeek via C05 local server and return the response.

    Uses the C05 local server at localhost:8129 to access DeepSeek's models.
    No API key required — the server handles authentication.

    - Sends NDJSON streaming request to /chat endpoint
    - Accumulates content events and returns only the content text
    - Properly exits on the "end" event to avoid hanging

    Args:
        target_name: The name of the target (e.g. "1.20.5-fabric").
        prompt_text: The prompt to send.
        retry_context: If non-empty, this is a retry — the text explains what
                       was wrong with the previous response.
    """
    import http.client
    import urllib.parse

    # Build system prompt from author info if available
    system_parts = [
        "You are an excellent and professional Minecraft mod developer.",
        "You are expert at reading and following technical instructions precisely.",
        "You write clean, correct, and well-structured Java code.",
        "Provide ALL files requested with complete implementations — no stubs, no TODOs, no placeholders.",
    ]

    user_content = prompt_text
    if retry_context:
        user_content = f"{retry_context}\n\n---\n\n{prompt_text}"

    payload: dict[str, Any] = {
        "hoster": AI_HOSTER,
        "model": AI_MODEL,
        "system_prompt": "\n".join(system_parts),
        "user_prompt": user_content,
        "extra_body": {
            "thinking": False,
            "web_search": False,
        },
    }

    body_bytes = json.dumps(payload).encode("utf-8")

    parsed = urllib.parse.urlparse(AI_C05_BASE)
    host = parsed.hostname or "localhost"
    port = parsed.port or 8129

    full_response = ""
    accumulated = 0
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
                raise RetryableHTTPError(f"HTTP {resp.status}: {error_detail}")
            raise RuntimeError(f"HTTP {resp.status}: {error_detail}")

        buffer = ""
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
                    raise RuntimeError(str(data["error"]))

                event = data.get("event", "")

                if event == "content":
                    content = data.get("content", "") or ""
                    if content:
                        full_response += content
                        accumulated += len(content)
                        if accumulated % 2000 < 100:
                            print(f"  {target_name}: {accumulated:,} bytes received...")

                if data.get("status") == "end":
                    end_content = data.get("content", "") or ""
                    if end_content and not full_response:
                        full_response = end_content
                        accumulated = len(end_content)
                    done = True
                    break
    except RetryableHTTPError:
        conn.close()
        raise
    except Exception:
        conn.close()
        raise

    conn.close()

    if not full_response:
        raise RuntimeError("Empty response from DeepSeek — no content generated.")

    print(f"  {target_name}: ✓ {accumulated:,} bytes total")
    return full_response


def _run_ai_coding_stage(bundle_dir: Path, previous_progress: dict[str, str] | None = None) -> int:
    """Execute the AI coding stage: send prompts to DeepSeek via C05 server.

    Iterates through all target directories programmatically. For each target:
      1. Sends the prompt to DeepSeek (via C05 local server)
      2. Saves the response as airesponse.txt
      3. If the response lacks Java source files, retries with enhanced prompt
         (up to AI_JAVA_RETRIES times)
      4. Saves conversation.json for successful responses

    Fully automated AI interaction. No user intervention required.

    Args:
        bundle_dir: Path to the analysis bundle directory.
        previous_progress: Dict mapping target_name -> status ("complete" or "failed")
                           from a previous partial run.
    """
    is_resume = bool(previous_progress)
    previous_progress = previous_progress or {}

    # ── Find targets ──────────────────────────────────────────────────────
    all_target_dirs = sorted([
        d for d in bundle_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])
    if not all_target_dirs:
        print("ERROR: No target directories found in bundle.", file=sys.stderr)
        return 1

    prompt_targets: list[Path] = []
    skipped_names: list[str] = []

    for td in all_target_dirs:
        prompt_path = td / "prompt.txt"
        if not prompt_path.exists():
            continue
        if is_resume:
            prev_status = previous_progress.get(td.name, "")
            if prev_status in ("complete", "failed") and (td / "airesponse.txt").exists():
                skipped_names.append(td.name)
                _log(f"  Skipping already-processed target: {td.name} (status: {prev_status})")
                continue
            if (td / "airesponse.txt").exists():
                _log(f"  Target already has airesponse.txt: {td.name} — skipping")
                continue
        else:
            for fname in ("airesponse.txt", "conversation.json"):
                fp = td / fname
                if fp.exists():
                    fp.unlink()
        prompt_targets.append(td)

    if skipped_names:
        print(f"\n  Skipping {len(skipped_names)} already-processed target(s): {', '.join(skipped_names)}")
        print()
    if not prompt_targets:
        print("All targets already processed. Skipping AI coding stage.")
        return 0

    print()
    print("=" * 72)
    print("  PHASE 2 — AI MOD CODING via DeepSeek")
    print("=" * 72)
    print()
    print(f"  {len(prompt_targets)} target(s) to process")
    print(f"  AI: DeepSeek (via C05 local server)")
    print(f"  Model: {AI_MODEL}")
    print(f"  Max Java retries: {AI_JAVA_RETRIES}")
    print()

    # ── Process each target sequentially ──────────────────────────────────
    results: dict[str, str] = {}  # target_name -> "complete" | "failed"
    total = len(prompt_targets)

    for idx, td in enumerate(prompt_targets):
        target_name = td.name
        prompt_text = (td / "prompt.txt").read_text(encoding="utf-8")
        author, mod_path = _read_author_info(td)

        # Check for launcher failure log and prepend to prompt
        launcher_failure_path = td / "launcher-failure.txt"
        if launcher_failure_path.exists():
            crash_log = launcher_failure_path.read_text(encoding="utf-8", errors="replace")
            launcher_failure_context = (
                "\n=== LAUNCHER TEST FAILURE ===\n"
                "The mod was built successfully but FAILED to launch in Minecraft.\n"
                "The crash log is below. Fix the issues that caused this crash:\n"
                "\n"
                f"{crash_log}\n"
                "=== END LAUNCHER TEST FAILURE ===\n"
            )
            prompt_text = launcher_failure_context + "\n" + prompt_text
            print(f"[{idx + 1}/{total}] {target_name}  ⚠ LAUNCHER FAILURE DETECTED")
        else:
            print(f"[{idx + 1}/{total}] {target_name}")
        print(f"  Prompt size: {len(prompt_text):,} chars")

        # Build system prompt with author context
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

        # ── Send to DeepSeek with retry loop for no-Java responses ────────────
        response = ""
        final_status = "failed"
        current_prompt = prompt_text
        retry_context = ""

        for attempt in range(1 + AI_JAVA_RETRIES):
            try:
                if attempt == 0:
                    print(f"  Sending to DeepSeek...", end=" ", flush=True)
                else:
                    print(f"  Retry {attempt}/{AI_JAVA_RETRIES} (previous response had no Java)...", end=" ", flush=True)

                # Build the full payload
                full_user_prompt = current_prompt

                # Send via the low-level HTTP helper
                response = _send_prompt_to_deepseek(
                    target_name,
                    full_user_prompt,
                    retry_context=retry_context,
                )

                if not response or not response.strip():
                    print(f"\n  ⚠ Empty response from DeepSeek")
                    if attempt < AI_JAVA_RETRIES:
                        retry_context = (
                            "YOUR PREVIOUS RESPONSE WAS EMPTY.\n"
                            "You MUST generate COMPLETE Java source files for this mod."
                        )
                        current_prompt = prompt_text
                        time.sleep(2)
                        continue
                    break

                # Save the response
                (td / "airesponse.txt").write_text(response, encoding="utf-8")

                if _response_has_java_files(response):
                    # Save conversation for successful responses
                    conversation = [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": prompt_text},
                        {"role": "assistant", "content": response},
                    ]
                    (td / "conversation.json").write_text(
                        json.dumps(conversation, indent=2), encoding="utf-8"
                    )
                    final_status = "complete"
                    print(f"  ✓ Complete ({len(response):,} chars)")
                    break
                else:
                    print(f"\n  ⚠ No Java source files detected in response")
                    if attempt < AI_JAVA_RETRIES:
                        retry_context = (
                            "YOUR PREVIOUS RESPONSE HAD NO JAVA SOURCE FILES.\n"
                            "This means the build will produce empty jars with no .class files.\n"
                            "You MUST generate COMPLETE Java source files for this mod, "
                            "each one starting with the correct `package` declaration.\n"
                            "Output each file under `src/main/java/` with its full package path.\n"
                            "Do NOT output the same JSON/config files again — they are already present."
                        )
                        current_prompt = prompt_text
                        time.sleep(2)
                        continue
                    else:
                        print(f"  ✗ Failed after {AI_JAVA_RETRIES} retries (no Java files)")
                        final_status = "failed"
                        break

            except RetryableHTTPError as e:
                print(f"\n  ⚠ Retryable error: {e}")
                if attempt < AI_JAVA_RETRIES:
                    time.sleep(10)
                    continue
                final_status = "failed"
                break
            except Exception as e:
                print(f"\n  ✗ Error: {e}")
                final_status = "failed"
                break

        results[target_name] = final_status

        # Brief pause between targets to respect rate limits
        if idx < total - 1:
            print(f"  Waiting 3s before next target...")
            time.sleep(3)
        print()

    # ── Summary ──────────────────────────────────────────────────────────
    success_count = sum(1 for v in results.values() if v == "complete")
    fail_count = sum(1 for v in results.values() if v == "failed")

    print()
    print("=" * 72)
    print("  PHASE 2 — AI MOD CODING Complete")
    print("=" * 72)
    print()
    for name, status in results.items():
        icon = "✓" if status == "complete" else "✗"
        print(f"  {icon} {name}")
    print()
    print(f"  {success_count} success, {fail_count} failed")
    print()

    # ── Save progress ───────────────────────────────────────────────────
    ai_progress: dict[str, str] = {}
    for td in all_target_dirs:
        resp_path = td / "airesponse.txt"
        if resp_path.exists():
            content = resp_path.read_text(encoding="utf-8")
            ai_progress[td.name] = "complete" if _response_has_java_files(content) else "failed"
        else:
            ai_progress[td.name] = results.get(td.name, "failed")
    for n in skipped_names:
        if n not in ai_progress:
            ai_progress[n] = previous_progress.get(n, "skipped")

    out_root = bundle_dir.parent.parent
    ai_progress_path = out_root / "ai_stage_progress.json"
    try:
        ai_progress_path.write_text(json.dumps(ai_progress, indent=2), encoding="utf-8")
        _log(f"  Saved AI stage progress to {ai_progress_path}")
    except Exception as exc:
        _log(f"  Warning: Failed to save AI stage progress: {exc}")

    return 0 if fail_count == 0 else 1


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
            "                              execute prompts via DeepSeek AI\n"
            "                              and save airesponse.txt.\n"
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
            candidate = Path(args.continuefrom)
            if candidate.is_absolute() and candidate.exists():
                self.out_root = candidate.resolve()
            elif candidate.exists():
                self.out_root = candidate.resolve()
            else:
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
            "has_launcher_failure_logs": False,
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
                if (target_dir / "launcher-failure.txt").exists():
                    progress["has_launcher_failure_logs"] = True
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
            print(f"  Launcher fails:  {'⚠ detected' if progress['has_launcher_failure_logs'] else '✓ none'}")
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

    def _list_artifacts(self) -> list[dict]:
        out = _gh([
            "api", f"repos/{self.repo}/actions/runs/{self.run_id}/artifacts",
        ], token=self.token)
        data = json.loads(out or "{}")
        return data.get("artifacts", [])

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

        # Crash artifacts (launcher test failures)
        crash_artifacts = []
        try:
            all_artifacts = self._list_artifacts()
            crash_artifacts = [a for a in all_artifacts if a.get("name", "").startswith("crash-")]
            if crash_artifacts:
                print(f"  Found {len(crash_artifacts)} crash artifact(s) out of {len(all_artifacts)} total")
        except RunError as exc:
            print(f"  Could not list artifacts: {exc}")

        for artifact in crash_artifacts:
            aname = artifact.get("name", "unknown")
            dest = artifacts_dir / aname
            def _make_crash_dl(an=aname, d=dest):
                def _dl():
                    try:
                        self._download_artifact(an, d)
                    except RunError as exc:
                        print(f"  (artifact {an} not available: {exc})")
                return _dl
            tasks.append((f"artifact:{aname}", _make_crash_dl()))

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

        # Copy crash logs into target directories as launcher-failure.txt
        self._copy_crash_logs_to_targets(artifacts_dir)

    def _copy_crash_logs_to_targets(self, artifacts_dir: Path) -> None:
        bundle_dir = artifacts_dir / ANALYSIS_ARTIFACT
        if not bundle_dir.exists():
            return

        crash_dirs = sorted(artifacts_dir.glob("crash-*"))
        if not crash_dirs:
            return

        target_dirs = {
            d.name: d for d in bundle_dir.iterdir()
            if d.is_dir() and not d.name.startswith(".")
        }

        for crash_dir in crash_dirs:
            crash_key = crash_dir.name[len("crash-"):]
            matching_target = target_dirs.get(crash_key)

            if not matching_target:
                sanitized = re.sub(r"[^A-Za-z0-9._-]+", "_", crash_key).strip("_")
                matching_target = target_dirs.get(sanitized)

            if not matching_target:
                continue

            crash_files = sorted(
                f for f in crash_dir.rglob("*")
                if f.is_file() and f.suffix in (".txt", ".log")
            )
            if not crash_files:
                crash_files = sorted(f for f in crash_dir.rglob("*") if f.is_file())

            if not crash_files:
                continue

            parts = []
            for cf in crash_files:
                rel = cf.relative_to(crash_dir)
                content = cf.read_text(encoding="utf-8", errors="replace")
                parts.append(f"--- {rel} ---\n{content}")

            launcher_failure_path = matching_target / "launcher-failure.txt"
            launcher_failure_path.write_text("\n\n".join(parts), encoding="utf-8")
            print(f"  \u2713 launcher-failure.txt written to {matching_target.name}/")

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

            # Launcher test results (crash artifacts)
            crash_artifact_dirs = sorted(artifacts_dir.glob("crash-*"))
            if crash_artifact_dirs:
                lines += ["", "## Launcher Test Results"]
                lines.append(f"\n{len(crash_artifact_dirs)} target(s) failed launcher testing.")
                lines.append("")
                for cd in crash_artifact_dirs:
                    files = list(cd.rglob("*"))
                    file_names = [f.name for f in files if f.is_file()]
                    lines.append(f"- `{cd.name}/` ({len(file_names)} file(s))")
                    for fn in file_names[:10]:
                        lines.append(f"  - `{fn}`")
                    if len(file_names) > 10:
                        lines.append(f"  - ... and {len(file_names) - 10} more")
                    crash_key = cd.name[len("crash-"):]
                    target_dir = bundle_dir / crash_key
                    if not target_dir.exists():
                        sanitized = re.sub(r"[^A-Za-z0-9._-]+", "_", crash_key).strip("_")
                        target_dir = bundle_dir / sanitized
                    lf = target_dir / "launcher-failure.txt" if target_dir.exists() else None
                    if lf and lf.exists():
                        lines[-1] += "  [✓ launcher-failure.txt]"
                lines.append("")

            # Launcher failure logs in target directories
            launcher_failure_files = list(bundle_dir.rglob("launcher-failure.txt"))
            if launcher_failure_files:
                lines += ["", "## Launcher Failure Logs"]
                lines.append("")
                for lf in sorted(launcher_failure_files):
                    rel = lf.relative_to(bundle_dir)
                    text = lf.read_text(encoding="utf-8")
                    lines.append(f"### {rel}")
                    lines.append("")
                    lines.append("```")
                    for line in text.splitlines()[:80]:
                        lines.append(line)
                    if text.count("\n") > 80:
                        lines.append(f"... ({text.count(chr(10)) - 80} more lines)")
                    lines.append("```")
                    lines.append("")
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
