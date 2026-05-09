#!/usr/bin/env python3
"""
bundle_builder.py — Bundle creation and GitHub Actions build trigger
=====================================================================
Implements the correct multi-version build workflow:

1. AI writes source files for all targets into session_dir/bundle/<target>/src/
2. AI calls build_bundle() which:
   a. Generates mod.txt and version.txt for each target
   b. Creates a zip in incoming/<slug>-<session_id>.zip
   c. Commits and pushes the zip to the repo
   d. Triggers the GitHub Actions build.yml workflow via run_build.py
   e. Streams live progress and returns structured results
3. AI reads results, fixes only failed targets
4. AI calls build_bundle(failed_only=True) to rebuild only failures

Bundle zip format (matches build_mods.py prepare expectations):
  <target_folder>/
    mod.txt          - mod metadata (mod_id, name, group, entrypoint_class, etc.)
    version.txt      - version info (minecraft_version, loader)
    src/
      main/java/...  - Java source files
      main/resources/- Resource files (mods.toml, fabric.mod.json, etc.)
      client/java/...  - Client-only source (Fabric split)
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Any

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _make_mod_txt(metadata: Dict) -> str:
    """Generate mod.txt content from metadata dict."""
    lines = []
    for key in ("mod_id", "name", "mod_version", "group", "entrypoint_class",
                "description", "authors", "license"):
        val = metadata.get(key, "")
        if isinstance(val, list):
            val = ", ".join(str(v) for v in val)
        lines.append(f"{key}={val}")
    for key in ("homepage", "sources", "issues", "runtime_side"):
        val = metadata.get(key)
        if val:
            lines.append(f"{key}={val}")
    return "\n".join(lines) + "\n"


def _make_version_txt(minecraft_version: str, loader: str) -> str:
    return f"minecraft_version={minecraft_version}\nloader={loader}\n"


def create_bundle_zip(
    session_dir: Path,
    metadata: Dict,
    target_list: List[Dict],
    slug: str,
    session_id: str,
    repo_root: Path,
    failed_only: bool = False,
    previous_results: Optional[Dict] = None,
) -> Path:
    """
    Create a bundle zip from source files written by the AI.

    The AI writes source files to:
      session_dir/bundle/<mc_version>-<loader>/src/main/java/...
      session_dir/bundle/<mc_version>-<loader>/src/main/resources/...

    This function:
    1. Reads those source files
    2. Adds mod.txt and version.txt for each target
    3. Creates a zip in incoming/<slug>-<session_id>.zip

    Returns the path to the created zip.
    """
    bundle_dir = session_dir / "bundle"
    bundle_dir.mkdir(parents=True, exist_ok=True)

    # Determine which targets to include
    if failed_only and previous_results:
        failed_targets = {
            f"{r['minecraft_version']}/{r['loader']}"
            for r in previous_results.get("failed", [])
        }
        targets = [t for t in target_list
                   if f"{t['minecraft_version']}/{t['loader']}" in failed_targets]
        if not targets:
            targets = target_list  # fallback: include all if no failures recorded
    else:
        targets = target_list

    mod_txt_content = _make_mod_txt(metadata)

    for target in targets:
        mc_version = target["minecraft_version"]
        loader = target["loader"]
        folder_name = f"{mc_version}-{loader}"
        target_dir = bundle_dir / folder_name

        # Write mod.txt and version.txt
        _write(target_dir / "mod.txt", mod_txt_content)
        _write(target_dir / "version.txt", _make_version_txt(mc_version, loader))

        # Source files should already be in target_dir/src/ (written by AI)
        # If not, create a minimal placeholder
        src_dir = target_dir / "src"
        if not src_dir.exists() or not any(src_dir.rglob("*.java")):
            # Create minimal placeholder so the zip is valid
            pkg_path = metadata.get("group", "com.example.mod").replace(".", "/")
            class_name = metadata.get("entrypoint_class", "com.example.mod.Mod").split(".")[-1]
            placeholder = target_dir / "src" / "main" / "java" / pkg_path / f"{class_name}.java"
            _write(placeholder, f"package {metadata.get('group', 'com.example.mod')};\n// TODO: implement\n")

    # Create the zip
    zip_name = f"{slug}-{session_id}.zip"
    zip_path = repo_root / "incoming" / zip_name
    zip_path.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(bundle_dir.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(bundle_dir)
            if len(rel.parts) < 2:
                continue  # skip files directly in bundle_dir root
            zf.write(path, rel)

    return zip_path


def trigger_github_build(
    zip_path: Path,
    repo_root: Path,
    modrinth_url: str = "",
    max_parallel: str = "all",
    output_dir: Optional[Path] = None,
    timeout: int = 7200,
) -> Dict[str, Any]:
    """
    Commit the zip, push it, and trigger the GitHub Actions build workflow.
    Uses scripts/run_build.py which handles everything.

    Returns a dict with:
      - success: bool
      - conclusion: str (success/failure/cancelled)
      - run_url: str
      - output_dir: str
      - summary: str (content of SUMMARY.md)
      - failed_targets: list of {minecraft_version, loader} dicts
      - passed_targets: list of {minecraft_version, loader} dicts
      - error: str (if something went wrong)
    """
    run_build = repo_root / "scripts" / "run_build.py"
    if not run_build.exists():
        return {"success": False, "error": f"run_build.py not found at {run_build}"}

    # Check gh CLI is available
    if not shutil.which("gh"):
        return {"success": False, "error": "GitHub CLI (gh) not found. Install it and authenticate with 'gh auth login'."}

    # Commit and push the zip
    zip_rel = str(zip_path.relative_to(repo_root))
    commit_result = _commit_and_push(repo_root, zip_rel)
    if not commit_result["ok"]:
        return {"success": False, "error": f"Failed to commit/push zip: {commit_result['error']}"}

    # Set up output directory
    if output_dir is None:
        output_dir = repo_root / "ModCompileRuns"

    # Build the run_build.py command
    cmd = [
        sys.executable, str(run_build),
        zip_rel,
        "--output-dir", str(output_dir),
        "--timeout", str(timeout),
        "--max-parallel", max_parallel,
    ]
    if modrinth_url:
        cmd += ["--modrinth", modrinth_url]

    # Run it — this streams progress to stdout and waits for completion
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd=str(repo_root),
            timeout=timeout + 120,  # extra buffer
        )
        stdout = result.stdout
        stderr = result.stderr
        success = result.returncode == 0
    except subprocess.TimeoutExpired:
        return {"success": False, "error": f"run_build.py timed out after {timeout + 120}s"}
    except Exception as e:
        return {"success": False, "error": str(e)}

    # Parse the output to find the run folder
    run_folder = _extract_run_folder(stdout, output_dir)

    # Read SUMMARY.md if available
    summary = ""
    failed_targets = []
    passed_targets = []

    if run_folder and run_folder.exists():
        summary_path = run_folder / "SUMMARY.md"
        if summary_path.exists():
            summary = summary_path.read_text(encoding="utf-8")

        build_summary = run_folder / "artifacts" / "all-mod-builds" / "SUMMARY.md"
        if build_summary.exists():
            summary = build_summary.read_text(encoding="utf-8")
            failed_targets, passed_targets = _parse_build_summary(summary)

    # Extract run URL from stdout
    run_url = ""
    for line in stdout.splitlines():
        if "github.com" in line and "/actions/runs/" in line:
            run_url = line.strip()
            break

    return {
        "success": success,
        "conclusion": "success" if success else "failure",
        "run_url": run_url,
        "output_dir": str(run_folder) if run_folder else str(output_dir),
        "summary": summary[:5000] if summary else "(no summary available)",
        "failed_targets": failed_targets,
        "passed_targets": passed_targets,
        "stdout": stdout[-3000:] if len(stdout) > 3000 else stdout,
        "stderr": stderr[-1000:] if stderr else "",
        "error": None,
    }


def _commit_and_push(repo_root: Path, zip_rel: str) -> Dict:
    """Commit and push the bundle zip to the repo."""
    try:
        # Stage the zip
        r = subprocess.run(
            ["git", "add", zip_rel],
            capture_output=True, text=True, cwd=str(repo_root)
        )
        if r.returncode != 0:
            return {"ok": False, "error": r.stderr}

        # Commit
        r = subprocess.run(
            ["git", "commit", "-m", f"Add bundle: {zip_rel}"],
            capture_output=True, text=True, cwd=str(repo_root)
        )
        if r.returncode != 0 and "nothing to commit" not in r.stdout:
            return {"ok": False, "error": r.stderr}

        # Push
        r = subprocess.run(
            ["git", "push"],
            capture_output=True, text=True, cwd=str(repo_root)
        )
        if r.returncode != 0:
            return {"ok": False, "error": r.stderr}

        return {"ok": True}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def _extract_run_folder(stdout: str, output_dir: Path) -> Optional[Path]:
    """Extract the run folder path from run_build.py output."""
    for line in stdout.splitlines():
        if "Run folder:" in line or "run-" in line:
            parts = line.split(":", 1)
            if len(parts) == 2:
                candidate = Path(parts[1].strip())
                if candidate.exists():
                    return candidate
    # Try to find the most recent run folder
    if output_dir.exists():
        runs = sorted(output_dir.glob("run-*"), key=lambda p: p.stat().st_mtime, reverse=True)
        if runs:
            return runs[0]
    return None


def _parse_build_summary(summary: str) -> tuple:
    """Parse SUMMARY.md to extract failed and passed targets."""
    failed = []
    passed = []
    for line in summary.splitlines():
        # Look for table rows: | mod_id | name | version | folder | loader | status | ...
        if "|" not in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 7:
            continue
        # Status is typically in column 6 (0-indexed after leading |)
        status = parts[5].lower() if len(parts) > 5 else ""
        mc_version = parts[3] if len(parts) > 3 else ""
        loader = parts[5] if len(parts) > 5 else ""
        if "success" in status:
            passed.append({"minecraft_version": mc_version, "loader": loader})
        elif "fail" in status or "error" in status:
            failed.append({"minecraft_version": mc_version, "loader": loader})
    return failed, passed
