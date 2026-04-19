#!/usr/bin/env python3
"""Check the current state of a Modrinth project — how many real vs shell versions."""
import json
import sys
import urllib.request
import urllib.parse
import os

def check_project(slug: str):
    token = os.environ.get("MODRINTH_TOKEN", "")
    headers = {"User-Agent": "ModCompiler/1.0"}
    if token:
        headers["Authorization"] = token

    # Get project
    req = urllib.request.Request(
        f"https://api.modrinth.com/v2/project/{slug}",
        headers=headers
    )
    with urllib.request.urlopen(req) as r:
        proj = json.loads(r.read())

    print(f"\n=== {proj['title']} ({slug}) ===")
    print(f"Total downloads: {proj.get('downloads', 0):,}")
    print(f"Game versions: {proj.get('game_versions', [])}")
    print(f"Loaders: {proj.get('loaders', [])}")

    # Get all versions
    req2 = urllib.request.Request(
        f"https://api.modrinth.com/v2/project/{slug}/version",
        headers=headers
    )
    with urllib.request.urlopen(req2) as r:
        versions = json.loads(r.read())

    print(f"\nVersions ({len(versions)} total):")
    shells = []
    real = []
    for v in sorted(versions, key=lambda x: x.get("date_published", "")):
        files = v.get("files", [])
        size = max((f.get("size", 0) for f in files), default=0)
        mc = v.get("game_versions", [])
        loaders = v.get("loaders", [])
        vid = v["id"]
        vnum = v.get("version_number", "")
        status = "SHELL" if size < 5000 else "real"
        if size < 5000:
            shells.append(v)
        else:
            real.append(v)
        print(f"  [{vid}] {vnum} mc={mc} loader={loaders} size={size:,}B → {status}")

    print(f"\nSummary: {len(real)} real, {len(shells)} shells")
    if shells:
        print("Shell IDs:", [s["id"] for s in shells])
    return shells, real

if __name__ == "__main__":
    slugs = sys.argv[1:] if len(sys.argv) > 1 else ["set-home-anywhere", "sort-chest"]
    for slug in slugs:
        try:
            check_project(slug)
        except Exception as e:
            print(f"Error checking {slug}: {e}")
