#!/usr/bin/env python3
"""
phase1_missing_versions.py — Phase 1: Detect Missing Versions
==============================================================
Identifies all missing or corrupt version/loader combinations for a Modrinth project.
Produces target_list.json.

Usage:
    python3 aibasedversionupgrader/phase1_missing_versions.py <modrinth_url>
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from typing import Dict, List, Optional, Set

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

MODRINTH_API = "https://api.modrinth.com/v2"
USER_AGENT = "ModVersionConverter/1.0"
OUTPUT_FILE = Path("target_list.json")


def _get(url: str, token: Optional[str] = None, retries: int = 5) -> object:
    headers = {"User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = token
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
            else:
                raise
        except Exception:
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
            else:
                raise


def normalize_project_ref(url: str) -> str:
    url = url.strip().rstrip("/")
    if "modrinth.com/mod/" in url:
        return url.split("modrinth.com/mod/")[-1].split("/")[0]
    if "modrinth.com/project/" in url:
        return url.split("modrinth.com/project/")[-1].split("/")[0]
    return url


def load_manifest(repo_root: Path) -> Dict:
    manifest_path = repo_root / "version-manifest.json"
    if not manifest_path.exists():
        print(f"WARNING: version-manifest.json not found at {manifest_path}", file=sys.stderr)
        return {"ranges": []}
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def get_all_supported_targets(manifest: Dict) -> List[Dict]:
    """Get all version/loader combinations supported by the manifest."""
    targets = []
    for rng in manifest.get("ranges", []):
        for loader, loader_cfg in rng.get("loaders", {}).items():
            supported = loader_cfg.get("supported_versions", [])
            if not supported:
                # Use min/max range
                supported = [rng.get("anchor_version", rng.get("max_version", ""))]
            for version in supported:
                if version:
                    targets.append({
                        "minecraft_version": version,
                        "loader": loader,
                        "range_folder": rng["folder"],
                    })
    return targets


def get_published_targets(project_ref: str, token: Optional[str]) -> Set[str]:
    """Get set of 'version/loader' keys that are already published on Modrinth."""
    print(f"  Fetching published versions from Modrinth...")
    versions = _get(f"{MODRINTH_API}/project/{project_ref}/version", token)
    published = set()
    for v in versions:
        loaders = v.get("loaders", [])
        game_versions = v.get("game_versions", [])
        for loader in loaders:
            for gv in game_versions:
                published.add(f"{gv}/{loader}")
    print(f"  Found {len(published)} published version/loader combinations")
    return published


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: phase1_missing_versions.py <modrinth_url>", file=sys.stderr)
        sys.exit(1)

    modrinth_url = sys.argv[1].strip()
    token = os.environ.get("MODRINTH_TOKEN", "").strip() or None
    project_ref = normalize_project_ref(modrinth_url)

    print(f"Detecting missing versions for: {project_ref}")

    manifest = load_manifest(ROOT)
    all_targets = get_all_supported_targets(manifest)
    print(f"  Manifest supports {len(all_targets)} version/loader combinations")

    # Fetch project slug for context
    try:
        project = _get(f"{MODRINTH_API}/project/{project_ref}", token)
        slug = project.get("slug", project_ref)
    except Exception:
        slug = project_ref

    published = get_published_targets(project_ref, token)

    # Find missing targets
    missing = []
    for target in all_targets:
        key = f"{target['minecraft_version']}/{target['loader']}"
        if key not in published:
            missing.append({
                "minecraft_version": target["minecraft_version"],
                "loader": target["loader"],
                "slug": slug,
                "status": "missing",
                "range_folder": target["range_folder"],
            })

    print(f"\nResults:")
    print(f"  Total supported targets: {len(all_targets)}")
    print(f"  Already published: {len(published)}")
    print(f"  Missing: {len(missing)}")

    if not missing:
        print("\n✅ All versions are already present on Modrinth. Nothing to build.")
        OUTPUT_FILE.write_text(json.dumps([], indent=2), encoding="utf-8")
        # Write GitHub step summary if available
        summary_file = os.environ.get("GITHUB_STEP_SUMMARY", "")
        if summary_file:
            with open(summary_file, "a") as f:
                f.write(f"## ✅ All Versions Present\n\nAll {len(all_targets)} version/loader combinations are already published on Modrinth. Phase 2 skipped.\n")
        sys.exit(0)

    # Print missing targets
    for t in missing[:20]:
        print(f"  - {t['minecraft_version']}/{t['loader']}")
    if len(missing) > 20:
        print(f"  ... and {len(missing) - 20} more")

    OUTPUT_FILE.write_text(json.dumps(missing, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nTarget list saved to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
