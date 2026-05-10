#!/usr/bin/env python3
"""
prompt_generator.py — Generate Background Info.txt and prompt.txt per target
=============================================================================
Takes the output of Phase 1 (project_info/ + target_list.json) and:

Step 1 — For each version+loader folder in the bundle directory:
  Composes a Background Info.txt containing:
    1. Working template files from the repository for that target version/loader,
       converted into the format:
         filepath (relative, not full)
         ```
         code_here_
         ```
    2. DIF (Developer Issue Finder) search results — lists common issues for
       this target version and loader, attaching up to 4 of the most common
       issues with their solutions.

Step 2 — For each target:
  Composes a prompt.txt file that combines:
    - Project info (from project_info/)
    - Background Info (from Step 1)
    - A predesigned prompt template instructing an AI to code the mod
      for this version and loader, outputting every file in the format:
        filepath (relative, not full)
        ```
        code_here_
        ```
    - With explicit instructions to provide ALL necessary files.

Usage:
    python3 aibasedversionupgrader/prompt_generator.py <modrinth_url>
    python3 aibasedversionupgrader/prompt_generator.py --project-info-dir project_info/ --target-list target_list.json --output-dir prompt_output/

Options:
    --project-info-dir DIR   Path to project_info/ directory (default: project_info/)
    --target-list FILE       Path to target_list.json (default: target_list.json)
    --output-dir DIR         Output directory for prompt files (default: generated_prompts/)
    --bundle-dir DIR         Bundle directory (default: generated_prompts/bundle/)
    --max-dif-results N      Max DIF results per target (default: 4)
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


_
… [truncated] …
ent import run_phase1_project_info, run_phase1_missing_versions
        run_phase1_project_info(args.modrinth_url)
        run_phase1_missing_versions(args.modrinth_url)

    # Validate inputs
    if not target_list_path.exists():
        print(f"ERROR: target_list.json not found at {target_list_path}", file=sys.stderr)
        print("Run Phase 1 first or provide a Modrinth URL.", file=sys.stderr)
        sys.exit(1)

    target_list = json.loads(_read_text(target_list_path))
    if not target_list:
        print("No targets to process — all versions already published.")
        return

    if not project_info_dir.exists():
        print(f"ERROR: project_info/ not found at {project_info_dir}", file=sys.stderr)
        sys.exit(1)

    # Load manifest
    manifest_path = _REPO_ROOT / "version-manifest.json"
    manifest: Dict = {}
    if manifest_path.exists():
        manifest = json.loads(_read_text(manifest_path))
    else:
        print(f"ERROR: version-manifest.json not found at {manifest_path}", file=sys.stderr)
        sys.exit(1)

    repo_root = _REPO_ROOT

    print(f"Generating prompts for {len(target_list)} target(s)...")
    print(f"  Project info: {project_info_dir}")
    print(f"  Target list:  {target_list_path}")
    print(f"  Output dir:   {output_dir}")
    print(f"  Bundle dir:   {bundle_dir}")
    print(f"  Max DIF:      {max_dif}")
    print()

    results = generate_all(
        repo_root=repo_root,
        project_info_dir=project_info_dir,
        target_list=target_list,
        manifest=manifest,
        output_dir=output_dir,
        bundle_dir=bundle_dir,
        max_dif_results=max_dif,
    )

    print(f"\n{'=' * 70}")
    print(f"Generation complete!")
    print(f"  Background Info.txt files: {len(results['background_info_files'])}")
    print(f"  prompt.txt files:          {len(results['prompt_files'])}")
    print(f"\nOutput directory: {output_dir.resolve()}")
    print(f"{'=' * 70}")


if __name__ == "__main__":
    main()
