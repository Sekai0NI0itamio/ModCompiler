"""
mod_wizard/build_engine.py — Build a mod from extracted AI response files.

Handles: clean workspace → place files → update build.gradle →
run Gradle build → collect jar.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

# Support both direct and package imports
if __name__ == "__main__" and __package__ is None:
    _ROOT = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(_ROOT / "scripts"))
    from mod_wizard import display
else:
    from . import display

ROOT = Path(__file__).resolve().parents[2]
WORKSPACE = ROOT / "Mod Development" / "1.12.2-forge"
JAVA8_HOME = "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"


def build(files: dict[str, str], metadata: dict[str, str]) -> tuple[bool, str]:
    """Place files, configure, compile, and collect the jar.

    Returns (success: bool, build_log: str).
    """
    java_dir = WORKSPACE / "src" / "main" / "java"
    resources_dir = WORKSPACE / "src" / "main" / "resources"
    assets_dir = resources_dir / "assets"
    build_libs = WORKSPACE / "build" / "libs"
    log_lines: list[str] = []

    def log(msg: str) -> None:
        log_lines.append(msg)

    display.header("Building mod...", step="Compile")

    # 1. Clean workspace
    display.info("Cleaning workspace...")
    log("Cleaning workspace...")
    _clean_workspace(java_dir, assets_dir)

    # 2. Place files
    display.info(f"Placing {len(files)} source files...")
    log(f"Placing {len(files)} source files...")
    _place_files(files, java_dir, resources_dir)

    # 3. Update build.gradle
    display.info("Updating build.gradle...")
    log("Updating build.gradle...")
    _update_build_gradle(metadata)

    # 4. Build
    display.info("Running ./gradlew build (this takes ~30-60s)...")
    log("Running ./gradlew build...")
    success, gradle_log = _run_gradle_build()
    log_lines.append(gradle_log)

    full_log = "\n".join(log_lines)

    if not success:
        return False, full_log

    display.ok("Build successful")
    log("Build successful")

    # 5. Collect jar
    jar_ok = _collect_jar(build_libs)
    if jar_ok:
        log("JAR collected successfully")
    return jar_ok, full_log


def _clean_workspace(java_dir: Path, assets_dir: Path) -> None:
    """Remove all previous mod source files from the workspace."""
    for p in java_dir.iterdir():
        if p.is_dir():
            shutil.rmtree(p)
    for p in java_dir.glob("*.java"):
        p.unlink()
    if assets_dir.exists():
        for p in assets_dir.iterdir():
            if p.is_dir():
                shutil.rmtree(p)
            else:
                p.unlink()


def _place_files(
    files: dict[str, str], java_dir: Path, resources_dir: Path
) -> None:
    """Copy extracted files into the appropriate workspace directories."""
    for filepath, content in files.items():
        if filepath.endswith(".java"):
            clean = filepath
            for px in ("src/main/java/", "src/main/", "src/"):
                if clean.startswith(px):
                    clean = clean[len(px):]
                    break
            target = java_dir / clean
        else:
            clean = filepath
            for px in ("src/main/resources/", "src/main/", "src/"):
                if clean.startswith(px):
                    clean = clean[len(px):]
                    break
            target = resources_dir / clean

        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")


def _update_build_gradle(metadata: dict[str, str]) -> None:
    """Update build.gradle with group, archivesBaseName, and version."""
    build_gradle = WORKSPACE / "build.gradle"
    if not build_gradle.exists():
        return

    text = build_gradle.read_text(encoding="utf-8")
    for key, gradle_key in [
        ("group", "group"),
        ("archivesBaseName", "archivesBaseName"),
        ("mod_version", "version"),
    ]:
        if key in metadata and metadata[key]:
            text = re.sub(
                rf'(?m)^\s*{gradle_key}\s*=\s*["\'][^"\']+["\']',
                f'{gradle_key} = "{metadata[key]}"',
                text,
            )
    build_gradle.write_text(text, encoding="utf-8")


def _run_gradle_build() -> tuple[bool, str]:
    """Execute gradle clean build.

    Returns (success: bool, log_output: str).
    """
    gradlew = WORKSPACE / "gradlew"
    if not gradlew.exists():
        return False, "gradlew not found — cannot build"

    env = os.environ.copy()
    if os.path.exists(JAVA8_HOME):
        env["JAVA_HOME"] = JAVA8_HOME
        env["PATH"] = f"{JAVA8_HOME}/bin:{env.get('PATH', '')}"

    try:
        result = subprocess.run(
            [str(gradlew), "clean", "build"],
            cwd=WORKSPACE,
            capture_output=True,
            text=True,
            timeout=600,
            env=env,
        )
    except subprocess.TimeoutExpired:
        display.err("Build timed out after 600s")
        return False, "Build timed out after 600 seconds"

    combined = result.stdout + "\n" + result.stderr

    if result.returncode != 0:
        display.err("Build FAILED")
        for line in combined.splitlines():
            lower = line.lower()
            if any(
                kw in lower
                for kw in (
                    "error:", "cannot find symbol", "does not exist",
                    "BUILD FAILED", "compilation error",
                )
            ):
                print(f"    {line.strip()}")
        return False, combined

    return True, combined



PRISM_MODS = Path.home() / "Library/Application Support/PrismLauncher/instances/1.12.2/minecraft/mods"

def _deploy_to_prism(jar_path: Path) -> None:
    """Clear all mods from PrismLauncher and deploy only the newly compiled jar."""
    if not PRISM_MODS.exists():
        return
    # Remove ALL jars
    for old in PRISM_MODS.glob("*.jar"):
        try:
            old.unlink()
        except OSError:
            pass
    # Copy new jar
    shutil.copy2(jar_path, PRISM_MODS / jar_path.name)

def _collect_jar(build_libs: Path) -> bool:
    """Find the built jar and copy it to ModCollection and ReadyMods."""
    jars = sorted(build_libs.glob("*.jar"))
    valid = [
        j
        for j in jars
        if not any(x in j.name.lower() for x in ("-sources", "-javadoc", "-dev"))
    ]
    if not valid:
        display.err("No JAR found in build/libs/")
        return False

    jar_path = valid[0]

    for dest_dir in [WORKSPACE / "ModCollection", WORKSPACE / "ReadyMods"]:
        dest_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar_path, dest_dir / jar_path.name)

    # Auto-deploy to PrismLauncher for instant testing
    _deploy_to_prism(jar_path)

    with zipfile.ZipFile(jar_path) as zf:
        class_count = len([n for n in zf.namelist() if n.endswith(".class")])

    size_kb = jar_path.stat().st_size / 1024
    display.build_result_box(jar_path.name, size_kb, class_count)
    return True
