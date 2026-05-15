#!/usr/bin/env python3
"""
Test script to generate and verify prompts with Version Guide for ALL
supported version+loader combinations.

Usage:
  python3 scripts/test_prompt_generation.py
  python3 scripts/test_prompt_generation.py --verbose
  python3 scripts/test_prompt_generation.py --output-dir /tmp/test_prompts
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Dict, List

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from generate_prompts_in_bundle import (
    _collect_template_files,
    _get_version_range_for_version,
    _find_guide_file,
    _get_fallback_mod_values,
    _load_version_guide,
    _parse_projectinfo,
    generate_prompt,
)


def get_supported_targets(manifest_path: Path) -> List[Dict]:
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
                    "template_dir": loader_config["template_dir"],
                })
    return targets


SAMPLE_PROJECTINFO = """Mod Name: Test Mod
Mod Author: Itamio
Mod Path: com.itamio.testmod
Mod Summary:
A test mod for verification.
Mod Description (what it intends to do):
Test mod that does test things.
Source Mod ID: 999999
  Source: 1.20.1 (forge)"""


def verify_version_guide_inclusion(target: Dict, prompt_content: str) -> List[str]:
    errors = []
    mc = target["minecraft_version"]
    ld = target["loader"]

    if "VERSION GUIDE" not in prompt_content:
        errors.append("VERSION GUIDE section missing from prompt")
    else:
        # Verify the guide is not empty
        guide_start = prompt_content.find("VERSION GUIDE —")
        if guide_start == -1:
            errors.append("VERSION GUIDE header malformed")
        else:
            guide_section = prompt_content[guide_start:]
            next_section = guide_section.find("PART 3:")
            if next_section == -1:
                errors.append("PART 3 not found after VERSION GUIDE")
            else:
                guide_content = guide_section[:next_section]
                # The guide content should be substantial (not just the header lines)
                guide_lines = [l for l in guide_content.split("\n") if l.strip()]
                if len(guide_lines) < 10:
                    errors.append(f"VERSION GUIDE too short ({len(guide_lines)} lines) — may be empty or missing")

    return errors


def verify_no_unsubstituted_vars(prompt_content: str) -> List[str]:
    errors = []
    unsub = re.findall(r'\$\{[A-Z_]+\}', prompt_content)
    if unsub:
        unique = sorted(set(unsub))
        errors.append(f"Unsubstituted variables found: {unique}")
    return errors


def verify_no_hardcoded_examples_in_guide(prompt_content: str) -> List[str]:
    """Only check the VERSION GUIDE section for hardcoded examples.
    The template files section legitimately contains build system references."""
    errors = []
    guide_section = _extract_version_guide_section(prompt_content)
    if not guide_section:
        return errors

    patterns = [
        (r'\bExampleMod\b', "ExampleMod"),
        (r'\bMyMod\b', "MyMod"),
        (r'"examplemod"', '"examplemod"'),
        (r'"example_mod"', '"example_mod"'),
        (r'com\.example\.examplemod', "com.example.examplemod"),
        (r'com\.yourname\.modid', "com.yourname.modid"),
    ]
    for pat, name in patterns:
        if re.search(pat, guide_section):
            errors.append(f"Hardcoded example in guide: {name}")
    return errors


def _extract_version_guide_section(prompt_content: str) -> str:
    """Extract just the VERSION GUIDE section from the prompt."""
    guide_start = prompt_content.find("VERSION GUIDE —")
    if guide_start == -1:
        return ""
    guide_section = prompt_content[guide_start:]
    next_section = guide_section.find("\n─\nPART 3:")
    if next_section == -1:
        return ""
    return guide_section[:next_section]


def verify_no_confusing_content_in_guide(prompt_content: str) -> List[str]:
    """Only check the VERSION GUIDE section for confusing content.
    Template files legitimately contain build.gradle, settings.gradle, etc."""
    errors = []
    guide_section = _extract_version_guide_section(prompt_content)
    if not guide_section:
        return errors

    confusing = [
        (r'build\.gradle', "build.gradle reference"),
        (r'settings\.gradle', "settings.gradle reference"),
        (r'gradle\.properties', "gradle.properties reference"),
        (r'gradlew\b', "gradlew reference"),
        (r'genIntellijRuns', "genIntellijRuns reference"),
        (r'genEclipseRuns', "genEclipseRuns reference"),
        (r'setupDecompWorkspace', "setupDecompWorkspace reference"),
        (r'\bMDK\b', "MDK reference"),
        (r'install.*JDK', "install JDK reference"),
        (r'Eclipse Temurin', "Eclipse Temurin reference"),
        (r'AdoptOpenJDK', "AdoptOpenJDK reference"),
        (r'JAVA_HOME', "JAVA_HOME reference"),
    ]
    for pat, name in confusing:
        if re.search(pat, guide_section, re.IGNORECASE):
            errors.append(f"Confusing content in guide: {name}")
    return errors


def test_version_guide_loading(target: Dict, manifest: Dict, repo_root: Path) -> List[str]:
    errors = []
    mc = target["minecraft_version"]
    ld = target["loader"]

    # Test range detection
    range_folder = _get_version_range_for_version(mc, manifest)
    if not range_folder:
        errors.append(f"No range folder found for {mc}")
        return errors

    # Test guide file existence
    guide_path = _find_guide_file(range_folder, ld)
    if not guide_path:
        errors.append(f"No guide file for range={range_folder} loader={ld}")
        return errors

    # Test guide loading with substitution
    meta = _parse_projectinfo(SAMPLE_PROJECTINFO)
    guide = _load_version_guide(mc, ld, manifest, meta, substitute=True)
    if not guide:
        errors.append(f"Guide loaded but empty for {mc}-{ld}")
        return errors

    # Check NO remaining variables
    var_matches = re.findall(r'\$\{[A-Z_]+\}', guide)
    if var_matches:
        unique = sorted(set(var_matches))
        errors.append(f"Unsubstituted variables remain in guide: {unique}")

    # Verify at least some substitutions happened (guide should contain mod-specific values)
    if "${MOD_ID}" in guide:
        errors.append("${MOD_ID} not substituted")
    if "${MOD_CLASS}" in guide:
        errors.append("${MOD_CLASS} not substituted")

    return errors


def test_full_prompt_generation(target: Dict, manifest: Dict, repo_root: Path, output_dir: Path | None) -> List[str]:
    errors = []
    mc = target["minecraft_version"]
    ld = target["loader"]
    template_dir = repo_root / target["template_dir"]

    prompt = generate_prompt(
        minecraft_version=mc,
        loader=ld,
        projectinfo_text=SAMPLE_PROJECTINFO,
        background_info="(test background info)",
        template_dir=template_dir,
        repo_root=repo_root,
        manifest=manifest,
        substitute_guide=True,
    )

    errors.extend(verify_version_guide_inclusion(target, prompt))
    errors.extend(verify_no_unsubstituted_vars(prompt))
    errors.extend(verify_no_hardcoded_examples_in_guide(prompt))
    errors.extend(verify_no_confusing_content_in_guide(prompt))

    if output_dir:
        output_dir.mkdir(parents=True, exist_ok=True)
        out_path = output_dir / f"{mc}-{ld}.txt"
        out_path.write_text(prompt, encoding="utf-8")

    return errors


def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="Test prompt generation with Version Guides for all targets."
    )
    parser.add_argument("--verbose", action="store_true", help="Show detailed output")
    parser.add_argument("--output-dir", default=None, help="Save generated prompts to directory")
    args = parser.parse_args()

    repo_root = _REPO_ROOT
    manifest_path = repo_root / "version-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    targets = get_supported_targets(manifest_path)
    output_dir = Path(args.output_dir) if args.output_dir else None

    print(f"Found {len(targets)} supported targets:")
    for t in targets:
        print(f"  {t['minecraft_version']:>8s} - {t['loader']}")
    print()

    # ── Phase 1: Version Guide Loading ────────────────────────────
    print("=" * 72)
    print("PHASE 1: Version Guide Loading & Substitution")
    print("=" * 72)
    print()

    all_errors: List[str] = []
    guide_errors = 0
    for target in targets:
        mc = target["minecraft_version"]
        ld = target["loader"]
        errs = test_version_guide_loading(target, manifest, repo_root)
        if errs:
            guide_errors += 1
            print(f"  ❌ {mc} - {ld}:")
            for e in errs:
                print(f"       {e}")
            all_errors.extend(errs)
        elif args.verbose:
            print(f"  ✅ {mc} - {ld}: Guide loaded, all vars substituted")

    if guide_errors:
        print(f"\n  {guide_errors}/{len(targets)} targets had guide loading errors")
    else:
        print(f"  ✅ All {len(targets)} targets loaded guides successfully")
    print()

    # ── Phase 2: Full Prompt Generation ───────────────────────────
    print("=" * 72)
    print("PHASE 2: Full Prompt Generation & Verification")
    print("=" * 72)
    print()

    prompt_errors = 0
    for target in targets:
        mc = target["minecraft_version"]
        ld = target["loader"]
        errs = test_full_prompt_generation(target, manifest, repo_root, output_dir)
        if errs:
            prompt_errors += 1
            print(f"  ❌ {mc} - {ld}:")
            for e in errs:
                print(f"       {e}")
            all_errors.extend(errs)
        elif args.verbose:
            print(f"  ✅ {mc} - {ld}: Prompt generated, verified clean")

    if prompt_errors:
        print(f"\n  {prompt_errors}/{len(targets)} targets had prompt errors")
    else:
        print(f"  ✅ All {len(targets)} targets generated clean prompts")
    print()

    # ── Summary ───────────────────────────────────────────────────
    print("=" * 72)
    total_errors = len(all_errors)
    if total_errors:
        print(f"❌ {total_errors} total error(s) across {len(targets)} targets")
        sys.exit(1)
    else:
        print(f"✅ ALL {len(targets)} targets passed all checks!")
        if output_dir:
            print(f"   Output saved to: {output_dir}")
        sys.exit(0)


if __name__ == "__main__":
    main()