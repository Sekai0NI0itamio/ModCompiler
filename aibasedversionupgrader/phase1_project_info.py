#!/usr/bin/env python3
"""
phase1_project_info.py — Phase 1: Gather Project Info
======================================================
Fetches Modrinth project metadata and decompiles the first published version.
Saves everything to project_info/ directory.

Usage:
    python3 aibasedversionupgrader/phase1_project_info.py <modrinth_url>
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

OUTPUT_DIR = Path("project_info")
MODRINTH_API = "https://api.modrinth.com/v2"
USER_AGENT = "ModVersionConverter/1.0 (github.com/mod-compiler)"


def _get(url: str, token: Optional[str] = None, retries: int = 5) -> dict:
    """HTTP GET with retry and exponential backoff."""
    from typing import Optional
    headers = {"User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = token
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            if attempt < retries - 1:
                wait = 2 ** attempt
                print(f"  HTTP {e.code}, retrying in {wait}s...")
                time.sleep(wait)
            else:
                raise
        except Exception as e:
            if attempt < retries - 1:
                wait = 2 ** attempt
                print(f"  Error: {e}, retrying in {wait}s...")
                time.sleep(wait)
            else:
                raise


def normalize_project_ref(url: str) -> str:
    """Extract project slug/ID from a Modrinth URL."""
    url = url.strip().rstrip("/")
    if "modrinth.com/mod/" in url:
        return url.split("modrinth.com/mod/")[-1].split("/")[0]
    if "modrinth.com/project/" in url:
        return url.split("modrinth.com/project/")[-1].split("/")[0]
    # Assume it's already a slug or ID
    return url


def main() -> None:
    from typing import Optional
    if len(sys.argv) < 2:
        print("Usage: phase1_project_info.py <modrinth_url>", file=sys.stderr)
        sys.exit(1)

    modrinth_url = sys.argv[1].strip()
    token = os.environ.get("MODRINTH_TOKEN", "").strip() or None
    project_ref = normalize_project_ref(modrinth_url)

    print(f"Fetching project info for: {project_ref}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Fetch project metadata
    print("  Fetching project metadata...")
    project = _get(f"{MODRINTH_API}/project/{project_ref}", token)
    (OUTPUT_DIR / "project.json").write_text(
        json.dumps(project, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"  Project: {project.get('title')} (slug: {project.get('slug')})")

    # Write summary
    summary_lines = [
        f"Title: {project.get('title', '?')}",
        f"Slug: {project.get('slug', '?')}",
        f"ID: {project.get('id', '?')}",
        f"Description: {project.get('description', '')[:500]}",
        f"Loaders: {project.get('loaders', [])}",
        f"Game versions: {project.get('game_versions', [])[:30]}",
        f"Downloads: {project.get('downloads', 0)}",
        f"License: {project.get('license', {}).get('id', '?')}",
    ]
    (OUTPUT_DIR / "summary.txt").write_text("\n".join(summary_lines), encoding="utf-8")

    # Fetch all versions
    print("  Fetching version list...")
    versions = _get(f"{MODRINTH_API}/project/{project_ref}/version", token)
    if not versions:
        print("  No versions found.")
        return

    # Sort by date_published ascending to find the first version
    versions_sorted = sorted(versions, key=lambda v: v.get("date_published", ""))
    first_version = versions_sorted[0]
    print(f"  First published version: {first_version.get('version_number')} ({first_version.get('date_published', '')[:10]})")

    # Save first version metadata
    first_dir = OUTPUT_DIR / "first_version"
    first_dir.mkdir(parents=True, exist_ok=True)
    (first_dir / "version.json").write_text(
        json.dumps(first_version, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    # Download the primary jar
    files = first_version.get("files", [])
    primary_file = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not primary_file:
        print("  No downloadable jar found for first version.")
        return

    jar_url = primary_file.get("url", "")
    jar_name = primary_file.get("filename", "mod.jar")
    jar_path = first_dir / jar_name

    print(f"  Downloading {jar_name}...")
    req = urllib.request.Request(jar_url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        jar_path.write_bytes(resp.read())
    print(f"  Downloaded: {jar_path} ({jar_path.stat().st_size // 1024} KB)")

    # Decompile the jar — try multiple decompilers in priority order:
    #   1. Vineflower (if available in tools/)
    #   2. CFR (download if needed — small, no dependencies)
    #   3. Procyon (download if needed)
    #   4. javap (last resort — produces bytecode summaries, not real Java)
    decompile_dir = first_dir / "decompiled"
    decompile_dir.mkdir(parents=True, exist_ok=True)

    import shutil as _shutil
    import tempfile as _tempfile
    import zipfile as _zipfile

    def _count_java(d: Path) -> int:
        return sum(1 for _ in d.rglob("*.java")) if d.exists() else 0

    def _try_vineflower(jar_path: Path, out_dir: Path) -> int:
        """Try Vineflower via build_mods.py. Returns number of .java files produced."""
        build_mods = ROOT / "build_mods.py"
        vf_candidates = [
            ROOT / "tools" / "vineflower.jar",
            ROOT / "tools" / "decompiler.jar",
            ROOT / "vineflower.jar",
        ]
        vf_jar = next((c for c in vf_candidates if c.exists()), None)
        if not vf_jar or not build_mods.exists():
            return 0
        with _tempfile.TemporaryDirectory() as tmp_run:
            r = subprocess.run(
                [sys.executable, str(build_mods), "decompile-jar",
                 "--jar-path", str(jar_path),
                 "--manifest", str(ROOT / "version-manifest.json"),
                 "--decompiler-jar", str(vf_jar),
                 "--output-dir", tmp_run],
                capture_output=True, text=True, cwd=str(ROOT)
            )
            if r.returncode != 0:
                return 0
            # Extract zip if produced
            for zf_path in Path(tmp_run).glob("*.zip"):
                with _zipfile.ZipFile(zf_path) as zf:
                    zf.extractall(out_dir)
                break
            # Also copy any .java files directly
            for jf in Path(tmp_run).rglob("*.java"):
                _shutil.copy2(str(jf), str(out_dir / jf.name))
        return _count_java(out_dir)

    def _try_cfr(jar_path: Path, out_dir: Path) -> int:
        """Try CFR decompiler. Downloads it if not present. Returns .java file count."""
        cfr_jar = ROOT / "tools" / "cfr.jar"
        if not cfr_jar.exists():
            try:
                import urllib.request as _ur
                cfr_jar.parent.mkdir(parents=True, exist_ok=True)
                cfr_url = "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar"
                print("  Downloading CFR decompiler...")
                _ur.urlretrieve(cfr_url, cfr_jar)
                print(f"  CFR downloaded ({cfr_jar.stat().st_size // 1024} KB)")
            except Exception as e:
                print(f"  Could not download CFR: {e}")
                return 0
        with _tempfile.TemporaryDirectory() as tmp_out:
            r = subprocess.run(
                ["java", "-jar", str(cfr_jar), str(jar_path),
                 "--outputdir", tmp_out, "--silent", "true"],
                capture_output=True, text=True
            )
            if r.returncode != 0 and not list(Path(tmp_out).rglob("*.java")):
                return 0
            for jf in Path(tmp_out).rglob("*.java"):
                _shutil.copy2(str(jf), str(out_dir / jf.name))
        return _count_java(out_dir)

    def _try_procyon(jar_path: Path, out_dir: Path) -> int:
        """Try Procyon decompiler. Downloads it if not present. Returns .java file count."""
        procyon_jar = ROOT / "tools" / "procyon.jar"
        if not procyon_jar.exists():
            try:
                import urllib.request as _ur
                procyon_jar.parent.mkdir(parents=True, exist_ok=True)
                procyon_url = "https://github.com/mstrobel/procyon/releases/download/v0.6.0/procyon-decompiler-0.6.0.jar"
                print("  Downloading Procyon decompiler...")
                _ur.urlretrieve(procyon_url, procyon_jar)
                print(f"  Procyon downloaded ({procyon_jar.stat().st_size // 1024} KB)")
            except Exception as e:
                print(f"  Could not download Procyon: {e}")
                return 0
        with _tempfile.TemporaryDirectory() as tmp_classes, _tempfile.TemporaryDirectory() as tmp_out:
            # Extract class files
            with _zipfile.ZipFile(jar_path) as zf:
                class_files = [n for n in zf.namelist() if n.endswith(".class") and "$" not in n]
                zf.extractall(tmp_classes)
            for cf in class_files[:50]:
                cf_path = Path(tmp_classes) / cf
                subprocess.run(
                    ["java", "-jar", str(procyon_jar), str(cf_path), "-o", tmp_out],
                    capture_output=True, text=True
                )
            for jf in Path(tmp_out).rglob("*.java"):
                _shutil.copy2(str(jf), str(out_dir / jf.name))
        return _count_java(out_dir)

    def _try_javap(jar_path: Path, out_dir: Path) -> int:
        """Last resort: javap class summaries. Returns number of .txt files produced."""
        with _zipfile.ZipFile(jar_path) as zf:
            class_files = [n for n in zf.namelist() if n.endswith(".class") and not n.startswith("META-INF")]
        with _tempfile.TemporaryDirectory() as tmp:
            with _zipfile.ZipFile(jar_path) as zf:
                zf.extractall(tmp)
            count = 0
            for cf in class_files[:50]:
                out_name = cf.replace("/", "_").replace(".class", ".txt")
                r = subprocess.run(
                    ["javap", "-c", "-p", str(Path(tmp) / cf)],
                    capture_output=True, text=True
                )
                if r.returncode == 0:
                    (out_dir / out_name).write_text(r.stdout, encoding="utf-8")
                    count += 1
        return count

    # Try decompilers in priority order
    best_count = 0
    used_decompiler = "none"

    with _tempfile.TemporaryDirectory() as vf_tmp:
        vf_out = Path(vf_tmp)
        print("  Trying Vineflower...")
        n = _try_vineflower(jar_path, vf_out)
        print(f"    Vineflower: {n} .java files")
        if n > best_count:
            best_count = n
            used_decompiler = "vineflower"
            for f in vf_out.rglob("*.java"):
                _shutil.copy2(str(f), str(decompile_dir / f.name))

    with _tempfile.TemporaryDirectory() as cfr_tmp:
        cfr_out = Path(cfr_tmp)
        print("  Trying CFR...")
        n = _try_cfr(jar_path, cfr_out)
        print(f"    CFR: {n} .java files")
        if n > best_count:
            best_count = n
            used_decompiler = "cfr"
            # Clear previous and copy CFR output
            for f in decompile_dir.glob("*.java"):
                f.unlink()
            for f in cfr_out.rglob("*.java"):
                _shutil.copy2(str(f), str(decompile_dir / f.name))

    with _tempfile.TemporaryDirectory() as proc_tmp:
        proc_out = Path(proc_tmp)
        print("  Trying Procyon...")
        n = _try_procyon(jar_path, proc_out)
        print(f"    Procyon: {n} .java files")
        if n > best_count:
            best_count = n
            used_decompiler = "procyon"
            for f in decompile_dir.glob("*.java"):
                f.unlink()
            for f in proc_out.rglob("*.java"):
                _shutil.copy2(str(f), str(decompile_dir / f.name))

    if best_count == 0:
        print("  All decompilers failed — falling back to javap summaries...")
        n = _try_javap(jar_path, decompile_dir)
        used_decompiler = "javap"
        print(f"    javap: {n} summaries")

    print(f"  Decompilation complete: {best_count} files via {used_decompiler}")
    print(f"  Source written to: {decompile_dir}")

    print(f"\nProject info saved to {OUTPUT_DIR}/")
    print(f"  project.json, summary.txt, first_version/")


if __name__ == "__main__":
    main()
