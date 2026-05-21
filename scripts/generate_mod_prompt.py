#!/usr/bin/env python3
"""
generate_mod_prompt.py — Generate a detailed AI prompt for Minecraft mod creation.

This script creates a self-contained prompt file that you can paste to any AI
assistant (Claude, GPT, DeepSeek, etc.). The AI's response will be in a
structured format that can be extracted and built automatically.

Usage:
    # Interactive mode (two clean question sessions):
    python3 scripts/generate_mod_prompt.py

    # Command-line mode:
    python3 scripts/generate_mod_prompt.py \\
        --name "Super Jump" \\
        --description "Makes the player jump 10 blocks high when pressing J" \\
        --output my_mod_prompt.txt

    # Read description from a file:
    python3 scripts/generate_mod_prompt.py \\
        --name "Vein Miner" \\
        --desc-file vein_miner_spec.txt \\
        --output vein_miner_prompt.txt

What it outputs:
    A prompt file that, when pasted to an AI, will cause it to respond with
    complete, compilable 1.12.2 Forge mod source code in a parseable format.

After getting the AI response, use extract_ai_response.py to build the mod:
    python3 scripts/extract_ai_response.py ai_response.txt
"""

from __future__ import annotations

import argparse
import os
import platform
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
WORKSPACE = ROOT / "Mod Developement" / "1.12.2-forge"


# ── Prompt template sections ─────────────────────────────────────────────────

SYSTEM_CONTEXT = r"""You are an expert Minecraft mod developer creating a mod for Minecraft 1.12.2 with Forge. You write complete, compilable Java code. Every file you output must be syntactically correct and complete.

## PROJECT CONTEXT

This is the ModCompiler project — a system for building Minecraft mods. The local 1.12.2 Forge workspace is at:

    Mod Developement/1.12.2-forge/

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
- MinecraftForge.TERRAIN_GEN_BUS: terrain generation events
- GameRegistry: registerBlock(), registerItem()
- Configuration: config.get(category, key, default, comment)

### Message Sending (1.12.2 API):
```java
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

player.sendMessage(new TextComponentString(
    TextFormatting.GREEN + "Message text"));
```

## BUILD.GRADLE TEMPLATE

The build.gradle already exists in the workspace with ForgeGradle 2.3 configured. You only need to provide:
- group (in the mod metadata below)
- archivesBaseName (in the mod metadata below)
- DO NOT output the build.gradle file itself

## MCMOD.INFO TEMPLATE

The mcmod.info file already exists. You should provide an updated version:
```json
[
{
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
}
]
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

OUTPUT_FORMAT_INSTRUCTIONS = r"""
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

---FILE: src/main/resources/assets/<modid>/lang/en_us.lang---
```
# lang file content
```

After all files, end with:

---METADATA---
group: asd.itamio.<modid>
archivesBaseName: <Archives-Base-Name>
---

### Format rules:
1. File path MUST start with "src/main/" (java, resources) or "src/main/resources/" (assets etc.)
2. Use ---FILE: path--- as the file delimiter (exactly 3 dashes, word FILE, colon, space, path, 3 dashes)
3. Put the content inside ```lang``` code fences where lang is java/json/text
4. End the response with ---METADATA--- block containing group and archivesBaseName
5. Do NOT include build.gradle or any gradle files
6. Every Java file MUST compile — include ALL imports
"""


# ── Prompt builder ───────────────────────────────────────────────────────────

def build_prompt(
    name: str,
    description: str,
    mc_version: str = "1.12.2",
    loader: str = "forge",
    extra_context: str = "",
) -> str:
    """Build the complete AI prompt."""

    mod_id = suggest_mod_id(name)

    prompt_parts = []

    prompt_parts.append(SYSTEM_CONTEXT)

    spec = f"""## MOD SPECIFICATION

### Mod Name: {name}
### Target: Minecraft {mc_version}, Forge

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
"""
    prompt_parts.append(spec)

    if extra_context:
        prompt_parts.append(f"""## ADDITIONAL CONTEXT

{extra_context}
""")

    prompt_parts.append(OUTPUT_FORMAT_INSTRUCTIONS)

    prompt_parts.append(f"""## FINAL CHECKLIST BEFORE RESPONDING

1. Does every Java file have "package asd.itamio.{mod_id};"?
2. Does the main class end with "Mod" (e.g., {name.replace(" ", "").replace("'", "")}Mod)?
3. Are ALL imports included?
4. Is TextComponentString used (not Component.literal)?
5. Is the @Mod annotation correct with modid, name, version?
6. Is authorList set to ["Itamio"] in mcmod.info?
7. Does the output follow the ---FILE: path--- format exactly?
8. Did you include the ---METADATA--- block at the end?

Generate the complete mod source code now:""")

    return "\n\n".join(prompt_parts)


def suggest_mod_id(name: str) -> str:
    """Convert a mod name to a suggested mod_id."""
    mod_id = re.sub(r'[^a-zA-Z0-9\s]', '', name)
    mod_id = mod_id.lower().strip()
    mod_id = re.sub(r'\s+', '_', mod_id)
    return mod_id


# ── Clipboard helper ─────────────────────────────────────────────────────────

def read_clipboard() -> str | None:
    """Read text from the system clipboard programmatically.

    Uses native OS commands — NO keyboard paste required.
    The script reads the clipboard directly via pbpaste (macOS),
    wl-paste/xclip (Linux), or Get-Clipboard (Windows).

    Returns the clipboard text, or None if empty or unavailable.
    """
    system = platform.system()

    try:
        if system == "Darwin":
            result = subprocess.run(
                ["pbpaste"], capture_output=True, text=True, timeout=5
            )
            content = result.stdout
        elif system == "Linux":
            for cmd in (
                ["wl-paste"],
                ["wl-paste", "--primary"],
                ["xclip", "-o", "-selection", "clipboard"],
                ["xclip", "-o"],
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
    """Return a truncated preview of text for terminal display."""
    if len(text) <= max_chars:
        return text
    return text[:max_chars].rstrip() + "\n... (truncated)"


# ── Interactive input sessions ───────────────────────────────────────────────

def interactive_name() -> str:
    """Session 1 of 2: Ask the user for the preferred mod name."""
    print()
    print("=" * 60)
    print("  QUESTION 1 OF 2 — Mod Name")
    print("=" * 60)
    print()
    print("  What is the preferred name of your mod?")
    print()
    print("  Examples: 'Super Jump', 'Vein Miner', 'Auto Fish'")
    print()

    while True:
        try:
            name = input("  > ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)
        if name:
            print()
            print(f"  \u2713 Mod name: \"{name}\"")
            return name
        print("  \u26a0  Please enter a name for your mod.")


def interactive_description() -> str:
    """Session 2 of 2: Ask the user to describe the mod.

    The user can:
    - Type 'paste' → script reads clipboard automatically (no Cmd+V needed)
    - Type description manually, press Enter twice (blank line) to finish
    - Press Ctrl+D when done typing
    """
    print()
    print("=" * 60)
    print("  QUESTION 2 OF 2 — Mod Description")
    print("=" * 60)
    print()
    print("  Describe your mod. What does it do?")
    print("  What features, commands, or behaviors should it have?")
    print()
    print("  ┌─────────────────────────────────────────────────────┐")
    print("  │ Options:                                            │")
    print("  │                                                     │")
    print("  │  • Type 'paste' → read clipboard automatically      │")
    print("  │    (no Cmd+V needed — script reads it for you)      │")
    print("  │                                                     │")
    print("  │  • Type your description, then press Enter twice    │")
    print("  │    on an empty line to finish                        │")
    print("  │                                                     │")
    print("  │  • Press Ctrl+D when done typing                    │")
    print("  └─────────────────────────────────────────────────────┘")
    print()

    while True:
        try:
            first_line = input("  > ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)

        if first_line.lower() == "paste":
            print()
            print("  Reading clipboard contents...")
            clip = read_clipboard()

            if clip is None or not clip.strip():
                print("  ⚠  Clipboard is empty or unavailable.")
                print("  Please type your description manually instead.")
                print()
                continue

            print(f"  ✓  Clipboard: {len(clip)} characters read")
            print()
            print("  ── Preview ──")
            for line in _preview(clip, 400).splitlines()[:15]:
                print(f"  │ {line}")
            print("  ─────────────")
            print()

            while True:
                choice = input(
                    "  Use this clipboard content? (yes / no / edit): "
                ).strip().lower()
                if choice in ("yes", "y", ""):
                    print()
                    print(f"  ✓  Description: {len(clip)} characters (from clipboard)")
                    return clip
                elif choice in ("no", "n"):
                    print()
                    print("  Please type your description manually:")
                    print()
                    break
                elif choice == "edit":
                    print()
                    print("  The clipboard content has been loaded.")
                    print("  Type additional lines to append, then press Enter twice to finish:")
                    print()
                    extra_lines = _read_multiline()
                    if extra_lines:
                        clip = clip.rstrip() + "\n" + extra_lines
                    print()
                    print(f"  ✓  Description: {len(clip)} characters (clipboard + edits)")
                    return clip
                else:
                    print("  Please answer: yes, no, or edit")

        elif first_line:
            lines = [first_line]
            extra = _read_multiline()
            if extra:
                lines.append(extra)
            description = "\n".join(lines).strip()
            if description:
                print()
                print(f"  ✓  Description: {len(description)} characters")
                return description
            print("  ⚠  Description cannot be empty.")
            print()


def _read_multiline() -> str | None:
    """Read multiple lines from stdin until a blank line or EOF."""
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


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate an AI prompt for creating a Minecraft 1.12.2 Forge mod",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 scripts/generate_mod_prompt.py --name "Super Jump" --description "Lets players jump 10 blocks high"

  python3 scripts/generate_mod_prompt.py --name "Vein Miner" --desc-file vein_miner.txt

  python3 scripts/generate_mod_prompt.py --name "Auto Fish" --description "Auto fishes" --output fish_prompt.txt

After getting the AI response, build the mod with:
  python3 scripts/extract_ai_response.py <response_file>
""",
    )

    parser.add_argument("--name", "-n", help="Display name of the mod (e.g., 'Super Jump')")
    parser.add_argument("--description", "-d", help="Detailed description of what the mod does")
    parser.add_argument("--desc-file", "-f", help="Read description from a file")
    parser.add_argument("--mc-version", default="1.12.2", help="Minecraft version (default: 1.12.2)")
    parser.add_argument("--loader", default="forge", help="Mod loader (default: forge)")
    parser.add_argument("--output", "-o", help="Output file path (default: prompts/<name>_prompt.txt)")
    parser.add_argument("--context-file", help="Additional context/API notes file to include")

    args = parser.parse_args()

    # ── Session 1: Mod name ──
    name = args.name
    if not name:
        name = interactive_name()
    if not name:
        print("ERROR: Mod name is required.")
        sys.exit(1)

    # ── Session 2: Description ──
    description = args.description
    if args.desc_file:
        desc_path = Path(args.desc_file)
        if desc_path.exists():
            description = desc_path.read_text(encoding="utf-8").strip()
        else:
            print(f"ERROR: Description file not found: {args.desc_file}")
            sys.exit(1)
    if not description:
        description = interactive_description()
    if not description:
        print("ERROR: Mod description is required.")
        sys.exit(1)

    extra_context = ""
    if args.context_file:
        ctx_path = Path(args.context_file)
        if ctx_path.exists():
            extra_context = ctx_path.read_text(encoding="utf-8").strip()

    prompt = build_prompt(
        name=name,
        description=description,
        mc_version=args.mc_version,
        loader=args.loader,
        extra_context=extra_context,
    )

    if args.output:
        output_path = Path(args.output)
    else:
        prompts_dir = ROOT / "prompts"
        prompts_dir.mkdir(parents=True, exist_ok=True)
        safe_name = name.lower().replace(" ", "_").replace("'", "").replace("?", "")
        output_path = prompts_dir / f"{safe_name}_prompt.txt"

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(prompt, encoding="utf-8")

    print()
    print(f"{'='*60}")
    print(f"  ✓  Prompt generated successfully!")
    print(f"  Output: {output_path}")
    print(f"  Lines:  {len(prompt.splitlines())}")
    print(f"  Chars:  {len(prompt)}")
    print(f"{'='*60}")
    print()
    print("  Next steps:")
    print(f"    1. Copy the contents of {output_path}")
    print(f"    2. Paste to your AI assistant (Claude, ChatGPT, DeepSeek, etc.)")
    print(f"    3. Save the AI's response to a file")
    print(f"    4. Run: python3 scripts/extract_ai_response.py <response_file>")
    print()


if __name__ == "__main__":
    main()
