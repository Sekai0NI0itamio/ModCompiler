"""Bundle-writing helpers for generate_veinminer_bundle.py"""
import os
from pathlib import Path

def write_files(base: Path, files: dict):
    """Write a dict of {relative_path: content} under base."""
    for rel, content in files.items():
        p = base / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")

def mod_txt(extra=""):
    from generate_veinminer_bundle import MOD_TXT_BASE
    return MOD_TXT_BASE + extra

def version_txt(mc_version, loader):
    return f"minecraft_version={mc_version}\nloader={loader}\n"

