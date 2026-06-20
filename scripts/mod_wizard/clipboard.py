"""
mod_wizard/clipboard.py — Cross-platform clipboard read/write with backup.

Reads and writes the system clipboard programmatically.
Automatically saves/restores original content when used.
"""

import platform
import subprocess


# ── Internal state ───────────────────────────────────────────────────────────

_backup: str | None = None


# ── Read ─────────────────────────────────────────────────────────────────────

def read() -> str | None:
    """Read text from the system clipboard.

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


# ── Write ────────────────────────────────────────────────────────────────────

def set(text: str, *, save_backup: bool = True) -> bool:
    """Write text to the system clipboard.

    If save_backup is True (default), the current clipboard content
    is saved and can be restored later with restore().

    Returns True on success, False if clipboard tools are unavailable.
    """
    global _backup

    if save_backup:
        _backup = read()

    system = platform.system()

    try:
        if system == "Darwin":
            subprocess.run(
                ["pbcopy"], input=text, text=True, timeout=10, check=True
            )
            return True
        elif system == "Linux":
            for cmd in (["wl-copy"], ["xclip", "-selection", "clipboard"]):
                try:
                    subprocess.run(
                        cmd, input=text, text=True, timeout=10, check=True
                    )
                    return True
                except (FileNotFoundError, subprocess.TimeoutExpired):
                    continue
            return False
        elif system == "Windows":
            subprocess.run(
                ["powershell", "-Command", "Set-Clipboard -Value $input"],
                input=text, text=True, timeout=10, check=True,
            )
            return True
        else:
            return False
    except (FileNotFoundError, subprocess.TimeoutExpired, subprocess.CalledProcessError):
        return False


# ── Backup / restore ─────────────────────────────────────────────────────────

def backup() -> None:
    """Explicitly save current clipboard content for later restore."""
    global _backup
    _backup = read()


def restore() -> bool:
    """Restore the previously saved clipboard content.

    Returns True if restored, False if there was no backup or writing failed.
    """
    global _backup
    if _backup is None:
        return False
    result = set(_backup, save_backup=False)
    _backup = None
    return result


def has_backup() -> bool:
    """Check if there's a saved clipboard backup."""
    return _backup is not None


# ── Display ──────────────────────────────────────────────────────────────────

def preview(text: str, max_chars: int = 400) -> str:
    """Return a truncated preview suitable for terminal display."""
    if len(text) <= max_chars:
        return text
    return text[:max_chars].rstrip() + "\n... (truncated)"
