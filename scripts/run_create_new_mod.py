#!/usr/bin/env python3
"""
run_create_new_mod.py — AI-powered mod creation workflow.

Usage:
  python3 scripts/run_create_new_mod.py
  python3 scripts/run_create_new_mod.py --description "A /heal command mod" --mc-version 1.21.4 --loader fabric
  python3 scripts/run_create_new_mod.py --description "..." --mc-version 1.12.2 --loader forge
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(SCRIPTS))

from _ai_client import send_prompt, ResponseTooLarge  # type: ignore[import-untyped]
from _key_manager import KeyManager  # type: ignore[import-untyped]

from modcompiler.common import (
    ModCompilerError,
    load_json,
    resolve_range,
    write_json,
    safe_rmtree,
    copy_tree,
    safe_extract_zip,
    discover_top_level_mod_dirs,
    validate_mod_dir,
    build_prepare_plan,
    make_slug,
)

# ── Constants ────────────────────────────────────────────────────────────────

AI_BASE_URL = "https://api.deepseek.com/v1"
AI_MODEL = "deepseek-chat"
AI_TEMPERATURE = 0.2
MAX_RESPONSE_BYTES = 100_000

LOCAL_WORKSPACE = ROOT / "Mod Developement" / "1.12.2-forge"
OUTPUT_ROOT = ROOT / "output" / "create_mod"
INCOMING_DIR = ROOT / "incoming"
BUILD_WORKFLOW_FILE = "build.yml"
POLL_INTERVAL = 30
MAX_RETRIES = 5
GH_RETRIES = 4

# ── Output structure ─────────────────────────────────────────────────────────

class CreateModOutput:
    def __init__(self, output_dir: Path) -> None:
        self.dir = output_dir
        self.src_dir = output_dir / "src"
        self.jars_dir = output_dir / "jars"
        self.published_dir = output_dir / "published"
        self.prompt_path = output_dir / "prompt.txt"
        self.airesponse_path = output_dir / "airesponse.txt"
        self.mod_txt_path = output_dir / "mod.txt"
        self.version_txt_path = output_dir / "version.txt"
        self.build_log_path = output_dir / "build.log"
        self.summary_md_path = output_dir / "create_mod_summary.md"
        self.summary_json_path = output_dir / "create_mod_summary.json"

    def ensure_dirs(self) -> None:
        self.dir.mkdir(parents=True, exist_ok=True)
        self.src_dir.mkdir(parents=True, exist_ok=True)
        self.jars_dir.mkdir(parents=True, exist_ok=True)


# ── Helpers ──────────────────────────────────────────────────────────────────

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def log(msg: str) -> None:
    print(msg, flush=True)


def run_cmd(cmd: list[str], cwd: Path | None = None, log_file: Path | None = None) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)
    if log_file:
        with log_file.open("a", encoding="utf-8") as f:
            f.write(f"$ {' '.join(cmd)}\n{result.stdout}\n{result.stderr}\n")
    return result


def gh(args: list[str], *, token: str = "") -> str:
    env = os.environ.copy()
    if token:
        env["GH_TOKEN"] = token
        env["GITHUB_TOKEN"] = token
    last_err = ""
    for attempt in range(1, GH_RETRIES + 1):
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
            if attempt < GH_RETRIES and transient:
                time.sleep(3 * attempt)
                continue
            break
    raise RuntimeError(f"gh {' '.join(args[:3])}... failed: {last_err}")


def detect_github_repo() -> str:
    try:
        url = subprocess.check_output(
            ["git", "remote", "get-url", "origin"],
            stderr=subprocess.DEVNULL, text=True).strip()
    except subprocess.CalledProcessError:
        return ""
    m = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    return m.group(1) if m else ""


def detect_github_token() -> str:
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


def get_current_branch() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        capture_output=True, text=True)
    return result.stdout.strip()


# ── Phase 1: AI Metadata Generation ──────────────────────────────────────────

def generate_metadata_prompt(user_description: str, mc_version: str, loader: str) -> str:
    return f"""You are a Minecraft mod metadata generator. Given a mod description, Minecraft version, and mod loader, generate the following metadata fields.

Return ONLY a JSON object with these exact keys:
- mod_id: short lowercase id (no spaces, hyphens allowed)
- name: display name
- mod_version: start with "1.0.0"
- group: Java package group (e.g. com.myname.modid)
- entrypoint_class: full qualified main mod class (e.g. com.myname.modid.MyMod)
- description: a concise 1-2 sentence description of what the mod does
- authors: list with one author name
- license: "MIT" or "All Rights Reserved"
- runtime_side: "both", "client", or "server"

User's mod description: {user_description}
Minecraft version: {mc_version}
Mod loader: {loader}

Return ONLY the JSON object, no other text."""


def generate_code_prompt(
    user_description: str,
    mc_version: str,
    loader: str,
    metadata: dict[str, Any],
    template_code: str,
    dif_entries: list[str],
) -> str:
    loader_notes = {
        "fabric": "- Use Fabric API (fabric-api) for events\n- Main class annotated with @Mod\n- Mixins use @Mixin + @Inject\n- Client code goes in src/client/java",
        "forge": "- Use @Mod + @Mod.EventHandler for Forge events\n- mcmod.info for 1.12.2 metadata\n- mods.toml for 1.13+ Forge\n- Register items/blocks in FMLInitializationEvent or RegistryEvent",
        "neoforge": "- Use @Mod + bus events for NeoForge\n- neoforge.mods.toml for metadata\n- NeoForge events use NeoForge.EVENT_BUS",
    }
    loader_note = loader_notes.get(loader, "")

    dif_section = ""
    if dif_entries:
        dif_section = "\n\n## Known Issues & Fixes for this version/loader\n"
        for entry in dif_entries:
            dif_section += f"\n---\n{entry}\n"

    return f"""You are a Minecraft mod developer. Create a complete Minecraft mod based on the description below.

## Mod Description
{user_description}

## Mod Metadata
- mod_id: {metadata['mod_id']}
- name: {metadata['name']}
- version: {metadata['mod_version']}
- group: {metadata['group']}
- entrypoint: {metadata['entrypoint_class']}
- runtime_side: {metadata.get('runtime_side', 'both')}
- authors: {', '.join(metadata.get('authors', ['Author']))}
- license: {metadata.get('license', 'MIT')}

## Target
- Minecraft version: {mc_version}
- Loader: {loader}

## Loader-Specific Notes
{loader_note}

## Reference Template Code
The following is the template code for this version and loader. Follow the same patterns, package structure, and annotations:
```java
{template_code}
```

{dif_section}

## Requirements
1. Create ALL necessary Java source files for the mod to compile and work
2. Create ALL necessary resource files (mixins.json, fabric.mod.json, mods.toml, etc.)
3. Use the correct package structure: src/main/java/<group-path>/
4. For Fabric with split source: src/client/java/ for client-only code
5. Include proper annotations, event handlers, and registrations
6. Make sure the mod actually does what the description says

## Output Format
For each file, output:

filepath: <relative-path-from-src>
```java
<file-content>
```

For example:
filepath: main/java/com/example/ExampleMod.java
```java
package com.example;
...
```

Do NOT include build.gradle, settings.gradle, gradle.properties, gradlew, or gradlew.bat — only source files."""


# ── Phase 1: Parse AI Response ───────────────────────────────────────────────

def extract_ai_files(airesponse_text: str) -> dict[str, str]:
    files: dict[str, str] = {}
    pending_path: str = ""
    pattern = re.compile(r"```([a-zA-Z0-9_+\-]*)\n(.*?)\n```", re.DOTALL)
    prev_end: int | None = None

    for m in pattern.finditer(airesponse_text):
        lang_spec = m.group(1)
        raw_content = m.group(2)

        s = raw_content.strip().strip("`*[]'\"")
        content_is_path = False
        if "/" in s:
            last_part = s.split("/")[-1]
            if "." in last_part and not last_part.startswith("."):
                pending_path = s
                content_is_path = True

        if content_is_path:
            prev_end = m.end()
            continue

        filepath = ""
        if pending_path:
            filepath = pending_path
            pending_path = ""

        if not filepath:
            text_before = (
                airesponse_text[prev_end:m.start()] if prev_end is not None
                else airesponse_text[:m.start()]
            )
            before_lines = text_before.splitlines()
            for line in reversed(before_lines):
                s = line.strip().strip("`*[]'\"")
                if "/" in s:
                    last_part = s.split("/")[-1]
                    if "." in last_part and not last_part.startswith("."):
                        filepath = s
                        break

        if not filepath:
            prev_end = m.end()
            continue

        code_patterns = ["package ", "import ", " class ", "public ", "private ",
                         "protected ", " @", "//", "/*", " =>", " ->"]
        if any(p in filepath for p in code_patterns):
            prev_end = m.end()
            continue
        for segment in filepath.split("/"):
            if len(segment) > 120:
                prev_end = m.end()
                filepath = ""
                continue
        if not filepath:
            continue

        for prefix in ("./",):
            if filepath.startswith(prefix):
                filepath = filepath[len(prefix):]

        build_files = {
            "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts",
            "gradle.properties", "gradlew", "gradlew.bat",
        }
        if filepath.lower() in build_files or "/gradle/" in filepath.lower():
            prev_end = m.end()
            continue

        clean_lines = [
            ln for ln in raw_content.splitlines()
            if not ln.strip().startswith("```")
        ]
        clean_content = "\n".join(clean_lines).strip()

        if filepath and clean_content and filepath not in files:
            files[filepath] = clean_content

        prev_end = m.end()

    return files


# ── Phase 1: Write files to bundle structure ─────────────────────────────────

def write_ai_files_to_bundle(
    files: dict[str, str],
    metadata: dict[str, Any],
    mc_version: str,
    loader: str,
    output: CreateModOutput,
) -> None:
    for filepath, content in files.items():
        # Strip leading "src/" or "src/main/" from the filepath if present
        clean_path = filepath
        for prefix in ("src/main/", "src/", "main/"):
            if clean_path.startswith(prefix):
                clean_path = clean_path[len(prefix):]
                break
        target = output.src_dir / clean_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    mod_txt_content = (
        f"mod_id={metadata['mod_id']}\n"
        f"name={metadata['name']}\n"
        f"mod_version={metadata['mod_version']}\n"
        f"group={metadata['group']}\n"
        f"entrypoint_class={metadata['entrypoint_class']}\n"
        f"description={metadata.get('description', '')}\n"
        f"authors={', '.join(metadata.get('authors', ['Author']))}\n"
        f"license={metadata.get('license', 'MIT')}\n"
    )
    homepage = metadata.get("homepage", "")
    sources = metadata.get("sources", "")
    issues = metadata.get("issues", "")
    if homepage:
        mod_txt_content += f"homepage={homepage}\n"
    if sources:
        mod_txt_content += f"sources={sources}\n"
    if issues:
        mod_txt_content += f"issues={issues}\n"

    output.mod_txt_path.write_text(mod_txt_content, encoding="utf-8")
    output.version_txt_path.write_text(
        f"minecraft_version={mc_version}\nloader={loader}\n",
        encoding="utf-8",
    )


# ── Phase 2: Local 1.12.2 Build ─────────────────────────────────────────────

def build_local_1122(
    metadata: dict[str, Any],
    mc_version: str,
    output: CreateModOutput,
) -> bool:
    log(f"\n{'='*60}")
    log(f"  Phase 2: Local 1.12.2 Build")
    log(f"{'='*60}")
    log(f"  Workspace: {LOCAL_WORKSPACE}")

    if not LOCAL_WORKSPACE.exists():
        log(f"  ERROR: Local workspace not found at {LOCAL_WORKSPACE}")
        return False

    workspace = LOCAL_WORKSPACE
    java_dir = workspace / "src" / "main" / "java"
    resources_dir = workspace / "src" / "main" / "resources"
    assets_dir = resources_dir / "assets"
    build_libs = workspace / "build" / "libs"

    with output.build_log_path.open("w", encoding="utf-8") as log_file:
        # Step 1: Clean workspace
        log_file.write(f"[{now_iso()}] Step 1: Cleaning workspace\n")
        log("  Step 1: Cleaning workspace...")
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

        # Step 2: Run gradle clean
        log_file.write(f"\n[{now_iso()}] Step 2: Gradle clean\n")
        log("  Step 2: Running gradle clean...")
        gradlew = workspace / "gradlew"
        if not gradlew.exists():
            log_file.write("gradlew not found, skipping gradle clean\n")
        else:
            result = subprocess.run(
                [str(gradlew), "clean"],
                cwd=workspace,
                capture_output=True, text=True,
                timeout=300,
            )
            log_file.write(result.stdout + "\n" + result.stderr + "\n")
            if result.returncode != 0:
                log_file.write(f"gradle clean exit code: {result.returncode}\n")

        # Step 3: Place AI-generated source files
        log_file.write(f"\n[{now_iso()}] Step 3: Placing source files\n")
        log("  Step 3: Placing AI-generated source files...")

        group_path = metadata["group"].replace(".", "/")
        target_java_dir = java_dir / group_path
        target_java_dir.mkdir(parents=True, exist_ok=True)

        for java_file in output.src_dir.rglob("*.java"):
            rel = java_file.relative_to(output.src_dir)
            dest = java_dir / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(java_file, dest)
            log_file.write(f"  Copied: {rel}\n")

        # Copy resources (non-java files)
        for res_file in output.src_dir.rglob("*"):
            if res_file.is_file() and not res_file.suffix == ".java":
                rel = res_file.relative_to(output.src_dir)
                dest = resources_dir / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(res_file, dest)
                log_file.write(f"  Copied resource: {rel}\n")

        # Step 4: Update build.gradle
        log_file.write(f"\n[{now_iso()}] Step 4: Updating build.gradle\n")
        log("  Step 4: Updating build.gradle...")
        build_gradle = workspace / "build.gradle"
        if build_gradle.exists():
            text = build_gradle.read_text(encoding="utf-8")
            text = re.sub(
                r'(?m)^version\s*=\s*["\'][^"\']+["\']',
                f'version = "{metadata["mod_version"]}"',
                text,
            )
            text = re.sub(
                r'(?m)^group\s*=\s*["\'][^"\']+["\']',
                f'group = "{metadata["group"]}"',
                text,
            )
            text = re.sub(
                r'(?m)^archivesBaseName\s*=\s*["\'][^"\']+["\']',
                f'archivesBaseName = "{metadata["mod_id"]}"',
                text,
            )
            build_gradle.write_text(text, encoding="utf-8")
            log_file.write(f"  Updated build.gradle\n")

        # Step 5: Update mcmod.info
        log_file.write(f"\n[{now_iso()}] Step 5: Updating mcmod.info\n")
        log("  Step 5: Updating mcmod.info...")
        mcmod_path = resources_dir / "mcmod.info"
        if mcmod_path.exists():
            try:
                mcmod_data = json.loads(mcmod_path.read_text(encoding="utf-8"))
                if isinstance(mcmod_data, list) and mcmod_data:
                    mcmod_data[0]["modid"] = metadata["mod_id"]
                    mcmod_data[0]["name"] = metadata["name"]
                    mcmod_data[0]["description"] = metadata.get("description", "")
                    mcmod_data[0]["version"] = metadata["mod_version"]
                    mcmod_data[0]["authorList"] = metadata.get("authors", ["Author"])
                mcmod_path.write_text(json.dumps(mcmod_data, indent=2) + "\n", encoding="utf-8")
                log_file.write(f"  Updated mcmod.info\n")
            except (json.JSONDecodeError, KeyError) as e:
                log_file.write(f"  Warning: Could not update mcmod.info: {e}\n")

        # Step 6: Build
        log_file.write(f"\n[{now_iso()}] Step 6: Running gradle build\n")
        log("  Step 6: Running gradle build (this may take a while)...")
        log_file.flush()

        try:
            result = subprocess.run(
                [str(gradlew), "clean", "build"],
                cwd=workspace,
                capture_output=True, text=True,
                timeout=600,
            )
            log_file.write(result.stdout + "\n" + result.stderr + "\n")
            log_file.write(f"\ngradle exit code: {result.returncode}\n")

            if result.returncode == 0:
                # Step 7: Extract JAR
                log_file.write(f"\n[{now_iso()}] Step 7: Extracting JAR\n")
                log("  Step 7: Extracting JAR...")
                jars = sorted(build_libs.glob("*.jar"))
                valid_jars = [
                    j for j in jars
                    if not any(x in j.name.lower() for x in ("-sources", "-javadoc", "-dev"))
                ]
                if valid_jars:
                    jar = valid_jars[0]
                    dest_jar = output.jars_dir / f"{metadata['mod_id']}-{mc_version}-{metadata['mod_version']}.jar"
                    shutil.copy2(jar, dest_jar)
                    log_file.write(f"  Copied: {jar.name} -> {dest_jar.name}\n")
                    log(f"  JAR saved: {dest_jar}")

                    # Verify JAR has .class files
                    with zipfile.ZipFile(dest_jar) as zf:
                        class_files = [n for n in zf.namelist() if n.endswith(".class")]
                    log_file.write(f"  Class files in JAR: {len(class_files)}\n")
                    if class_files:
                        log(f"  JAR verified: {len(class_files)} class files")
                        return True
                    else:
                        log_file.write("  ERROR: JAR has no .class files!\n")
                        log("  ERROR: Built JAR contains no .class files")
                        return False
                else:
                    log_file.write("  ERROR: No valid JAR found in build/libs/\n")
                    log("  ERROR: No JAR produced by build")
                    return False
            else:
                log_file.write(f"Build failed with exit code {result.returncode}\n")
                log(f"  Build failed (exit code {result.returncode})")
                return False

        except subprocess.TimeoutExpired:
            log_file.write("ERROR: gradle build timed out after 600s\n")
            log("  ERROR: Build timed out")
            return False


# ── Phase 2: Remote Build (non-1.12.2) ──────────────────────────────────────

def build_remote(
    metadata: dict[str, Any],
    mc_version: str,
    loader: str,
    output: CreateModOutput,
) -> bool:
    log(f"\n{'='*60}")
    log(f"  Phase 2: Remote Build via GitHub")
    log(f"{'='*60}")

    repo = detect_github_repo()
    token = detect_github_token()
    if not repo or not token:
        log("  ERROR: GitHub repo or token not found. Cannot dispatch remote build.")
        return False

    branch = get_current_branch()

    # Step 1: Create build zip
    log("  Step 1: Creating build zip...")
    bundle_dir = output.dir / ".bundle"
    safe_rmtree(bundle_dir)
    bundle_dir.mkdir(parents=True, exist_ok=True)

    mod_dir = bundle_dir / metadata["mod_id"]
    mod_dir.mkdir(parents=True, exist_ok=True)

    # Copy src/ into mod_dir
    src_target = mod_dir / "src"
    if output.src_dir.exists():
        copy_tree(output.src_dir, src_target)

    # Write mod.txt and version.txt into the mod dir (for build system)
    mod_txt_content = (
        f"mod_id={metadata['mod_id']}\n"
        f"name={metadata['name']}\n"
        f"mod_version={metadata['mod_version']}\n"
        f"group={metadata['group']}\n"
        f"entrypoint_class={metadata['entrypoint_class']}\n"
        f"description={metadata.get('description', '')}\n"
        f"authors={', '.join(metadata.get('authors', ['Author']))}\n"
        f"license={metadata.get('license', 'MIT')}\n"
    )
    (mod_dir / "mod.txt").write_text(mod_txt_content, encoding="utf-8")
    (mod_dir / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader={loader}\n",
        encoding="utf-8",
    )

    zip_path = output.dir / "build.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for file_path in bundle_dir.rglob("*"):
            if file_path.is_file():
                arcname = str(file_path.relative_to(bundle_dir))
                zf.write(file_path, arcname)
    safe_rmtree(bundle_dir)
    log(f"  Build zip created: {zip_path}")

    # Step 2: Commit to incoming/
    log("  Step 2: Committing to incoming/ and pushing...")
    incoming_target = ROOT / INCOMING_DIR / f"{metadata['mod_id']}-{mc_version}-{loader}.zip"
    incoming_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(zip_path, incoming_target)

    # Stage, commit, push
    commit_msg = f"Create mod: {metadata['mod_id']} v{metadata['mod_version']} for {mc_version}-{loader}"
    subprocess.run(["git", "add", str(incoming_target)], capture_output=True)
    subprocess.run(["git", "commit", "-m", commit_msg], capture_output=True)

    push_result = subprocess.run(["git", "push", "origin", branch], capture_output=True, text=True)
    if push_result.returncode != 0:
        log(f"  Warning: Push failed: {push_result.stderr.strip()[:200]}")
        # Try with rebase
        subprocess.run(["git", "pull", "--rebase", "origin", branch], capture_output=True)
        push_result = subprocess.run(["git", "push", "origin", branch], capture_output=True, text=True)
        if push_result.returncode != 0:
            log(f"  ERROR: Could not push to remote: {push_result.stderr.strip()[:200]}")
            return False

    log("  Pushed successfully")

    # Step 3: Dispatch build workflow
    log("  Step 3: Dispatching build workflow...")
    zip_rel_path = str(incoming_target.relative_to(ROOT))
    before_runs = set()
    try:
        runs_out = gh([
            "run", "list", "-R", repo,
            "-w", BUILD_WORKFLOW_FILE,
            "-e", "workflow_dispatch",
            "--json", "databaseId",
            "-L", "10",
        ], token=token)
        before_runs = {r["databaseId"] for r in json.loads(runs_out or "[]")}
    except Exception:
        pass

    try:
        gh([
            "workflow", "run", BUILD_WORKFLOW_FILE, "-R", repo,
            "-f", f"zip_path={zip_rel_path}",
            "-f", "max_parallel=all",
            "-f", "modrinth_project_url=",
        ], token=token)
    except RuntimeError as e:
        log(f"  ERROR: Failed to dispatch workflow: {e}")
        return False

    # Wait for run to appear
    run_id: int | None = None
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            runs_out = gh([
                "run", "list", "-R", repo,
                "-w", BUILD_WORKFLOW_FILE,
                "-e", "workflow_dispatch",
                "--json", "databaseId,status,createdAt",
                "-L", "10",
            ], token=token)
            runs = json.loads(runs_out or "[]")
            for run in runs:
                rid = run["databaseId"]
                if rid not in before_runs:
                    run_id = rid
                    break
        except Exception:
            pass
        if run_id is not None:
            break
        time.sleep(4)

    if run_id is None:
        log("  ERROR: Build workflow dispatched but no new run appeared")
        return False

    log(f"  Build run #{run_id} dispatched")

    # Step 4: Wait for build
    log("  Step 4: Waiting for build to complete...")
    deadline = time.time() + 7200
    while time.time() < deadline:
        time.sleep(POLL_INTERVAL)
        try:
            out = gh([
                "run", "view", str(run_id), "-R", repo,
                "--json", "status,conclusion",
            ], token=token)
            info = json.loads(out or "{}")
            status = info.get("status", "")
            conclusion = info.get("conclusion") or ""
            log(f"    Status: {status} / {conclusion}")
            if status == "completed":
                break
        except Exception:
            continue
    else:
        log("  ERROR: Build timed out after 7200s")
        return False

    # Step 5: Download artifacts
    log("  Step 5: Downloading build artifacts...")
    artifacts_dir = output.dir / ".artifacts"
    safe_rmtree(artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    try:
        gh([
            "run", "download", str(run_id),
            "-R", repo, "-n", "build-artifacts", "-D", str(artifacts_dir),
        ], token=token)
    except RuntimeError:
        # Try without specific name
        try:
            gh([
                "run", "download", str(run_id),
                "-R", repo, "-D", str(artifacts_dir),
            ], token=token)
        except RuntimeError as e:
            log(f"  Warning: Could not download artifacts: {e}")

    # Find JARs
    jars_found = list(artifacts_dir.rglob("*.jar"))
    if jars_found:
        for jar in jars_found:
            if not any(x in jar.name.lower() for x in ("-sources", "-javadoc", "-dev")):
                dest = output.jars_dir / jar.name
                shutil.copy2(jar, dest)
                log(f"  JAR saved: {dest}")

        # Also download build logs
        try:
            gh([
                "run", "download", str(run_id),
                "-R", repo, "-n", "build-logs", "-D", str(artifacts_dir),
            ], token=token)
            for log_file in artifacts_dir.rglob("build.log"):
                shutil.copy2(log_file, output.build_log_path)
                log(f"  Build log saved")
        except RuntimeError:
            pass

        return True
    else:
        # Try to get build summary/logs
        try:
            for summary_file in artifacts_dir.rglob("*"):
                if "summary" in summary_file.name.lower() or "result" in summary_file.name.lower():
                    shutil.copy2(summary_file, output.dir / summary_file.name)
        except Exception:
            pass
        log("  ERROR: No JARs found in build artifacts")
        return False


# ── Phase 3: Retry Cycle ─────────────────────────────────────────────────────

def analyze_build_errors(build_log_text: str) -> str:
    from dif_core import match_errors_to_dif, is_infrastructure_failure, extract_missing_symbols

    is_infra, reason = is_infrastructure_failure(build_log_text)
    if is_infra:
        return f"[INFRASTRUCTURE FAILURE] {reason}"

    symbols = extract_missing_symbols(build_log_text)
    dif_matches = match_errors_to_dif(build_log_text, threshold=0.3)

    sections = ["## Build Error Analysis"]
    if symbols:
        sections.append(f"\nMissing symbols: {', '.join(sorted(symbols))}")
    if dif_matches:
        sections.append("\n## Matching DIF Entries")
        for score, entry in dif_matches[:3]:
            pct = int(score * 100)
            sections.append(f"\n### {pct}% match — {entry.title}")
            sections.append(entry.body[:500])
    return "\n".join(sections)


def compose_fix_prompt(
    user_description: str,
    metadata: dict[str, Any],
    mc_version: str,
    loader: str,
    current_sources: dict[str, str],
    error_analysis: str,
    build_log: str,
) -> str:
    source_section = "\n".join(
        f"=== {path} ===\n{content}"
        for path, content in current_sources.items()
    )
    return f"""The following Minecraft mod failed to compile. Please fix the source files.

## Mod Description
{user_description}

## Mod Metadata
- mod_id: {metadata['mod_id']}
- name: {metadata['name']}
- version: {metadata['mod_version']}
- group: {metadata['group']}
- entrypoint: {metadata['entrypoint_class']}

## Target
- Minecraft: {mc_version}
- Loader: {loader}

## Current Source Files
{source_section}

## Build Log (last 200 lines)
{build_log[-5000:]}

## Error Analysis
{error_analysis}

## Instructions
1. Fix ALL compilation errors in the source files
2. Return ONLY the files that need to be changed (in full)
3. Use the same output format: filepath: path
```java
content
```"""


def retry_cycle(
    user_description: str,
    metadata: dict[str, Any],
    mc_version: str,
    loader: str,
    output: CreateModOutput,
    api_key: str,
) -> bool:
    from dif_core import SourceSearchEngine, match_errors_to_dif, is_infrastructure_failure, extract_missing_symbols

    log(f"\n{'='*60}")
    log(f"  Phase 3: Retry Cycle")
    log(f"{'='*60}")

    build_log = output.build_log_path.read_text(encoding="utf-8") if output.build_log_path.exists() else ""

    if not build_log:
        log("  No build log to analyze. Cannot retry.")
        return False

    # Analyze errors
    error_analysis = analyze_build_errors(build_log)
    log(f"  Error analysis completed")

    # For the first retry, try source search for missing symbols
    if "Missing symbols" in error_analysis:
        from dif_core import extract_missing_symbols
        symbols = extract_missing_symbols(build_log)
        if symbols:
            log(f"  Searching decompiled MC source for: {', '.join(sorted(symbols)[:5])}...")
            try:
                search_engine = SourceSearchEngine()
                search_dir = output.dir / ".source_search"
                safe_rmtree(search_dir)
                search_result = search_engine.search(
                    version=mc_version,
                    loader=loader,
                    symbols=list(symbols)[:8],
                    out_dir=search_dir,
                )
                if search_result.get("java_count", 0) > 0:
                    error_analysis += f"\n\n## Source Search Results\nFound {search_result['java_count']} Java files in decompiled sources."
                    # Include query results
                    for q_file in search_dir.rglob("*.txt"):
                        if q_file.parent.name == "queries":
                            error_analysis += f"\n\n{q_file.read_text(encoding='utf-8')[:1000]}"
            except Exception as e:
                log(f"  Source search skipped: {e}")

    # Read current source files
    current_sources: dict[str, str] = {}
    for java_file in output.src_dir.rglob("*.java"):
        rel = str(java_file.relative_to(output.src_dir))
        current_sources[rel] = java_file.read_text(encoding="utf-8")
    for res_file in output.src_dir.rglob("*"):
        if res_file.is_file() and res_file.suffix in (".json", ".toml", ".cfg", ".info"):
            rel = str(res_file.relative_to(output.src_dir))
            if rel not in current_sources:
                current_sources[rel] = res_file.read_text(encoding="utf-8")

    if not current_sources:
        log("  No source files to fix. Cannot retry.")
        return False

    # Compose fix prompt
    fix_prompt = compose_fix_prompt(
        user_description=user_description,
        metadata=metadata,
        mc_version=mc_version,
        loader=loader,
        current_sources=current_sources,
        error_analysis=error_analysis,
        build_log=build_log,
    )

    # Send to AI
    log("  Sending fix prompt to AI...")
    fix_dir = output.dir / f"fix-attempt-1"
    fix_dir.mkdir(parents=True, exist_ok=True)
    (fix_dir / "fix_prompt.txt").write_text(fix_prompt, encoding="utf-8")

    try:
        airesponse = send_prompt(
            model=AI_MODEL,
            base_url=AI_BASE_URL,
            api_key=api_key,
            messages=[
                {"role": "system", "content": "You are a Minecraft mod developer fixing compilation errors. Return ONLY the fixed source files in the specified format."},
                {"role": "user", "content": fix_prompt},
            ],
            temperature=AI_TEMPERATURE,
            stream=True,
            size_limit=MAX_RESPONSE_BYTES,
        )
    except ResponseTooLarge:
        log("  ERROR: AI response too large")
        return False
    except RuntimeError as e:
        log(f"  ERROR: AI request failed: {e}")
        return False

    (fix_dir / "airesponse.txt").write_text(airesponse, encoding="utf-8")

    # Extract fixed files
    fixed_files = extract_ai_files(airesponse)
    if not fixed_files:
        log("  No fixed files extracted from AI response")
        return False

    log(f"  Extracted {len(fixed_files)} fixed file(s)")

    # Merge fixed files back into src/
    for filepath, content in fixed_files.items():
        clean_path = filepath
        for prefix in ("src/main/", "src/", "main/"):
            if clean_path.startswith(prefix):
                clean_path = clean_path[len(prefix):]
                break
        target = output.src_dir / clean_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    log("  Fixed files merged. Rebuilding...")
    return None  # Signal to caller to rebuild


# ── Phase 4: Publish to Modrinth ─────────────────────────────────────────────

def publish_to_modrinth(output: CreateModOutput, mc_version: str, loader: str) -> bool:
    log(f"\n{'='*60}")
    log(f"  Phase 4: Creating Modrinth Draft Project")
    log(f"{'='*60}")

    # Run auto_create_modrinth.py generate then create-drafts
    auto_script = ROOT / "scripts" / "auto_create_modrinth_draft_projects.py"

    # Step 1: Generate Modrinth bundle
    log("  Step 1: Generating Modrinth bundle...")
    gen_result = subprocess.run(
        [sys.executable, str(auto_script), "generate",
         "--input-dir", str(output.jars_dir.parent),
         "--output-dir", str(output.published_dir),
         "--manifest", str(ROOT / "version-manifest.json"),
         "--template-dir", str(ROOT),
         "--background-dir", str(output.published_dir),
         "--c05-hoster", "deepseek",
         "--c05-model", AI_MODEL,
         ],
        capture_output=True, text=True,
    )
    log(gen_result.stdout[-1000:] if gen_result.stdout else "")
    if gen_result.returncode != 0:
        log(f"  Warning: Bundle generation had issues (exit {gen_result.returncode})")
        log(f"  Stderr: {gen_result.stderr[-500:]}" if gen_result.stderr else "")

    # Check if verify.txt was created
    verify_files = list(output.published_dir.rglob("verify.txt"))
    if not verify_files:
        log("  No verify.txt found. Bundle may not have been generated correctly.")
        log("  Attempting to create draft anyway...")

    # Step 2: Create Modrinth draft
    log("  Step 2: Creating Modrinth draft project...")
    modrinth_token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not modrinth_token:
        log("  ERROR: MODRINTH_TOKEN not set. Cannot create draft.")
        log("  Set it in your environment and run again.")
        return False

    draft_result = subprocess.run(
        [sys.executable, str(auto_script), "create-drafts",
         "--output-dir", str(output.published_dir),
         "--modrinth-token", modrinth_token,
         "--create-via", "local",
         ],
        capture_output=True, text=True,
    )
    log(draft_result.stdout[-1000:] if draft_result.stdout else "")
    if draft_result.returncode != 0:
        log(f"  Warning: Draft creation had issues (exit {draft_result.returncode})")
        log(f"  Stderr: {draft_result.stderr[-500:]}" if draft_result.stderr else "")
        return False

    log("  Modrinth draft project created successfully!")
    return True


# ── Summary ──────────────────────────────────────────────────────────────────

def write_summary(
    output: CreateModOutput,
    metadata: dict[str, Any],
    mc_version: str,
    loader: str,
    user_description: str,
    build_success: bool,
    published: bool,
    draft_url: str = "",
) -> None:
    jars = list(output.jars_dir.glob("*.jar"))
    jar_list = "\n".join(f"  - {j.name}" for j in jars) if jars else "  - (none)"

    summary_md = f"""# Create Mod Summary

**Generated:** {now_iso()}

## Mod Info
- **Name:** {metadata.get('name', 'N/A')}
- **Mod ID:** {metadata.get('mod_id', 'N/A')}
- **Version:** {metadata.get('mod_version', 'N/A')}
- **Description:** {metadata.get('description', user_description)}
- **Target:** {mc_version} ({loader})

## Build Result
- **Status:** {"✅ SUCCESS" if build_success else "❌ FAILED"}
- **JAR files:**
{jar_list}

## Output Location
- **Output dir:** `{output.dir}`
- **Source files:** `{output.src_dir}`
- **Build log:** `{output.build_log_path.name if output.build_log_path.exists() else "(none)"}`

## Published to Modrinth
{"✅ Yes" if published else "❌ No"}
{f"- **Draft URL:** {draft_url}" if draft_url else ""}
"""
    output.summary_md_path.write_text(summary_md, encoding="utf-8")

    summary_json = {
        "generated_at": now_iso(),
        "mod_id": metadata.get("mod_id", ""),
        "name": metadata.get("name", ""),
        "version": metadata.get("mod_version", ""),
        "description": metadata.get("description", user_description),
        "minecraft_version": mc_version,
        "loader": loader,
        "build_success": build_success,
        "published": published,
        "draft_url": draft_url,
        "jars": [j.name for j in jars],
        "output_dir": str(output.dir),
        "src_dir": str(output.src_dir),
    }
    write_json(output.summary_json_path, summary_json)

    log(f"\n{'='*60}")
    log(f"  SUMMARY")
    log(f"{'='*60}")
    log(f"  Mod: {metadata.get('name', 'N/A')} ({metadata.get('mod_id', 'N/A')})")
    log(f"  Version: {metadata.get('mod_version', 'N/A')}")
    log(f"  Target: {mc_version} ({loader})")
    log(f"  Build: {'✅ SUCCESS' if build_success else '❌ FAILED'}")
    log(f"  Published: {'✅ Yes' if published else '❌ No'}")
    log(f"  Output: {output.dir}")
    if jars:
        log(f"  JARs:")
        for j in jars:
            log(f"    {j}")
    if draft_url:
        log(f"  Modrinth Draft: {draft_url}")
    log(f"{'='*60}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(
        description="AI-powered mod creation workflow. Creates a Minecraft mod from a description.",
    )
    parser.add_argument("--description", default="",
                        help="Description of the mod to create")
    parser.add_argument("--mc-version", default="",
                        help="Minecraft version (e.g. 1.12.2, 1.20.1, 1.21.4)")
    parser.add_argument("--loader", default="",
                        choices=["fabric", "forge", "neoforge", ""],
                        help="Mod loader")
    parser.add_argument("--author", default="",
                        help="Author name")
    parser.add_argument("--publish", action="store_true",
                        help="Publish to Modrinth after successful build")
    parser.add_argument("--skip-prompt", action="store_true",
                        help="Skip AI prompt, only show plan")
    args = parser.parse_args()

    # ── Gather inputs ────────────────────────────────────────────────────
    log(f"\n{'='*60}")
    log(f"  AI-Powered Mod Creation")
    log(f"{'='*60}")

    description = args.description.strip()
    if not description:
        description = input("  Describe the mod you want to create: ").strip()
        if not description:
            log("  No description provided. Exiting.")
            return 1

    mc_version = args.mc_version.strip()
    if not mc_version:
        mc_version = input("  Minecraft version (e.g. 1.12.2, 1.20.1) [1.21.4]: ").strip() or "1.21.4"

    loader = args.loader.strip().lower()
    if not loader:
        loader = input("  Mod loader [fabric/forge/neoforge] [fabric]: ").strip().lower() or "fabric"
        if loader not in ("fabric", "forge", "neoforge"):
            log(f"  Invalid loader: {loader}")
            return 1

    author = args.author.strip() or "ModAuthor"

    log(f"\n  Creating mod for: {mc_version} ({loader})")
    log(f"  Description: {description[:100]}...")

    # ── Load API key ─────────────────────────────────────────────────────
    log(f"\n{'='*60}")
    log(f"  Loading API key...")
    log(f"{'='*60}")
    try:
        key_mgr = KeyManager(
            key_path=ROOT / "C05LocalAi" / "keys" / "deepseek.txt",
            env_vars=("DEEPSEEK_API_KEY",),
        )
        api_key = key_mgr.acquire()
        log(f"  API key loaded ({key_mgr.key_count} key(s) available)")
    except RuntimeError as e:
        log(f"  ERROR: {e}")
        return 1

    # ── Resolve template ─────────────────────────────────────────────────
    log(f"\n{'='*60}")
    log(f"  Phase 1: Planning & AI Code Generation")
    log(f"{'='*60}")
    log(f"  Resolving template for {mc_version} ({loader})...")

    manifest_path = ROOT / "version-manifest.json"
    manifest = load_json(manifest_path)

    try:
        resolved_range = resolve_range(manifest, mc_version)
        range_folder = resolved_range["folder"]
        if loader not in resolved_range["loaders"]:
            log(f"  ERROR: Loader '{loader}' not supported in range {range_folder}")
            log(f"  Supported loaders: {', '.join(resolved_range['loaders'].keys())}")
            return 1
        loader_config = resolved_range["loaders"][loader]
        template_dir = ROOT / loader_config["template_dir"]
        log(f"  Template: {template_dir}")
    except ModCompilerError as e:
        log(f"  ERROR: {e}")
        return 1

    # Read template code
    template_code = ""
    for java_file in sorted(template_dir.rglob("*.java")):
        rel = java_file.relative_to(template_dir)
        template_code += f"// {rel}\n{java_file.read_text(encoding='utf-8')}\n\n"

    # ── Search DIF entries ───────────────────────────────────────────────
    log(f"  Searching DIF knowledge base for {mc_version} ({loader})...")
    dif_entries: list[str] = []
    try:
        from dif_core import search_dif
        query = f"{mc_version} {loader} {description}"
        dif_results = search_dif(query, top_n=5)
        for score, entry in dif_results:
            pct = int(score * 100)
            if pct >= 20:
                dif_entries.append(f"### {entry.title} ({pct}% match)\n{entry.body[:300]}")
                log(f"    DIF: {entry.title} ({pct}%)")
    except ImportError:
        log(f"  DIF search unavailable")
    except Exception as e:
        log(f"  DIF search warning: {e}")

    # ── Generate metadata via AI ─────────────────────────────────────────
    log(f"  Generating mod metadata via AI...")
    meta_prompt = generate_metadata_prompt(description, mc_version, loader)

    try:
        meta_response = send_prompt(
            model=AI_MODEL,
            base_url=AI_BASE_URL,
            api_key=api_key,
            messages=[
                {"role": "system", "content": "You are a Minecraft mod metadata generator. Return ONLY valid JSON."},
                {"role": "user", "content": meta_prompt},
            ],
            temperature=AI_TEMPERATURE,
            stream=True,
            size_limit=20000,
        )
    except RuntimeError as e:
        log(f"  ERROR: AI request failed: {e}")
        return 1

    metadata: dict[str, Any] = {}
    try:
        metadata = json.loads(meta_response)
        # Ensure required fields
        metadata.setdefault("mod_id", "mymod")
        metadata.setdefault("name", "My Mod")
        metadata.setdefault("mod_version", "1.0.0")
        metadata.setdefault("group", f"com.{author.lower()}.{metadata.get('mod_id', 'mymod')}")
        metadata.setdefault("entrypoint_class",
                           f"com.{author.lower()}.{metadata.get('mod_id', 'mymod')}.{metadata.get('name', 'MyMod').replace(' ', '')}")
        metadata.setdefault("authors", [author])
        metadata.setdefault("license", "MIT")
        metadata.setdefault("description", description)
        metadata.setdefault("runtime_side", "both")
        log(f"  Mod ID: {metadata['mod_id']}")
        log(f"  Name: {metadata['name']}")
        log(f"  Version: {metadata['mod_version']}")
        log(f"  Group: {metadata['group']}")
    except json.JSONDecodeError:
        log(f"  ERROR: AI returned invalid JSON for metadata")
        log(f"  Response: {meta_response[:200]}")
        # Fallback to basic metadata
        slug = make_slug(description[:20], loader, mc_version)
        metadata = {
            "mod_id": slug[:30],
            "name": description[:50],
            "mod_version": "1.0.0",
            "group": f"com.{author.lower()}.{slug[:20]}",
            "entrypoint_class": f"com.{author.lower()}.{slug[:20]}.{slug[:20].title().replace('-', '')}Mod",
            "description": description,
            "authors": [author],
            "license": "MIT",
            "runtime_side": "both",
        }
        log(f"  Using fallback metadata")

    # Save metadata
    output = CreateModOutput(OUTPUT_ROOT / f"{metadata['mod_id']}-{mc_version}-{loader}")
    output.ensure_dirs()
    write_json(output.dir / "metadata.json", metadata)

    # ── Generate code via AI ─────────────────────────────────────────────
    log(f"  Generating mod source code via AI (this may take a while)...")
    code_prompt = generate_code_prompt(
        user_description=description,
        mc_version=mc_version,
        loader=loader,
        metadata=metadata,
        template_code=template_code,
        dif_entries=dif_entries,
    )
    output.prompt_path.write_text(code_prompt, encoding="utf-8")

    if args.skip_prompt:
        log(f"\n  --skip-prompt set. Prompt saved to {output.prompt_path}")
        log(f"  Run without --skip-prompt to generate code.")
        key_mgr.release(api_key)
        return 0

    try:
        airesponse = send_prompt(
            model=AI_MODEL,
            base_url=AI_BASE_URL,
            api_key=api_key,
            messages=[
                {"role": "system", "content": "You are a Minecraft mod developer. Write complete, compilable Java code for the mod described."},
                {"role": "user", "content": code_prompt},
            ],
            temperature=AI_TEMPERATURE,
            stream=True,
            size_limit=MAX_RESPONSE_BYTES,
        )
    except ResponseTooLarge:
        log(f"  ERROR: AI response too large")
        key_mgr.release(api_key)
        return 1
    except RuntimeError as e:
        log(f"  ERROR: AI request failed: {e}")
        key_mgr.release(api_key)
        return 1

    output.airesponse_path.write_text(airesponse, encoding="utf-8")

    # ── Extract AI files ─────────────────────────────────────────────────
    log(f"  Extracting source files from AI response...")
    extracted_files = extract_ai_files(airesponse)
    if not extracted_files:
        log(f"  ERROR: No source files could be extracted from AI response")
        log(f"  Check {output.airesponse_path} for the raw response")
        key_mgr.release(api_key)
        return 1

    log(f"  Extracted {len(extracted_files)} file(s):")
    for fpath in sorted(extracted_files.keys()):
        log(f"    - {fpath}")

    write_ai_files_to_bundle(extracted_files, metadata, mc_version, loader, output)
    log(f"  Source files saved to {output.src_dir}")

    # ── Phase 2: Build ───────────────────────────────────────────────────
    is_1122 = mc_version.startswith("1.12")
    build_success = False

    if is_1122:
        build_success = build_local_1122(metadata, mc_version, output)
    else:
        build_success = build_remote(metadata, mc_version, loader, output)

    # ── Phase 3: Retry Cycle ─────────────────────────────────────────────
    retry_count = 0
    while not build_success and retry_count < MAX_RETRIES:
        retry_count += 1
        log(f"\n  Retry attempt {retry_count}/{MAX_RETRIES}...")
        result = retry_cycle(description, metadata, mc_version, loader, output, api_key)
        if result is True:
            build_success = True
            break
        elif result is False:
            log(f"  Retry failed, cannot continue")
            break
        else:
            # result is None — rebuild
            if is_1122:
                build_success = build_local_1122(metadata, mc_version, output)
            else:
                build_success = build_remote(metadata, mc_version, loader, output)

    # ── Phase 4: Publish ─────────────────────────────────────────────────
    published = False
    draft_url = ""
    if build_success and args.publish:
        published = publish_to_modrinth(output, mc_version, loader)
        if published:
            # Try to find the draft URL from the publish output
            summary_files = list(output.published_dir.rglob("*summary*"))
            for sf in summary_files:
                text = sf.read_text(encoding="utf-8")
                m = re.search(r"https://modrinth\.com/project/[a-z0-9-]+", text)
                if m:
                    draft_url = m.group(0)
                    break

    # ── Write Summary ────────────────────────────────────────────────────
    write_summary(
        output=output,
        metadata=metadata,
        mc_version=mc_version,
        loader=loader,
        user_description=description,
        build_success=build_success,
        published=published,
        draft_url=draft_url,
    )

    key_mgr.release(api_key)
    return 0 if build_success else 1


if __name__ == "__main__":
    raise SystemExit(main())