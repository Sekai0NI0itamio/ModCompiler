#!/usr/bin/env python3
"""
mod_wizard.py — Continuous interactive wizard for creating Minecraft mods.

Walks you through every step in sequence:
  1. Ask for mod name
  2. Ask for description (with automatic clipboard paste)
  3. Generate the AI prompt file
  4. Pose a task — "copy this to your AI and save the response"
  5. Wait for you to confirm the task is done
  6. Ask for the AI response file path
  7. Extract, place, and build the mod automatically
  8. Show results — jar size, class count, save locations
  9. Ask if you want to create another mod (loops back to step 1)

Usage:
    python3 scripts/mod_wizard.py

    # Skip directly to building an existing AI response:
    python3 scripts/mod_wizard.py --from-response ai_response.txt
"""

from __future__ import annotations

import json
import os
import platform
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
WORKSPACE = ROOT / "Mod Development" / "1.12.2-forge"
PROMPTS_DIR = ROOT / "prompts"
JAVA8_HOME = "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"

# Ensure prompts dir exists
PROMPTS_DIR.mkdir(parents=True, exist_ok=True)


# ═══════════════════════════════════════════════════════════════════════════════
#  Helpers
# ═══════════════════════════════════════════════════════════════════════════════

def _print_header(title: str, step: str = "") -> None:
    """Print a formatted section header."""
    if step:
        print(f"\n{'─' * 60}")
        print(f"  {step}: {title}")
        print(f"{'─' * 60}")
    else:
        print(f"\n{'=' * 60}")
        print(f"  {title}")
        print(f"{'=' * 60}")


def _print_task(title: str) -> None:
    """Print a formatted task box."""
    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'TASK: ' + title:<50} │")
    print(f"  └{'─' * 54}┘")
    print()


def _print_info(msg: str) -> None:
    """Print an info line."""
    print(f"  ℹ  {msg}")


def _print_ok(msg: str) -> None:
    """Print a success line."""
    print(f"  ✓  {msg}")


def _print_warn(msg: str) -> None:
    """Print a warning line."""
    print(f"  ⚠  {msg}")


def _print_err(msg: str) -> None:
    """Print an error line."""
    print(f"  ✗  {msg}")


def _ask(prompt: str) -> str:
    """Ask the user a question, handle EOF/Ctrl+C gracefully."""
    try:
        return input(f"  > {prompt}").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        print()
        print("  Goodbye!")
        sys.exit(0)


def _confirm(prompt: str, default_yes: bool = True) -> bool:
    """Ask a yes/no question."""
    default = "Y/n" if default_yes else "y/N"
    while True:
        ans = _ask(f"{prompt} ({default}): ").lower()
        if not ans:
            return default_yes
        if ans in ("yes", "y"):
            return True
        if ans in ("no", "n"):
            return False


def read_clipboard() -> str | None:
    """Read text from the system clipboard programmatically."""
    system = platform.system()
    try:
        if system == "Darwin":
            result = subprocess.run(
                ["pbpaste"], capture_output=True, text=True, timeout=5
            )
            content = result.stdout
        elif system == "Linux":
            for cmd in (
                ["wl-paste"], ["xclip", "-o", "-selection", "clipboard"], ["xclip", "-o"]
            ):
                try:
                    result = subprocess.run(
                        cmd, capture_output=True, text=True, timeout=5
                    )
                    if result.returncode == 0 and result.stdout.strip():
                        return result.stdout
                except (FileNotFoundError, subprocess.TimeoutExpired):
                    continue
            return None
        elif system == "Windows":
            result = subprocess.run(
                ["powershell", "-Command", "Get-Clipboard"],
                capture_output=True, text=True, timeout=10,
            )
            content = result.stdout
        else:
            return None
        return content if content.strip() else None
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None


def _preview(text: str, max_chars: int = 400) -> str:
    """Return a truncated preview."""
    if len(text) <= max_chars:
        return text
    return text[:max_chars].rstrip() + "\n... (truncated)"


def _read_multiline() -> str | None:
    """Read multiple lines until blank line or EOF."""
    lines = []
    try:
        while True:
            line = input()
            if not line.strip():
                break
            lines.append(line)
    except EOFError:
        pass
    return "\n".join(lines) if lines else None


# ═══════════════════════════════════════════════════════════════════════════════
#  Prompt generation (inline — no external dependency needed)
# ═══════════════════════════════════════════════════════════════════════════════

_SYSTEM_CONTEXT = r"""You are an expert Minecraft mod developer creating a mod for Minecraft 1.12.2 with Forge. You write complete, compilable Java code. Every file you output must be syntactically correct and complete.

## PROJECT CONTEXT

This is the ModCompiler project — a system for building Minecraft mods. The local 1.12.2 Forge workspace is at:

    Mod Development/1.12.2-forge/

The workspace uses:
- Forge 1.12.2-14.23.5.2847
- ForgeGradle 2.3 (build system)
- MCP mappings stable_39
- Java 8
- Gradle wrapper at ./gradlew

## CRITICAL PACKAGE NAMING RULES

ALL mods MUST use the package prefix **asd.itamio.<modname>**
- Author is ALWAYS "Itamio" (authorList: ["Itamio"])
- Main class MUST be named <ModName>Mod (e.g., SuperJumpMod)
- modid: lowercase, underscores allowed (e.g., super_jump)
- Package: asd.itamio.<modid> (all lowercase)
- Build group: asd.itamio.<modid>
- archivesBaseName: PascalCase or kebab-case (e.g., Super-Jump)

## FORGE 1.12.2 API PATTERNS

### Main mod class example:
```java
package asd.itamio.MODID;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = ModNameMod.MODID, name = ModNameMod.NAME, version = ModNameMod.VERSION, acceptableRemoteVersions = "*")
public class ModNameMod {
    public static final String MODID = "MODID";
    public static final String NAME = "Display Name";
    public static final String VERSION = "1.0.0";
    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Register event handlers, recipes, etc.
    }
}
```

### Event handler example:
```java
package asd.itamio.MODID;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class MyEventHandler {
    @SubscribeEvent
    public void onSomeEvent(SomeEvent event) {
        // Handle event
    }
}
```

Register in init: MinecraftForge.EVENT_BUS.register(new MyEventHandler());

### Command example:
```java
package asd.itamio.MODID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandMyCommand extends CommandBase {
    @Override public String getName() { return "mycommand"; }
    @Override public String getUsage(ICommandSender sender) { return "/mycommand"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // Command logic
    }
}
```

Register in serverStarting: event.registerServerCommand(new CommandMyCommand());

### Key Forge 1.12.2 APIs:
- FMLPreInitializationEvent: getModLog(), getSuggestedConfigurationFile()
- FMLInitializationEvent: register blocks/items/recipes
- FMLServerStartingEvent: registerServerCommand()
- MinecraftForge.EVENT_BUS: register event handlers
- GameRegistry: registerBlock(), registerItem()
- Configuration: config.get(category, key, default, comment)

### Message Sending (1.12.2 API):
```java
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Message text"));
```

## BUILD.GRADLE TEMPLATE

The build.gradle already exists. DO NOT output it. Only provide group and archivesBaseName in the METADATA block.

## MCMOD.INFO TEMPLATE

Provide an updated mcmod.info:
```json
[{
  "modid": "MODID",
  "name": "Display Name",
  "description": "Description",
  "version": "${version}",
  "mcversion": "${mcversion}",
  "url": "",
  "updateUrl": "",
  "authorList": ["Itamio"],
  "credits": "",
  "logoFile": "",
  "screenshots": [],
  "dependencies": []
}]
```

## IMPORTANT RULES

1. Every Java file MUST have a package declaration matching asd.itamio.<modid>
2. Every file MUST be complete — no "..." or "// TODO" placeholders
3. Include ALL necessary imports
4. Use TextComponentString + TextFormatting for chat messages (NOT Component.literal or .translatable)
5. Use @Mod.EventHandler (NOT @SubscribeEvent for mod lifecycle events)
6. The mod MUST actually implement the described functionality
7. Do NOT create build.gradle, gradle.properties, or gradle wrapper files
8. If creating commands, extend CommandBase and override getName/getUsage/execute
9. Player reference: EntityPlayerMP for server-side, EntityPlayer for client
10. World reference: use player.getEntityWorld() or sender.getEntityWorld()
"""

_OUTPUT_FORMAT = r"""
## STRICT OUTPUT FORMAT

For each file you create, use this EXACT format (critical for automated extraction):

---FILE: src/main/java/asd/itamio/<modid>/<ClassName>.java---
```java
package asd.itamio.<modid>;
// ... complete Java code with ALL imports ...
```

---FILE: src/main/resources/mcmod.info---
```json
[ ... mcmod.info content ... ]
```

After all files, end with:

---METADATA---
group: asd.itamio.<modid>
archivesBaseName: <Archives-Base-Name>
---

### Format rules:
1. File path MUST start with "src/main/"
2. Use ---FILE: path--- as the file delimiter
3. Put content inside ```lang``` code fences
4. End with ---METADATA--- block containing group and archivesBaseName
5. Do NOT include build.gradle or any gradle files
6. Every Java file MUST compile — include ALL imports
"""


def _build_prompt(name: str, description: str) -> str:
    """Build the complete AI prompt string."""
    mod_id = re.sub(r'[^a-zA-Z0-9\s]', '', name).lower().strip()
    mod_id = re.sub(r'\s+', '_', mod_id)

    parts = [
        _SYSTEM_CONTEXT,
        f"""## MOD SPECIFICATION

### Mod Name: {name}
### Target: Minecraft 1.12.2, Forge

### Description:
{description}

### Suggested identifiers (follow these or suggest better ones):
- mod_id: {mod_id}
- group: asd.itamio.{mod_id}
- archivesBaseName: {name.replace(" ", "-")}
- main class: {name.replace(" ", "").replace("'", "")}Mod
- author: Itamio

### Requirements:
- The mod MUST implement ALL features described above
- Every file must be complete and compilable
- Use the package asd.itamio.{mod_id}
- Follow the STRICT OUTPUT FORMAT exactly
""",
        _OUTPUT_FORMAT,
        f"""## FINAL CHECKLIST BEFORE RESPONDING

1. Does every Java file have "package asd.itamio.{mod_id};"?
2. Does the main class end with "Mod"?
3. Are ALL imports included?
4. Is TextComponentString used (not Component.literal)?
5. Is the @Mod annotation correct with modid, name, version?
6. Is authorList set to ["Itamio"] in mcmod.info?
7. Does the output follow the ---FILE: path--- format exactly?
8. Did you include the ---METADATA--- block at the end?

Generate the complete mod source code now:""",
    ]
    return "\n\n".join(parts)


# ═══════════════════════════════════════════════════════════════════════════════
#  AI Response parsing
# ═══════════════════════════════════════════════════════════════════════════════

def _parse_ai_response(text: str) -> dict[str, Any]:
    """Parse an AI response into files dict and metadata dict."""
    files: dict[str, str] = {}
    metadata: dict[str, str] = {}

    # Extract METADATA block
    meta_match = re.search(r'---METADATA---\s*\n(.*?)\n---', text, re.DOTALL)
    if meta_match:
        for line in meta_match.group(1).strip().splitlines():
            if ':' in line:
                key, val = line.split(':', 1)
                metadata[key.strip()] = val.strip()

    # Extract FILE blocks
    file_pattern = re.compile(
        r'---FILE:\s*(.+?)---\s*\n```(\w*)\s*\n(.*?)\n```',
        re.DOTALL
    )

    for match in file_pattern.finditer(text):
        filepath = match.group(1).strip().lstrip("./")
        content = match.group(3).strip()

        if not content or len(content) < 30:
            continue
        if filepath.endswith("build.gradle") or "gradle/" in filepath:
            continue

        if not filepath.startswith("src/"):
            if filepath.startswith("main/"):
                filepath = "src/" + filepath
            elif filepath.startswith("java/"):
                filepath = "src/main/" + filepath
            elif filepath.endswith(".java") and "/" not in filepath:
                filepath = "src/main/java/asd/itamio/unknown/" + filepath

        files[filepath] = content

    # Fallback: filepath: pattern
    fb_pattern = re.compile(
        r'(?:^|\n)(?:filepath|path):\s*(.+?\.(?:java|json|lang|txt|info))\s*\n'
        r'```(\w*)\s*\n(.*?)\n```',
        re.DOTALL | re.MULTILINE
    )
    for match in fb_pattern.finditer(text):
        fp = match.group(1).strip().lstrip("./")
        ct = match.group(3).strip()
        if fp not in files and len(ct) > 30:
            if not fp.startswith("src/"):
                if fp.startswith("main/"):
                    fp = "src/" + fp
                elif fp.endswith(".java") and "/" not in fp:
                    fp = "src/main/java/asd/itamio/unknown/" + fp
            if not fp.endswith("build.gradle") and "gradle/" not in fp:
                files[fp] = ct

    # Auto-extract metadata from source files
    if not metadata:
        metadata = _extract_metadata_from_files(files)

    return {"files": files, "metadata": metadata}


def _extract_metadata_from_files(files: dict[str, str]) -> dict[str, str]:
    """Extract mod metadata from Java files and mcmod.info."""
    md: dict[str, str] = {}

    for content in files.values():
        if not content or ".java" not in str(content)[:50]:
            continue

        m = re.search(r'public\s+static\s+final\s+String\s+MODID\s*=\s*"([^"]+)"', content)
        if m and "mod_id" not in md:
            md["mod_id"] = m.group(1)

        m = re.search(r'public\s+static\s+final\s+String\s+NAME\s*=\s*"([^"]+)"', content)
        if m and "name" not in md:
            md["name"] = m.group(1)

        m = re.search(r'^package\s+([a-z0-9_.]+)\s*;', content, re.MULTILINE)
        if m and "group" not in md:
            md["group"] = m.group(1)

        m = re.search(r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"', content)
        if m and "mod_version" not in md:
            md["mod_version"] = m.group(1)

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

    if "name" in md and "archivesBaseName" not in md:
        md["archivesBaseName"] = md["name"].replace(" ", "-").replace("'", "")

    md.setdefault("mod_id", "unknown_mod")
    md.setdefault("group", "asd.itamio.unknown")
    md.setdefault("mod_version", "1.0.0")
    md.setdefault("archivesBaseName", md["mod_id"])

    return md


# ═══════════════════════════════════════════════════════════════════════════════
#  Build engine
# ═══════════════════════════════════════════════════════════════════════════════

def _build_mod(files: dict[str, str], metadata: dict[str, str]) -> bool:
    """Place files in workspace, update config, build, collect jar."""
    java_dir = WORKSPACE / "src" / "main" / "java"
    resources_dir = WORKSPACE / "src" / "main" / "resources"
    assets_dir = resources_dir / "assets"
    build_libs = WORKSPACE / "build" / "libs"

    _print_header("Building mod...", "Step 5")

    # Clean workspace
    _print_info("Cleaning workspace...")
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

    # Place files
    _print_info(f"Placing {len(files)} source files...")
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

    # Update build.gradle
    _print_info("Updating build.gradle...")
    build_gradle = WORKSPACE / "build.gradle"
    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")
        for key, gradle_key in [("group", "group"), ("archivesBaseName", "archivesBaseName"), ("mod_version", "version")]:
            if key in metadata:
                text = re.sub(
                    rf'(?m)^\s*{gradle_key}\s*=\s*["\'][^"\']+["\']',
                    f'{gradle_key} = "{metadata[key]}"',
                    text,
                )
        build_gradle.write_text(text, encoding="utf-8")

    # Build
    _print_info("Running ./gradlew build (this takes ~30-60s)...")
    gradlew = WORKSPACE / "gradlew"
    if not gradlew.exists():
        _print_err("gradlew not found — cannot build")
        return False

    env = os.environ.copy()
    if os.path.exists(JAVA8_HOME):
        env["JAVA_HOME"] = JAVA8_HOME
        env["PATH"] = f"{JAVA8_HOME}/bin:{env.get('PATH', '')}"

    try:
        result = subprocess.run(
            [str(gradlew), "clean", "build"],
            cwd=WORKSPACE, capture_output=True, text=True, timeout=600, env=env
        )
    except subprocess.TimeoutExpired:
        _print_err("Build timed out after 600s")
        return False

    if result.returncode != 0:
        _print_err("Build FAILED")
        # Show relevant errors
        for line in (result.stderr + result.stdout).splitlines():
            lower = line.lower()
            if any(kw in lower for kw in ("error:", "cannot find symbol", "does not exist", "BUILD FAILED")):
                print(f"    {line.strip()}")
        return False

    _print_ok("Build successful")

    # Collect jar
    jars = sorted(build_libs.glob("*.jar"))
    valid = [j for j in jars if not any(x in j.name.lower() for x in ("-sources", "-javadoc", "-dev"))]
    if not valid:
        _print_err("No JAR found in build/libs/")
        return False

    jar_path = valid[0]
    for dest_dir in [WORKSPACE / "ModCollection", WORKSPACE / "ReadyMods"]:
        dest_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar_path, dest_dir / jar_path.name)

    with zipfile.ZipFile(jar_path) as zf:
        class_count = len([n for n in zf.namelist() if n.endswith(".class")])

    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'BUILD COMPLETE!':^54} │")
    print(f"  ├{'─' * 54}┤")
    print(f"  │ {'Jar:':<10} {jar_path.name:<42} │")
    print(f"  │ {'Size:':<10} {jar_path.stat().st_size / 1024:>6.1f} KB{'':<36} │")
    print(f"  │ {'Classes:':<10} {class_count:>6}{'':<36} │")
    print(f"  │ {'Saved:':<10} ModCollection/{jar_path.name:<26} │")
    print(f"  │ {'':<10} ReadyMods/{jar_path.name:<29} │")
    print(f"  └{'─' * 54}┘")
    print()

    return True


# ═══════════════════════════════════════════════════════════════════════════════
#  Workflow steps
# ═══════════════════════════════════════════════════════════════════════════════

def step1_get_name() -> str:
    """Step 1: Ask for the mod name."""
    _print_header("What is the preferred name of your mod?", "Step 1")
    print()
    print("  Examples: 'Super Jump', 'Vein Miner', 'Auto Fish'")
    print()

    while True:
        name = _ask("Mod name: ")
        if name:
            _print_ok(f"Name: \"{name}\"")
            return name
        _print_warn("Please enter a name.")


def step2_get_description() -> str:
    """Step 2: Ask for the mod description (with clipboard support)."""
    _print_header("Describe your mod — what does it do?", "Step 2")
    print()
    print("  ┌─────────────────────────────────────────────────────┐")
    print("  │  • Type 'paste' → read clipboard automatically      │")
    print("  │    (no Cmd+V — the script reads it for you)         │")
    print("  │                                                     │")
    print("  │  • Type your description, press Enter twice to end  │")
    print("  │  • Press Ctrl+D when done                           │")
    print("  └─────────────────────────────────────────────────────┘")
    print()

    while True:
        first = _ask("Description: ")

        if first.lower() == "paste":
            print()
            print("  Reading clipboard...")
            clip = read_clipboard()
            if not clip or not clip.strip():
                _print_warn("Clipboard is empty. Please type manually.")
                continue

            _print_ok(f"{len(clip)} characters read")
            print()
            print("  ── Preview ──")
            for line in _preview(clip).splitlines()[:15]:
                print(f"  │ {line}")
            print("  ─────────────")
            print()

            if _confirm("Use this clipboard content?", default_yes=True):
                _print_ok(f"Description: {len(clip)} chars (clipboard)")
                return clip
            else:
                print("  Please type your description manually:")
                print()
                continue

        elif first:
            lines = [first]
            extra = _read_multiline()
            if extra:
                lines.append(extra)
            desc = "\n".join(lines).strip()
            if desc:
                _print_ok(f"Description: {len(desc)} characters")
                return desc
            _print_warn("Description cannot be empty.")
            print()


def step3_generate_prompt(name: str, description: str) -> Path:
    """Step 3: Generate the AI prompt file."""
    _print_header("Generating AI prompt...", "Step 3")

    prompt = _build_prompt(name, description)
    safe_name = name.lower().replace(" ", "_").replace("'", "").replace("?", "")
    prompt_path = PROMPTS_DIR / f"{safe_name}_prompt.txt"
    prompt_path.write_text(prompt, encoding="utf-8")

    _print_ok(f"Prompt saved: {prompt_path}")
    _print_info(f"Size: {len(prompt)} chars, {len(prompt.splitlines())} lines")

    return prompt_path


def step4_pose_ai_task(prompt_path: Path) -> Path | None:
    """Step 4: Tell user to use the AI, wait for them to return with a response file."""
    _print_header("Your task: Get AI response", "Step 4")

    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'YOUR TASK':^54} │")
    print(f"  ├{'─' * 54}┤")
    print(f"  │ {'1. Open this file:':<54} │")
    print(f"  │ {str(prompt_path):<54} │")
    print(f"  │ {'':<54} │")
    print(f"  │ {'2. Copy its ENTIRE contents':<54} │")
    print(f"  │ {'3. Paste to your AI assistant':<54} │")
    print(f"  │ {'   (Claude, ChatGPT, DeepSeek)':<54} │")
    print(f"  │ {'4. Copy the AI full response':<54} │")
    print(f"  │ {'5. Save it to a .txt file':<54} │")
    print(f"  │ {'6. Return here when done':<54} │")
    print(f"  └{'─' * 54}┘")
    print()

    _ask("Press Enter when you have saved the AI response...")

    print()
    _print_info("Where is the AI response file?")

    while True:
        resp = _ask("Path to response file (or 'skip' to skip build): ")
        if resp.lower() == "skip":
            return None
        rp = Path(resp).expanduser().resolve()
        if rp.exists():
            _print_ok(f"Found: {rp}")
            return rp
        _print_warn(f"File not found: {rp}")
        print("  Try again, or type 'skip' to skip the build step.")


def step5_build_from_response(response_path: Path) -> bool:
    """Step 5: Parse AI response and build the mod."""
    _print_header("Parsing AI response...", "Step 5")

    text = response_path.read_text(encoding="utf-8")
    result = _parse_ai_response(text)
    files = result["files"]
    metadata = result["metadata"]

    if not files:
        _print_err("No files found in the AI response.")
        _print_info("Make sure the response uses ---FILE: path--- format.")
        return False

    _print_ok(f"Found {len(files)} files:")
    for fp in sorted(files):
        print(f"      {fp}")

    _print_info(f"Metadata: mod_id={metadata.get('mod_id')}, "
                f"group={metadata.get('group')}, "
                f"archivesBaseName={metadata.get('archivesBaseName')}")

    return _build_mod(files, metadata)


def step6_ask_continue() -> bool:
    """Step 6: Ask if user wants to create another mod."""
    _print_header("All tasks complete!", "Done")
    return _confirm("Create another mod?", default_yes=True)


# ═══════════════════════════════════════════════════════════════════════════════
#  Main wizard loop
# ═══════════════════════════════════════════════════════════════════════════════

def run_wizard() -> None:
    """Run the full continuous mod creation wizard."""
    print()
    print("  ╔══════════════════════════════════════════════════════╗")
    print("  ║         Minecraft Mod Creation Wizard v1.0           ║")
    print("  ║                                                      ║")
    print("  ║  Creates 1.12.2 Forge mods with AI assistance.       ║")
    print("  ║  Guides you through every step — name, describe,     ║")
    print("  ║  prompt AI, parse response, build, repeat.           ║")
    print("  ╚══════════════════════════════════════════════════════╝")

    mods_built = 0
    mods_skipped = 0

    while True:
        # Steps 1-2: Gather requirements
        name = step1_get_name()
        description = step2_get_description()

        # Step 3: Generate the prompt
        prompt_path = step3_generate_prompt(name, description)

        # Step 4: User goes to AI, comes back with response
        response_path = step4_pose_ai_task(prompt_path)

        # Step 5: Build (if user provided a response)
        if response_path:
            if step5_build_from_response(response_path):
                mods_built += 1
            else:
                mods_skipped += 1
        else:
            _print_info("Build skipped.")
            mods_skipped += 1

        # Step 6: Continue?
        if not step6_ask_continue():
            break

    # Final summary
    print()
    print(f"  {'=' * 60}")
    print(f"  Session summary: {mods_built} built, {mods_skipped} skipped")
    print(f"  Jars saved in: Mod Development/1.12.2-forge/ReadyMods/")
    print(f"  {'=' * 60}")
    print()
    print("  Goodbye!")
    print()


# ═══════════════════════════════════════════════════════════════════════════════
#  Quick-build mode (skip questions, build from existing AI response)
# ═══════════════════════════════════════════════════════════════════════════════

def run_quick_build(response_path: Path) -> None:
    """Build directly from an existing AI response file."""
    print()
    print(f"  Quick-build mode: {response_path}")
    success = step5_build_from_response(response_path)
    if not success:
        sys.exit(1)


# ═══════════════════════════════════════════════════════════════════════════════
#  Entry point
# ═══════════════════════════════════════════════════════════════════════════════

def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(
        description="Continuous interactive wizard for Minecraft mod creation"
    )
    parser.add_argument(
        "--from-response", "-r",
        help="Skip questions — build directly from an existing AI response file"
    )
    args = parser.parse_args()

    if args.from_response:
        rp = Path(args.from_response).expanduser().resolve()
        if not rp.exists():
            print(f"ERROR: File not found: {args.from_response}")
            sys.exit(1)
        run_quick_build(rp)
    else:
        run_wizard()


if __name__ == "__main__":
    main()
