"""
mod_wizard/display.py — Terminal display and formatting utilities.

All print formatting lives here. No logic, no I/O beyond printing.
"""

import sys
from typing import NoReturn


def header(title: str, *, step: str = "") -> None:
    """Print a formatted section header with optional step label."""
    if step:
        print(f"\n{'─' * 60}")
        print(f"  {step}: {title}")
        print(f"{'─' * 60}")
    else:
        print(f"\n{'=' * 60}")
        print(f"  {title}")
        print(f"{'=' * 60}")


def task_box(title: str) -> None:
    """Print a task description box — what the user needs to do."""
    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'TASK: ' + title:<50} │")
    print(f"  └{'─' * 54}┘")
    print()


def build_result_box(jar_name: str, size_kb: float, class_count: int) -> None:
    """Print a build-complete result box."""
    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'BUILD COMPLETE!':^54} │")
    print(f"  ├{'─' * 54}┤")
    print(f"  │ {'Jar:':<10} {jar_name:<42} │")
    print(f"  │ {'Size:':<10} {size_kb:>6.1f} KB{'':<36} │")
    print(f"  │ {'Classes:':<10} {class_count:>6}{'':<36} │")
    print(f"  │ {'Saved:':<10} ModCollection/{jar_name:<26} │")
    print(f"  │ {'':<10} ReadyMods/{jar_name:<29} │")
    print(f"  └{'─' * 54}┘")
    print()


def info(msg: str) -> None:
    """Print an informational line."""
    print(f"  ℹ  {msg}")


def ok(msg: str) -> None:
    """Print a success/confirmation line."""
    print(f"  ✓  {msg}")


def warn(msg: str) -> None:
    """Print a warning line."""
    print(f"  ⚠  {msg}")


def err(msg: str) -> None:
    """Print an error line."""
    print(f"  ✗  {msg}")


def banner() -> None:
    """Print the wizard welcome banner."""
    print()
    print("  ╔══════════════════════════════════════════════════════╗")
    print("  ║         Minecraft Mod Creation Wizard v1.0           ║")
    print("  ║                                                      ║")
    print("  ║  Creates 1.12.2 Forge mods with AI assistance.       ║")
    print("  ║  Guides you through every step — name, describe,     ║")
    print("  ║  prompt AI, parse response, build, repeat.           ║")
    print("  ╚══════════════════════════════════════════════════════╝")


def summary(built: int, skipped: int) -> None:
    """Print the end-of-session summary."""
    print()
    print(f"  {'=' * 60}")
    print(f"  Session: {built} built, {skipped} skipped")
    print(f"  Jars in: Mod Development/1.12.2-forge/ReadyMods/")
    print(f"  {'=' * 60}")
    print()
    print("  Goodbye!")
    print()


def goodbye() -> NoReturn:
    """Print goodbye and exit cleanly."""
    print()
    print("  Goodbye!")
    sys.exit(0)
