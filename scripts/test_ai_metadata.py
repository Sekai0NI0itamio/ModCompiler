#!/usr/bin/env python3
"""
Test the AI metadata generation and prompt substitution end-to-end.

Usage:
  python3 scripts/test_ai_metadata.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_REPO_ROOT = _HERE.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from run_mod_to_all_converter import (
    _generate_metadata_with_ai,
    _substitute_prompt_variables,
)


def main():
    raw_name = "Lifesteal Mod"

    print("=" * 72)
    print("AI METADATA FORMATTING TEST")
    print("=" * 72)
    print()
    print(f"  Raw name:      {raw_name}")
    print()
    print("  Calling Groq AI to format metadata...")
    print()

    try:
        metadata = _generate_metadata_with_ai(raw_name)
    except Exception as e:
        print(f"  ❌ FAILED: {e}")
        sys.exit(1)

    if not metadata:
        print("  ❌ FAILED: Empty metadata returned")
        sys.exit(1)

    print("  ✅ AI generated mod name + metadata:")
    print()
    for k, v in metadata.items():
        print(f"    {k:20s} = {v}")
    print()

    # Validate expected values
    errors = []

    if metadata.get("author_name") != "Itamio":
        errors.append(f"author_name should be 'Itamio', got '{metadata.get('author_name')}'")

    if not metadata.get("package_path", "").startswith("asd.itamio."):
        errors.append(f"package_path should start with 'asd.itamio.', got '{metadata.get('package_path')}'")

    if not metadata.get("mod_class", "").endswith("Mod"):
        errors.append(f"mod_class should end with 'Mod', got '{metadata.get('mod_class')}'")

    if not metadata.get("mod_client_class", "").endswith("Client"):
        errors.append(f"mod_client_class should end with 'Client', got '{metadata.get('mod_client_class')}'")

    mod_id = metadata.get("mod_id", "")
    if re.search(r"[^a-z0-9]", mod_id):
        errors.append(f"mod_id should be alphanumeric lowercase only, got '{mod_id}'")

    display_name = metadata.get("mod_display_name", "")
    if re.search(r"[!]{2,}", display_name):
        errors.append(f"mod_display_name should not have exclamation marks, got '{display_name}'")

    if errors:
        print("  ❌ Validation errors:")
        for e in errors:
            print(f"    - {e}")
        sys.exit(1)

    # ── Test substitution ─────────────────────────────────────────
    print("  Testing prompt variable substitution...")
    print()

    sample_prompt = """public class ${MOD_CLASS} {
    public static final String MOD_ID = "${MOD_ID}";
    public static final String NAME = "${MOD_DISPLAY_NAME}";
    private static final Logger LOGGER = LogManager.getLogger();
}

@Mod(${MOD_CLASS}.MOD_ID)
public class ${MOD_CLASS} {
    public ${MOD_CLASS}() {
        LOGGER.info("${MOD_DISPLAY_NAME} initialized!");
    }
}

package ${PACKAGE_PATH}.mixin;

// assets/${MOD_ID}/textures/
// "authors": ["${AUTHOR_NAME}"]
"""

    result = _substitute_prompt_variables(sample_prompt, metadata)

    # Verify no remaining variables
    remaining = re.findall(r'\$\{[A-Z_]+\}', result)
    if remaining:
        print(f"  ❌ Unsubstituted variables: {remaining}")
        sys.exit(1)

    print("  ✅ All variables substituted:")
    print()
    for line in result.strip().split("\n"):
        print(f"    {line}")
    print()

    print("=" * 72)
    print("✅ ALL TESTS PASSED")
    print("=" * 72)


if __name__ == "__main__":
    main()