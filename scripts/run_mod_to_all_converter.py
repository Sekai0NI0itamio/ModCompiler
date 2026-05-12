#!/usr/bin/env python3
"""
run_mod_to_all_converter.py
----------------------------
Triggers the "Automated Mod to ALL Version Converter" workflow on GitHub
Actions, waits for completion, and downloads all artifacts and logs.

After the workflow completes, the script **always** enters the second stage
(mode="prompt-and-code" by default) that sends each generated
prompt to an AI (via C05LocalAi / NVIDIA) and saves the response as
airesponse.txt in each version folder.

Pass --mode prompt-creation to skip the AI coding stage.

Usage
-----
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-and-code
  python3 scripts/run_mod_to_all_converter.py https://modrinth.com/mod/sort-chest --mode prompt-creation

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
DEFAULT_TIMEOUT = 7200
POLL_INTERVAL = 15
LOG_POLL_INTERVAL = 30
MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


class RunError(Exception):
    pass


class RetryableHTTPError(RuntimeError):
    """An HTTP error that can be retried (429, 503)."""
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
    RED = "\033[91m"
    YELLOW = "\033[93m"
    GREEN = "\033[92m"
    BLACK_BOLD = "\033[1;30m"
    RESET = "\033[0m"
    BOLD = "\033[1m"


_STATUS_LABELS = {
    "queued": ("Queued", Colors.GREY),
    "in_progress": ("In Progress", Colors.RED),
    "streaming": ("Streaming", Colors.YELLOW),
    "complete": ("Complete", Colors.GREEN),
    "retrying": ("Retrying", Colors.BLACK_BOLD),
    "failed": ("Failed", Colors.BLACK_BOLD),
}


# ─────────────────────────────────────────────────────────────────────────────
# AI Coding Stage — sends prompts to C05LocalAi / NVIDIA
# ─────────────────────────────────────────────────────────────────────────────

# --- AI Provider Config ---
AI_PROVIDERS = {
    "default": {
        "base_url": "https://integrate.api.nvidia.com/v1",
        "model": "z-ai/glm4.7",
        "provider_name": "NVIDIA Integrate API",
        "key_file": "C05LocalAi/keys/nvidia.txt",
        "key_env_vars": ("NVIDIA_API_KEY", "NVAPI_KEY"),
    },
    "intelligent": {
        "base_url": "https://api.deepseek.com",
        "model": "deepseek-chat",
        "provider_name": "DeepSeek API",
        "key_file": "C05LocalAi/keys/deepseek.txt",
        "key_env_vars": ("DEEPSEEK_API_KEY", "DEEPSEEK_KEY", "NVIDIA_API_KEY", "NVAPI_KEY"),
    },
}

AI_NVIDIA_BASE = AI_PROVIDERS["default"]["base_url"]
AI_MODEL = AI_PROVIDERS["default"]["model"]


def _get_ai_config(intelligent: bool = False) -> dict:
    mode = "intelligent" if intelligent else "default"
    return AI_PROVIDERS[mode]


def _apply_ai_config(intelligent: bool = False) -> None:
    global AI_NVIDIA_BASE, AI_MODEL
    c = _get_ai_config(intelligent)
    AI_NVIDIA_BASE = c["base_url"]
    AI_MODEL = c["model"]


def _load_ai_key(intelligent: bool = False) -> str:
    """Load a DeepSeek API key from C05LocalAi/keys/deepseek.txt."""
    c = _get_ai_config(intelligent)
    key_path = Path(c["key_file"])
    if key_path.exists():
        for line in key_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                return line
    for var in c["key_env_vars"]:
        val = os.environ.get(var, "").strip()
        if val:
            return val
    key_path.parent.mkdir(parents=True, exist_ok=True)
    raise RunError(
        f"No API key found. Set {c['key_env_vars'][0]} env var "
        f"or add a key to {c['key_file']}"
    )


def _run_ai_coding_stage(bundle_dir: Path, nvidia_key: str, intelligent: bool = False) -> int:
    """Execute the AI coding stage: send prompts to NVIDIA and save responses."""
    import threading

    print()
    print("=" * 72)
    print("  PHASE 2 — AI MOD CODING (Local)")
    print(f"  Model: {AI_MODEL}")
    print(f'  Provider: {_get_ai_config(intelligent)["provider_name"]}')
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

    # Filter to those with prompt.txt
    prompt_targets = []
    for td in target_dirs:
        prompt_path = td / "prompt.txt"
        if prompt_path.exists():
            prompt_targets.append(td)

    if not prompt_targets:
        print("No prompt.txt files found. Skipping AI coding stage.")
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

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        fut_to_target = {}
        for td in prompt_targets:
            prompt_path = td / "prompt.txt"
            fut = pool.submit(
                _send_prompt_to_nvidia,
                td.name,
                prompt_path.read_text(encoding="utf-8"),
                nvidia_key,
                _update_line,
            )
            fut_to_target[fut] = td

        for fut in as_completed(fut_to_target):
            td = fut_to_target[fut]
            try:
                response_text = fut.result()
                resp_path = td / "airesponse.txt"
                resp_path.write_text(response_text, encoding="utf-8")
                _update_line(td.name, "complete", f"{len(response_text):,} chars")
                success_count += 1
            except RetryableHTTPError:
                # Retry on 429/503 with backoff
                _update_line(td.name, "retrying", "server busy, retrying in 30s...")
                _log(f"  Retrying {td.name} after 30s (503 ResourceExhausted)...")
                time.sleep(30)
                try:
                    prompt_path = td / "prompt.txt"
                    response_text = _send_prompt_to_nvidia(
                        td.name,
                        prompt_path.read_text(encoding="utf-8"),
                        nvidia_key,
                        _update_line,
                    )
                    resp_path = td / "airesponse.txt"
                    resp_path.write_text(response_text, encoding="utf-8")
                    _update_line(td.name, "complete", f"{len(response_text):,} chars")
                    success_count += 1
                except Exception as exc2:
                    error_msg = str(exc2)
                    _update_line(td.name, "failed", error_msg[:80])
                    fail_count += 1
                    fail_path = td / "airesponse.txt"
                    fail_path.write_text(f"AI CODING FAILED\n\nError: {error_msg}\n", encoding="utf-8")
            except Exception as exc:
                error_msg = str(exc)
                _update_line(td.name, "failed", error_msg[:80])
                fail_count += 1
                fail_path = td / "airesponse.txt"
                fail_path.write_text(f"AI CODING FAILED\n\nError: {error_msg}\n", encoding="utf-8")

    print()
    print(f"{'─' * 72}")
    print(f"  AI Coding Stage Complete: {success_count} success, {fail_count} failed")
    print(f"{'─' * 72}")

    return 0 if fail_count == 0 else 1


def _send_prompt_to_nvidia(
    target_name: str,
    prompt_text: str,
    api_key: str,
    status_callback: callable,
) -> str:
    """Send a single prompt to the NVIDIA API and return the full response."""
    import urllib.request
    import urllib.error

    url = f"{AI_NVIDIA_BASE}/v1/chat/completions"

    messages = []
    messages.append({
        "role": "system",
        "content": (
            "You are an excellent and professional Minecraft mod developer. "
            "You are expert at reading and following technical instructions precisely. "
            "You write clean, correct, and well-structured Java code. "
            "Provide ALL files requested with complete implementations — no stubs, no TODOs, no placeholders."
        )
    })
    messages.append({"role": "user", "content": prompt_text})

    payload: dict[str, Any] = {
        "model": AI_MODEL,
        "messages": messages,
        "temperature": 0.2,
        "stream": True,
    }

    # Enable reasoning/thinking for stepfun models via NVIDIA's chat_template_kwargs
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

    status_callback(target_name, "in_progress")

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

                # Process SSE events
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
                                content = delta.get("content", "") or delta.get("reasoning_content", "") or ""
                                if content:
                                    full_response += content
                                    accumulated += len(content)
                                    if accumulated % 500 < 100:
                                        status_callback(
                                            target_name, "streaming",
                                            f"{accumulated:,} bytes received"
                                        )
                        except json.JSONDecodeError:
                            pass
    except urllib.error.HTTPError as e:
        error_code = e.code
        error_detail = e.read().decode("utf-8", errors="replace")[:500]
        # Retry on 429 (rate limit) and 503 (service busy / ResourceExhausted)
        if error_code in (429, 503):
            status_callback(target_name, "retrying", f"HTTP {error_code}: {error_detail}")
            import time as _time
            _time.sleep(30)
            # Tell caller to retry via a special signal
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
    parser.add_argument("modrinth_url",
        help="Modrinth project URL or slug (e.g. https://modrinth.com/mod/sort-chest)")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR,
        help=f"Root folder for run outputs (default: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
        help=f"Max seconds to wait for workflow (default: {DEFAULT_TIMEOUT})")
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
        self.mode = args.mode
        self.intelligent = getattr(args, 'intelligent', False)

        # Derive slug from URL
        m = re.search(r"modrinth\.com/(?:mod|plugin|resourcepack|shader|datapack|modpack)/([^/?#]+)", self.modrinth_url)
        self.slug = m.group(1) if m else self.modrinth_url.replace("https://", "").replace("/", "-")

        ts = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        self.out_root = Path(args.output_dir).resolve() / f"{self.slug}-{ts}"

    def run(self) -> int:
        self.out_root.mkdir(parents=True, exist_ok=True)
        _ensure_gh()

        print(f"Mode:        {self.mode}")
        print(f"Repo:        {self.repo}")
        print(f"Modrinth:    {self.modrinth_url}")
        print(f"Output dir:  {self.out_root}")
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

        if conclusion != "success":
            print(f"\n\u26a0 Workflow had conclusion '{conclusion}' — checking if bundle is still usable...")

        # 5. AI Coding Stage (only in prompt-and-code mode)
        if self.mode in ("prompt-and-code",):
            bundle_dir = artifacts_dir / ANALYSIS_ARTIFACT
            if not bundle_dir.exists():
                print("ERROR: Analysis bundle directory not found. Cannot run AI coding stage.",
                      file=sys.stderr)
                return 1

            if conclusion != "success":
                print(f"  Bundle found at {bundle_dir} — proceeding despite workflow conclusion '{conclusion}'.")

            nvidia_key = _load_ai_key(self.intelligent)
            ai_result = _run_ai_coding_stage(bundle_dir, nvidia_key, self.intelligent)

            # Update SUMMARY.md with AI results
            self._append_ai_summary(artifacts_dir)

            if ai_result != 0:
                print("\n⚠ AI coding stage had failures. Proceeding to compile cycle anyway...")

            # 6. Compile Cycle: extract, build, retry
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
                + (["--intelligent"] if self.intelligent else []),
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
