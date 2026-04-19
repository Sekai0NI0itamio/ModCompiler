#!/usr/bin/env python3
"""
Delete shell versions (< threshold bytes) from a Modrinth project.
Used to clean up orphaned empty/broken jars after real versions have been uploaded.

Usage:
  export MODRINTH_TOKEN=<your-token>
  python3 scripts/_delete_modrinth_shells.py --project set-home-anywhere [--dry-run]
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
        body_text = e.read().decode()
        print(f"HTTP {e.code} {method} {path}: {body_text[:200]}", file=sys.stderr)
        raise

def main():
    ap = argparse.ArgumentParser(description="Delete shell versions from a Modrinth project")
    ap.add_argument("--project", required=True, help="Modrinth project slug or ID")
    ap.add_argument("--threshold", type=int, default=5000,
                    help="Max file size in bytes to consider a shell (default: 5000)")
    ap.add_argument("--dry-run", action="store_true",
                    help="Show what would be deleted without actually deleting")
    args = ap.parse_args()

    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token:
        print("ERROR: Set MODRINTH_TOKEN environment variable", file=sys.stderr)
        sys.exit(1)

    # Get all versions
    print(f"Fetching versions for project: {args.project}")
    versions = modrinth_request("GET", f"/project/{args.project}/version", token)
    print(f"Total versions on Modrinth: {len(versions)}")

    # Identify shells
    shells = []
    real = []
    for v in versions:
        files = v.get("files", [])
        size = max((f.get("size", 0) for f in files), default=0)
        mc = v.get("game_versions", [])
        loaders = v.get("loaders", [])
        vnum = v.get("version_number", "")
        if size < args.threshold:
            shells.append((v["id"], vnum, mc, loaders, size))
        else:
            real.append((v["id"], vnum, mc, loaders, size))

    print(f"\nReal versions (>= {args.threshold}B): {len(real)}")
    print(f"Shell versions (< {args.threshold}B): {len(shells)}")

    if not shells:
        print("\nNo shells found. Nothing to delete.")
        return

    print("\nShells to delete:")
    for vid, vnum, mc, loaders, size in shells:
        print(f"  [{vid}] v{vnum} {loaders} {mc} ({size}B)")

    if args.dry_run:
        print(f"\n[DRY RUN] Would delete {len(shells)} shell versions. Run without --dry-run to proceed.")
        return

    print(f"\nDeleting {len(shells)} shell versions...")
    deleted = 0
    failed = 0

    for vid, vnum, mc, loaders, size in shells:
        try:
            modrinth_request("DELETE", f"/version/{vid}", token)
            print(f"  ✓ Deleted [{vid}] v{vnum} {loaders} {mc} ({size}B)")
            deleted += 1
            time.sleep(0.5)  # Rate limit
        except Exception as e:
            print(f"  ✗ Failed [{vid}]: {e}", file=sys.stderr)
            failed += 1

    print(f"\nResult: {deleted} deleted, {failed} failed")
    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
