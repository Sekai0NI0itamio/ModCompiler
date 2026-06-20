"""
mod_wizard/diagnosis.py — Diagnose extracted AI response for issues.

Checks for missing files, structural problems, and common errors
before attempting to compile.
"""

import re
from typing import Any


def diagnose(files: dict[str, str], metadata: dict[str, str]) -> list[str]:
    """Analyze extracted files and return a list of issues found.

    Returns an empty list if everything looks good.
    Each issue is a human-readable string.
    """
    issues: list[str] = []

    # ── Check for Java source files ──
    java_files = {fp: ct for fp, ct in files.items() if fp.endswith(".java")}
    if not java_files:
        issues.append("No Java source files found in the response")
        return issues

    # ── Check for main mod class ──
    main_class_found = False
    for fp, ct in java_files.items():
        if "package asd.itamio" in ct and "@Mod" in ct:
            main_class_found = True
            # Check MODID constant
            if 'MODID = "' not in ct and 'MODID=' not in ct:
                issues.append(f"Main class '{fp}' is missing the MODID constant")
            # Check NAME constant
            if 'NAME = "' not in ct and 'NAME=' not in ct:
                issues.append(f"Main class '{fp}' is missing the NAME constant")
            # Check @Mod annotation
            if "@Mod(" not in ct and "@Mod (" not in ct:
                issues.append(f"Main class '{fp}' is missing the @Mod annotation")
            break

    if not main_class_found:
        issues.append(
            "No main mod class found (expected @Mod annotation in asd.itamio.* package)"
        )

    # ── Check for mcmod.info ──
    has_mcmod = any("mcmod.info" in fp for fp in files)
    if not has_mcmod:
        issues.append("Missing mcmod.info file")

    # ── Check each Java file ──
    for fp, ct in java_files.items():
        # Package declaration
        if not re.search(r'^\s*package\s+', ct, re.MULTILINE):
            issues.append(f"'{fp}' is missing package declaration")

        # Check imports exist if needed
        if "import " not in ct and "class " in ct:
            # Files with no imports but using classes — check common patterns
            if "Logger" in ct and "import org.apache.logging.log4j.Logger" not in ct:
                issues.append(f"'{fp}' uses Logger but missing 'import org.apache.logging.log4j.Logger'")
            if "@Mod" in ct and "import net.minecraftforge.fml.common.Mod" not in ct:
                issues.append(f"'{fp}' uses @Mod but missing 'import net.minecraftforge.fml.common.Mod'")
            if "CommandBase" in ct and "extends" in ct and "import net.minecraft.command.CommandBase" not in ct:
                issues.append(f"'{fp}' extends CommandBase but missing the import")
            if "TextComponentString" in ct and "import net.minecraft.util.text.TextComponentString" not in ct:
                issues.append(f"'{fp}' uses TextComponentString but missing the import")
            if "TextFormatting" in ct and "import net.minecraft.util.text.TextFormatting" not in ct:
                issues.append(f"'{fp}' uses TextFormatting but missing the import")
            if "SubscribeEvent" in ct and "import net.minecraftforge.fml.common.eventhandler.SubscribeEvent" not in ct:
                issues.append(f"'{fp}' uses @SubscribeEvent but missing the import")
            if "MinecraftForge" in ct and "import net.minecraftforge.common.MinecraftForge" not in ct:
                issues.append(f"'{fp}' uses MinecraftForge but missing 'import net.minecraftforge.common.MinecraftForge'")

        # Check for common 1.12.2 API mistakes
        if "Component.literal" in ct:
            issues.append(f"'{fp}' uses Component.literal which does not exist in 1.12.2 (use TextComponentString)")
        if "Component.translatable" in ct:
            issues.append(f"'{fp}' uses Component.translatable which does not exist in 1.12.2 (use TextComponentString)")
        if "net.minecraft.network.chat.Component" in ct:
            issues.append(f"'{fp}' imports Component from chat package (1.12.2 uses net.minecraft.util.text.TextComponentString)")

    # ── Check metadata ──
    if "group" not in metadata or not metadata["group"]:
        issues.append("Missing 'group' in ---METADATA--- block (e.g., group: asd.itamio.modid)")
    if "archivesBaseName" not in metadata or not metadata["archivesBaseName"]:
        issues.append("Missing 'archivesBaseName' in ---METADATA--- block")

    # ── Check output format ──
    if not issues:
        issues.append("✓ No structural issues detected — all required files and metadata present")

    return issues


def summarize_for_fix_prompt(issues: list[str]) -> str:
    """Convert diagnosis issues into a concise summary for the fix prompt."""
    if not issues:
        return "No issues detected."
    return "\n".join(f"- {i}" for i in issues)
