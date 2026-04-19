#!/usr/bin/env python3
"""
Delete the 1.0.0 shell versions from Set Home Anywhere on Modrinth.
The real 1.0.1 versions already exist — we just need to clean up the orphaned shells.

Usage:
  export MODRINTH_TOKEN=<your-token>
  python3 scripts/_delete_sethome_shells.py [--dry-run]
"""
import json
import os
import sys
import time
import urllib.request
import urllib.parse
import argparse

def modrinth_request(method, path, token, body=None):
    url = "https://api.modrinth.com/v2" + path
    data = json.dumps(body).encode() if body else None
    headers = {
        "Authorization": token,
        "User-Agent": "ModCompiler/1.0",
    }
    if data:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            if r.status == 204:
                return {}
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} {method} {path}: {e.read().decode()[:200]}", file=sys.stderr)
        raise

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--project", default="set-home-anywhere")
    ap.add_argument("--threshold", type=int, default=5000)
    args = ap.parse_args()

    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token:
        print("Set MODRINTH_TOKEN env var", file=sys.stderr)
        sys.exit(1)

    # Get all versions
    versions = modrinth_request("GET", f"/project/{args.project}/version", token)
    print(f"Total versions: {len(versions)}")

    shells = []
    for v in versions:
        files = v.get("files", [])
        size = max((f.get("size", 0) for f in files), default=0)
        if size < args.threshold:
            mc = v.get("game_versions", [])
            loaders = v.get("loaders", [])
            vnum = v.get("version_number", "")
            shells.append((v["id"], vnum, mc, loaders, size))

    print(f"Shells (< {args.threshold}B): {len(shells)}")
    for vid, vnum, mc, loaders, size in shells:
        print(f"  [{vid}] {vnum} mc={mc} loader={loaders} size={size}B")

    if not shells:
        print("No shells to delete.")
        return

    if args.dry_run:
        print(f"\n[DRY RUN] Would delete {len(shells)} shell versions")
        return

    print(f"\nDeleting {len(shells)} shells...")
    deleted = 0
    failed = 0
    for vid, vnum, mc, loaders, size in shells:
        try:
            modrinth_request("DELETE", f"/version/{vid}", token)
            print(f"  Deleted [{vid}] {vnum} {loaders} {mc}")
            deleted += 1
            time.sleep(0.5)
        except Exception as e:
            print(f"  FAILED [{vid}]: {e}", file=sys.stderr)
            failed += 1

    print(f"\nDone: {deleted} deleted, {failed} failed")

if __name__ == "__main__":
    main()
