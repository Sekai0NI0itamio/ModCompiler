#!/usr/bin/env python3
"""
dif_core.py — Documentary of Issues and Fixes (DIF) Core Engine
================================================================
Unified core logic shared by:
  - run_build.py  (auto-search after failed builds + DIF matching)
  - ai_source_search.py  (source search + DIF matching)
  - dif_search.py  (standalone local NLP search tool)

The DIF system stores issue/fix documents in dif/ at the repo root.
Each file is a Markdown document with structured front-matter.

DIF file format
---------------
  ---
  id: UNIQUE-ID
  title: Short human-readable title
  tags: [forge, fabric, neoforge, compile-error, api-change, ...]
  versions: [1.21.6, 1.21.7, 1.21.8]
  loaders: [forge]
  symbols: [FarmlandTrampleEvent, EventBusSubscriber]
  error_patterns: ["cannot find symbol", "package.*does not exist"]
  ---

  ## Issue
  Description of the problem.

  ## Error
  ```
  exact error text
  ```

  ## Root Cause
  Why it happens.

  ## Fix
  How to fix it.

  ## Verified
  Which versions/runs confirmed this fix works.

Public API
----------
  from scripts.dif_core import (
      DifEngine,
      SourceSearchEngine,
      search_dif,          # NLP search returning ranked results
      match_errors_to_dif, # match build log errors to DIF entries
      run_source_search,   # unified source search (local repo or workflow)
  )
"""

from __future__ import annotations

import json
import math
import os
import re
import subprocess
import sys
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DIF_DIR = ROOT / "dif"
DECOMPILED_DIR = ROOT / "DecompiledMinecraftSourceCode"

# ---------------------------------------------------------------------------
# DIF Engine — load, index, search
# ---------------------------------------------------------------------------

class DifEntry:
    """One parsed DIF document."""

    def __init__(self, path: Path, front: dict, body: str) -> None:
        self.path = path
        self.id: str = front.get("id", path.stem)
        self.title: str = front.get("title", "")
        self.tags: list[str] = _listify(front.get("tags", []))
        self.versions: list[str] = _listify(front.get("versions", []))
        self.loaders: list[str] = _listify(front.get("loaders", []))
        self.symbols: list[str] = _listify(front.get("symbols", []))
        self.error_patterns: list[str] = _listify(front.get("error_patterns", []))
        self.body: str = body
        self.full_text: str = (
            self.title + " " + " ".join(self.tags) + " " +
            " ".join(self.symbols) + " " + body
        ).lower()

    def matches_error(self, log_text: str) -> float:
        """Return a 0-1 confidence that this entry matches the given build log."""
        score = 0.0
        log_lower = log_text.lower()

        # Error pattern matches (highest weight)
        for pat in self.error_patterns:
            try:
                if re.search(pat, log_text, re.IGNORECASE):
                    score += 0.4
            except re.error:
                if pat.lower() in log_lower:
                    score += 0.4

        # Symbol matches
        for sym in self.symbols:
            if sym.lower() in log_lower:
                score += 0.2

        # Tag matches (e.g. "compile-error", "forge")
        for tag in self.tags:
            if tag.lower() in log_lower:
                score += 0.05

        return min(score, 1.0)

    def nlp_score(self, query_tokens: list[str]) -> float:
        """TF-IDF-style relevance score for a natural language query."""
        if not query_tokens:
            return 0.0
        matches = sum(1 for t in query_tokens if t in self.full_text)
        # Boost for title matches
        title_matches = sum(1 for t in query_tokens if t in self.title.lower())
        # Boost for symbol matches
        sym_matches = sum(1 for t in query_tokens
                          if any(t in s.lower() for s in self.symbols))
        raw = (matches + title_matches * 2 + sym_matches * 1.5) / max(len(query_tokens), 1)
        return min(raw, 1.0)

    def to_summary(self) -> str:
        """One-line summary for search results."""
        return f"[{self.id}] {self.title}"

    def to_result_block(self, score: float) -> str:
        """Full result block shown in search output."""
        pct = int(score * 100)
        lines = [
            f"### {pct}% — {self.title}",
            f"**File**: `{self.path.relative_to(ROOT)}`  ",
            f"**ID**: `{self.id}`  ",
        ]
        if self.tags:
            lines.append(f"**Tags**: {', '.join(self.tags)}  ")
        if self.versions:
            lines.append(f"**Versions**: {', '.join(self.versions)}  ")
        if self.loaders:
            lines.append(f"**Loaders**: {', '.join(self.loaders)}  ")
        if self.symbols:
            lines.append(f"**Symbols**: `{'`, `'.join(self.symbols)}`  ")
        lines.append("")
        # Include first 40 lines of body
        body_lines = self.body.strip().splitlines()[:40]
        lines.extend(body_lines)
        if len(self.body.strip().splitlines()) > 40:
            lines.append(f"\n*... ({len(self.body.strip().splitlines()) - 40} more lines — read `{self.path.relative_to(ROOT)}`)*")
        return "\n".join(lines)


class DifEngine:
    """Load and search the DIF directory."""

    def __init__(self, dif_dir: Path = DIF_DIR) -> None:
        self.dif_dir = dif_dir
        self._entries: list[DifEntry] | None = None

    def entries(self) -> list[DifEntry]:
        if self._entries is None:
            self._entries = self._load_all()
        return self._entries

    def _load_all(self) -> list[DifEntry]:
        if not self.dif_dir.exists():
            return []
        result = []
        for f in sorted(self.dif_dir.glob("*.md")):
            entry = _parse_dif_file(f)
            if entry:
                result.append(entry)
        return result

    def search(self, query: str, top_n: int = 10) -> list[tuple[float, DifEntry]]:
        """
        Natural language search. Returns list of (score, entry) sorted by score desc.
        Always returns at least top_n results (or all if fewer exist).
        """
        tokens = _tokenize(query)
        scored = [(e.nlp_score(tokens), e) for e in self.entries()]
        scored.sort(key=lambda x: x[0], reverse=True)
        # Filter out zero-score entries unless we have fewer than top_n
        nonzero = [(s, e) for s, e in scored if s > 0]
        if len(nonzero) >= top_n:
            return nonzero[:top_n]
        # Pad with zero-score entries if needed
        return scored[:top_n]

    def match_errors(self, log_text: str, threshold: float = 0.3) -> list[tuple[float, DifEntry]]:
        """
        Match a build log against all DIF entries.
        Returns entries with confidence >= threshold, sorted desc.
        """
        scored = [(e.matches_error(log_text), e) for e in self.entries()]
        scored = [(s, e) for s, e in scored if s >= threshold]
        scored.sort(key=lambda x: x[0], reverse=True)
        return scored

    def create_entry(self, path: Path, front: dict, body: str) -> DifEntry:
        """Write a new DIF entry to disk and add it to the index."""
        content = _render_dif_file(front, body)
        path.write_text(content, encoding="utf-8")
        entry = DifEntry(path, front, body)
        if self._entries is not None:
            # Remove any existing entry with same id
            self._entries = [e for e in self._entries if e.id != entry.id]
            self._entries.append(entry)
        return entry


# ---------------------------------------------------------------------------
# Source Search Engine — unified local + workflow search
# ---------------------------------------------------------------------------

class SourceSearchEngine:
    """
    Unified source search. Searches DecompiledMinecraftSourceCode/ locally
    (instant) and falls back to the GitHub Actions workflow if the folder
    is missing.
    """

    def __init__(self, repo: str = "", token: str = "") -> None:
        self.repo = repo or _detect_repo()
        self.token = token or _detect_token()

    def search(
        self,
        version: str,
        loader: str,
        symbols: list[str],
        out_dir: Path,
        context_lines: int = 8,
        dump_full_class: bool = True,
    ) -> dict:
        """
        Search for symbols in the decompiled sources for version+loader.
        Returns a result dict with keys: source, java_count, query_results,
        full_classes, out_dir, symbols.
        """
        slug = f"{version}-{loader}"
        sources_folder = DECOMPILED_DIR / slug
        java_files = list(sources_folder.rglob("*.java")) if sources_folder.is_dir() else []

        if not java_files:
            return self._workflow_search(version, loader, symbols, out_dir)

        return self._local_search(
            version, loader, symbols, java_files, sources_folder, out_dir,
            context_lines, dump_full_class
        )

    def _local_search(
        self,
        version: str,
        loader: str,
        symbols: list[str],
        java_files: list[Path],
        sources_folder: Path,
        out_dir: Path,
        context_lines: int,
        dump_full_class: bool,
    ) -> dict:
        out_dir.mkdir(parents=True, exist_ok=True)
        queries = _symbols_to_queries(set(symbols))
        query_results: dict[str, list[str]] = {}
        full_classes: dict[str, str] = {}

        for query in queries:
            matches = []
            for jf in java_files:
                try:
                    text = jf.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                if query.lower() in text.lower():
                    lines = text.splitlines()
                    for i, line in enumerate(lines):
                        if query.lower() in line.lower():
                            start = max(0, i - context_lines)
                            end = min(len(lines), i + context_lines + 1)
                            ctx = lines[start:end]
                            rel = str(jf.relative_to(sources_folder))
                            matches.append(f"=== {rel} (line {i+1}) ===")
                            matches.extend(ctx)
                            matches.append("")
                            if dump_full_class:
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
            (queries_dir / f"{safe_q}.txt").write_text("\n".join(lines), encoding="utf-8")

        # Write full class files
        if dump_full_class:
            classes_dir = out_dir / "full-classes"
            classes_dir.mkdir(exist_ok=True)
            for fname, content in list(full_classes.items())[:20]:
                safe_fname = re.sub(r"[^A-Za-z0-9._-]+", "_", fname)
                (classes_dir / safe_fname).write_text(content, encoding="utf-8")

        # API overview
        overview_dir = out_dir / "api-overview"
        overview_dir.mkdir(exist_ok=True)
        event_files = [str(f.relative_to(sources_folder))
                       for f in java_files if "event" in f.name.lower()]
        render_files = [str(f.relative_to(sources_folder))
                        for f in java_files
                        if any(k in f.name.lower()
                               for k in ("render", "gui", "hud", "overlay", "layer"))]
        loader_files = [str(f.relative_to(sources_folder))
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
        (out_dir / "all-java-files.txt").write_text(
            "\n".join(str(f.relative_to(sources_folder)) for f in sorted(java_files)),
            encoding="utf-8")

        return {
            "source": "repo",
            "java_count": len(java_files),
            "query_results": {q: len(v) for q, v in query_results.items()},
            "full_classes": list(full_classes.keys()),
            "out_dir": str(out_dir),
            "symbols": sorted(symbols),
        }

    def _workflow_search(
        self,
        version: str,
        loader: str,
        symbols: list[str],
        out_dir: Path,
    ) -> dict:
        """Fall back to GitHub Actions workflow for versions not in the repo."""
        queries = _symbols_to_queries(set(symbols))
        query_str = ",".join(queries[:6])
        run_id = _trigger_source_search_workflow(
            version=version, loader=loader, queries=query_str,
            repo=self.repo, token=self.token,
        )
        if not run_id:
            return {"source": "workflow-failed", "java_count": 0,
                    "out_dir": str(out_dir), "symbols": sorted(symbols)}

        # Wait for completion
        deadline = time.time() + 1800
        while time.time() < deadline:
            time.sleep(20)
            try:
                data = json.loads(_gh([
                    "run", "view", str(run_id), "-R", self.repo,
                    "--json", "status,conclusion",
                ], token=self.token))
            except Exception:
                continue
            if data.get("status") == "completed":
                break

        out_dir.mkdir(parents=True, exist_ok=True)
        try:
            _gh(["run", "download", str(run_id), "-R", self.repo,
                 "-D", str(out_dir)], token=self.token)
        except Exception:
            pass

        java_count = 0
        for info_file in out_dir.rglob("search-info.txt"):
            for line in info_file.read_text(encoding="utf-8").splitlines():
                if line.startswith("Java files:"):
                    try:
                        java_count = int(line.split(":")[1].strip())
                    except ValueError:
                        pass
            break

        return {
            "source": "workflow",
            "run_id": run_id,
            "java_count": java_count,
            "out_dir": str(out_dir),
            "symbols": sorted(symbols),
        }


# ---------------------------------------------------------------------------
# Public convenience functions
# ---------------------------------------------------------------------------

_dif_engine = DifEngine()


def search_dif(query: str, top_n: int = 10, show_all: bool = False) -> list[tuple[float, DifEntry]]:
    """
    Natural language search of the DIF database.
    Returns (score, entry) pairs sorted by relevance descending.
    Always returns top 3 minimum (or all if show_all=True).
    """
    results = _dif_engine.search(query, top_n=top_n)
    if show_all:
        return results
    return results[:max(top_n, 3)]


def match_errors_to_dif(log_text: str, threshold: float = 0.25) -> list[tuple[float, DifEntry]]:
    """
    Match a build log against the DIF database.
    Returns (confidence, entry) pairs for likely matches.
    """
    return _dif_engine.match_errors(log_text, threshold=threshold)


def run_source_search(
    version: str,
    loader: str,
    symbols: list[str],
    out_dir: Path,
    repo: str = "",
    token: str = "",
    context_lines: int = 8,
    dump_full_class: bool = True,
) -> dict:
    """
    Unified source search. Searches locally if available, falls back to workflow.
    """
    engine = SourceSearchEngine(repo=repo, token=token)
    return engine.search(
        version=version, loader=loader, symbols=symbols, out_dir=out_dir,
        context_lines=context_lines, dump_full_class=dump_full_class,
    )


def extract_missing_symbols(log_text: str) -> set[str]:
    """Extract missing class/package names from a Java compile error log."""
    symbols: set[str] = set()
    for line in log_text.splitlines():
        m = re.search(r"symbol:\s+class\s+(\w+)", line)
        if m:
            sym = m.group(1)
            if sym not in _SKIP_SYMBOLS and len(sym) > 3:
                symbols.add(sym)
            continue
        m = re.search(r"package\s+([\w.]+)\s+does not exist", line)
        if m:
            pkg = m.group(1)
            parts = pkg.split(".")
            if len(parts) >= 2:
                symbols.add(parts[-1])
    return symbols


def print_search_results(
    results: list[tuple[float, DifEntry]],
    query: str = "",
    always_show_top3: bool = True,
) -> None:
    """
    Print search results in a human-readable format.
    Always shows the full content of the top 3 results.
    """
    if not results:
        print("No DIF entries found.")
        return

    if query:
        print(f"\n{'=' * 60}")
        print(f"DIF SEARCH: {query!r}")
        print(f"{'=' * 60}")

    print(f"\nFound {len(results)} result(s):\n")

    for i, (score, entry) in enumerate(results):
        pct = int(score * 100)
        is_top3 = i < 3

        if is_top3 or always_show_top3:
            # Full content for top 3
            print(f"\n{'─' * 60}")
            print(f"#{i+1}  {pct}% match — {entry.title}")
            print(f"File: {entry.path.relative_to(ROOT)}")
            if entry.tags:
                print(f"Tags: {', '.join(entry.tags)}")
            if entry.versions:
                print(f"Versions: {', '.join(entry.versions)}")
            if entry.symbols:
                print(f"Symbols: {', '.join(entry.symbols)}")
            print()
            # Print full body
            body_lines = entry.body.strip().splitlines()
            for line in body_lines[:60]:
                print(line)
            if len(body_lines) > 60:
                print(f"\n... ({len(body_lines) - 60} more lines — read {entry.path.relative_to(ROOT)})")
        else:
            # Summary for lower results
            print(f"  #{i+1}  {pct}%  {entry.to_summary()}")

    print()


# ---------------------------------------------------------------------------
# DIF file parsing and rendering
# ---------------------------------------------------------------------------

def _parse_dif_file(path: Path) -> DifEntry | None:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return None

    front: dict = {}
    body = text

    # Parse YAML-like front matter between --- delimiters
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            fm_text = text[3:end].strip()
            body = text[end + 4:].strip()
            for line in fm_text.splitlines():
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if ":" in line:
                    key, _, val = line.partition(":")
                    key = key.strip()
                    val = val.strip()
                    # Handle list values: [a, b, c] or - item
                    if val.startswith("[") and val.endswith("]"):
                        items = [x.strip().strip('"\'') for x in val[1:-1].split(",")]
                        front[key] = [x for x in items if x]
                    else:
                        front[key] = val.strip('"\'')

    return DifEntry(path, front, body)


def _render_dif_file(front: dict, body: str) -> str:
    lines = ["---"]
    for key, val in front.items():
        if isinstance(val, list):
            lines.append(f"{key}: [{', '.join(str(v) for v in val)}]")
        else:
            lines.append(f"{key}: {val}")
    lines.append("---")
    lines.append("")
    lines.append(body.strip())
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# NLP helpers
# ---------------------------------------------------------------------------

_STOP_WORDS = {
    "a", "an", "the", "is", "it", "in", "on", "at", "to", "for", "of",
    "and", "or", "but", "not", "with", "this", "that", "was", "are",
    "be", "been", "have", "has", "had", "do", "does", "did", "will",
    "would", "could", "should", "may", "might", "can", "from", "by",
    "as", "if", "when", "then", "so", "also", "which", "how", "what",
    "why", "where", "who", "i", "we", "you", "he", "she", "they",
    "my", "our", "your", "his", "her", "its", "their",
}


def _tokenize(text: str) -> list[str]:
    """Tokenize text for NLP search — lowercase, split on non-alphanumeric."""
    tokens = re.findall(r"[a-zA-Z0-9_]+", text.lower())
    # Keep camelCase components too
    expanded = []
    for t in tokens:
        expanded.append(t)
        # Split camelCase: FarmlandBlock -> farmland, block
        parts = re.findall(r"[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)", t)
        expanded.extend(p.lower() for p in parts if len(p) > 2)
    return [t for t in expanded if t not in _STOP_WORDS and len(t) > 1]


def _listify(val: Any) -> list[str]:
    if isinstance(val, list):
        return [str(x) for x in val]
    if isinstance(val, str) and val:
        return [val]
    return []


# ---------------------------------------------------------------------------
# Symbol extraction helpers
# ---------------------------------------------------------------------------

_SKIP_SYMBOLS = {
    "String", "int", "long", "boolean", "void", "Object", "List", "Map",
    "Set", "Optional", "File", "Path", "IOException", "Exception",
    "Override", "Nullable", "NotNull", "ApiStatus", "Deprecated",
    "Cancelable", "Event", "IModBusEvent", "SubscribeEvent",
}

_PRIORITY_KEYWORDS = [
    "Render", "Gui", "Hud", "Overlay", "Layer", "Event",
    "Register", "Callback", "Draw", "Graphics", "Farmland",
    "Trample", "Block", "Screen", "Button",
]


def _symbols_to_queries(symbols: set[str]) -> list[str]:
    prioritized = []
    others = []
    for sym in sorted(symbols):
        if any(kw.lower() in sym.lower() for kw in _PRIORITY_KEYWORDS):
            prioritized.append(sym)
        else:
            others.append(sym)
    result = prioritized[:5] + others[:3]
    return result[:8] if result else list(symbols)[:6]


# ---------------------------------------------------------------------------
# GitHub CLI helpers (shared with run_build.py and ai_source_search.py)
# ---------------------------------------------------------------------------

MAX_GH_RETRIES = 4
GH_RETRY_DELAY = 3.0


def _detect_repo() -> str:
    try:
        url = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            stderr=subprocess.DEVNULL, text=True).strip()
    except subprocess.CalledProcessError:
        return ""
    m = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    return m.group(1) if m else ""


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


def _gh(args: list[str], *, token: str = "", retries: int = MAX_GH_RETRIES) -> str:
    env = os.environ.copy()
    if token:
        env["GH_TOKEN"] = token
        env["GITHUB_TOKEN"] = token
    last_err = ""
    for attempt in range(1, retries + 1):
        try:
            result = subprocess.run(
                ["gh"] + args, env=env, check=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            )
            return result.stdout
        except subprocess.CalledProcessError as exc:
            stderr = (exc.stderr or "").strip()
            stdout = (exc.stdout or "").strip()
            last_err = stderr or stdout or f"exit {exc.returncode}"
            transient = any(m in last_err.lower() for m in (
                "connection reset", "tls handshake", "i/o timeout",
                "timeout", "unexpected eof", "connection refused",
                "temporary failure", "no such host",
            ))
            if attempt < retries and transient:
                time.sleep(GH_RETRY_DELAY * attempt)
                continue
            break
    raise RuntimeError(f"gh {' '.join(args[:3])}... failed: {last_err}")


def _trigger_source_search_workflow(
    version: str, loader: str, queries: str, repo: str, token: str,
) -> int | None:
    before_trigger = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    try:
        _gh([
            "workflow", "run", "ai-source-search.yml", "-R", repo,
            "-f", f"minecraft_version={version}",
            "-f", f"loader={loader}",
            "-f", f"queries={queries}",
            "-f", "file_patterns=*.java",
            "-f", "context_lines=8",
            "-f", "dump_full_class=yes",
        ], token=token)
    except RuntimeError:
        return None
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
        except Exception:
            pass
    return None
