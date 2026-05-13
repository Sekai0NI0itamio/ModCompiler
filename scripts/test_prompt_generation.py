#!/usr/bin/env python3
"""
Test script to generate and verify prompts for all supported versions/loaders.

Usage:
  python3 scripts/test_prompt_generation.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Dict, List, Set

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

# Reuse functions from generate_prompts_in_bundle.py
from generate_prompts_in_bundle import _collect_template_files


def get_supported_targets(manifest_path: Path) -> List[Dict]:
    """
    Extract all supported (minecraft_version, loader) combinations from version-manifest.json.
    """
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    
    targets = []
    for range_entry in manifest["ranges"]:
        folder = range_entry["folder"]
        for loader_name, loader_config in range_entry["loaders"].items():
            supported_versions = loader_config.get("supported_versions", [])
            anchor_version = loader_config.get("anchor_version")
            
            if supported_versions:
                versions = supported_versions
            else:
                versions = [anchor_version]
            
            for mc_version in versions:
                targets.append({
                    "folder": folder,
                    "loader": loader_name,
                    "minecraft_version": mc_version,
                    "template_dir": loader_config["template_dir"]
                })
    return targets


def verify_prompt_requirements(target: Dict, prompt_content: str) -> List[str]:
    """
    Verify the generated prompt has all required sections and restrictions.
    """
    errors = []
    
    # Check that "DO NOT create build files" section exists
    required_texts = [
        "DO NOT create build files",
        "build.gradle",
        "settings.gradle",
        "gradle.properties",
        "gradlew",
        "gradle/wrapper"
    ]
    for text in required_texts:
        if text not in prompt_content:
            errors.append(f"Missing required text: '{text}'")
    
    # Check that build files are NOT in "FILES TO CREATE" section
    forbidden_files = [
        "build.gradle",
        "settings.gradle",
        "gradle.properties",
        "gradlew",
        "gradlew.bat"
    ]
    
    files_to_create_section = prompt_content.split("## FILES TO CREATE")[1].split("##")[0] if "## FILES TO CREATE" in prompt_content else ""
    for forbidden in forbidden_files:
        if forbidden in files_to_create_section:
            errors.append(f"Forbidden file '{forbidden}' found in FILES TO CREATE")
    
    # Check that only source/resource files are in FILES TO CREATE
    allowed_extensions = {".java", ".json", ".toml", ".mcmeta", ".txt"}
    forbidden_extensions = {".gradle", ".bat", ".properties", ".md"}
    # TODO: Extract filenames and verify
    
    return errors


def test_template_files(target: Dict, repo_root: Path) -> List[str]:
    """
    Test that template files are collected correctly (no build files).
    """
    template_dir = repo_root / target["template_dir"]
    if not template_dir.exists():
        return [f"Template dir does not exist: {template_dir}"]
    
    template_files = _collect_template_files(template_dir, repo_root)
    
    errors = []
    forbidden_files = {
        "build.gradle",
        "build.gradle.kts", 
        "settings.gradle",
        "settings.gradle.kts",
        "gradle.properties",
        "gradlew",
        "gradlew.bat"
    }
    
    for file_entry in template_files:
        filename = file_entry["filename"]
        if filename in forbidden_files or filename.startswith("gradle/"):
            errors.append(f"Forbidden template file included: {filename}")
    
    return errors


def main():
    repo_root = _REPO_ROOT
    manifest_path = repo_root / "version-manifest.json"
    
    targets = get_supported_targets(manifest_path)
    
    print(f"Found {len(targets)} supported targets:")
    for target in targets:
        print(f"  - {target['minecraft_version']} - {target['loader']}")
    
    print("\n" + "="*80)
    print("Testing template file collection...")
    print("="*80)
    
    all_errors = []
    for target in targets:
        print(f"\nTesting {target['minecraft_version']} - {target['loader']}...")
        template_errors = test_template_files(target, repo_root)
        
        if template_errors:
            print(f"  ❌ {len(template_errors)} errors:")
            for err in template_errors:
                print(f"    - {err}")
            all_errors.extend(template_errors)
        else:
            print("  ✅ OK")
    
    print("\n" + "="*80)
    if all_errors:
        print(f"❌ Total errors: {len(all_errors)}")
        sys.exit(1)
    else:
        print("✅ All tests passed!")
        sys.exit(0)


if __name__ == "__main__":
    main()
