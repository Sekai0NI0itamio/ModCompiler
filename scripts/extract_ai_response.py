#!/usr/bin/env python3
"""
extract_ai_response.py — Parse an AI response and build a 1.12.2 Forge mod.

Takes the AI's response (from a file or stdin), extracts the source files,
places them in the 1.12.2 Forge workspace, updates build configuration,
and compiles the mod.

Usage:
    # From a file:
    python3 scripts/extract_ai_response.py ai_response.txt

    # From clipboard (macOS):
    pbpaste | python3 scripts/extract_ai_response.py --stdin

    # From clipboard (Linux):
    xclip -o | python3 scripts/extract_ai_response.py --stdin

    # Dry run (show what would be extracted without building):
    python3 scripts/extract_ai_response.py ai_response.txt --dry-run

    # Skip build (just extract files):
    python3 scripts/extract_ai_response.py ai_response.txt --no-build

What it does:
    1. Parses the AI response using ---FILE: path--- format
    2. Extracts all Java source files and resource files
    3. Cleans the workspace (removes old mod files)
    4. Copies extracted files into the workspace
    5. Updates build.gradle (group, archivesBaseName, version)
    6. Updates mcmod.info (modid, name, description)
    7. Runs ./gradlew clean build
    8. Copies the built jar to ModCollection/ and ReadyMods/
    9. Writes a summary report
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
WORKSPACE = ROOT / "Mod Developement" / "1.12.2-forge"

# Java 8 home for 1.12.2 Forge
JAVA8_HOME = "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"


# ── Parsing ──────────────────────────────────────────────────────────────────

def parse_ai_response(text: str) -> dict[str, Any]:
    """
    Parse an AI response into:
    - files: dict of {relative_path: content}
    - metadata: dict with group, archivesBaseName, mod_id, name, etc.
    """
    files: dict[str, str] = {}
    metadata: dict[str, str] = {}

    # ---- Extract METADATA block ----
    meta_match = re.search(
        r'---METADATA---\s*\n(.*?)\n---',
        text, re.DOTALL
    )
    if meta_match:
        for line in meta_match.group(1).strip().splitlines():
            line = line.strip()
            if ':' in line:
                key, val = line.split(':', 1)
                metadata[key.strip()] = val.strip()

    # ---- Extract FILE blocks ----
    # Pattern: ---FILE: path--- followed by code block
    file_pattern = re.compile(
        r'---FILE:\s*(.+?)---\s*\n'
        r'```(\w*)\s*\n'
        r'(.*?)\n'
        r'```',
        re.DOTALL
    )

    for match in file_pattern.finditer(text):
        filepath = match.group(1).strip()
        lang = match.group(2).strip()
        content = match.group(3)

        # Clean the filepath
        filepath = filepath.lstrip("./")
        # Ensure it starts with src/
        if not filepath.startswith("src/"):
            if filepath.startswith("main/"):
                filepath = "src/" + filepath
            elif filepath.startswith("java/") or filepath.startswith("resources/"):
                filepath = "src/main/" + filepath
            elif "/" not in filepath and filepath.endswith(".java"):
                filepath = "src/main/java/asd/itamio/unknown/" + filepath
            else:
                # Try to recognize common patterns
                if "java/" in filepath:
                    idx = filepath.find("java/")
                    filepath = "src/main/" + filepath[idx:]
                elif "resources/" in filepath:
                    idx = filepath.find("resources/")
                    filepath = "src/main/" + filepath[idx:]
                elif filepath.endswith(".json") or filepath.endswith(".lang") or filepath.endswith(".txt"):
                    filepath = "src/main/resources/" + filepath

        # Skip build files
        if filepath.endswith("build.gradle") or filepath.endswith("gradle.properties"):
            continue
        if "gradle/" in filepath or filepath.endswith("gradlew"):
            continue

        # Clean content
        content = content.strip()
        if not content:
            continue

        # Skip content that looks like a path, not code
        if len(content) < 50 and "/" in content and "\n" not in content:
            continue

        files[filepath] = content

    # Also try to find filepath: pattern before code blocks (fallback)
    fallback_pattern = re.compile(
        r'(?:^|\n)(?:filepath|path|file):\s*(.+?\.(?:java|json|lang|txt|info|cfg|properties|mcmeta))\s*\n'
        r'```(\w*)\s*\n'
        r'(.*?)\n'
        r'```',
        re.DOTALL | re.MULTILINE
    )

    for match in fallback_pattern.finditer(text):
        filepath = match.group(1).strip().lstrip("./")
        content = match.group(3).strip()

        if filepath in files:
            continue
        if len(content) < 30:
            continue

        if not filepath.startswith("src/"):
            if filepath.startswith("main/"):
                filepath = "src/" + filepath
            elif filepath.endswith(".java") and "java/" not in filepath:
                filepath = "src/main/java/asd/itamio/unknown/" + filepath

        if filepath.endswith("build.gradle") or "gradle/" in filepath:
            continue

        files[filepath] = content

    # ---- Extract mod metadata from files ----
    if not metadata:
        metadata = extract_metadata_from_files(files)

    return {"files": files, "metadata": metadata}


def extract_metadata_from_files(files: dict[str, str]) -> dict[str, str]:
    """Try to extract mod metadata from the Java files and mcmod.info."""
    metadata: dict[str, str] = {}

    # Look for mod_id and name in Java main class
    for filepath, content in files.items():
        if not filepath.endswith(".java"):
            continue

        # Extract modid
        modid_match = re.search(
            r'public\s+static\s+final\s+String\s+MODID\s*=\s*"([^"]+)"',
            content
        )
        if modid_match and "mod_id" not in metadata:
            metadata["mod_id"] = modid_match.group(1)

        # Extract name
        name_match = re.search(
            r'public\s+static\s+final\s+String\s+NAME\s*=\s*"([^"]+)"',
            content
        )
        if name_match and "name" not in metadata:
            metadata["name"] = name_match.group(1)

        # Extract package -> group
        pkg_match = re.search(r'^package\s+([a-z0-9_.]+)\s*;', content, re.MULTILINE)
        if pkg_match and "group" not in metadata:
            metadata["group"] = pkg_match.group(1)

        # Extract version
        ver_match = re.search(
            r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"',
            content
        )
        if ver_match and "mod_version" not in metadata:
            metadata["mod_version"] = ver_match.group(1)

    # Try mcmod.info for additional metadata
    for filepath, content in files.items():
        if "mcmod.info" in filepath:
            try:
                mcmod = json.loads(content)
                if isinstance(mcmod, list) and mcmod:
                    entry = mcmod[0]
                    if "modid" in entry and "mod_id" not in metadata:
                        metadata["mod_id"] = entry["modid"]
                    if "name" in entry and "name" not in metadata:
                        metadata["name"] = entry["name"]
            except json.JSONDecodeError:
                pass

    # Derive archivesBaseName from name
    if "name" in metadata and "archivesBaseName" not in metadata:
        name = metadata["name"]
        archives_name = name.replace(" ", "-").replace("'", "")
        metadata["archivesBaseName"] = archives_name

    # Defaults
    if "mod_id" not in metadata:
        metadata["mod_id"] = "unknown_mod"
    if "group" not in metadata:
        metadata["group"] = "asd.itamio.unknown"
    if "mod_version" not in metadata:
        metadata["mod_version"] = "1.0.0"
    if "archivesBaseName" not in metadata:
        metadata["archivesBaseName"] = metadata["mod_id"]

    return metadata


# ── Build ────────────────────────────────────────────────────────────────────

def build_mod(
    files: dict[str, str],
    metadata: dict[str, str],
    dry_run: bool = False,
    verbose: bool = True,
) -> bool:
    """Place files in workspace, update config, build, and collect jar."""

    workspace = WORKSPACE
    java_dir = workspace / "src" / "main" / "java"
    resources_dir = workspace / "src" / "main" / "resources"
    assets_dir = resources_dir / "assets"
    build_libs = workspace / "build" / "libs"

    def log(msg: str) -> None:
        if verbose:
            print(msg, flush=True)

    log(f"\n{'='*60}")
    log(f"  Extracting AI Mod Response")
    log(f"{'='*60}")
    log(f"  Files found: {len(files)}")
    log(f"  Metadata:   {json.dumps(metadata, indent=2)}")
    log(f"{'='*60}")

    if dry_run:
        log("\n  DRY RUN — showing files that would be created:")
        for filepath, content in files.items():
            log(f"\n  --- {filepath} ---")
            preview = content[:200] + ("..." if len(content) > 200 else "")
            log(f"  {preview}")
        log(f"\n  DRY RUN complete. {len(files)} files would be created.")
        return True

    # Step 1: Clean workspace
    log("\n  Step 1: Cleaning workspace...")
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

    # Step 2: Place files
    log("  Step 2: Placing source files...")
    java_count = 0
    res_count = 0

    for filepath, content in files.items():
        # Determine target directory
        if filepath.endswith(".java"):
            # java source -> src/main/java/
            clean = filepath
            for prefix in ("src/main/java/", "src/main/", "src/"):
                if clean.startswith(prefix):
                    clean = clean[len(prefix):]
                    break
            target = java_dir / clean
            java_count += 1
        else:
            # resource -> src/main/resources/
            clean = filepath
            for prefix in ("src/main/resources/", "src/main/", "src/"):
                if clean.startswith(prefix):
                    clean = clean[len(prefix):]
                    break
            target = resources_dir / clean
            res_count += 1

        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        log(f"    Created: {clean}")

    log(f"  Placed {java_count} Java files and {res_count} resource files")

    # Step 3: Update build.gradle
    log("  Step 3: Updating build.gradle...")
    build_gradle = workspace / "build.gradle"
    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")

        if "group" in metadata:
            text = re.sub(
                r'(?m)^\s*group\s*=\s*["\'][^"\']+["\']',
                f'group = "{metadata["group"]}"',
                text,
            )
        if "archivesBaseName" in metadata:
            text = re.sub(
                r'(?m)^\s*archivesBaseName\s*=\s*["\'][^"\']+["\']',
                f'archivesBaseName = "{metadata["archivesBaseName"]}"',
                text,
            )
        if "mod_version" in metadata:
            text = re.sub(
                r'(?m)^\s*version\s*=\s*["\'][^"\']+["\']',
                f'version = "{metadata["mod_version"]}"',
                text,
            )

        build_gradle.write_text(text, encoding="utf-8")
        log("    Updated group, archivesBaseName, version")

    # Step 4: Run gradle clean
    log("  Step 4: Running gradle clean...")
    gradlew = workspace / "gradlew"
    if gradlew.exists():
        env = os.environ.copy()
        if os.path.exists(JAVA8_HOME):
            env["JAVA_HOME"] = JAVA8_HOME
            env["PATH"] = f"{JAVA8_HOME}/bin:{env.get('PATH', '')}"

        result = subprocess.run(
            [str(gradlew), "clean"],
            cwd=workspace,
            capture_output=True,
            text=True,
            timeout=300,
            env=env,
        )
        if result.returncode != 0:
            log(f"    Warning: gradle clean had non-zero exit: {result.returncode}")
            log(f"    {result.stderr[:500]}")
    else:
        log("    WARNING: gradlew not found!")

    # Step 5: Build
    log("  Step 5: Running gradle build (this may take a while)...")
    if not gradlew.exists():
        log("    ERROR: Cannot build — gradlew not found")
        return False

    try:
        env = os.environ.copy()
        if os.path.exists(JAVA8_HOME):
            env["JAVA_HOME"] = JAVA8_HOME
            env["PATH"] = f"{JAVA8_HOME}/bin:{env.get('PATH', '')}"

        result = subprocess.run(
            [str(gradlew), "build"],
            cwd=workspace,
            capture_output=True,
            text=True,
            timeout=600,
            env=env,
        )

        if result.returncode != 0:
            log(f"\n{'='*60}")
            log(f"  BUILD FAILED")
            log(f"{'='*60}")

            # Show relevant error lines
            error_lines = []
            for line in result.stderr.splitlines() + result.stdout.splitlines():
                lower = line.lower()
                if any(kw in lower for kw in
                       ("error", "cannot find symbol", "does not exist",
                        "unmappable", "failed", "exception", "BUILD FAILED",
                        "compilation error")):
                    error_lines.append(line)
                if "What went wrong" in line:
                    error_lines.append("---")

            if error_lines:
                log("\n  Build errors:")
                for line in error_lines[:40]:
                    log(f"    {line}")

            # Write full log
            log_path = workspace / "build.log"
            log_path.write_text(
                f"STDOUT:\n{result.stdout}\n\nSTDERR:\n{result.stderr}",
                encoding="utf-8",
            )
            log(f"\n  Full build log: {log_path}")
            return False

        log("    Build successful!")

    except subprocess.TimeoutExpired:
        log("    ERROR: Build timed out after 600s")
        return False

    # Step 6: Collect jar
    log("  Step 6: Collecting JAR...")
    jars = sorted(build_libs.glob("*.jar"))
    valid_jars = [
        j for j in jars
        if not any(x in j.name.lower() for x in ("-sources", "-javadoc", "-dev"))
    ]

    if valid_jars:
        jar_path = valid_jars[0]

        # Copy to ModCollection
        mod_collection = workspace / "ModCollection"
        mod_collection.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar_path, mod_collection / jar_path.name)

        # Copy to ReadyMods
        ready_mods = workspace / "ReadyMods"
        ready_mods.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar_path, ready_mods / jar_path.name)

        # Show jar info
        with __import__("zipfile").ZipFile(jar_path) as zf:
            class_count = len([n for n in zf.namelist() if n.endswith(".class")])

        log(f"\n{'='*60}")
        log(f"  BUILD COMPLETE!")
        log(f"{'='*60}")
        log(f"  Jar:     {jar_path.name}")
        log(f"  Size:    {jar_path.stat().st_size / 1024:.1f} KB")
        log(f"  Classes: {class_count}")
        log(f"  Saved:   ModCollection/{jar_path.name}")
        log(f"  Saved:   ReadyMods/{jar_path.name}")
        log(f"{'='*60}")
        return True
    else:
        log("    ERROR: No JAR found in build/libs/")
        return False


# ── Summary report ───────────────────────────────────────────────────────────

def write_summary(
    files: dict[str, str],
    metadata: dict[str, str],
    success: bool,
    output_dir: Path | None = None,
) -> None:
    """Write a summary report."""
    if not output_dir:
        output_dir = Path.cwd()

    summary_path = output_dir / "mod_build_summary.txt"
    lines = []
    lines.append(f"Mod Build Summary — {datetime.now(timezone.utc).isoformat()}")
    lines.append("=" * 60)
    lines.append(f"Status: {'SUCCESS' if success else 'FAILED'}")
    lines.append(f"Mod ID: {metadata.get('mod_id', 'unknown')}")
    lines.append(f"Name:   {metadata.get('name', 'unknown')}")
    lines.append(f"Group:  {metadata.get('group', 'unknown')}")
    lines.append(f"Files:  {len(files)}")
    lines.append("")
    lines.append("Files created:")
    for fp in sorted(files):
        lines.append(f"  {fp}")
    lines.append("")

    summary_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"\n  Summary written to: {summary_path}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract AI response and build a Minecraft 1.12.2 Forge mod",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 scripts/extract_ai_response.py ai_response.txt
  python3 scripts/extract_ai_response.py ai_response.txt --dry-run
  pbpaste | python3 scripts/extract_ai_response.py --stdin
""",
    )

    parser.add_argument("input_file", nargs="?", help="Path to the AI response text file")
    parser.add_argument("--stdin", action="store_true", help="Read AI response from stdin")
    parser.add_argument("--dry-run", action="store_true", help="Show files without building")
    parser.add_argument("--no-build", action="store_true", help="Extract files but don't compile")
    parser.add_argument("--verbose", "-v", action="store_true", default=True, help="Verbose output")
    parser.add_argument("--output-dir", help="Directory for summary output")

    args = parser.parse_args()

    # Read input
    if args.stdin:
        text = sys.stdin.read()
    elif args.input_file:
        input_path = Path(args.input_file)
        if not input_path.exists():
            print(f"ERROR: File not found: {args.input_file}")
            sys.exit(1)
        text = input_path.read_text(encoding="utf-8")
    else:
        print("ERROR: Provide an input file or use --stdin")
        print("Usage: python3 scripts/extract_ai_response.py <response_file>")
        sys.exit(1)

    if not text.strip():
        print("ERROR: Input is empty")
        sys.exit(1)

    # Parse
    print("Parsing AI response...")
    result = parse_ai_response(text)

    files = result["files"]
    metadata = result["metadata"]

    if not files:
        print("ERROR: No files found in the AI response.")
        print("Make sure the response uses the ---FILE: path--- format.")
        print("\nFirst 500 chars of input:")
        print(text[:500])
        sys.exit(1)

    print(f"  Found {len(files)} files")
    for fp in sorted(files):
        print(f"    {fp}")

    # Build
    success = False
    if not args.no_build:
        success = build_mod(
            files=files,
            metadata=metadata,
            dry_run=args.dry_run,
            verbose=args.verbose,
        )

    # Summary
    output_dir = Path(args.output_dir) if args.output_dir else None
    write_summary(files, metadata, success, output_dir)


if __name__ == "__main__":
    main()
