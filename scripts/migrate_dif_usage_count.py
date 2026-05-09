#!/usr/bin/env python3
"""
migrate_dif_usage_count.py — Add usage_count to all existing DIF files
========================================================================
One-time migration script to add usage_count: 1 to all existing DIF entries
and inject the usage tracking block at the top of the body.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from dif_core import DIF_DIR, _parse_dif_file, _render_dif_file


def migrate_all_dif_files():
    """Add usage_count: 1 to all existing DIF files that don't have it."""
    if not DIF_DIR.exists():
        print(f"DIF directory not found: {DIF_DIR}")
        return
    
    files = sorted(DIF_DIR.glob("*.md"))
    # Exclude README.md
    files = [f for f in files if f.name != "README.md"]
    
    print(f"Migrating {len(files)} DIF files...\n")
    
    updated_count = 0
    skipped_count = 0
    
    for dif_file in files:
        entry = _parse_dif_file(dif_file)
        if not entry:
            print(f"  ⚠️  Could not parse: {dif_file.name}")
            continue
        
        # Check if usage_count field already exists in the front-matter
        # (even if it's 0, we skip it to avoid overwriting intentional values)
        text = dif_file.read_text(encoding="utf-8")
        has_usage_count = "usage_count:" in text.split("---")[1] if text.startswith("---") and text.count("---") >= 2 else False
        
        if has_usage_count:
            print(f"  ⏭️  Skipped (already has usage_count={entry.usage_count}): {dif_file.name}")
            skipped_count += 1
            continue
        
        # Re-parse to get the raw front-matter dict
        text = dif_file.read_text(encoding="utf-8")
        front = {}
        body = text
        
        if text.startswith("---"):
            end = text.find("\n---", 3)
            if end != -1:
                fm_text = text[3:end].strip()
                body = text[end + 4:].strip()
                for line in fm_text.splitlines():
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    if ":" in line:
                        key, _, val = line.partition(":")
                        key = key.strip()
                        val = val.strip()
                        if val.startswith("[") and val.endswith("]"):
                            items = [x.strip().strip('"\'') for x in val[1:-1].split(",")]
                            front[key] = [x for x in items if x]
                        else:
                            front[key] = val.strip('"\'')
        
        # Set usage_count to 1 (default for existing entries)
        front["usage_count"] = 1
        
        # Render and write back
        new_content = _render_dif_file(front, body)
        dif_file.write_text(new_content, encoding="utf-8")
        
        print(f"  ✓  Updated: {dif_file.name}")
        updated_count += 1
    
    print(f"\n{'=' * 60}")
    print(f"Migration complete!")
    print(f"  Updated: {updated_count} files")
    print(f"  Skipped: {skipped_count} files (already had usage_count)")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    migrate_all_dif_files()
