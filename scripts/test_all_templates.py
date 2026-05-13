#!/usr/bin/env python3
"""
Test all Minecraft version templates automatically!
Runs verify_template.py for every range and loader in version-manifest.json.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def main() -> int:
    print("=" * 80)
    print("Testing ALL ModCompiler Templates")
    print("=" * 80)

    manifest_path = ROOT / "version-manifest.json"
    with manifest_path.open("r", encoding="utf-8") as f:
        manifest = json.load(f)

    all_results = []
    overall_success = True

    # Set some fast build options
    env = dict(os.environ)
    env["MODCOMPILER_GRADLE_TASKS"] = "jar"
    env["MODCOMPILER_FAST_BUILD"] = "0"
    env["MODCOMPILER_SKIP_TESTS"] = "1"
    env["MODCOMPILER_ALLOW_DEV_JARS"] = "1"

    for range_entry in manifest["ranges"]:
        range_folder = range_entry["folder"]
        loaders = list(range_entry["loaders"].keys())

        print(f"\n{'='*80}")
        print(f"Testing range: {range_folder} (loaders: {', '.join(loaders)})")
        print("=" * 80)

        for loader in loaders:
            output_dir = ROOT / ".workflow_artifacts" / "test-all-templates" / f"{range_folder}-{loader}"
            output_dir.mkdir(parents=True, exist_ok=True)

            print(f"\n--- {range_folder} - {loader} ---")

            cmd = [
                sys.executable,
                str(ROOT / "scripts" / "verify_template.py"),
                "--range-folder", range_folder,
                "--loader", loader,
                "--output-dir", str(output_dir),
                "--timeout-minutes", "10"
            ]

            result = subprocess.run(cmd, cwd=ROOT, env=env)
            all_results.append({
                "range_folder": range_folder,
                "loader": loader,
                "exit_code": result.returncode,
                "output_dir": str(output_dir)
            })

            if result.returncode != 0:
                overall_success = False
                print(f"FAILED: {range_folder} - {loader} (exit code {result.returncode})")
            else:
                print(f"SUCCESS: {range_folder} - {loader}")

    # Print summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    for res in all_results:
        status = "SUCCESS" if res["exit_code"] == 0 else "FAILED "
        print(f"  [{status}] {res['range_folder']} - {res['loader']} -> {res['output_dir']}")

    if overall_success:
        print("\n✅ All templates passed!")
        return 0
    else:
        print("\n❌ Some templates failed!")
        return 1


if __name__ == "__main__":
    sys.exit(main())
