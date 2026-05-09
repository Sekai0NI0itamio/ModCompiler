"""
agent.py — AI Mod Version Converter Agent
==========================================
The main agentic loop that drives the compilation process.
Uses qwen/qwen3-coder-480b-a35b-instruct via NVIDIA API.

Session directory model
-----------------------
Each run gets a unique session directory:
  ai-sessions/<session-id>/

Everything the AI touches lives here:
  ai-sessions/<session-id>/
    project_info/          ← copy of Phase 1 output (read/write)
    target_list.json       ← copy of target list (read/write)
    source/                ← decompiled source from first version (read/write)
    build/<mc>-<loader>/   ← per-target build workspace (read/write)
    artifacts/             ← compiled jars staged here (read/write)
    session_state.json     ← success/failed tracking
    session_memory.json    ← AI scratchpad
    session_todos.json     ← AI task checklist

The AI's "ls" shows this session dir PLUS a read-only view of repo templates.
CMD is restricted to the session dir — any attempt to touch files outside is blocked.
"""
from __future__ import annotations

import asyncio
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple
import urllib.request
import urllib.error

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from aibasedversionupgrader.key_scheduler import KeyStressScheduler
from aibasedversionupgrader import tools as T

MODEL = "nvidia/nemotron-3-nano-30b-a3b"
NVIDIA_API_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
MAX_TOKENS = 16384
THINKING_EFFORT = "high"  # nemotron supports thinking effort: low, medium, high
MAX_503_RETRIES = 5
_503_BASE_BACKOFF = 2.0
_503_MAX_BACKOFF = 60.0


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

@dataclass
class AgentConfig:
    nvidia_keys: List[str]
    modrinth_token: str
    target_list_path: Path
    project_info_dir: Path
    sessions_base_dir: Path      # ai-sessions/
    artifact_dir: Path           # final output dir (outside session)
    repo_root: Path
    is_local: bool = False
    max_iterations: int = 200
    summary_file: Optional[Path] = None
    log_file: Optional[Path] = None


@dataclass
class AgentResult:
    success_list: List[Dict] = field(default_factory=list)
    failed_list: List[Dict] = field(default_factory=list)
    total_targets: int = 0
    iterations: int = 0
    session_dir: Optional[Path] = None
    error: Optional[str] = None


# ---------------------------------------------------------------------------
# Tool definitions
# ---------------------------------------------------------------------------

TOOL_DEFINITIONS = [
    {"type": "function", "function": {"name": "ls", "description": "List files in your session directory (and optionally repo templates). Use batch() to call ls() on multiple paths simultaneously instead of one at a time.", "parameters": {"type": "object", "properties": {"path": {"type": "string", "description": "Relative path inside your session dir, or a repo template path prefixed with 'repo:' (e.g. 'repo:1.21.2-1.21.8/fabric/template'). Omit for session root."}}}}},
    {"type": "function", "function": {"name": "read", "description": "Read any file — either in your session dir (plain path) or in the repo (prefix with 'repo:'). Use batch() to read multiple files simultaneously. Example: 'repo:1.21.2-1.21.8/fabric/template/build.gradle'", "parameters": {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}}},
    {"type": "function", "function": {"name": "write", "description": "Create or overwrite a file in your session directory. Parent directories are created automatically — do NOT call mkdir before writing. IMPORTANT: Use batch() to write multiple files at once instead of calling write() one at a time.", "parameters": {"type": "object", "properties": {"path": {"type": "string"}, "content": {"type": "string"}}, "required": ["path", "content"]}}},
    {"type": "function", "function": {"name": "edit", "description": "Replace old_str with new_str in a session file.", "parameters": {"type": "object", "properties": {"path": {"type": "string"}, "old_str": {"type": "string"}, "new_str": {"type": "string"}, "replace_all": {"type": "boolean"}}, "required": ["path", "old_str", "new_str"]}}},
    {"type": "function", "function": {"name": "multiedit", "description": "Apply multiple sequential edits to one session file.", "parameters": {"type": "object", "properties": {"path": {"type": "string"}, "edits": {"type": "array", "items": {"type": "object", "properties": {"old_str": {"type": "string"}, "new_str": {"type": "string"}, "replace_all": {"type": "boolean"}}, "required": ["old_str", "new_str"]}}}, "required": ["path", "edits"]}}},
    {"type": "function", "function": {"name": "patch", "description": "Apply a unified diff patch to session files.", "parameters": {"type": "object", "properties": {"patch": {"type": "string"}}, "required": ["patch"]}}},
    {"type": "function", "function": {"name": "delete", "description": "Delete a file from your session directory.", "parameters": {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}}},
    {"type": "function", "function": {"name": "move", "description": "Move or rename a file within your session directory.", "parameters": {"type": "object", "properties": {"src": {"type": "string"}, "dst": {"type": "string"}}, "required": ["src", "dst"]}}},
    {"type": "function", "function": {"name": "grep", "description": "Regex search across files in your session dir or the repo. Use root='session' (default) or root='repo'.", "parameters": {"type": "object", "properties": {"pattern": {"type": "string"}, "root": {"type": "string", "enum": ["session", "repo"]}, "glob": {"type": "string"}, "context": {"type": "integer"}}, "required": ["pattern"]}}},
    {"type": "function", "function": {"name": "glob", "description": "Find files by glob pattern in your session dir or the repo.", "parameters": {"type": "object", "properties": {"pattern": {"type": "string"}, "root": {"type": "string", "enum": ["session", "repo"]}}, "required": ["pattern"]}}},
    {"type": "function", "function": {"name": "cmd", "description": "Run a shell command. Restricted to your session directory — cannot touch files outside it. Do NOT use mkdir — the write tool creates directories automatically.", "parameters": {"type": "object", "properties": {"command": {"type": "string"}, "timeout": {"type": "integer"}}, "required": ["command"]}}},
    {"type": "function", "function": {"name": "compile", "description": "Compile a mod for a specific minecraft_version + loader. Copies the correct template into build/<mc>-<loader>/, runs build_adapter.py, returns the full build log.", "parameters": {"type": "object", "properties": {"minecraft_version": {"type": "string"}, "loader": {"type": "string"}}, "required": ["minecraft_version", "loader"]}}},
    {"type": "function", "function": {"name": "diagnostics", "description": "Parse a build log into structured errors and warnings (javac, gradle, mixin).", "parameters": {"type": "object", "properties": {"log": {"type": "string"}}, "required": ["log"]}}},
    {"type": "function", "function": {"name": "dif", "description": "Query or extend the DIF knowledge base. ops: list, search, read, create, update.", "parameters": {"type": "object", "properties": {"op": {"type": "string", "enum": ["list", "search", "read", "create", "update"]}, "query": {"type": "string"}, "id": {"type": "string"}, "entry": {"type": "object"}}, "required": ["op"]}}},
    {"type": "function", "function": {"name": "mcsource", "description": "Search or read Minecraft decompiled source. ops: list, search, read. NOTE: Some versions (e.g. 1.16.5-forge) only contain Forge-specific source, not vanilla Minecraft classes — if search returns empty, use websearch instead.", "parameters": {"type": "object", "properties": {"op": {"type": "string", "enum": ["list", "search", "read"]}, "version": {"type": "string"}, "loader": {"type": "string"}, "pattern": {"type": "string"}, "path": {"type": "string"}}, "required": ["op"]}}},
    {"type": "function", "function": {"name": "websearch", "description": "Search the web for Minecraft API docs, Gradle errors, loader migration guides.", "parameters": {"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}}},
    {"type": "function", "function": {"name": "webfetch", "description": "Fetch a URL and return its content as markdown.", "parameters": {"type": "object", "properties": {"url": {"type": "string"}}, "required": ["url"]}}},
    {"type": "function", "function": {"name": "memory", "description": "Read or write your persistent scratchpad. ops: read, write, list.", "parameters": {"type": "object", "properties": {"op": {"type": "string", "enum": ["read", "write", "list"]}, "key": {"type": "string"}, "value": {"type": "string"}}, "required": ["op", "key"]}}},
    {"type": "function", "function": {"name": "think", "description": "Emit a reasoning step to the log. No side effects — use this to plan before acting.", "parameters": {"type": "object", "properties": {"reasoning": {"type": "string"}}, "required": ["reasoning"]}}},
    {"type": "function", "function": {"name": "progress", "description": "Report a status update (shown in GitHub Actions step summary).", "parameters": {"type": "object", "properties": {"message": {"type": "string"}}, "required": ["message"]}}},
    {"type": "function", "function": {"name": "stage_artifact", "description": "Copy a compiled jar from your session dir to the final artifact output.", "parameters": {"type": "object", "properties": {"path": {"type": "string"}, "name": {"type": "string"}}, "required": ["path"]}}},
    {"type": "function", "function": {"name": "todo", "description": "Manage your task checklist. Status values: 'not_started', 'in_progress', 'completed'. To UPDATE specific items (partial update, other items unchanged): todo(todos=[{\"id\": \"3\", \"status\": \"in_progress\"}]). To mark done and start next: todo(todos=[{\"id\": \"3\", \"status\": \"completed\"}, {\"id\": \"4\", \"status\": \"in_progress\"}]). To READ: todo() or todo(op='read'). The todo list is shown after every tool call — you MUST update it manually as you progress.", "parameters": {"type": "object", "properties": {"op": {"type": "string", "enum": ["read"]}, "replace": {"type": "boolean", "description": "If true, replace the entire list instead of merging"}, "todos": {"type": "array", "items": {"type": "object", "properties": {"id": {"type": "string"}, "content": {"type": "string"}, "status": {"type": "string", "enum": ["not_started", "in_progress", "completed"]}}, "required": ["id", "status"]}}}}}},
    {"type": "function", "function": {"name": "batch", "description": "Run up to 25 tool calls in PARALLEL. This is the REQUIRED way to write multiple files — never call write() one at a time. Example: batch(tool_calls=[{\"tool\": \"write\", \"parameters\": {\"path\": \"bundle/1.8.9-forge/src/main/java/com/example/Mod.java\", \"content\": \"...\"}}, {\"tool\": \"write\", \"parameters\": {\"path\": \"bundle/1.16.5-forge/src/main/java/com/example/Mod.java\", \"content\": \"...\"}}])", "parameters": {"type": "object", "properties": {"tool_calls": {"type": "array", "items": {"type": "object", "properties": {"tool": {"type": "string"}, "parameters": {"type": "object"}}, "required": ["tool", "parameters"]}}}, "required": ["tool_calls"]}}},
    {"type": "function", "function": {"name": "build_bundle", "description": "Create a bundle zip from source files you've written in bundle/<mc>-<loader>/src/ and prepare it for GitHub Actions compilation. Call this after writing ALL source files for ALL targets. Returns the zip path and target count.", "parameters": {"type": "object", "properties": {"failed_only": {"type": "boolean", "description": "If true, only include targets that failed in the previous build"}, "modrinth_url": {"type": "string", "description": "Optional Modrinth project URL for auto-publish after successful build"}, "previous_results": {"type": "object", "description": "Results from a previous trigger_build call, used with failed_only"}}}}},
    {"type": "function", "function": {"name": "trigger_build", "description": "Commit the bundle zip, push it, and trigger the GitHub Actions build workflow. Waits for completion (30-120 min) and returns pass/fail results per target. NEVER run Gradle locally — always use this tool.", "parameters": {"type": "object", "properties": {"modrinth_url": {"type": "string"}, "max_parallel": {"type": "string", "description": "Max parallel jobs (default: all)"}, "timeout": {"type": "integer", "description": "Max seconds to wait (default: 7200)"}}}}},
]


# ---------------------------------------------------------------------------
# Logger
# ---------------------------------------------------------------------------

class AgentLogger:
    def __init__(self, is_local: bool, log_file: Optional[Path] = None):
        self.is_local = is_local
        self.log_file = log_file
        self._fh = None
        if log_file:
            log_file.parent.mkdir(parents=True, exist_ok=True)
            self._fh = open(log_file, "w", encoding="utf-8")

    def _ts(self) -> str:
        return datetime.now().strftime("%H:%M:%S")

    def _out(self, line: str, stderr: bool = False) -> None:
        if self.is_local:
            print(line, file=sys.stderr if stderr else sys.stdout)
        if self._fh:
            self._fh.write(line + "\n")
            self._fh.flush()

    def sent_to_gpt(self, msg: Dict) -> None:
        role = msg.get("role", "?")
        content = msg.get("content", "") or ""
        if isinstance(content, list):
            content = " | ".join(p.get("text", str(p))[:200] for p in content if isinstance(p, dict))
        preview = str(content)[:500].replace("\n", "↵")
        self._out(f"[{self._ts()}] [→ GPT] [{role}] {preview}")

    def received_from_gpt(self, msg: Dict) -> None:
        content = msg.get("content", "") or ""
        tcs = msg.get("tool_calls", []) or []
        preview = str(content)[:500].replace("\n", "↵")
        suffix = f" [TOOLS: {', '.join(tc.get('function',{}).get('name','?') for tc in tcs)}]" if tcs else ""
        self._out(f"[{self._ts()}] [← GPT] {preview}{suffix}")

    def tool_call(self, name: str, params: Dict) -> None:
        self._out(f"[{self._ts()}] [TOOL CALL] {name}: {json.dumps(params, ensure_ascii=False)[:300]}")

    def tool_result(self, name: str, result: Dict) -> None:
        ok = "✓" if (result.get("ok") or result.get("success")) else "✗"
        self._out(f"[{self._ts()}] [TOOL RESULT] {ok} {name}: {json.dumps(result, ensure_ascii=False)[:400]}")

    def think(self, text: str) -> None:
        self._out(f"[{self._ts()}] [THINK] {text[:300].replace(chr(10), '↵')}")

    def progress(self, text: str) -> None:
        self._out(f"[{self._ts()}] [PROGRESS] {text}")

    def info(self, text: str) -> None:
        self._out(f"[{self._ts()}] [INFO] {text}")

    def error(self, text: str) -> None:
        self._out(f"[{self._ts()}] [ERROR] {text}", stderr=True)

    def close(self) -> None:
        if self._fh:
            self._fh.close()


# ---------------------------------------------------------------------------
# NVIDIA API Client
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# NVIDIA API Client — with full C05-style error classification
# ---------------------------------------------------------------------------

class NvidiaClient:
    def __init__(self, scheduler: KeyStressScheduler, logger: AgentLogger):
        self.scheduler = scheduler
        self.logger = logger

    async def chat(self, messages: List[Dict], tools: Optional[List] = None) -> Dict:
        """
        Send a chat request with full C05-style error handling:
        - AUTH errors → mark key exhausted, try next key
        - RATE_LIMIT (permanent quota) → mark key exhausted, try next key
        - TEMP_RATE (transient 429) → cooldown key, try next key
        - SERVER_BUSY (503 ResourceExhausted) → exponential backoff, retry same request
        - TOO_LARGE → raise immediately (not a key problem)
        - CONNECTION → rollback key, retry up to 3 times
        - UNKNOWN → raise
        """
        from aibasedversionupgrader.key_scheduler import ErrorKind

        payload = {
            "model": MODEL,
            "messages": messages,
            "max_tokens": MAX_TOKENS,
            "stream": False,
        }
        # Enable thinking effort for models that support it (nemotron)
        if THINKING_EFFORT:
            payload["thinking"] = {"effort": THINKING_EFFORT}
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        # 503 retry loop (server busy — not a key problem)
        for busy_attempt in range(MAX_503_RETRIES):
            key = await self.scheduler.acquire()
            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                NVIDIA_API_URL, data=data,
                headers={
                    "Authorization": f"Bearer {key}",
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=300) as resp:
                    body = json.loads(resp.read().decode("utf-8"))
                await self.scheduler.release(key)
                return body["choices"][0]["message"]

            except urllib.error.HTTPError as e:
                err_text = ""
                try:
                    err_text = e.read().decode("utf-8", errors="replace")
                except Exception:
                    err_text = str(e)
                full_text = f"HTTP {e.code}: {err_text}"
                kind, cooldown = ErrorKind.classify(full_text)

                if kind == ErrorKind.SERVER_BUSY:
                    await self.scheduler.release(key)
                    backoff = min(_503_BASE_BACKOFF * (2 ** busy_attempt), _503_MAX_BACKOFF)
                    self.logger.error(
                        f"503 ResourceExhausted (attempt {busy_attempt+1}/{MAX_503_RETRIES}), "
                        f"retrying in {backoff:.1f}s..."
                    )
                    await asyncio.sleep(backoff)
                    continue  # retry with a fresh key

                elif kind == ErrorKind.AUTH:
                    await self.scheduler.mark_exhausted(key)
                    self.logger.error(f"Key exhausted (auth error). Trying next key...")
                    if self.scheduler.all_exhausted():
                        raise RuntimeError("All NVIDIA API keys are exhausted (auth errors).")
                    return await self.chat(messages, tools)  # recurse with next key

                elif kind == ErrorKind.RATE_LIMIT:
                    await self.scheduler.mark_exhausted(key)
                    self.logger.error(f"Key exhausted (quota exceeded). Trying next key...")
                    if self.scheduler.all_exhausted():
                        raise RuntimeError("All NVIDIA API keys have hit their quota.")
                    return await self.chat(messages, tools)

                elif kind == ErrorKind.TEMP_RATE:
                    await self.scheduler.mark_cooldown(key, cooldown)
                    self.logger.error(
                        f"Transient rate limit on key, cooling for {cooldown}s. Trying next key..."
                    )
                    return await self.chat(messages, tools)

                elif kind == ErrorKind.TOO_LARGE:
                    await self.scheduler.release(key)
                    raise RuntimeError(f"Request too large for NVIDIA API: {err_text[:200]}")

                else:
                    await self.scheduler.release(key)
                    raise RuntimeError(f"NVIDIA API error: {full_text[:400]}")

            except urllib.error.URLError as e:
                # Network/connection error — rollback (don't count against key's rate limit)
                await self.scheduler.rollback(key)
                self.logger.error(f"Connection error: {e}. Retrying...")
                await asyncio.sleep(1.0)
                return await self.chat(messages, tools)

            except TimeoutError as e:
                # Read timeout — rollback key (not its fault), retry with backoff
                await self.scheduler.rollback(key)
                backoff = min(_503_BASE_BACKOFF * (2 ** busy_attempt), _503_MAX_BACKOFF)
                self.logger.error(
                    f"API read timeout (attempt {busy_attempt+1}/{MAX_503_RETRIES}), "
                    f"retrying in {backoff:.1f}s..."
                )
                await asyncio.sleep(backoff)
                continue  # retry in the 503 loop

            except Exception as e:
                await self.scheduler.release(key)
                raise

        raise RuntimeError(f"NVIDIA API returned 503 ResourceExhausted after {MAX_503_RETRIES} retries.")


# ---------------------------------------------------------------------------
# Tool call parsing
# ---------------------------------------------------------------------------

_XML_TC_RE = re.compile(
    r"<tool_call>\s*<name>(.*?)</name>\s*<arguments>(.*?)</arguments>\s*</tool_call>",
    re.DOTALL,
)

def _parse_xml_tool_calls(text: str) -> List[Dict]:
    calls = []
    for m in _XML_TC_RE.finditer(text):
        name = m.group(1).strip()
        raw = m.group(2).strip()
        try:
            args = json.loads(raw)
        except Exception:
            jm = re.search(r"\{.*\}", raw, re.DOTALL)
            args = json.loads(jm.group()) if jm else {"raw": raw}
        calls.append({"name": name, "arguments": args})
    return calls

def extract_tool_calls(msg: Dict) -> List[Tuple[str, Dict, Optional[str]]]:
    calls = []
    for tc in msg.get("tool_calls", []) or []:
        fn = tc.get("function", {})
        try:
            args = json.loads(fn.get("arguments", "{}"))
        except Exception:
            args = {"raw": fn.get("arguments", "")}
        calls.append((fn.get("name", ""), args, tc.get("id")))
    for xc in _parse_xml_tool_calls(msg.get("content", "") or ""):
        calls.append((xc["name"], xc["arguments"], None))
    return calls


def _sanitize_tool_params(params: Dict) -> Dict:
    """
    Strip XML parameter injection that the model sometimes produces.
    e.g. op="search\n<parameter=pattern>\nSnow" → op="search"
    Also strips leading/trailing whitespace from all string values.
    """
    _XML_TAG_RE = re.compile(r"<[^>]+>.*", re.DOTALL)
    _LEADING_NEWLINE_RE = re.compile(r"^\s+")

    cleaned = {}
    for key, val in params.items():
        if not isinstance(val, str):
            cleaned[key] = val
            continue
        # If value contains an XML tag, take only the text before the first tag
        if "<" in val and ">" in val:
            # Strip everything from the first < onwards
            before_xml = val[:val.index("<")].strip()
            if before_xml:
                cleaned[key] = before_xml
            else:
                # The whole value is XML — strip all tags and take remaining text
                stripped = re.sub(r"<[^>]+>", "", val).strip()
                cleaned[key] = stripped
        else:
            cleaned[key] = val.strip()
    return cleaned


def _tool_ls_tree(rel_path: str, sandbox_dir: Path) -> Dict:
    """
    Show a depth-2 tree of the sandbox (or a subdirectory).
    Directories are shown collapsed with their immediate children listed.
    This avoids flooding the context with hundreds of template license files.
    """
    sandbox_resolved = sandbox_dir.resolve()
    if rel_path:
        root = (sandbox_dir / rel_path).resolve()
        try:
            root.relative_to(sandbox_resolved)
        except ValueError:
            return {"error": f"Path escape attempt: {rel_path!r}"}
    else:
        root = sandbox_resolved

    if not root.exists():
        return {"error": f"Path not found: {rel_path or '(sandbox root)'}"}

    entries = []
    try:
        for item in sorted(root.iterdir()):
            rel = str(item.resolve().relative_to(sandbox_resolved))
            if item.is_dir():
                entries.append(rel + "/")
                # Show immediate children (depth 1 inside this dir)
                try:
                    children = sorted(item.iterdir())
                    shown = 0
                    for child in children:
                        child_rel = str(child.resolve().relative_to(sandbox_resolved))
                        if child.is_dir():
                            entries.append("  " + child_rel + "/")
                        else:
                            entries.append("  " + child_rel)
                        shown += 1
                        if shown >= 20:
                            remaining = len(children) - shown
                            if remaining > 0:
                                entries.append(f"  ... ({remaining} more items)")
                            break
                except PermissionError:
                    pass
            else:
                entries.append(rel)
    except PermissionError:
        return {"error": f"Permission denied: {rel_path}"}

    return {"ok": True, "path": rel_path or ".", "entries": entries, "count": len(entries)}


# ---------------------------------------------------------------------------
# Main Agent
# ---------------------------------------------------------------------------

class ModVersionConverterAgent:
    def __init__(self, config: AgentConfig):
        self.config = config
        self.session_id = uuid.uuid4().hex[:8]
        self.session_dir = config.sessions_base_dir / self.session_id
        self.session_dir.mkdir(parents=True, exist_ok=True)
        # State files live inside the session dir
        self.memory_file = self.session_dir / "session_memory.json"
        self.todos_file  = self.session_dir / "session_todos.json"
        self.state_file  = self.session_dir / "session_state.json"
        self.artifact_dir = self.session_dir / "artifacts"
        self.artifact_dir.mkdir(parents=True, exist_ok=True)
        self.logger = AgentLogger(
            config.is_local,
            self.session_dir / "agent_log.txt" if config.is_local else config.log_file,
        )
        self.scheduler = KeyStressScheduler(config.nvidia_keys)
        self.client = NvidiaClient(self.scheduler, self.logger)
        self.manifest = self._load_manifest()
        self.success_list: List[Dict] = []

    # ── helpers ──────────────────────────────────────────────────────────────

    def _load_manifest(self) -> Dict:
        p = self.config.repo_root / "version-manifest.json"
        return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {"ranges": []}

    def _load_metadata(self) -> Dict:
        """Load the pre-generated metadata.json from the session dir."""
        meta_path = self.session_dir / "metadata.json"
        if meta_path.exists():
            try:
                return json.loads(meta_path.read_text(encoding="utf-8"))
            except Exception:
                pass
        return {}

    def _load_target_list(self) -> List[Dict]:
        """Load target_list.json from the session dir."""
        tl_path = self.session_dir / "target_list.json"
        if tl_path.exists():
            try:
                return json.loads(tl_path.read_text(encoding="utf-8"))
            except Exception:
                pass
        return []

    def _get_slug(self) -> str:
        """Get the mod slug from metadata or project.json."""
        meta = self._load_metadata()
        if meta.get("mod_id"):
            return meta["mod_id"]
        proj_path = self.session_dir / "project_info" / "project.json"
        if proj_path.exists():
            try:
                proj = json.loads(proj_path.read_text(encoding="utf-8"))
                return proj.get("slug", "mod")
            except Exception:
                pass
        return "mod"

    def _session_path(self, rel: str) -> Path:
        """Resolve a path that must stay inside the session dir."""
        resolved = (self.session_dir / rel).resolve()
        try:
            resolved.relative_to(self.session_dir.resolve())
        except ValueError:
            raise ValueError(f"Path escape blocked: {rel!r}")
        return resolved

    def _repo_path(self, rel: str) -> Path:
        """Resolve a read-only repo path."""
        resolved = (self.config.repo_root / rel).resolve()
        try:
            resolved.relative_to(self.config.repo_root.resolve())
        except ValueError:
            raise ValueError(f"Repo path escape blocked: {rel!r}")
        return resolved

    # ── tool dispatcher ───────────────────────────────────────────────────────

    def _dispatch(self, name: str, params: Dict) -> Dict:
        sd = self.session_dir
        rr = self.config.repo_root

        # ── Unwrap "parameter" envelope ──────────────────────────────────────
        # nemotron-3-nano sometimes wraps tool arguments as a JSON string inside
        # a single key called "parameter" instead of passing them flat.
        # e.g. {"parameter": "{\"todos\": [...]}"} instead of {"todos": [...]}
        # Detect and unwrap this before dispatching.
        if len(params) == 1 and "parameter" in params:
            raw = params["parameter"]
            if isinstance(raw, str):
                try:
                    unwrapped = json.loads(raw)
                    if isinstance(unwrapped, dict):
                        params = unwrapped
                except Exception:
                    pass  # leave params as-is if JSON parse fails

        # ── unified ls ──────────────────────────────────────────────────────
        if name == "ls":
            raw_path = params.get("path", "") or ""
            if raw_path.startswith("repo:"):
                repo_rel = raw_path[5:]
                return T.tool_outside_sandbox_ls({"path": repo_rel}, rr)
            else:
                result = _tool_ls_tree(raw_path, sd)
                if not raw_path:
                    # Append repo template listing at root level
                    repo_entries = []
                    for d in sorted(rr.iterdir()):
                        if d.is_dir() and re.match(r"^\d", d.name):
                            repo_entries.append(f"[REPO READ-ONLY] {d.name}/")
                    if repo_entries:
                        result["repo_templates"] = repo_entries
                        result["note"] = "Prefix path with 'repo:' to browse repo templates. E.g. ls('repo:1.21.2-1.21.8/fabric/template')"
                return result

        # ── unified read ────────────────────────────────────────────────────
        elif name == "read":
            raw_path = params.get("path", "")
            if raw_path.startswith("repo:"):
                return T.tool_read_outside_sandbox({"path": raw_path[5:]}, rr)
            else:
                return T.tool_file_read({"path": raw_path}, sd)

        # ── write / edit / patch / delete / move ────────────────────────────
        elif name == "write":
            return T.tool_file_write(params, sd)
        elif name == "edit":
            return T.tool_file_edit(params, sd)
        elif name == "multiedit":
            return T.tool_multi_edit(params, sd)
        elif name == "patch":
            return T.tool_apply_patch(params, sd)
        elif name == "delete":
            return T.tool_file_delete(params, sd)
        elif name == "move":
            return T.tool_file_move(params, sd)

        # ── grep / glob ──────────────────────────────────────────────────────
        elif name == "grep":
            root = params.get("root", "session")
            mapped = dict(params)
            mapped["root"] = "sandbox" if root == "session" else "repo"
            return T.tool_grep_search(mapped, sd, rr)
        elif name == "glob":
            root = params.get("root", "session")
            mapped = dict(params)
            mapped["root"] = "sandbox" if root == "session" else "repo"
            return T.tool_glob_find(mapped, sd, rr)

        # ── cmd — restricted to session dir ─────────────────────────────────
        elif name == "cmd":
            command = params.get("command", "")
            # Block cd — the cmd tool always runs in the session dir
            if re.match(r"^\s*cd\b", command):
                return {"error": "CMD does not support 'cd'. The command always runs in your session directory. "
                                 "Use the 'workdir' parameter or relative paths instead. "
                                 "To run Gradle, use the compile tool instead of running gradlew directly."}
            # Block any attempt to reference paths outside the session dir
            dangerous = ["../", "../../", "/etc/", "/usr/", "/bin/", "/var/",
                         "/home/", "/root/", "/tmp/", "~", "$HOME"]
            session_abs = str(self.session_dir.resolve())
            for d in dangerous:
                if d in command and session_abs not in command:
                    return {"error": f"CMD blocked: command references path outside session directory. Use relative paths only."}
            return T.tool_cmd(params, sd)

        # ── compile ──────────────────────────────────────────────────────────
        elif name == "compile":
            # Pass the session_dir as sandbox so compile can find metadata.json
            # and create build/<mc>-<loader>/ correctly
            return T.tool_compile_bundle(params, self.session_dir, rr, self.manifest)

        # ── diagnostics ──────────────────────────────────────────────────────
        elif name == "diagnostics":
            return T.tool_get_diagnostics(params)

        # ── dif ──────────────────────────────────────────────────────────────
        elif name == "dif":
            return T.tool_diff_system_access(params, rr)

        # ── mcsource ─────────────────────────────────────────────────────────
        elif name == "mcsource":
            # Sanitize params — strip XML tags that the model sometimes injects
            params = _sanitize_tool_params(params)
            return T.tool_minecraft_source_access(params, rr)

        # ── web ──────────────────────────────────────────────────────────────
        elif name == "websearch":
            return T.tool_web_search(params)
        elif name == "webfetch":
            return T.tool_web_fetch(params)

        # ── agent state ──────────────────────────────────────────────────────
        elif name == "memory":
            return T.tool_session_memory(params, self.memory_file)
        elif name == "think":
            self.logger.think(params.get("reasoning", ""))
            return {"ok": True}
        elif name == "progress":
            msg = params.get("message", "")
            self.logger.progress(msg)
            return T.tool_report_progress(params, self.config.summary_file, not self.config.is_local)
        elif name == "stage_artifact":
            return T.tool_artifact_upload(params, self.session_dir, self.artifact_dir)
        elif name == "todo":
            return T.tool_todo_write(params, self.todos_file)
        elif name == "batch":
            return T.tool_batch_tool_call(params, self._dispatch)
        elif name == "build_bundle":
            meta = self._load_metadata()
            target_list = self._load_target_list()
            slug = self._get_slug()
            return T.tool_build_bundle(
                params, self.session_dir, self.config.repo_root,
                meta, target_list, slug, self.session_id,
            )
        elif name == "trigger_build":
            # Auto-create bundle zip if it doesn't exist yet (agent skipped build_bundle)
            incoming_dir = self.config.repo_root / "incoming"
            existing_zips = list(incoming_dir.glob(f"*{self.session_id}*.zip")) if incoming_dir.exists() else []
            if not existing_zips:
                self.logger.info("trigger_build called without prior build_bundle — auto-creating bundle zip...")
                meta = self._load_metadata()
                target_list_auto = self._load_target_list()
                slug = self._get_slug()
                bundle_result = T.tool_build_bundle(
                    {}, self.session_dir, self.config.repo_root,
                    meta, target_list_auto, slug, self.session_id,
                )
                if not bundle_result.get("ok"):
                    return {"error": f"Auto build_bundle failed: {bundle_result.get('error', '?')}. "
                                     "Call build_bundle() manually first.", "ok": False}
            return T.tool_trigger_build(
                params, self.session_dir, self.config.repo_root,
                self._get_slug(), self.session_id,
            )

        # ── legacy aliases (in case model uses old names) ────────────────────
        elif name in ("File_Write", "File_Edit", "MultiEdit", "ApplyPatch",
                      "File_Read", "sandboxls", "GlobFind", "GrepSearch",
                      "FileDelete", "FileMove", "CMD", "CompileBundle",
                      "GetDiagnostics", "LSPDiagnostics", "Outside_Sandbox_LS",
                      "Read_Outside_Sandbox", "DiffSystemAccess",
                      "MinecraftSourceCodeAccess", "WebSearch", "WebFetch",
                      "SessionMemory", "ThinkAloud", "ReportProgress",
                      "ArtifactUpload", "TodoWrite", "BatchToolCall"):
            alias_map = {
                "File_Write": "write", "File_Edit": "edit", "MultiEdit": "multiedit",
                "ApplyPatch": "patch", "File_Read": "read", "sandboxls": "ls",
                "GlobFind": "glob", "GrepSearch": "grep", "FileDelete": "delete",
                "FileMove": "move", "CMD": "cmd", "CompileBundle": "compile",
                "GetDiagnostics": "diagnostics", "LSPDiagnostics": "diagnostics",
                "Outside_Sandbox_LS": "ls", "Read_Outside_Sandbox": "read",
                "DiffSystemAccess": "dif", "MinecraftSourceCodeAccess": "mcsource",
                "WebSearch": "websearch", "WebFetch": "webfetch",
                "SessionMemory": "memory", "ThinkAloud": "think",
                "ReportProgress": "progress", "ArtifactUpload": "stage_artifact",
                "TodoWrite": "todo", "BatchToolCall": "batch",
            }
            new_name = alias_map.get(name, name)
            # Fix param names for legacy callers
            if name == "Outside_Sandbox_LS":
                params = {"path": "repo:" + params.get("path", "")}
            elif name == "Read_Outside_Sandbox":
                params = {"path": "repo:" + params.get("path", "")}
            elif name == "GrepSearch":
                params = dict(params)
                if params.get("root") == "sandbox":
                    params["root"] = "session"
            return self._dispatch(new_name, params)

        else:
            return {"error": f"Unknown tool: {name!r}. Available tools: ls, read, write, edit, multiedit, patch, delete, move, grep, glob, cmd, compile, diagnostics, dif, mcsource, websearch, webfetch, memory, think, progress, stage_artifact, todo, batch"}


    # ── session setup ─────────────────────────────────────────────────────────

    def _setup_session(self, target_list: List[Dict]) -> None:
        """Copy project_info and target_list into the session dir, pre-generate metadata.json,
        and pre-create the bundle directory structure for all targets."""
        # Copy project_info/
        src_pi = self.config.project_info_dir
        dst_pi = self.session_dir / "project_info"
        if src_pi.exists() and not dst_pi.exists():
            shutil.copytree(str(src_pi), str(dst_pi))

        # Copy target_list.json
        src_tl = self.config.target_list_path
        dst_tl = self.session_dir / "target_list.json"
        if src_tl.exists() and not dst_tl.exists():
            shutil.copy2(str(src_tl), str(dst_tl))

        # Pre-generate metadata.json so compile works immediately
        metadata_path = self.session_dir / "metadata.json"
        if not metadata_path.exists():
            try:
                from aibasedversionupgrader.metadata_builder import generate_metadata_json
                generate_metadata_json(dst_pi, metadata_path)
                self.logger.info(f"Pre-generated metadata.json: {metadata_path}")
            except Exception as e:
                self.logger.error(f"Could not pre-generate metadata.json: {e}")

        # Pre-create bundle directory structure for all targets
        # Copy the template ExampleMod.java into each target's bundle dir so the AI
        # can see the exact file it needs to replace
        self._prebuild_bundle_scaffold(target_list)

        # Write a README
        readme = self.session_dir / "README.txt"
        readme.write_text(
            f"Session ID: {self.session_id}\n"
            f"Started: {datetime.now().isoformat()}\n\n"
            "Contents of this session directory:\n"
            "  project_info/          - Modrinth project metadata + first version source\n"
            "  target_list.json       - List of version/loader combinations to build\n"
            "  metadata.json          - Mod metadata for build_adapter.py (pre-generated)\n"
            "  bundle/                - Pre-scaffolded bundle dirs (one per target)\n"
            "    <mc>-<loader>/       - e.g. bundle/1.8.9-forge/\n"
            "      src/main/java/...  - REPLACE the ExampleMod.java here with your mod source\n"
            "      src/main/resources/- Resource files (mcmod.info, mods.toml, etc.)\n"
            "  build/<mc>-<loader>/   - Build workspace per target (created by 'compile')\n"
            "  artifacts/             - Successfully compiled jars\n\n"
            "WORKFLOW:\n"
            "  1. For each target in bundle/, replace ExampleMod.java with your mod source\n"
            "  2. Call build_bundle() to create the zip\n"
            "  3. Call trigger_build() to compile via GitHub Actions\n\n"
            "To browse repo templates (read-only):\n"
            "  ls('repo:1.21.2-1.21.8/fabric/template')\n",
            encoding="utf-8",
        )

    def _prebuild_bundle_scaffold(self, target_list: List[Dict]) -> None:
        """
        Pre-create bundle/<mc>-<loader>/ directories for all targets.
        Copies the template's ExampleMod.java and resource files so the AI
        can see exactly what it needs to replace.
        """
        manifest = self.manifest
        bundle_dir = self.session_dir / "bundle"

        for target in target_list:
            mc_version = target["minecraft_version"]
            loader = target["loader"]
            range_folder = target.get("range_folder", "")
            target_dir = bundle_dir / f"{mc_version}-{loader}"

            if target_dir.exists():
                continue  # already scaffolded

            # Find the template dir
            if not range_folder:
                from aibasedversionupgrader.tools import _find_range_for_version
                rng = _find_range_for_version(manifest, mc_version, loader)
                if rng:
                    range_folder = rng["folder"]

            if not range_folder:
                continue

            loader_cfg = manifest.get("ranges", [])
            template_dir = None
            for rng in manifest.get("ranges", []):
                if rng["folder"] == range_folder and loader in rng.get("loaders", {}):
                    template_dir = self.config.repo_root / rng["loaders"][loader]["template_dir"]
                    break

            if not template_dir or not template_dir.exists():
                continue

            # Copy only src/ from the template (not the full Gradle setup)
            src_dir = template_dir / "src"
            if src_dir.exists():
                target_src = target_dir / "src"
                try:
                    shutil.copytree(str(src_dir), str(target_src))
                except Exception:
                    pass

        if bundle_dir.exists():
            count = sum(1 for d in bundle_dir.iterdir() if d.is_dir())
            self.logger.info(f"Pre-scaffolded {count} bundle directories in {bundle_dir}")

    def _build_initial_context(self, target_list: List[Dict]) -> str:
        """Build the initial user message. The warmup history handles exploration,
        so this just needs to provide project context and the target list."""
        summary_path = self.session_dir / "project_info" / "summary.txt"
        project_json_path = self.session_dir / "project_info" / "project.json"

        project_info = ""
        if project_json_path.exists():
            try:
                proj = json.loads(project_json_path.read_text(encoding="utf-8"))
                project_info = (
                    f"Title: {proj.get('title', '?')}\n"
                    f"Slug: {proj.get('slug', '?')}\n"
                    f"Description: {proj.get('description', '')[:300]}\n"
                    f"Loaders: {proj.get('loaders', [])}\n"
                    f"Client side: {proj.get('client_side', '?')} | Server side: {proj.get('server_side', '?')}\n"
                )
            except Exception:
                pass
        if summary_path.exists():
            project_info += summary_path.read_text(encoding="utf-8")[:400]

        # Pre-generated metadata
        metadata_path = self.session_dir / "metadata.json"
        metadata_info = ""
        meta: Dict = {}
        if metadata_path.exists():
            try:
                meta = json.loads(metadata_path.read_text(encoding="utf-8"))
                metadata_info = (
                    f"mod_id: {meta.get('mod_id')} | group: {meta.get('group')}\n"
                    f"entrypoint_class: {meta.get('entrypoint_class')}\n"
                    f"runtime_side: {meta.get('runtime_side')} | authors: {meta.get('authors')}\n"
                )
            except Exception:
                pass

        # Decompiled source — prefer real Java (.java from CFR), fall back to javap .txt
        decompiled_dir = self.session_dir / "project_info" / "first_version" / "decompiled"
        decompiled_content = ""
        if decompiled_dir.exists():
            source_files = sorted(decompiled_dir.glob("*.java")) or sorted(decompiled_dir.glob("*.txt"))
            for f in source_files[:6]:
                try:
                    # Use more characters for real Java source (it's more useful)
                    limit = 3000 if f.suffix == ".java" else 600
                    decompiled_content += f"\n### {f.name}\n```java\n{f.read_text(encoding='utf-8')[:limit]}\n```\n"
                except Exception:
                    pass

        targets_str = json.dumps(target_list, indent=2)

        # List available repo range folders
        range_folders = sorted(
            d.name for d in self.config.repo_root.iterdir()
            if d.is_dir() and re.match(r"^\d", d.name)
        )

        # List available Minecraft source versions
        mcsource_root = self.config.repo_root / "DecompiledMinecraftSourceCode"
        available_mcsource = sorted(d.name for d in mcsource_root.iterdir() if d.is_dir()) if mcsource_root.exists() else []

        pkg = meta.get("group", "com.example.mod").replace(".", "/")
        slug = self._get_slug()
        
        # Derive correct PascalCase class name from entrypoint
        entrypoint = meta.get("entrypoint_class", "")
        if entrypoint:
            main_class_name = entrypoint.split(".")[-1]
        else:
            mod_id_raw = meta.get("mod_id", "mod")
            main_class_name = "".join(w.capitalize() for w in re.split(r"[-_]", mod_id_raw)) + "Mod"

        return f"""You are starting a mod porting session. Session ID: `{self.session_id}`

## Project
{project_info}

## Pre-Generated Metadata (metadata.json is ready in your session dir)
{metadata_info}
**CRITICAL: The Java package path is ALWAYS `{pkg}` — copy this EXACTLY into every file path and every `package` declaration.**
**CRITICAL: The main class name is `{main_class_name}` — use this EXACT capitalisation. Java filenames must match class names.**

## Package & Author Standard (from docs/PACKAGE_NAMING_STANDARD.md)
- **Author:** `Itamio` — use in ALL resource files (authorList, authors field)
- **Package prefix:** `asd.itamio.<modname>` — ALL mods use this, no exceptions
- **This mod's package:** `{pkg}` (already set in metadata.json)
- **Main class:** `{main_class_name}` — file must be `{main_class_name}.java`
- ❌ NEVER use `com.example`, `com.itamio`, `net.itamio`, or any other prefix
- ❌ NEVER use `["ExampleDude"]`, `["YourName"]`, or any placeholder author
- ❌ NEVER get class name capitalisation wrong — Java filenames must match the class name exactly
  - e.g. if the class is `{main_class_name}`, the file must be `{main_class_name}.java`
  - A common mistake: `{main_class_name[0].lower() + main_class_name[1:] if main_class_name else "myMod"}.java` is WRONG — capitalisation must match exactly

## Decompiled Source Code (real Java — use this as your implementation reference)
This is the ACTUAL source code of the original mod, decompiled with CFR.
Port this logic to each target version — adapt the API calls for each loader/version.
Do NOT write stubs. The logic is right here — implement it.
{decompiled_content}

## Targets to Build ({len(target_list)} total)
```json
{targets_str}
```

## ═══════════════════════════════════════════════════════════════════════════
## CRITICAL WORKFLOW — READ THIS SECTION CAREFULLY
## ═══════════════════════════════════════════════════════════════════════════

### ⚠️ HARD RULES — VIOLATING THESE IS A PROTOCOL ERROR

1. **DO NOT EXPLORE** — You already have ALL context you need from the warmup above
   - DO NOT call `ls()` to check if files exist — just write them
   - DO NOT call `read()` on templates — you already saw them in warmup
   - DO NOT call `mcsource()` or `dif()` unless you hit a specific compilation error
   - DO NOT call `mkdir` via `cmd` — the `write` tool creates directories automatically

2. **USE BATCH() FOR ALL WRITES — FILL EACH BATCH TO 25** — This is MANDATORY
   - Your FIRST action MUST be a `batch()` call writing ALL {len(target_list)} targets
   - Each target needs 4 files (3 Java + 1 resource) = {len(target_list) * 4} files total
   - batch() accepts up to 25 calls — use ALL 25 slots every time
   - 4 files per target × 6 targets = 24 writes per batch — do 6 targets per batch
   - ❌ WRONG: batch([4 writes]) → batch([4 writes]) → ... (wastes 21 slots each time)
   - ✅ RIGHT: batch([24 writes]) → batch([24 writes]) → ... ({(len(target_list) * 4 + 23) // 24} batches total)

3. **WRITE REAL IMPLEMENTATIONS** — Stubs will be rejected
   - The write tool will warn you if your file is too short or contains placeholder comments
   - Implement the ACTUAL logic from the decompiled bytecode above
   - Handler must implement real tick-based snow accumulation logic
   - ConfigManager must implement real file-based config loading/saving

4. **PROGRESS IS SHOWN AFTER EVERY TOOL CALL** — You do not need to check it manually
   - After each tool call you will see the full todo list showing which tasks are done
   - Update the todo list AFTER completing work, not before starting it
   - After writing all files for a batch of targets: `todo(todos=[{{"id": "N", "status": "completed"}}, ...])`
   - You can update multiple items at once: `todo(todos=[{{"id": "3", "status": "completed"}}, {{"id": "4", "status": "completed"}}])`
   - Do NOT call todo() before writing — write first, then update todos

### Resource File Format — EXACT RULES (do not mix these up)

| Version range | Loader | Resource file path | Format |
|---|---|---|---|
| 1.8.9, 1.12.x | forge | `bundle/X-forge/src/main/resources/mcmod.info` | JSON array |
| 1.16.5 – 1.20.1 | forge | `bundle/X-forge/src/main/resources/META-INF/mods.toml` | TOML |
| 1.20.2 – 1.20.4 | neoforge | `bundle/X-neoforge/src/main/resources/META-INF/mods.toml` | TOML |
| 1.20.5+ | neoforge | `bundle/X-neoforge/src/main/resources/META-INF/neoforge.mods.toml` | TOML |
| 26.x | neoforge | `bundle/X-neoforge/src/main/resources/META-INF/neoforge.mods.toml` | TOML |
| any | fabric | `bundle/X-fabric/src/main/resources/fabric.mod.json` | JSON |

**DO NOT write mcmod.info for NeoForge or modern Forge — it will be ignored.**

### Loader API Reference — EXACT PATTERNS

**1.8.9 / 1.12.2 Forge:**
```java
@Mod(modid = "modid", name = "Mod Name", version = "1.0.0")
public class MyMod {{
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {{
        MinecraftForge.EVENT_BUS.register(new MyHandler());
    }}
}}
```

**1.16.5 – 1.19.x Forge:**
```java
@Mod("modid")
public class MyMod {{
    public MyMod() {{
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new MyHandler());
    }}
    private void setup(FMLCommonSetupEvent e) {{}}
}}
```

**1.20.x Forge:**
```java
@Mod("modid")
public class MyMod {{
    public MyMod(IEventBus modEventBus) {{
        modEventBus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new MyHandler());
    }}
    private void setup(FMLCommonSetupEvent e) {{}}
}}
```

**NeoForge (1.20.2+, 26.x):**
```java
@Mod("modid")
public class MyMod {{
    public MyMod(IEventBus modEventBus) {{
        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(new MyHandler());
    }}
    private void setup(FMLCommonSetupEvent e) {{}}
}}
// Handler uses @SubscribeEvent on NeoForge.EVENT_BUS
// Import: net.neoforged.neoforge.common.NeoForge
// Import: net.neoforged.bus.api.IEventBus
// Import: net.neoforged.fml.common.Mod
// DO NOT import FMLPreInitializationEvent — it does not exist in NeoForge
```

**Fabric:**
```java
public class MyMod implements ModInitializer {{
    @Override
    public void onInitialize() {{
        ServerTickEvents.END_SERVER_TICK.register(server -> handler.onServerTick(server));
    }}
}}
```

### Valid mods.toml (Forge 1.16.5+):
```toml
modLoader="javafml"
loaderVersion="[36,)"
license="MIT"
[[mods]]
modId="modid"
version="1.0.0"
displayName="Mod Name"
description="Mod description"
[[dependencies.modid]]
    modId="forge"
    mandatory=true
    versionRange="[36,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.modid]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.16.5,1.17)"
    ordering="NONE"
    side="BOTH"
```

### Valid neoforge.mods.toml (NeoForge 1.20.5+):
```toml
modLoader="javafml"
loaderVersion="[1,)"
license="MIT"
[[mods]]
modId="modid"
version="1.0.0"
displayName="Mod Name"
description="Mod description"
[[dependencies.modid]]
    modId="neoforge"
    type="required"
    versionRange="[21,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.modid]]
    modId="minecraft"
    type="required"
    versionRange="[1.20.5,)"
    ordering="NONE"
    side="BOTH"
```

### Step-by-Step Workflow

**Step 1: Write ALL source files using batch() — 6 targets per batch (24 writes)**

Package path: `{pkg}` | Main class: `{main_class_name}`

For each target, write these 4 files:
- `bundle/{{mc}}-{{loader}}/src/main/java/{pkg}/{main_class_name}.java`
- `bundle/{{mc}}-{{loader}}/src/main/java/{pkg}/SnowAccumulationHandler.java`
- `bundle/{{mc}}-{{loader}}/src/main/java/{pkg}/ConfigManager.java`
- Resource file (see table above)

**Step 2: Call build_bundle()**

**Step 3: Call trigger_build()**
```
trigger_build(modrinth_url="https://modrinth.com/mod/{slug}")
```

**Step 4: Fix failures and rebuild**
```
build_bundle(failed_only=True, previous_results=<results>)
trigger_build()
```

## Available Minecraft Source Code (for API lookups ONLY when you hit a compile error)

**Known limitation:** Some versions (like 1.16.5-forge) only contain Forge-specific source,
not vanilla Minecraft classes. If mcsource() returns empty results, use websearch instead.

Available versions:
{chr(10).join("  " + v for v in available_mcsource) if available_mcsource else "  (none available — use websearch instead)"}

## Available Repo Templates (read-only)
{chr(10).join("  repo:" + f + "/" for f in range_folders)}

## Final Reminders
- ✅ START with batch() — write ALL {len(target_list)} targets immediately
- ✅ Fill each batch to 25 writes (6 targets × 4 files = 24 per batch)
- ✅ Package path: `{pkg}` — copy exactly, no typos
- ✅ Main class: `{main_class_name}` — exact capitalisation, file must match
- ✅ Implement REAL logic from the decompiled bytecode — no stubs
- ✅ Progress is shown after every tool call — no need to ls() to check
- ❌ DO NOT write files one at a time
- ❌ DO NOT write mcmod.info for NeoForge
- ❌ DO NOT import FMLPreInitializationEvent in NeoForge code
- ❌ DO NOT write stub handlers with `// Handle snow accumulation logic`
- ❌ NEVER use the `compile` tool — use build_bundle + trigger_build
"""

    def _save_state(self) -> None:
        state = {
            "session_id": self.session_id,
            "success_list": self.success_list,
            "timestamp": datetime.now().isoformat(),
        }
        self.state_file.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")

    def _build_warmup_history(self, target_list: List[Dict]) -> List[Dict]:
        """
        Pre-simulate workspace exploration so the AI starts with full context.
        
        This warmup pre-runs ALL the commands the agent would naturally explore,
        so it has zero reason to explore on its own. The warmup covers:
        1. ls . (session root)
        2. read metadata.json
        3. read target_list.json  
        4. ls project_info (structure)
        5. read all decompiled class summaries
        6. ls bundle (all pre-scaffolded targets)
        7. ls bundle/<first-target>/src/main (directory structure)
        8. read first bundle java file (template placeholder)
        9. read template example from repo
        10. read PACKAGE_NAMING_STANDARD.md
        
        Then shows a concrete batch() write example with CORRECT class name capitalisation.
        Ends with a user message containing the initial todo status.
        """
        if not target_list:
            return []

        first = target_list[0]
        loader = first["loader"]
        range_folder = first.get("range_folder", "")
        meta = self._load_metadata()
        package_path = meta.get("group", "com.example.mod").replace(".", "/")
        
        # Derive the correct PascalCase class name from the entrypoint
        entrypoint = meta.get("entrypoint_class", "")
        if entrypoint:
            main_class_name = entrypoint.split(".")[-1]
        else:
            mod_id = meta.get("mod_id", "mod")
            main_class_name = "".join(w.capitalize() for w in re.split(r"[-_]", mod_id)) + "Mod"

        warmup: List[Dict] = []

        # ── Build the comprehensive exploration batch ─────────────────────────
        # This covers EVERY command the agent ran in the previous run (iterations 1-8)
        # plus additional useful context, all in one parallel batch.
        batch_calls = []
        
        # 1. ls session root (agent ran this in iter 1)
        batch_calls.append({"tool": "ls", "parameters": {}})
        
        # 2. read metadata.json
        batch_calls.append({"tool": "read", "parameters": {"path": "metadata.json"}})
        
        # 3. read target_list.json (agent ran this in iter 2 and 8)
        batch_calls.append({"tool": "read", "parameters": {"path": "target_list.json"}})
        
        # 4. ls project_info (agent ran this in iter 3)
        batch_calls.append({"tool": "ls", "parameters": {"path": "project_info"}})
        
        # 5. ls bundle (agent ran this in iter 7)
        batch_calls.append({"tool": "ls", "parameters": {"path": "bundle"}})
        
        # 6. Read all decompiled class summaries (agent ran these in iters 4-6)
        # These are now real Java source files (CFR-decompiled), not javap bytecode
        decompiled_dir = self.session_dir / "project_info" / "first_version" / "decompiled"
        if decompiled_dir.exists():
            # Prefer .java files (CFR output), fall back to .txt (javap output)
            source_files = sorted(decompiled_dir.glob("*.java")) or sorted(decompiled_dir.glob("*.txt"))
            for src_file in source_files[:6]:
                rel = str(src_file.relative_to(self.session_dir))
                batch_calls.append({"tool": "read", "parameters": {"path": rel}})
        
        # 7. Read template example from repo
        if range_folder:
            template_dir = self.config.repo_root / range_folder / loader / "template"
            if template_dir.exists():
                for java_file in sorted(template_dir.rglob("*.java"))[:1]:
                    repo_rel = f"repo:{java_file.relative_to(self.config.repo_root)}"
                    batch_calls.append({"tool": "read", "parameters": {"path": repo_rel}})
        
        # 8. Read first bundle java file (the template placeholder to replace)
        bundle_dir = self.session_dir / "bundle"
        first_bundle_java_rel = None
        if bundle_dir.exists():
            first_target_dir = next((d for d in sorted(bundle_dir.iterdir()) if d.is_dir()), None)
            if first_target_dir:
                java_files = list(first_target_dir.rglob("*.java"))
                if java_files:
                    first_java = java_files[0]
                    first_bundle_java_rel = str(first_java.relative_to(self.session_dir))
                    batch_calls.append({"tool": "read", "parameters": {"path": first_bundle_java_rel}})
                # Also ls the first target's src/main to show directory structure
                first_src_main = first_target_dir / "src" / "main"
                if first_src_main.exists():
                    first_src_rel = str(first_src_main.relative_to(self.session_dir))
                    batch_calls.append({"tool": "ls", "parameters": {"path": first_src_rel}})
        
        # 9. Read the package naming standard
        pkg_standard_path = self.config.repo_root / "docs" / "PACKAGE_NAMING_STANDARD.md"
        if pkg_standard_path.exists():
            batch_calls.append({"tool": "read", "parameters": {"path": "repo:docs/PACKAGE_NAMING_STANDARD.md"}})

        warmup.append({
            "role": "assistant",
            "content": (
                "Let me explore the complete workspace structure in one batch() call "
                "to understand everything I need before writing source files."
            ),
            "tool_calls": [{
                "id": "warmup_batch_explore",
                "type": "function",
                "function": {"name": "batch", "arguments": json.dumps({"tool_calls": batch_calls})}
            }],
        })
        
        # Execute the batch and return results
        batch_result = self._dispatch("batch", {"tool_calls": batch_calls})
        warmup.append({
            "role": "tool",
            "tool_call_id": "warmup_batch_explore",
            "content": json.dumps(batch_result)
        })

        # ── Show a CONCRETE batch write example with CORRECT class names ──────
        # Use the actual entrypoint class name (PascalCase, correct capitalisation)
        example_targets = target_list[:3]
        example_batch_calls = []
        
        for t in example_targets:
            mc_v = t["minecraft_version"]
            ldr = t["loader"]
            # Use the CORRECT class name from metadata — not a lowercased guess
            example_batch_calls.append({
                "tool": "write",
                "parameters": {
                    "path": f"bundle/{mc_v}-{ldr}/src/main/java/{package_path}/{main_class_name}.java",
                    "content": f"package {meta.get('group', 'asd.itamio.mod')};\n\n// Full implementation here"
                }
            })
        
        example_json = json.dumps(example_batch_calls, indent=2)
        
        warmup.append({
            "role": "assistant",
            "content": f"""I now have complete context. Here's what I know:

**Mod:** {meta.get("name", "?")} | **ID:** {meta.get("mod_id", "?")} | **Package:** {meta.get("group", "?")}
**Main class:** `{main_class_name}` (from entrypoint: `{entrypoint}`)
**Package path:** `{package_path}`
**Targets:** {len(target_list)} total

⚠️ CRITICAL — Class name capitalisation:
Java requires the filename to exactly match the public class name — capitalisation included.
The entrypoint class for this mod is `{main_class_name}`, so the file must be `{main_class_name}.java`.

A common mistake is to flatten or lowercase the camel-case segments. For example:
- ❌ WRONG: `{main_class_name[0].lower() + main_class_name[1:] if main_class_name else "myMod"}.java` — first letter lowercased
- ✅ RIGHT: `{main_class_name}.java` — copy the class name from metadata.json exactly

The same rule applies to all classes: derive the filename from the actual class name, never guess or lowercase it.

Here's the EXACT batch() format for the first {len(example_targets)} targets:

```json
{{
  "tool_calls": {example_json}
}}
```

I will now write ALL {len(target_list)} source files using batch() calls, filling each batch to 25 writes.""",
        })

        # Final user trigger with initial todo status
        try:
            # Initialise the todo list with all targets + compile/post-compile phases
            initial_todos = T.build_initial_todo_list(target_list)
            self._dispatch("todo", {"todos": initial_todos, "replace": True})
            initial_todo = T.compute_todo_status(
                self.todos_file,
                self.session_dir / "bundle",
                target_list,
            )
            initial_footer = T.format_todo_footer(initial_todo)
        except Exception:
            initial_footer = ""

        warmup.append({
            "role": "user",
            "content": (
                f"Good. You have complete context — no more exploration needed. "
                f"Write ALL {len(target_list)} source files NOW using batch() calls. "
                f"Fill each batch to 25 writes (4 files per target × 6 targets = 24 writes per batch). "
                f"Class name: `{main_class_name}` — exact capitalisation required. "
                f"Package: `{package_path}`. "
                f"Implement REAL logic from the decompiled bytecode — no stubs."
                + initial_footer
            ),
        })
        
        return warmup


    # ── main loop ─────────────────────────────────────────────────────────────
    def _save_state(self) -> None:
        state = {
            "session_id": self.session_id,
            "success_list": self.success_list,
            "timestamp": datetime.now().isoformat(),
        }
        self.state_file.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")

    async def run(self) -> AgentResult:
        result = AgentResult(session_dir=self.session_dir)

        if not self.config.target_list_path.exists():
            result.error = f"target_list.json not found at {self.config.target_list_path}"
            return result

        target_list = json.loads(self.config.target_list_path.read_text(encoding="utf-8"))
        result.total_targets = len(target_list)

        if not target_list:
            self.logger.info("No targets — all versions already present.")
            result.error = "No targets"
            return result

        self.logger.info(f"Session: {self.session_id}")
        self.logger.info(f"Session dir: {self.session_dir}")
        self.logger.info(f"Targets: {len(target_list)}")
        self.logger.info(f"Model: {MODEL}")
        self.logger.info(f"Keys: {len(self.config.nvidia_keys)}")

        # Set up session directory
        self._setup_session(target_list)

        # Load precomposed history (system prompt)
        history_file = _HERE / "initprecomposedhistory.txt"
        messages: List[Dict] = []
        if history_file.exists():
            try:
                messages = json.loads(history_file.read_text(encoding="utf-8"))
                self.logger.info(f"Loaded {len(messages)} precomposed history messages")
            except Exception as e:
                self.logger.error(f"Failed to load precomposed history: {e}")

        # Initial user message
        initial_msg = {"role": "user", "content": self._build_initial_context(target_list)}
        messages.append(initial_msg)
        self.logger.sent_to_gpt(initial_msg)

        # Inject pre-composed warmup history (ends with tool message — AI responds next)
        warmup = self._build_warmup_history(target_list)
        if warmup:
            self.logger.info(f"Injected {len(warmup)} warmup history messages")
            messages.extend(warmup)
            # Log warmup tool results for visibility
            for msg in warmup:
                if msg.get("role") == "tool":
                    try:
                        result_data = json.loads(msg["content"])
                        tc_id = msg.get("tool_call_id", "warmup")
                        self.logger.tool_result(f"[WARMUP] {tc_id[:30]}", result_data)
                    except Exception:
                        pass

        target_ids = {f"{t['minecraft_version']}/{t['loader']}" for t in target_list}
        iteration = 0

        while iteration < self.config.max_iterations:
            iteration += 1
            self.logger.info(f"--- Iteration {iteration}/{self.config.max_iterations} ---")

            try:
                response_msg = await self.client.chat(messages, TOOL_DEFINITIONS)
            except RuntimeError as e:
                self.logger.error(f"API error: {e}")
                result.error = str(e)
                break

            self.logger.received_from_gpt(response_msg)
            messages.append(response_msg)

            tool_calls = extract_tool_calls(response_msg)

            if not tool_calls:
                content = response_msg.get("content", "") or ""
                if any(p in content.lower() for p in ["all versions", "all targets", "compilation complete", "all builds", "finished", "done"]):
                    self.logger.info("Agent signalled completion.")
                    break
                # Compute current todo status for the nudge
                try:
                    todo_status = T.compute_todo_status(
                        self.todos_file, self.session_dir / "bundle", target_list
                    )
                    todo_footer = T.format_todo_footer(todo_status)
                    n_done = todo_status.get("completed", 0)
                    n_pending = todo_status.get("pending", 0)
                except Exception:
                    todo_footer = ""
                    n_done = 0
                    n_pending = len(target_list)

                if n_done > 0:
                    nudge_content = (
                        f"You've completed {n_done}/{len(target_list)} targets. "
                        f"{n_pending} still need source files. "
                        "Continue with batch() writes for the PENDING targets shown below. "
                        "Once ALL targets are done, call build_bundle() then trigger_build()."
                    )
                else:
                    nudge_content = (
                        f"You have {len(target_list)} target(s) to build. "
                        "Start immediately: use batch() to write source files for ALL targets. "
                        "Do NOT explore — you already have all the context you need. "
                        "Implement REAL logic from the decompiled bytecode — no stubs."
                    )
                nudge = {"role": "user", "content": nudge_content + todo_footer}
                messages.append(nudge)
                self.logger.sent_to_gpt(nudge)
                continue

            tool_results = []
            for tool_name, tool_params, tc_id in tool_calls:
                # Sanitize params to strip XML injection artifacts
                tool_params = _sanitize_tool_params(tool_params)
                self.logger.tool_call(tool_name, tool_params)
                try:
                    tool_result = self._dispatch(tool_name, tool_params)
                except Exception as e:
                    tool_result = {"error": str(e)}
                self.logger.tool_result(tool_name, tool_result)

                # Track successful compilations from trigger_build results
                if tool_name == "trigger_build" and tool_result.get("success"):
                    for t in tool_result.get("passed_targets", []):
                        mc_v = t.get("minecraft_version", "")
                        loader = t.get("loader", "")
                        key = f"{mc_v}/{loader}"
                        if key not in {f"{s['minecraft_version']}/{s['loader']}" for s in self.success_list}:
                            self.success_list.append({"minecraft_version": mc_v, "loader": loader})
                    self.logger.progress(f"✅ Build complete: {len(self.success_list)}/{len(target_ids)} targets passed")
                    self._save_state()

                # For compile results, include the full log tail
                result_content = json.dumps(tool_result, ensure_ascii=False)
                if tool_name == "compile" and "log" in tool_result:
                    log = tool_result.get("log", "")
                    log_tail = log[-3000:] if len(log) > 3000 else log
                    compact = dict(tool_result)
                    compact["log"] = log_tail
                    result_content = json.dumps(compact, ensure_ascii=False)

                # ── Inject live todo/progress footer into every tool result ──
                # This is NOT stored in history — it's appended to the content
                # string so the model always sees current progress without
                # needing to call any tool to check it.
                # For batch, inject after the batch completes (it may have written files).
                # Skip for build tools where the result itself is the progress signal.
                _skip_footer_tools = {"build_bundle", "trigger_build"}
                if tool_name not in _skip_footer_tools:
                    try:
                        todo_status = T.compute_todo_status(
                            self.todos_file,
                            self.session_dir / "bundle",
                            target_list,
                        )
                        footer = T.format_todo_footer(todo_status)
                        if footer:
                            result_content = result_content + footer
                    except Exception:
                        pass  # Never let footer injection crash the loop

                if tc_id:
                    tool_results.append({"role": "tool", "tool_call_id": tc_id, "content": result_content})
                else:
                    tool_results.append({"role": "user", "content": f"Tool result for {tool_name}:\n{result_content}"})

            for tr in tool_results:
                messages.append(tr)
                self.logger.sent_to_gpt(tr)

            if {f"{s['minecraft_version']}/{s['loader']}" for s in self.success_list} >= target_ids:
                self.logger.progress("🎉 All targets compiled!")
                break

        result.success_list = self.success_list
        result.failed_list = [
            t for t in target_list
            if f"{t['minecraft_version']}/{t['loader']}" not in
            {f"{s['minecraft_version']}/{s['loader']}" for s in self.success_list}
        ]
        result.iterations = iteration
        self._save_state()

        # Copy artifacts to final output dir
        if self.config.artifact_dir != self.artifact_dir:
            self.config.artifact_dir.mkdir(parents=True, exist_ok=True)
            for jar in self.artifact_dir.glob("*.jar"):
                shutil.copy2(str(jar), str(self.config.artifact_dir / jar.name))

        self.logger.close()
        return result
