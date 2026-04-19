#!/usr/bin/env python3
"""
Replace shell versions on Modrinth with the fixed jars from the latest build run.

A "shell" is a version where the primary jar is < 5000 bytes (no real classes).
This script:
1. Fetches all versions of the project from Modrinth
2. For each version that is a shell (< 5000 bytes), deletes it
3. Uploads the fixed jar from the latest build run in its place

Usage:
  python3 scripts/replace_modrinth_shells.py \
      --project https://modrinth.com/mod/common-server-core \
      [--run-dir ModCompileRuns/run-XXXX]  # defaults to latest
"""
import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def get_token() -> str:
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        # Try gh secret
        import subprocess
        r = subprocess.run(
            ["gh", "secret", "list", "--json", "name"],
            capture_output=True, text=True, cwd=ROOT
        )
        print("No MODRINTH_TOKEN env var. Set it with:")
        print("  export MODRINTH_TOKEN=<your-token>")
        sys.exit(1)
    return token

def modrinth_request(method: str, path: str, token: str, body=None, params=None) -> dict | list:
    base = "https://api.modrinth.com/v2"
    url = base + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    
    data = json.dumps(body).encode() if body else None
    headers = {
        "Authorization": token,
        "User-Agent": "ModCompiler/1.0",
    }
    if data:
        headers["Content-Type"] = "application/json"
    
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            if resp.status == 204:
                return {}
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode()
        print(f"HTTP {e.code} {method} {path}: {body_text[:200]}", file=sys.stderr)
        raise

def get_project_id(project_ref: str, token: str) -> str:
    slug = project_ref.rstrip("/").split("/")[-1]
    proj = modrinth_request("GET", f"/project/{slug}", token)
    return proj["id"]

def get_all_versions(project_id: str, token: str) -> list:
    return modrinth_request("GET", f"/project/{project_id}/version", token)

def get_version_file_size(version: dict) -> int:
    """Get the size of the primary jar file."""
    for f in version.get("files", []):
        if f.get("primary", False):
            return f.get("size", 0)
    # If no primary flag, take the first file
    files = version.get("files", [])
    if files:
        return files[0].get("size", 0)
    return 0

def delete_version(version_id: str, token: str):
    modrinth_request("DELETE", f"/version/{version_id}", token)
    print(f"  Deleted version {version_id}")

def upload_version(project_id: str, jar_path: Path, mc_version: str, loader: str,
                   version_number: str, token: str) -> dict:
    """Upload a new version to Modrinth."""
    import mimetypes
    
    name = f"ServerCore {version_number} ({loader} {mc_version})"
    payload = {
        "name": name,
        "version_number": version_number,
        "changelog": f"Fixed version replacing shell jar for {loader} {mc_version}.",
        "dependencies": [],
        "game_versions": [mc_version],
        "version_type": "release",
        "loaders": [loader],
        "featured": False,
        "project_id": project_id,
        "file_parts": ["file"],
        "primary_file": "file",
    }
    
    # Multipart upload
    boundary = "----ModCompilerBoundary"
    body_parts = []
    
    # data field
    data_json = json.dumps(payload).encode()
    body_parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + data_json + b'\r\n'
    )
    
    # file field
    jar_bytes = jar_path.read_bytes()
    body_parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar_path.name}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode()
        + jar_bytes + b'\r\n'
    )
    body_parts.append(f'--{boundary}--\r\n'.encode())
    
    body = b''.join(body_parts)
    
    url = "https://api.modrinth.com/v2/version"
    headers = {
        "Authorization": token,
        "User-Agent": "ModCompiler/1.0",
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    }
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read())
            print(f"  Uploaded → version ID {result.get('id')}")
            return result
    except urllib.error.HTTPError as e:
        body_text = e.read().decode()
        print(f"  Upload failed HTTP {e.code}: {body_text[:300]}", file=sys.stderr)
        raise

def find_built_jar(run_dir: Path, mc_version: str, loader: str) -> Path | None:
    """Find the built jar for a given MC version and loader in the run artifacts."""
    mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
    if not mods_dir.exists():
        return None
    
    # Slug format: servercore-{loader}-{mc_version_dashes}
    mc_dashes = mc_version.replace(".", "-")
    slug = f"servercore-{loader}-{mc_dashes}"
    
    mod_dir = mods_dir / slug
    if not mod_dir.exists():
        return None
    
    # Check it succeeded
    result_file = mod_dir / "result.json"
    if result_file.exists():
        result = json.loads(result_file.read_text())
        if result.get("status") != "success":
            return None
    
    # Find the jar
    jars_dir = mod_dir / "jars"
    if jars_dir.exists():
        jars = list(jars_dir.glob("*.jar"))
        if jars:
            return jars[0]
    
    return None

def main():
    ap = argparse.ArgumentParser(description="Replace Modrinth shell versions with fixed jars")
    ap.add_argument("--project", required=True, help="Modrinth project URL or slug")
    ap.add_argument("--run-dir", default=None, help="Build run directory (default: latest)")
    ap.add_argument("--dry-run", action="store_true", help="Show what would be done without doing it")
    ap.add_argument("--version-number", default="1.0.1", help="Version number to use for uploads (default: 1.0.1)")
    ap.add_argument("--shell-threshold", type=int, default=5000, help="Max bytes to consider a shell (default: 5000)")
    args = ap.parse_args()
    
    token = get_token()
    
    # Find run dir
    if args.run_dir:
        run_dir = Path(args.run_dir)
    else:
        runs_root = ROOT / "ModCompileRuns"
        run_dirs = sorted(runs_root.iterdir())
        if not run_dirs:
            print("No run directories found", file=sys.stderr)
            sys.exit(1)
        run_dir = run_dirs[-1]
    
    print(f"Using run dir: {run_dir}")
    
    # Get project
    project_id = get_project_id(args.project, token)
    print(f"Project ID: {project_id}")
    
    # Get all versions
    versions = get_all_versions(project_id, token)
    print(f"Total versions on Modrinth: {len(versions)}")
    
    shells = []
    for v in versions:
        size = get_version_file_size(v)
        if size < args.shell_threshold:
            mc_versions = v.get("game_versions", [])
            loaders = v.get("loaders", [])
            shells.append({
                "id": v["id"],
                "version_number": v.get("version_number", ""),
                "mc_versions": mc_versions,
                "loaders": loaders,
                "size": size,
            })
    
    print(f"Shell versions (< {args.shell_threshold} bytes): {len(shells)}")
    
    replaced = 0
    skipped = 0
    failed = 0
    
    for shell in shells:
        vid = shell["id"]
        mc_versions = shell["mc_versions"]
        loaders = shell["loaders"]
        
        if not mc_versions or not loaders:
            print(f"  [{vid}] Skipping — no MC version or loader info")
            skipped += 1
            continue
        
        mc_version = mc_versions[0]
        loader = loaders[0]
        
        # Find the built jar
        jar_path = find_built_jar(run_dir, mc_version, loader)
        if jar_path is None:
            print(f"  [{vid}] {loader} {mc_version} — no built jar found, skipping")
            skipped += 1
            continue
        
        print(f"  [{vid}] {loader} {mc_version} (size={shell['size']}B) → replacing with {jar_path.name} ({jar_path.stat().st_size:,}B)")
        
        if args.dry_run:
            print(f"    [DRY RUN] Would delete {vid} and upload {jar_path}")
            replaced += 1
            continue
        
        try:
            # Delete the shell
            delete_version(vid, token)
            time.sleep(0.5)  # Rate limit
            
            # Upload the fixed jar
            upload_version(
                project_id=project_id,
                jar_path=jar_path,
                mc_version=mc_version,
                loader=loader,
                version_number=args.version_number,
                token=token,
            )
            replaced += 1
            time.sleep(1.0)  # Rate limit
        except Exception as e:
            print(f"    FAILED: {e}", file=sys.stderr)
            failed += 1
    
    print(f"\nDone: {replaced} replaced, {skipped} skipped, {failed} failed")
    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
