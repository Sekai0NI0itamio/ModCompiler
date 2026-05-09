"""
tools.py — AI Agent Tool Implementations
=========================================
All tool functions used by the ModVersionConverterAgent.
Each tool takes a params dict and returns a dict result.
"""
from __future__ import annotations

import asyncio
import fnmatch
import glob as glob_module
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _sandbox_path(sandbox_dir: Path, rel: str) -> Path:
    """Resolve a relative path inside the sandbox, preventing escapes.
    Uses resolve() on both sides to handle macOS /tmp -> /private/tmp symlinks."""
    sandbox_resolved = sandbox_dir.resolve()
    p = (sandbox_dir / rel).resolve()
    try:
        p.relative_to(sandbox_resolved)
    except ValueError:
        raise ValueError(f"Path escape attempt: {rel!r} resolves outside sandbox")
    return p


def _read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# File Write
# ---------------------------------------------------------------------------

def tool_file_write(params: Dict, sandbox_dir: Path) -> Dict:
    """Create or overwrite a file in the sandbox."""
    rel = params.get("path", "")
    content = params.get("content", "")
    if not rel:
        return {"error": "path is required. Example: 'bundle/1.8.9-forge/src/main/java/com/example/MyMod.java'"}
    # Validate path looks like a file (has an extension) and isn't suspiciously short
    p = Path(rel)
    if not p.suffix:
        return {"error": f"path '{rel}' has no file extension — it looks like a directory or truncated path. "
                         f"Provide a full file path like 'bundle/1.8.9-forge/src/main/java/com/example/MyMod.java'"}
    if len(rel) < 5:
        return {"error": f"path '{rel}' is too short — it looks truncated. Provide the full file path."}
    # Reject placeholder/example paths that the model sometimes emits literally
    _PLACEHOLDER_PATTERNS = ["path/to/", "example/path", "your/path", "/path/to"]
    for pat in _PLACEHOLDER_PATTERNS:
        if pat in rel.lower():
            return {"error": f"path '{rel}' looks like a placeholder example, not a real path. "
                             f"Provide the actual bundle path, e.g. 'bundle/1.8.9-forge/src/main/java/com/example/MyMod.java'"}
    # Warn if content looks like a stub (very short or contains only comments/placeholders)
    stripped = content.strip()
    is_stub = (
        len(stripped) < 80 or
        (stripped.count("//") >= 2 and len(stripped) < 200) or
        "// TODO" in stripped or
        "// Handle" in stripped and len(stripped) < 300 or
        "// Placeholder" in stripped
    )
    dest = _sandbox_path(sandbox_dir, rel)
    _write_text(dest, content)
    try:
        rel_out = str(dest.relative_to(sandbox_dir.resolve()))
    except ValueError:
        rel_out = rel
    result = {"ok": True, "path": rel_out, "bytes": len(content.encode())}
    if is_stub:
        result["stub_warning"] = (
            "⚠️ STUB DETECTED — this file is too short or contains placeholder comments. "
            "You MUST rewrite it with the ACTUAL mod logic from the decompiled source. "
            "The build will fail if stubs are submitted. Rewrite this file before calling build_bundle()."
        )
    return result


# ---------------------------------------------------------------------------
# File Edit
# ---------------------------------------------------------------------------

def tool_file_edit(params: Dict, sandbox_dir: Path) -> Dict:
    """Replace old_str with new_str in a sandbox file."""
    rel = params.get("path", "")
    old_str = params.get("old_str", "")
    new_str = params.get("new_str", "")
    replace_all = bool(params.get("replace_all", False))
    if not rel:
        return {"error": "path is required"}
    if old_str == new_str:
        return {"error": "old_str and new_str are identical — no change needed"}
    dest = _sandbox_path(sandbox_dir, rel)
    if not dest.exists():
        return {"error": f"File not found: {rel}"}
    text = _read_text(dest)
    if old_str not in text:
        return {"error": f"old_str not found in {rel}"}
    if replace_all:
        new_text = text.replace(old_str, new_str)
    else:
        new_text = text.replace(old_str, new_str, 1)
    _write_text(dest, new_text)
    count = text.count(old_str)
    replaced = count if replace_all else 1
    return {"ok": True, "replacements": replaced}


# ---------------------------------------------------------------------------
# Multi Edit
# ---------------------------------------------------------------------------

def tool_multi_edit(params: Dict, sandbox_dir: Path) -> Dict:
    """Apply multiple sequential edits to one sandbox file."""
    rel = params.get("path", "")
    edits = params.get("edits", [])
    if not rel:
        return {"error": "path is required"}
    dest = _sandbox_path(sandbox_dir, rel)
    if not dest.exists():
        return {"error": f"File not found: {rel}"}
    text = _read_text(dest)
    results = []
    for i, edit in enumerate(edits):
        old_str = edit.get("old_str", "")
        new_str = edit.get("new_str", "")
        replace_all = bool(edit.get("replace_all", False))
        if old_str not in text:
            return {"error": f"Edit #{i+1}: old_str not found", "applied": i, "results": results}
        if replace_all:
            text = text.replace(old_str, new_str)
        else:
            text = text.replace(old_str, new_str, 1)
        results.append({"edit": i + 1, "ok": True})
    _write_text(dest, text)
    return {"ok": True, "edits_applied": len(edits), "results": results}


# ---------------------------------------------------------------------------
# Apply Patch (unified diff)
# ---------------------------------------------------------------------------

def tool_apply_patch(params: Dict, sandbox_dir: Path) -> Dict:
    """Apply a unified diff patch to sandbox files."""
    patch_text = params.get("patch", "")
    if not patch_text.strip():
        return {"error": "patch is empty"}
    # Write patch to temp file and apply with `patch` command
    import tempfile
    with tempfile.NamedTemporaryFile(mode="w", suffix=".patch", delete=False, encoding="utf-8") as f:
        f.write(patch_text)
        patch_file = f.name
    try:
        result = subprocess.run(
            ["patch", "-p1", "--input", patch_file, "--directory", str(sandbox_dir)],
            capture_output=True, text=True
        )
        if result.returncode == 0:
            return {"ok": True, "output": result.stdout}
        else:
            return {"error": result.stderr or result.stdout, "returncode": result.returncode}
    finally:
        os.unlink(patch_file)


# ---------------------------------------------------------------------------
# File Read
# ---------------------------------------------------------------------------

def tool_file_read(params: Dict, sandbox_dir: Path) -> Dict:
    """Read a file from the sandbox."""
    rel = params.get("path", "")
    if not rel:
        return {"error": "path is required"}
    dest = _sandbox_path(sandbox_dir, rel)
    if not dest.exists():
        return {"error": f"File not found: {rel}"}
    if dest.is_dir():
        entries = sorted(str(p.relative_to(dest)) for p in dest.rglob("*") if p.is_file())
        return {"type": "directory", "entries": entries}
    content = _read_text(dest)
    return {"ok": True, "path": rel, "content": content, "bytes": len(content.encode())}


# ---------------------------------------------------------------------------
# Sandbox LS
# ---------------------------------------------------------------------------

def tool_sandboxls(params: Dict, sandbox_dir: Path) -> Dict:
    """List files in the sandbox recursively."""
    rel = params.get("path", "")
    sandbox_resolved = sandbox_dir.resolve()
    if rel:
        root = (sandbox_dir / rel).resolve()
        try:
            root.relative_to(sandbox_resolved)
        except ValueError:
            return {"error": f"Path escape attempt: {rel!r}"}
    else:
        root = sandbox_resolved
    if not root.exists():
        return {"error": f"Path not found: {rel or '(sandbox root)'}"}
    entries = []
    for p in sorted(root.rglob("*")):
        try:
            entry_rel = str(p.resolve().relative_to(sandbox_resolved))
        except ValueError:
            entry_rel = str(p.relative_to(sandbox_dir))
        if p.is_dir():
            entries.append(entry_rel + "/")
        else:
            entries.append(entry_rel)
    return {"ok": True, "path": rel or ".", "entries": entries, "count": len(entries)}


# ---------------------------------------------------------------------------
# Glob Find
# ---------------------------------------------------------------------------

def tool_glob_find(params: Dict, sandbox_dir: Path, repo_root: Path) -> Dict:
    """Find files by glob pattern in sandbox or repo."""
    pattern = params.get("pattern", "**/*")
    root_name = params.get("root", "sandbox")
    search_root = sandbox_dir if root_name == "sandbox" else repo_root
    offset = int(params.get("offset", 0))
    limit = int(params.get("limit", 100))

    matches = []
    # Use rglob for ** patterns, fnmatch for simple patterns
    if "**" in pattern:
        # Split on ** to get the suffix glob
        suffix = pattern.split("**")[-1].lstrip("/\\")
        for p in sorted(search_root.rglob(suffix or "*")):
            if p.is_file():
                matches.append(str(p.relative_to(search_root)))
    else:
        for p in sorted(search_root.rglob("*")):
            if p.is_file() and fnmatch.fnmatch(str(p.relative_to(search_root)), pattern):
                matches.append(str(p.relative_to(search_root)))

    # Sort by mtime (most recent first) like Claude Code GlobTool
    def mtime(rel):
        try:
            return (search_root / rel).stat().st_mtime
        except OSError:
            return 0
    matches.sort(key=mtime, reverse=True)

    sliced = matches[offset:offset + limit]
    truncated = len(matches) - offset > limit
    return {
        "ok": True,
        "pattern": pattern,
        "root": root_name,
        "files": sliced,
        "count": len(sliced),
        "total": len(matches),
        "truncated": truncated,
    }


# ---------------------------------------------------------------------------
# Grep Search
# ---------------------------------------------------------------------------

def tool_grep_search(params: Dict, sandbox_dir: Path, repo_root: Path) -> Dict:
    """Regex search across files in sandbox or repo."""
    pattern = params.get("pattern", "")
    root_name = params.get("root", "sandbox")
    file_glob = params.get("glob", "")
    case_insensitive = bool(params.get("case_insensitive", False))
    context_lines = int(params.get("context", 0))
    head_limit = int(params.get("head_limit", 250))

    if not pattern:
        return {"error": "pattern is required"}

    search_root = sandbox_dir if root_name == "sandbox" else repo_root

    # Use ripgrep if available, else fallback to Python
    rg_path = shutil.which("rg")
    if rg_path:
        args = [rg_path, "--hidden", "--glob", "!.git", "--max-columns", "500"]
        if case_insensitive:
            args.append("-i")
        if context_lines:
            args.extend(["-C", str(context_lines)])
        if file_glob:
            args.extend(["--glob", file_glob])
        args.extend(["-n", pattern, str(search_root)])
        result = subprocess.run(args, capture_output=True, text=True)
        lines = result.stdout.splitlines()
        # Convert absolute paths to relative
        rel_lines = []
        for line in lines:
            if line.startswith(str(search_root)):
                line = line[len(str(search_root)):].lstrip("/\\")
            rel_lines.append(line)
        limited = rel_lines[:head_limit]
        return {
            "ok": True,
            "pattern": pattern,
            "root": root_name,
            "matches": limited,
            "count": len(limited),
            "truncated": len(rel_lines) > head_limit,
        }
    else:
        # Python fallback
        flags = re.IGNORECASE if case_insensitive else 0
        try:
            rx = re.compile(pattern, flags)
        except re.error as e:
            return {"error": f"Invalid regex: {e}"}
        matches = []
        for p in sorted(search_root.rglob("*")):
            if not p.is_file():
                continue
            if file_glob and not fnmatch.fnmatch(p.name, file_glob):
                continue
            try:
                text = _read_text(p)
            except Exception:
                continue
            for i, line in enumerate(text.splitlines(), 1):
                if rx.search(line):
                    rel = str(p.relative_to(search_root))
                    matches.append(f"{rel}:{i}: {line}")
                    if len(matches) >= head_limit:
                        return {"ok": True, "pattern": pattern, "root": root_name,
                                "matches": matches, "count": len(matches), "truncated": True}
        return {"ok": True, "pattern": pattern, "root": root_name,
                "matches": matches, "count": len(matches), "truncated": False}


# ---------------------------------------------------------------------------
# File Delete / Move
# ---------------------------------------------------------------------------

def tool_file_delete(params: Dict, sandbox_dir: Path) -> Dict:
    """Delete a file from the sandbox."""
    rel = params.get("path", "")
    if not rel:
        return {"error": "path is required"}
    dest = _sandbox_path(sandbox_dir, rel)
    if not dest.exists():
        return {"error": f"File not found: {rel}"}
    if dest.is_dir():
        shutil.rmtree(dest)
        return {"ok": True, "deleted": rel, "type": "directory"}
    dest.unlink()
    return {"ok": True, "deleted": rel}


def tool_file_move(params: Dict, sandbox_dir: Path) -> Dict:
    """Move or rename a file within the sandbox."""
    src_rel = params.get("src", "")
    dst_rel = params.get("dst", "")
    if not src_rel or not dst_rel:
        return {"error": "src and dst are required"}
    src = _sandbox_path(sandbox_dir, src_rel)
    dst = _sandbox_path(sandbox_dir, dst_rel)
    if not src.exists():
        return {"error": f"Source not found: {src_rel}"}
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dst))
    return {"ok": True, "src": src_rel, "dst": dst_rel}


# ---------------------------------------------------------------------------
# CMD — run shell command in sandbox
# ---------------------------------------------------------------------------

def tool_cmd(params: Dict, sandbox_dir: Path) -> Dict:
    """Run a shell command in the sandbox directory."""
    command = params.get("command", "")
    timeout = int(params.get("timeout", 120))
    if not command:
        return {"error": "command is required"}
    try:
        result = subprocess.run(
            command,
            shell=True,
            cwd=str(sandbox_dir),
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        output = result.stdout + result.stderr
        return {
            "ok": result.returncode == 0,
            "returncode": result.returncode,
            "output": output,
        }
    except subprocess.TimeoutExpired:
        return {"error": f"Command timed out after {timeout}s", "command": command}
    except Exception as e:
        return {"error": str(e)}


# ---------------------------------------------------------------------------
# Outside Sandbox LS / Read
# ---------------------------------------------------------------------------

def tool_outside_sandbox_ls(params: Dict, repo_root: Path) -> Dict:
    """List files in the repository (read-only)."""
    rel = params.get("path", "")
    target = (repo_root / rel).resolve() if rel else repo_root.resolve()
    if not str(target).startswith(str(repo_root.resolve())):
        return {"error": "Path escape attempt"}
    if not target.exists():
        return {"error": f"Path not found: {rel}"}
    if target.is_file():
        return {"type": "file", "path": rel}
    entries = []
    repo_resolved = repo_root.resolve()
    for p in sorted(target.iterdir()):
        try:
            entry_rel = str(p.resolve().relative_to(repo_resolved))
        except ValueError:
            entry_rel = str(p.relative_to(repo_root))
        if p.is_dir():
            entries.append(entry_rel + "/")
        else:
            entries.append(entry_rel)
    return {"ok": True, "path": rel or ".", "entries": entries}


def tool_read_outside_sandbox(params: Dict, repo_root: Path) -> Dict:
    """Read a repository file (read-only)."""
    rel = params.get("path", "")
    if not rel:
        return {"error": "path is required"}
    target = (repo_root / rel).resolve()
    if not str(target).startswith(str(repo_root.resolve())):
        return {"error": "Path escape attempt"}
    if not target.exists():
        return {"error": f"File not found: {rel}"}
    if target.is_dir():
        return {"error": f"{rel} is a directory — use Outside_Sandbox_LS"}
    content = _read_text(target)
    return {"ok": True, "path": rel, "content": content}


# ---------------------------------------------------------------------------
# Compile Bundle
# ---------------------------------------------------------------------------

def _find_range_for_version(manifest: Dict, minecraft_version: str, loader: str) -> Optional[Dict]:
    """Find the manifest range entry that covers a given MC version + loader."""
    from packaging.version import Version, InvalidVersion

    def ver(v: str):
        try:
            return Version(v)
        except InvalidVersion:
            return Version("0")

    target = ver(minecraft_version)
    for rng in manifest.get("ranges", []):
        if loader not in rng.get("loaders", {}):
            continue
        loader_cfg = rng["loaders"][loader]
        # Check supported_versions list first
        supported = loader_cfg.get("supported_versions", [])
        if supported and minecraft_version in supported:
            return rng
        # Fall back to min/max range check
        min_v = ver(rng.get("min_version", "0"))
        max_v = ver(rng.get("max_version", "99999"))
        if min_v <= target <= max_v:
            return rng
    return None


def tool_compile_bundle(
    params: Dict,
    sandbox_dir: Path,
    repo_root: Path,
    manifest: Dict,
) -> Dict:
    """
    Compile a mod bundle for a given minecraft_version + loader.

    Steps:
    1. Find the range folder from version-manifest.json
    2. Copy the template into build/<mc>-<loader>/ if not already there
    3. Generate metadata.json from project_info/ using metadata_builder
    4. Set up source_dir pointing at the decompiled/written source
    5. Run build_adapter.py with all required arguments
    6. Return full log + success status
    """
    mc_version = params.get("minecraft_version", "")
    loader = params.get("loader", "")
    if not mc_version or not loader:
        return {"error": "minecraft_version and loader are required"}

    rng = _find_range_for_version(manifest, mc_version, loader)
    if not rng:
        return {"error": f"No manifest range found for {mc_version}/{loader}"}

    loader_cfg = rng["loaders"][loader]
    range_folder = rng["folder"]
    template_dir = repo_root / loader_cfg["template_dir"]
    adapter_script = repo_root / range_folder / "build_adapter.py"

    if not adapter_script.exists():
        return {"error": f"build_adapter.py not found at {adapter_script}"}

    # Build directory: sandbox_dir/build/<mc>-<loader>/
    build_dir = sandbox_dir / "build" / f"{mc_version}-{loader}"
    build_dir.mkdir(parents=True, exist_ok=True)

    # Copy template — always re-copy if build.gradle is missing (handles stale dirs)
    if template_dir.exists() and not (build_dir / "build.gradle").exists():
        shutil.copytree(str(template_dir), str(build_dir), dirs_exist_ok=True)

    # metadata.json — use absolute path so adapter can find it regardless of cwd
    metadata_json_path = (sandbox_dir / "metadata.json").resolve()
    if not metadata_json_path.exists():
        try:
            sys.path.insert(0, str(repo_root))
            from aibasedversionupgrader.metadata_builder import generate_metadata_json
            project_info_dir = _find_project_info_dir(sandbox_dir)
            generate_metadata_json(project_info_dir, metadata_json_path)
        except Exception as e:
            return {"error": f"Failed to generate metadata.json: {e}. "
                             f"Create it manually at metadata.json with fields: "
                             f"mod_id, name, mod_version, group, entrypoint_class, "
                             f"runtime_side, description, authors, license"}

    # source_dir — use absolute path
    # If the AI has already written Java source into the build dir, stage it to a
    # separate directory so the adapter can copy it back without deleting it first.
    build_src = build_dir / "src"
    staged_source = build_dir.parent / f"{mc_version}-{loader}-source-stage"
    
    if build_src.exists() and (any(build_src.rglob("*.java")) or any(build_src.rglob("*.kt"))):
        # Stage the AI's source files so the adapter can copy them back safely
        if staged_source.exists():
            shutil.rmtree(staged_source)
        shutil.copytree(str(build_src), str(staged_source))
        source_dir = staged_source.resolve()
    else:
        source_dir = _find_source_dir(sandbox_dir, repo_root)
        if not source_dir:
            source_dir = (sandbox_dir / "source").resolve()
            source_dir.mkdir(parents=True, exist_ok=True)
        else:
            source_dir = source_dir.resolve()

    env = os.environ.copy()
    env["PYTHONPATH"] = str(repo_root)

    cmd = [
        sys.executable, str(adapter_script),
        "--loader", loader,
        "--minecraft-version", mc_version,
        "--manifest", str(repo_root / "version-manifest.json"),
        "--template-workspace", str(build_dir),
        "--metadata-json", str(metadata_json_path),
        "--source-dir", str(source_dir),
    ]

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd=str(build_dir),
            env=env,
            timeout=600,
        )
        log = result.stdout + result.stderr
        success = result.returncode == 0

        # If it's a Python exception (not a Gradle error), extract the key error line
        python_error = None
        if not success and "Traceback (most recent call last)" in log:
            lines = log.splitlines()
            # Find the last non-empty line — that's the actual exception
            for line in reversed(lines):
                if line.strip() and not line.startswith(" "):
                    python_error = line.strip()
                    break

        # Find output jar
        jar_glob = loader_cfg.get("jar_glob", "build/libs/*.jar")
        jars = list(build_dir.glob(jar_glob))

        result_dict = {
            "ok": success,
            "returncode": result.returncode,
            "log": log,
            "success": success,
            "jars": [str(j.relative_to(build_dir)) for j in jars],
            "minecraft_version": mc_version,
            "loader": loader,
            "range_folder": range_folder,
            "build_dir": str(build_dir.relative_to(sandbox_dir)),
        }
        if python_error:
            result_dict["python_error"] = python_error
            result_dict["hint"] = (
                "This is a Python setup error (not a Gradle/Java error). "
                "The build_adapter.py script failed before Gradle ran. "
                "Check the full log for the traceback."
            )
        return result_dict
    except subprocess.TimeoutExpired:
        return {"error": "Build timed out after 600s. Do NOT run gradlew directly — use the compile tool only.", "success": False}
    except Exception as e:
        return {"error": str(e), "success": False}


def _find_project_info_dir(sandbox_dir: Path) -> Path:
    """Find the project_info directory."""
    for candidate in [sandbox_dir / "project_info", Path("project_info")]:
        if candidate.exists():
            return candidate.resolve()
    return sandbox_dir / "project_info"


def _find_source_dir(sandbox_dir: Path, repo_root: Path) -> Optional[Path]:
    """Find the source directory (written Java source preferred, then decompiled)."""
    for candidate in [
        sandbox_dir / "source",
        sandbox_dir / "project_info" / "first_version" / "decompiled",
    ]:
        if candidate.exists():
            try:
                if any(candidate.rglob("*.java")) or any(candidate.rglob("*.kt")):
                    return candidate
            except Exception:
                pass
    for candidate in [Path("project_info") / "first_version" / "decompiled"]:
        if candidate.exists():
            try:
                if any(candidate.rglob("*.java")) or any(candidate.rglob("*.kt")):
                    return candidate.resolve()
            except Exception:
                pass
    return None


# ---------------------------------------------------------------------------
# Get Diagnostics — parse build log
# ---------------------------------------------------------------------------
_DIAG_PATTERNS = [
    (re.compile(r"^(.+\.java):(\d+):\s*(error|warning):\s*(.+)$", re.MULTILINE), "javac"),
    (re.compile(r"^> Task :(\S+) (FAILED|failed)", re.MULTILINE), "gradle"),
    (re.compile(r"(Mixin target .+? could not be found)", re.MULTILINE), "mixin"),
    (re.compile(r"(error: cannot find symbol)", re.MULTILINE), "javac"),
    (re.compile(r"(error: package .+? does not exist)", re.MULTILINE), "javac"),
]


def tool_get_diagnostics(params: Dict) -> Dict:
    """Parse a build log into structured errors and warnings."""
    log = params.get("log", "")
    if not log:
        return {"diagnostics": [], "error_count": 0, "warning_count": 0}
    diagnostics = []
    seen = set()
    javac_rx = re.compile(
        r"^(?P<file>[^\s:][^:]*\.java):(?P<line>\d+):\s*(?P<sev>error|warning):\s*(?P<msg>.+)$",
        re.MULTILINE,
    )
    for m in javac_rx.finditer(log):
        key = (m.group("file"), m.group("line"), m.group("msg")[:60])
        if key in seen:
            continue
        seen.add(key)
        diagnostics.append({"severity": m.group("sev"), "file": m.group("file"),
                             "line": int(m.group("line")), "column": None, "code": None,
                             "message": m.group("msg").strip(), "source": "javac"})
    gradle_rx = re.compile(r"> Task :(?P<task>\S+) (?P<status>FAILED|failed)", re.MULTILINE)
    for m in gradle_rx.finditer(log):
        key = ("gradle", m.group("task"))
        if key in seen:
            continue
        seen.add(key)
        diagnostics.append({"severity": "error", "file": None, "line": None, "column": None,
                             "code": "GRADLE_TASK_FAILED",
                             "message": f"Gradle task :{m.group('task')} FAILED", "source": "gradle"})
    mixin_rx = re.compile(r"(Mixin target .+? could not be found)", re.MULTILINE)
    for m in mixin_rx.finditer(log):
        key = ("mixin", m.group(1)[:80])
        if key in seen:
            continue
        seen.add(key)
        diagnostics.append({"severity": "error", "file": None, "line": None, "column": None,
                             "code": "MIXIN_TARGET_NOT_FOUND", "message": m.group(1).strip(), "source": "mixin"})
    symbol_rx = re.compile(r"error: cannot find symbol\s*\n\s*symbol\s*:\s*(.+)", re.MULTILINE)
    for m in symbol_rx.finditer(log):
        key = ("symbol", m.group(1)[:80])
        if key in seen:
            continue
        seen.add(key)
        diagnostics.append({"severity": "error", "file": None, "line": None, "column": None,
                             "code": "CANNOT_FIND_SYMBOL",
                             "message": f"cannot find symbol: {m.group(1).strip()}", "source": "javac"})
    errors = sum(1 for d in diagnostics if d["severity"] == "error")
    warnings = sum(1 for d in diagnostics if d["severity"] == "warning")
    return {"diagnostics": diagnostics, "error_count": errors, "warning_count": warnings}


def tool_lsp_diagnostics(params: Dict, sandbox_dir: Path) -> Dict:
    rel = params.get("path", "")
    return {"diagnostics": [], "note": "LSP not available. Use GetDiagnostics on the build log instead.", "path": rel}


# ---------------------------------------------------------------------------
# DiffSystemAccess
# ---------------------------------------------------------------------------

def _parse_dif_frontmatter(content: str) -> Tuple[Dict, str]:
    if not content.startswith("---"):
        return {}, content
    end = content.find("\n---", 3)
    if end == -1:
        return {}, content
    fm_text = content[3:end].strip()
    body = content[end + 4:].strip()
    meta: Dict = {}
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


def tool_diff_system_access(params: Dict, repo_root: Path) -> Dict:
    op = params.get("op", "list")
    dif_dir = repo_root / "dif"
    if op == "list":
        entries = []
        for f in sorted(dif_dir.glob("*.md")):
            content = _read_text(f)
            meta, _ = _parse_dif_frontmatter(content)
            entries.append({"id": meta.get("id", f.stem), "title": meta.get("title", f.stem),
                             "tags": meta.get("tags", []), "loaders": meta.get("loaders", []),
                             "usage_count": meta.get("usage_count", 0)})
        return {"ok": True, "entries": entries, "count": len(entries)}
    elif op == "search":
        query = params.get("query", "").lower()
        if not query:
            return {"error": "query is required for search"}
        results = []
        for f in sorted(dif_dir.glob("*.md")):
            content = _read_text(f)
            meta, body = _parse_dif_frontmatter(content)
            score = 0
            full_text = " ".join([str(meta.get("title", "")), " ".join(str(t) for t in meta.get("tags", [])),
                                   " ".join(str(p) for p in meta.get("error_patterns", [])),
                                   " ".join(str(s) for s in meta.get("symbols", [])), body]).lower()
            for word in query.split():
                if word in full_text:
                    score += 1
            if score > 0:
                results.append({"id": meta.get("id", f.stem), "title": meta.get("title", f.stem),
                                 "score": score, "tags": meta.get("tags", []),
                                 "loaders": meta.get("loaders", []), "versions": meta.get("versions", [])})
        results.sort(key=lambda x: x["score"], reverse=True)
        return {"ok": True, "query": query, "results": results[:20]}
    elif op == "read":
        entry_id = params.get("id", "")
        if not entry_id:
            return {"error": "id is required for read"}
        candidates = list(dif_dir.glob(f"{entry_id}.md")) + list(dif_dir.glob(f"*{entry_id}*.md"))
        if not candidates:
            return {"error": f"DIF entry not found: {entry_id}"}
        content = _read_text(candidates[0])
        meta, body = _parse_dif_frontmatter(content)
        return {"ok": True, "id": entry_id, "meta": meta, "content": content}
    elif op == "create":
        entry = params.get("entry", {})
        entry_id = entry.get("id", "")
        if not entry_id:
            return {"error": "entry.id is required"}
        existing = list(dif_dir.glob(f"{entry_id}.md"))
        if existing:
            return {"error": f"DIF entry already exists: {entry_id}"}
        title = entry.get("title", entry_id)
        tags = entry.get("tags", [])
        versions = entry.get("versions", [])
        loaders = entry.get("loaders", [])
        error_patterns = entry.get("error_patterns", [])
        fix_text = entry.get("fix", "")
        fm = f"""---
id: {entry_id}
title: {title}
tags: [{", ".join(tags)}]
versions: [{", ".join(versions)}]
loaders: [{", ".join(loaders)}]
error_patterns: [{", ".join(f'"{p}"' for p in error_patterns)}]
usage_count: 0
---

Used/Encountered this Issue: 0
> **MUST DO:** run `python3 scripts/dif_search.py --mark-used {entry_id}` to increment the counter.

## Issue
{entry.get("issue", "Describe the issue here.")}

## Fix
{fix_text or "Describe the fix here."}
"""
        dest = dif_dir / f"{entry_id}.md"
        _write_text(dest, fm)
        return {"ok": True, "created": str(dest.relative_to(repo_root))}
    elif op == "update":
        entry_id = params.get("id", "")
        if not entry_id:
            return {"error": "id is required for update"}
        candidates = list(dif_dir.glob(f"{entry_id}.md"))
        if not candidates:
            return {"error": f"DIF entry not found: {entry_id}"}
        content = _read_text(candidates[0])
        meta, _ = _parse_dif_frontmatter(content)
        new_count = meta.get("usage_count", 0) + 1
        new_content = re.sub(r"usage_count:\s*\d+", f"usage_count: {new_count}", content)
        new_content = re.sub(r"Used/Encountered this Issue:\s*\d+", f"Used/Encountered this Issue: {new_count}", new_content)
        _write_text(candidates[0], new_content)
        return {"ok": True, "id": entry_id, "usage_count": new_count}
    else:
        return {"error": f"Unknown op: {op}"}


# ---------------------------------------------------------------------------
# MinecraftSourceCodeAccess
# ---------------------------------------------------------------------------

def _resolve_mcsource_base(source_root: Path, version: str, loader: str) -> Optional[Path]:
    """
    Resolve the base Java source directory for a given version+loader.
    Handles both naming conventions:
      - version-loader combined: DecompiledMinecraftSourceCode/1.21.1-fabric/
      - version/loader separate: DecompiledMinecraftSourceCode/1.21.1/fabric/src/main/java
    Also handles the case where version already contains the loader suffix.
    """
    if not source_root.exists():
        return None

    # Candidates to try in order of preference
    candidates = []

    if version and loader:
        # Try combined: 1.21.1-fabric/
        combined = source_root / f"{version}-{loader}"
        candidates.append(combined)
        # Try separate with src/main/java: 1.21.1/fabric/src/main/java
        candidates.append(source_root / version / loader / "src" / "main" / "java")
        # Try separate without src: 1.21.1/fabric/
        candidates.append(source_root / version / loader)
        # Try just version (some folders have no loader subdir)
        candidates.append(source_root / version)
    elif version:
        # version might already be "1.21.1-fabric" (combined)
        candidates.append(source_root / version)
        # Or it might be just "1.21.1" with no loader
        candidates.append(source_root / version / "src" / "main" / "java")

    for candidate in candidates:
        if candidate.exists():
            # If this dir has java files directly or via src/main/java, use it
            if any(candidate.rglob("*.java")):
                return candidate
            # If it's a directory that contains src/main/java, descend
            deeper = candidate / "src" / "main" / "java"
            if deeper.exists() and any(deeper.rglob("*.java")):
                return deeper

    return None


def _list_available_mcsource_versions(source_root: Path) -> List[str]:
    """List all available version folders in DecompiledMinecraftSourceCode/."""
    if not source_root.exists():
        return []
    return sorted(d.name for d in source_root.iterdir() if d.is_dir())


def tool_minecraft_source_access(params: Dict, repo_root: Path) -> Dict:
    """
    List, search, or read Minecraft decompiled source files.

    Version naming: pass version as either:
      - "1.21.1-fabric" (combined, no loader needed)
      - version="1.21.1", loader="fabric" (separate)
    The tool handles both automatically.

    ops: list, search, read
    Extra for list: filter="SnowBlock" to filter file list by name
    """
    op = params.get("op", "list")
    source_root = repo_root / "DecompiledMinecraftSourceCode"
    available_versions = _list_available_mcsource_versions(source_root)

    version = params.get("version", "")
    loader = params.get("loader", "")

    if op == "list":
        name_filter = params.get("filter", "")

        if not version:
            # No version specified — list available versions
            return {
                "ok": True,
                "available_versions": available_versions,
                "note": "Pass version= to list files. Use 'version-loader' format e.g. version='1.21.1-fabric'",
            }

        base = _resolve_mcsource_base(source_root, version, loader)
        if not base:
            return {
                "error": f"Source not found for version='{version}' loader='{loader}'. "
                         f"Available versions: {available_versions[:20]}",
                "available_versions": available_versions,
            }

        files = [str(p.relative_to(base)) for p in sorted(base.rglob("*.java"))]
        if name_filter:
            files = [f for f in files if name_filter.lower() in f.lower()]
        total = len(files)
        result_dict = {
            "ok": True,
            "version": version,
            "loader": loader,
            "base_path": str(base.relative_to(repo_root)),
            "files": files[:500],
            "count": min(total, 500),
            "total": total,
            "truncated": total > 500,
            "tip": f"Use filter='ClassName' to narrow results. Use op='search' to grep content.",
        }
        if total == 0 and name_filter:
            result_dict["hint"] = (
                f"No files matching '{name_filter}' found in {version}. "
                "This version's source may only contain loader-specific files (not vanilla Minecraft classes). "
                "Try a fabric version for vanilla classes, e.g. version='1.21.1-fabric'."
            )
        return result_dict

    elif op == "search":
        pattern = params.get("pattern", "")
        if not pattern:
            return {"error": "pattern is required for search"}

        if not version:
            return {"error": "version is required for search. Use 'version-loader' format e.g. version='1.21.1-fabric'"}

        base = _resolve_mcsource_base(source_root, version, loader)
        if not base:
            return {
                "error": f"Source not found for version='{version}' loader='{loader}'. "
                         f"Available versions: {available_versions[:20]}",
                "available_versions": available_versions,
            }

        rg_path = shutil.which("rg")
        if rg_path:
            result = subprocess.run(
                [rg_path, "-n", "--max-columns", "300", pattern, str(base)],
                capture_output=True, text=True
            )
            lines = result.stdout.splitlines()[:200]
            base_str = str(base) + "/"
            rel_lines = [l.replace(base_str, "") for l in lines]
            result_dict = {"ok": True, "pattern": pattern, "version": version, "loader": loader,
                    "matches": rel_lines, "count": len(rel_lines)}
            if len(rel_lines) == 0:
                result_dict["hint"] = (
                    f"No matches found in {version}. "
                    "If this is a Forge/NeoForge-only source folder, it may only contain loader-specific files, "
                    "not vanilla Minecraft classes. Try a fabric version for vanilla classes "
                    "(e.g. version='1.21.1-fabric') or try a different search pattern."
                )
            return result_dict
        else:
            try:
                rx = re.compile(pattern)
            except re.error as e:
                return {"error": f"Invalid regex: {e}"}
            matches = []
            for p in sorted(base.rglob("*.java")):
                try:
                    text = _read_text(p)
                except Exception:
                    continue
                for i, line in enumerate(text.splitlines(), 1):
                    if rx.search(line):
                        rel = str(p.relative_to(base))
                        matches.append(f"{rel}:{i}: {line.strip()}")
                        if len(matches) >= 200:
                            return {"ok": True, "pattern": pattern, "matches": matches,
                                    "count": len(matches), "truncated": True}
            return {"ok": True, "pattern": pattern, "matches": matches, "count": len(matches)}

    elif op == "read":
        file_path = params.get("path", "")
        if not file_path:
            return {"error": "path is required for read"}

        if not version:
            return {"error": "version is required for read. Use 'version-loader' format e.g. version='1.21.1-fabric'"}

        base = _resolve_mcsource_base(source_root, version, loader)
        if not base:
            return {
                "error": f"Source not found for version='{version}' loader='{loader}'. "
                         f"Available versions: {available_versions[:20]}",
                "available_versions": available_versions,
            }

        target = (base / file_path).resolve()
        if not str(target).startswith(str(source_root.resolve())):
            return {"error": "Path escape attempt"}
        if not target.exists():
            # Try to find similar files to help the AI
            name = Path(file_path).name
            similar = [str(p.relative_to(base)) for p in base.rglob(f"*{name}*")][:5]
            hint = f" Similar files: {similar}" if similar else ""
            return {"error": f"File not found: {file_path}.{hint}"}
        return {"ok": True, "path": file_path, "version": version, "content": _read_text(target)}

    else:
        return {"error": f"Unknown op: {op}. Use list, search, or read."}


# ---------------------------------------------------------------------------
# Web Search / Fetch
# ---------------------------------------------------------------------------

def tool_web_search(params: Dict) -> Dict:
    query = params.get("query", "")
    num_results = int(params.get("num_results", 8))
    if not query:
        return {"error": "query is required"}
    payload = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                          "params": {"name": "web_search_exa", "arguments": {"query": query, "type": "auto",
                                                                               "numResults": num_results, "livecrawl": "fallback"}}}).encode()
    req = urllib.request.Request("https://mcp.exa.ai/mcp", data=payload,
                                  headers={"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read().decode("utf-8", errors="replace")
        for line in body.splitlines():
            if line.startswith("data: "):
                data = json.loads(line[6:])
                if data.get("result", {}).get("content"):
                    return {"ok": True, "query": query, "results": data["result"]["content"][0].get("text", "")}
        data = json.loads(body)
        if data.get("result", {}).get("content"):
            return {"ok": True, "query": query, "results": data["result"]["content"][0].get("text", "")}
        return {"ok": True, "query": query, "results": body[:2000]}
    except Exception as e:
        return {"error": str(e), "query": query}


def tool_web_fetch(params: Dict) -> Dict:
    url = params.get("url", "")
    if not url:
        return {"error": "url is required"}
    if not url.startswith(("http://", "https://")):
        return {"error": "URL must start with http:// or https://"}
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; ModVersionConverter/1.0)", "Accept": "text/html,text/plain,*/*"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content_type = resp.headers.get("Content-Type", "")
            raw = resp.read(5 * 1024 * 1024)
        text = raw.decode("utf-8", errors="replace")
        if "text/html" in content_type:
            text = re.sub(r"<script[^>]*>.*?</script>", "", text, flags=re.DOTALL | re.IGNORECASE)
            text = re.sub(r"<style[^>]*>.*?</style>", "", text, flags=re.DOTALL | re.IGNORECASE)
            text = re.sub(r"<[^>]+>", " ", text)
            text = re.sub(r"&nbsp;", " ", text)
            text = re.sub(r"&lt;", "<", text)
            text = re.sub(r"&gt;", ">", text)
            text = re.sub(r"&amp;", "&", text)
            text = re.sub(r"\s{3,}", "\n\n", text).strip()
        return {"ok": True, "url": url, "content": text[:50000], "content_type": content_type}
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}: {e.reason}", "url": url}
    except Exception as e:
        return {"error": str(e), "url": url}


# ---------------------------------------------------------------------------
# Session Memory / Think / Progress / Artifact / Todo / Batch
# ---------------------------------------------------------------------------

def tool_session_memory(params: Dict, memory_file: Path) -> Dict:
    op = params.get("op", "read")
    key = params.get("key", "")
    if not key:
        return {"error": "key is required"}
    data: Dict = {}
    if memory_file.exists():
        try:
            data = json.loads(memory_file.read_text(encoding="utf-8"))
        except Exception:
            data = {}
    if op == "read":
        return {"ok": True, "key": key, "value": data.get(key, "")}
    elif op == "write":
        value = params.get("value", "")
        data[key] = value
        memory_file.parent.mkdir(parents=True, exist_ok=True)
        memory_file.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
        return {"ok": True, "key": key, "written": True}
    elif op == "list":
        return {"ok": True, "keys": list(data.keys())}
    else:
        return {"error": f"Unknown op: {op}"}


def tool_think_aloud(params: Dict, logger: Any) -> Dict:
    reasoning = params.get("reasoning", "")
    if logger:
        logger(f"[THINK] {reasoning}")
    return {"ok": True, "logged": True}


def tool_report_progress(params: Dict, summary_file: Optional[Path], is_github: bool) -> Dict:
    message = params.get("message", "")
    if not message:
        return {"error": "message is required"}
    if is_github and summary_file:
        try:
            with open(summary_file, "a", encoding="utf-8") as f:
                f.write(f"{message}\n")
        except Exception as e:
            print(f"[PROGRESS] {message}")
            return {"ok": True, "note": f"Could not write to summary: {e}"}
    else:
        print(f"[PROGRESS] {message}")
    return {"ok": True, "message": message}


def tool_artifact_upload(params: Dict, sandbox_dir: Path, artifact_dir: Path) -> Dict:
    src_rel = params.get("path", "")
    dest_name = params.get("name", "")
    if not src_rel:
        return {"error": "path is required"}
    src = _sandbox_path(sandbox_dir, src_rel)
    if not src.exists():
        return {"error": f"Source file not found: {src_rel}"}
    artifact_dir.mkdir(parents=True, exist_ok=True)
    dest_name = dest_name or src.name
    dest = artifact_dir / dest_name
    shutil.copy2(str(src), str(dest))
    return {"ok": True, "staged": str(dest), "size": dest.stat().st_size}


def compute_todo_status(todos_file: Path, bundle_dir: Path, target_list: List[Dict]) -> Dict:
    """
    Read the current todo list from the state file.
    
    The todo list is managed manually by the agent via the todo() tool.
    This function just reads the persisted state and returns it.
    
    If no state file exists yet, returns an empty status (the warmup will
    initialise the list via tool_todo_write).
    """
    if not target_list:
        return {}

    todos: List[Dict] = []
    if todos_file.exists():
        try:
            todos = json.loads(todos_file.read_text(encoding="utf-8"))
        except Exception:
            todos = []

    total = len(todos)
    not_started = [t for t in todos if t.get("status") == "not_started"]
    in_progress  = [t for t in todos if t.get("status") == "in_progress"]
    completed    = [t for t in todos if t.get("status") == "completed"]

    n_done    = len(completed)
    n_active  = len(in_progress)
    n_pending = len(not_started)

    return {
        "total": total,
        "not_started": n_pending,
        "in_progress": n_active,
        "completed": n_done,
        "todos": todos,
        "all_done": total > 0 and n_pending == 0 and n_active == 0,
        "progress_bar": f"[{'█' * n_done}{'▒' * n_active}{'░' * n_pending}] {n_done}/{total}",
    }


def format_todo_footer(todo_status: Dict) -> str:
    """
    Format the todo list as a compact footer appended to every tool result.
    
    Shows the full checklist so the agent always knows exactly where it is
    in the workflow without needing to call any tool to check.
    """
    if not todo_status or not todo_status.get("todos"):
        return ""

    todos    = todo_status.get("todos", [])
    bar      = todo_status.get("progress_bar", "")
    n_done   = todo_status.get("completed", 0)
    total    = todo_status.get("total", 0)

    STATUS_ICON = {
        "not_started": "⬜",
        "in_progress":  "🔄",
        "completed":    "✅",
    }

    lines = [
        f"\n\n--- TODO LIST {bar} ({n_done}/{total} done) ---",
    ]
    for t in todos:
        icon    = STATUS_ICON.get(t.get("status", "not_started"), "⬜")
        content = t.get("content", "?")
        tid     = t.get("id", "?")
        lines.append(f"  {icon} [{tid}] {content}")

    # Contextual next-action hint
    in_prog = [t for t in todos if t.get("status") == "in_progress"]
    not_started = [t for t in todos if t.get("status") == "not_started"]
    if in_prog:
        lines.append(f"→ Currently in progress: {', '.join(t['id'] for t in in_prog[:3])}")
        lines.append(f"  When done, call: todo(todos=[...]) with status='completed' for those IDs")
    elif not_started:
        next_t = not_started[0]
        lines.append(f"→ Next task: [{next_t['id']}] {next_t['content']}")
        lines.append(f"  Start it: todo(todos=[...]) with status='in_progress' for id='{next_t['id']}'")
    else:
        lines.append("🎉 All tasks complete!")

    lines.append("---")
    return "\n".join(lines)


def build_initial_todo_list(target_list: List[Dict]) -> List[Dict]:
    """
    Build the initial todo list for a new session.
    
    Structure:
    - One item per target version (not_started)
    - A compile phase item
    - Post-compile workflow items
    
    The agent must manually mark items as in_progress / completed via todo().
    """
    todos = []
    idx = 1

    # One item per target
    for t in target_list:
        mc_v   = t["minecraft_version"]
        loader = t["loader"]
        todos.append({
            "id": str(idx),
            "content": f"Write source for {mc_v}/{loader}",
            "status": "not_started",
            "target": f"{mc_v}-{loader}",
        })
        idx += 1

    # Compile phase
    todos.append({
        "id": str(idx),
        "content": "Call build_bundle() then trigger_build() to compile all targets",
        "status": "not_started",
        "phase": "compile",
    })
    idx += 1

    # Post-compile workflow
    todos.append({
        "id": str(idx),
        "content": "Read build logs — identify which targets passed and which failed",
        "status": "not_started",
        "phase": "post_compile",
    })
    idx += 1

    todos.append({
        "id": str(idx),
        "content": "Fix failed targets — rewrite source for each failed version, then call build_bundle(failed_only=True) + trigger_build()",
        "status": "not_started",
        "phase": "fix_failures",
    })
    idx += 1

    todos.append({
        "id": str(idx),
        "content": "Document results — add DIF entries for every compile error encountered, note API differences per version",
        "status": "not_started",
        "phase": "document",
    })

    return todos


def tool_todo_write(params: Dict, state_file: Path) -> Dict:
    """
    Manage the task checklist.

    To UPDATE the list, pass todos as a list of objects:
      todo(todos=[{"id": "1", "content": "...", "status": "completed"}, ...])

    To READ the current list without modifying it:
      todo(op="read")   OR   todo()   (no arguments)

    Status values: "pending", "in_progress", "completed"

    Example — mark item 1 as completed:
      todo(todos=[{"id": "1", "content": "Write 1.8.9-forge", "status": "completed"},
                  {"id": "2", "content": "Write 1.16.5-forge", "status": "pending"}])
    """
    # Read current state
    old_todos: List = []
    if state_file.exists():
        try:
            old_todos = json.loads(state_file.read_text(encoding="utf-8"))
        except Exception:
            old_todos = []

    # If called with op="read" or no todos array, return current state without modifying
    op = params.get("op", "")
    todos = params.get("todos", None)

    # Handle case where model passes todos as a JSON string instead of a list
    if isinstance(todos, str):
        try:
            todos = json.loads(todos)
        except Exception:
            return {
                "error": (
                    "'todos' must be a list of objects, not a string. "
                    "Example: todo(todos=[{\"id\": \"1\", \"content\": \"...\", \"status\": \"completed\"}])"
                )
            }

    if op == "read" or todos is None:
        pending = sum(1 for t in old_todos if t.get("status") == "pending")
        in_progress = sum(1 for t in old_todos if t.get("status") == "in_progress")
        completed = sum(1 for t in old_todos if t.get("status") == "completed")
        return {
            "ok": True,
            "todos": old_todos,
            "count": len(old_todos),
            "pending": pending,
            "in_progress": in_progress,
            "completed": completed,
            "all_done": len(old_todos) > 0 and pending == 0 and in_progress == 0,
            "note": "Read-only — pass todos=[...] to update the list.",
        }

    # Validate todos is a list of dicts
    if not isinstance(todos, list):
        return {
            "error": (
                f"'todos' must be a list of objects, got {type(todos).__name__}. "
                "Example: todo(todos=[{\"id\": \"1\", \"content\": \"...\", \"status\": \"completed\"}])"
            )
        }

def tool_todo_write(params: Dict, state_file: Path) -> Dict:
    """
    Manage the task checklist.

    Status values: "not_started", "in_progress", "completed"

    To UPDATE specific items (partial update — other items unchanged):
      todo(todos=[{"id": "3", "status": "in_progress"}])
      todo(todos=[{"id": "3", "status": "completed"}, {"id": "4", "status": "in_progress"}])

    To REPLACE the entire list (used during initialisation):
      todo(todos=[{"id": "1", "content": "...", "status": "not_started"}, ...], replace=True)

    To READ the current list without modifying it:
      todo(op="read")   OR   todo()   (no arguments)

    Example — mark item 3 done and start item 4:
      todo(todos=[{"id": "3", "status": "completed"}, {"id": "4", "status": "in_progress"}])
    """
    # Read current state
    old_todos: List = []
    if state_file.exists():
        try:
            old_todos = json.loads(state_file.read_text(encoding="utf-8"))
        except Exception:
            old_todos = []

    op      = params.get("op", "")
    todos   = params.get("todos", None)
    replace = bool(params.get("replace", False))

    # Handle case where model passes todos as a JSON string instead of a list
    if isinstance(todos, str):
        try:
            todos = json.loads(todos)
        except Exception:
            return {
                "error": (
                    "'todos' must be a list of objects, not a string. "
                    "Example: todo(todos=[{\"id\": \"3\", \"status\": \"completed\"}])"
                )
            }

    # Read-only mode
    if op == "read" or todos is None:
        not_started = sum(1 for t in old_todos if t.get("status") == "not_started")
        in_progress  = sum(1 for t in old_todos if t.get("status") == "in_progress")
        completed    = sum(1 for t in old_todos if t.get("status") == "completed")
        return {
            "ok": True,
            "todos": old_todos,
            "count": len(old_todos),
            "not_started": not_started,
            "in_progress": in_progress,
            "completed": completed,
            "all_done": len(old_todos) > 0 and not_started == 0 and in_progress == 0,
            "note": "Read-only — pass todos=[...] to update items.",
        }

    # Validate todos is a list
    if not isinstance(todos, list):
        return {
            "error": (
                f"'todos' must be a list of objects, got {type(todos).__name__}. "
                "Example: todo(todos=[{\"id\": \"3\", \"status\": \"completed\"}])"
            )
        }

    valid_statuses = {"not_started", "in_progress", "completed"}

    if replace:
        # Full replacement — used during initialisation
        for t in todos:
            if t.get("status") not in valid_statuses:
                t["status"] = "not_started"
        new_todos = todos
    else:
        # Partial update — merge changes into existing list by id
        existing_by_id = {str(t.get("id", "")): t for t in old_todos}
        for update in todos:
            uid = str(update.get("id", ""))
            if not uid:
                continue
            if uid in existing_by_id:
                if "status" in update:
                    new_status = update["status"]
                    if new_status not in valid_statuses:
                        return {"error": f"Invalid status '{new_status}'. Must be one of: {sorted(valid_statuses)}"}
                    existing_by_id[uid]["status"] = new_status
                if "content" in update:
                    existing_by_id[uid]["content"] = update["content"]
            else:
                if update.get("status") not in valid_statuses:
                    update["status"] = "not_started"
                existing_by_id[uid] = update
        # Preserve original order, then append any new items
        seen_ids = {str(t.get("id", "")) for t in old_todos}
        new_todos = [existing_by_id[str(t.get("id", ""))] for t in old_todos if str(t.get("id", "")) in existing_by_id]
        for update in todos:
            uid = str(update.get("id", ""))
            if uid and uid not in seen_ids:
                new_todos.append(existing_by_id[uid])

    state_file.parent.mkdir(parents=True, exist_ok=True)
    state_file.write_text(json.dumps(new_todos, indent=2, ensure_ascii=False), encoding="utf-8")

    not_started = sum(1 for t in new_todos if t.get("status") == "not_started")
    in_progress  = sum(1 for t in new_todos if t.get("status") == "in_progress")
    completed    = sum(1 for t in new_todos if t.get("status") == "completed")
    return {
        "ok": True,
        "updated": len(todos),
        "total": len(new_todos),
        "not_started": not_started,
        "in_progress": in_progress,
        "completed": completed,
        "all_done": len(new_todos) > 0 and not_started == 0 and in_progress == 0,
    }


async def tool_batch_tool_call_async(params: Dict, tool_dispatcher: Callable[[str, Dict], Any]) -> Dict:
    tool_calls = params.get("tool_calls", [])
    if not tool_calls:
        return {"error": "tool_calls array is required. Pass tool_calls as a JSON array, not a string."}
    # Handle case where tool_calls is passed as a JSON string
    if isinstance(tool_calls, str):
        try:
            tool_calls = json.loads(tool_calls)
        except Exception:
            return {"error": "tool_calls must be a JSON array, not a string. Do not JSON-encode the array."}
    if not isinstance(tool_calls, list):
        return {"error": f"tool_calls must be an array, got {type(tool_calls).__name__}"}
    if len(tool_calls) > 25:
        discarded = tool_calls[25:]
        tool_calls = tool_calls[:25]
        discard_msg = f"WARNING: {len(discarded)} tool calls were discarded (max 25 per batch). Split into multiple batch() calls."
    else:
        discarded = []
        discard_msg = None

    # Detect duplicate write paths within this batch — warn before executing
    write_paths: Dict[str, int] = {}
    duplicate_warnings = []
    for idx, call in enumerate(tool_calls):
        if call.get("tool") == "write":
            path = call.get("parameters", {}).get("path", "")
            if path:
                if path in write_paths:
                    duplicate_warnings.append(
                        f"WARNING: path '{path}' is written twice in this batch "
                        f"(indices {write_paths[path]} and {idx}). "
                        f"The second write will overwrite the first."
                    )
                else:
                    write_paths[path] = idx

    async def run_one(call: Dict, idx: int) -> Dict:
        tool_name = call.get("tool", "")
        tool_params = call.get("parameters", call.get("params", {}))
        if tool_name == "BatchToolCall" or tool_name == "batch":
            return {"index": idx, "tool": tool_name, "error": "Recursive batch not allowed"}
        try:
            result = tool_dispatcher(tool_name, tool_params)
            if asyncio.iscoroutine(result):
                result = await result
            return {"index": idx, "tool": tool_name, "result": result}
        except Exception as e:
            return {"index": idx, "tool": tool_name, "error": str(e)}

    tasks = [run_one(call, i) for i, call in enumerate(tool_calls)]
    results = await asyncio.gather(*tasks)
    for call in discarded:
        results.append({"tool": call.get("tool", "?"), "error": "Discarded: max 25 tool calls per batch — split into multiple batch() calls"})
    successes = sum(1 for r in results if "error" not in r)
    failures = len(results) - successes
    response = {"ok": failures == 0, "total": len(results), "successes": successes, "failures": failures, "results": list(results)}
    if discard_msg:
        response["warning"] = discard_msg
    if duplicate_warnings:
        response["duplicate_path_warnings"] = duplicate_warnings
    return response


def tool_batch_tool_call(params: Dict, tool_dispatcher: Callable) -> Dict:
    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as pool:
                future = pool.submit(asyncio.run, tool_batch_tool_call_async(params, tool_dispatcher))
                return future.result()
        else:
            return loop.run_until_complete(tool_batch_tool_call_async(params, tool_dispatcher))
    except Exception as e:
        return {"error": str(e)}


# ---------------------------------------------------------------------------
# Build Bundle — create zip and trigger GitHub Actions build
# ---------------------------------------------------------------------------

def tool_build_bundle(
    params: Dict,
    session_dir: Path,
    repo_root: Path,
    metadata: Dict,
    target_list: List[Dict],
    slug: str,
    session_id: str,
) -> Dict:
    """
    Create a bundle zip from source files in session_dir/bundle/ and trigger
    the GitHub Actions build workflow. Returns structured build results.

    This is the CORRECT way to compile mods — never run Gradle locally.

    params:
      failed_only: bool — if True, only include previously failed targets
      modrinth_url: str — optional Modrinth URL for auto-publish
      max_parallel: str — max parallel jobs (default: "all")
      previous_results: dict — results from a previous build_bundle call
    """
    failed_only = bool(params.get("failed_only", False))
    modrinth_url = params.get("modrinth_url", "")
    max_parallel = params.get("max_parallel", "all")
    previous_results = params.get("previous_results")

    # ── Pre-bundle stub check ─────────────────────────────────────────────────
    # Refuse to bundle if the majority of Java files are stubs (< 200 bytes).
    # This catches the case where the agent wrote placeholder files and then
    # immediately called build_bundle without implementing real logic.
    bundle_dir = session_dir / "bundle"
    if bundle_dir.exists():
        all_java = list(bundle_dir.rglob("*.java"))
        if all_java:
            stub_files = [f for f in all_java if f.stat().st_size < 200]
            stub_ratio = len(stub_files) / len(all_java)
            if stub_ratio > 0.5:
                stub_examples = [str(f.relative_to(session_dir)) for f in stub_files[:5]]
                return {
                    "error": (
                        f"⛔ STUB FILES DETECTED — {len(stub_files)}/{len(all_java)} Java files are stubs "
                        f"(< 200 bytes). The build will fail. "
                        f"You MUST rewrite these files with real mod logic before calling build_bundle(). "
                        f"Examples of stub files: {stub_examples}. "
                        f"Read the decompiled source in project_info/first_version/decompiled/ "
                        f"and implement the actual logic."
                    ),
                    "ok": False,
                    "stub_count": len(stub_files),
                    "total_java_files": len(all_java),
                }

    try:
        sys.path.insert(0, str(repo_root))
        from aibasedversionupgrader.bundle_builder import create_bundle_zip, trigger_github_build

        # Create the zip
        zip_path = create_bundle_zip(
            session_dir=session_dir,
            metadata=metadata,
            target_list=target_list,
            slug=slug,
            session_id=session_id,
            repo_root=repo_root,
            failed_only=failed_only,
            previous_results=previous_results,
        )

        zip_size = zip_path.stat().st_size
        zip_rel = str(zip_path.relative_to(repo_root))

        # Count targets in zip
        import zipfile as _zipfile
        with _zipfile.ZipFile(zip_path) as zf:
            folders = {Path(n).parts[0] for n in zf.namelist() if "/" in n}
        target_count = len(folders)

        return {
            "ok": True,
            "zip_path": zip_rel,
            "zip_size_kb": zip_size // 1024,
            "target_count": target_count,
            "targets_included": sorted(folders),
            "message": (
                f"Bundle zip created: {zip_rel} ({zip_size // 1024} KB, {target_count} targets). "
                f"Now call trigger_build to start the GitHub Actions workflow."
            ),
        }
    except Exception as e:
        return {"error": str(e), "ok": False}


def tool_trigger_build(
    params: Dict,
    session_dir: Path,
    repo_root: Path,
    slug: str,
    session_id: str,
) -> Dict:
    """
    Commit the bundle zip, push it, and trigger the GitHub Actions build workflow.
    Waits for completion and returns structured results with pass/fail per target.

    This is a long-running operation (can take 30-120 minutes for large builds).
    Progress is streamed to the log.

    params:
      modrinth_url: str — optional Modrinth URL for auto-publish
      max_parallel: str — max parallel jobs (default: "all")
      timeout: int — max seconds to wait (default: 7200)
    """
    modrinth_url = params.get("modrinth_url", "")
    max_parallel = params.get("max_parallel", "all")
    timeout = int(params.get("timeout", 7200))

    # Find the most recent zip for this session
    incoming_dir = repo_root / "incoming"
    zips = sorted(incoming_dir.glob(f"*{session_id}*.zip"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not zips:
        # Try any zip in incoming
        zips = sorted(incoming_dir.glob("*.zip"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not zips:
        return {"error": "No bundle zip found in incoming/. Call build_bundle first.", "ok": False}

    zip_path = zips[0]
    zip_rel = str(zip_path.relative_to(repo_root))

    try:
        sys.path.insert(0, str(repo_root))
        from aibasedversionupgrader.bundle_builder import trigger_github_build

        output_dir = session_dir / "build_runs"
        result = trigger_github_build(
            zip_path=zip_path,
            repo_root=repo_root,
            modrinth_url=modrinth_url,
            max_parallel=max_parallel,
            output_dir=output_dir,
            timeout=timeout,
        )
        return result
    except Exception as e:
        return {"error": str(e), "ok": False}
