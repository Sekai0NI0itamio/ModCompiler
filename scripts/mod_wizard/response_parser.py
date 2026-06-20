"""
mod_wizard/response_parser.py — Parse AI responses into files and metadata.

Extracts source files and metadata from an AI response using the
---FILE: path--- format and ---METADATA--- block conventions.
"""

from __future__ import annotations

import json
import re
from typing import Any


def parse(text: str) -> dict[str, Any]:
    """Parse an AI response text into files, metadata, and summary.

    Returns:
        {"files": {path: content, ...}, "metadata": {key: value, ...},
         "summary": str}
    """
    files: dict[str, str] = {}
    metadata: dict[str, str] = {}
    summary = ""

    # ── Extract ---SUMMARY--- block ──
    summary_match = re.search(
        r'---SUMMARY---[ \t]*\n?(.*?)(?:\n---|$)', text, re.DOTALL
    )
    if summary_match:
        summary = summary_match.group(1).strip()

    # ── Extract explicit METADATA block ──
    meta_match = re.search(r'---METADATA---[ \t]*\n?(.*?)(?:\n---|$)', text, re.DOTALL)
    if meta_match:
        for line in meta_match.group(1).strip().splitlines():
            if ':' in line:
                key, val = line.split(':', 1)
                metadata[key.strip()] = val.strip()

    # ── Extract ---FILE: path--- blocks ──
    file_pattern = re.compile(
        r'(?:---|\\*\\*)?FILE:\s*(.+?)(?:---|\\*\\*)?\s*\n```(\w*)\s*\n((?:(?!(?:---|\\*\\*)?FILE:|---METADATA).)*?)\n```',
        re.DOTALL,
    )
    for match in file_pattern.finditer(text):
        filepath = match.group(1).strip().lstrip("./")
        content = match.group(3).strip()

        if _skip_file(filepath, content):
            continue

        filepath = _normalize_path(filepath)
        files[filepath] = content

    # ── Fallback: filepath: / path: lines before code blocks ──
    fb_pattern = re.compile(
        r'(?:^|\n)(?:filepath|path):\s*(.+?\.(?:java|json|lang|txt|info))\s*\n'
        r'```(\w*)\s*\n(.*?)\n```',
        re.DOTALL | re.MULTILINE,
    )
    for match in fb_pattern.finditer(text):
        fp = match.group(1).strip().lstrip("./")
        ct = match.group(3).strip()
        if fp not in files and not _skip_file(fp, ct):
            fp = _normalize_path(fp)
            if not fp.endswith("build.gradle") and "gradle/" not in fp:
                files[fp] = ct

    # ── Auto-extract metadata from source if not explicitly provided ──
    if not metadata:
        metadata = _extract_from_files(files)

    return {"files": files, "metadata": metadata, "summary": summary}


def _skip_file(filepath: str, content: str) -> bool:
    """Return True if this file should be skipped."""
    if not content or len(content) < 30:
        return True
    if filepath.endswith("build.gradle") or "gradle/" in filepath:
        return True
    if filepath.endswith("gradlew") or filepath.endswith("gradle.properties"):
        return True
    if len(content) < 50 and "/" in content and "\n" not in content:
        return True
    return False


def _normalize_path(filepath: str) -> str:
    """Normalize filepath to src/main/... format. Strips absolute paths."""
    # Strip absolute path prefixes (from fix responses that include full paths)
    abs_prefixes = [
        "/build/sources/main/",
        "/src/main/",
        "build/sources/main/",
    ]
    for prefix in abs_prefixes:
        idx = filepath.find(prefix)
        if idx >= 0:
            filepath = "src/main/" + filepath[idx + len(prefix):]
            break

    # Standard relative path normalization
    if filepath.startswith("src/"):
        return filepath
    if filepath.startswith("main/"):
        return "src/" + filepath
    if filepath.startswith("java/"):
        return "src/main/" + filepath
    if filepath.startswith("resources/"):
        return "src/" + filepath
    if filepath.endswith(".java") and "/" not in filepath:
        return "src/main/java/asd/itamio/unknown/" + filepath
    return filepath


def _extract_from_files(files: dict[str, str]) -> dict[str, str]:
    """Extract mod metadata from Java source files and mcmod.info."""
    md: dict[str, str] = {}

    for content in files.values():
        # MODID
        m = re.search(
            r'public\s+static\s+final\s+String\s+MODID\s*=\s*"([^"]+)"', content
        )
        if m and "mod_id" not in md:
            md["mod_id"] = m.group(1)

        # NAME
        m = re.search(
            r'public\s+static\s+final\s+String\s+NAME\s*=\s*"([^"]+)"', content
        )
        if m and "name" not in md:
            md["name"] = m.group(1)

        # Package → group
        m = re.search(r'^package\s+([a-z0-9_.]+)\s*;', content, re.MULTILINE)
        if m and "group" not in md:
            md["group"] = m.group(1)

        # VERSION
        m = re.search(
            r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"', content
        )
        if m and "mod_version" not in md:
            md["mod_version"] = m.group(1)

    # Try mcmod.info
    for fp, content in files.items():
        if "mcmod.info" in fp:
            try:
                mcmod = json.loads(content)
                if isinstance(mcmod, list) and mcmod:
                    e = mcmod[0]
                    if "modid" in e and "mod_id" not in md:
                        md["mod_id"] = e["modid"]
                    if "name" in e and "name" not in md:
                        md["name"] = e["name"]
            except json.JSONDecodeError:
                pass

    # Derive archivesBaseName from name
    if "name" in md and "archivesBaseName" not in md:
        md["archivesBaseName"] = md["name"].replace(" ", "-").replace("'", "")

    # Defaults
    md.setdefault("mod_id", "unknown_mod")
    md.setdefault("group", "asd.itamio.unknown")
    md.setdefault("mod_version", "1.0.0")
    md.setdefault("archivesBaseName", md["mod_id"])

    return md
