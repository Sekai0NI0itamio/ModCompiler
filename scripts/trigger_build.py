#!/usr/bin/env python3
"""
Trigger GitHub Actions workflow for building mods.
Uses GitHub token from parent directory folder.
"""

import os
import sys
import json
import urllib.request
import urllib.error
from pathlib import Path

# Configuration
REPO_OWNER = "Sekai0NI0itamio"
REPO_NAME = "ModCompiler"
WORKFLOW_NAME = "build-mods.yml"

# Token folder path (parent of workspace, folder with spaces)
TOKEN_FOLDER_NAME = "the github token"
TOKEN_FILE_PATTERN = "github_pat"

def find_github_token():
    """Find GitHub token file in parent directory."""
    # Get parent of workspace
    workspace = Path(__file__).resolve().parents[1]
    parent_dir = workspace.parent

    token_folder = parent_dir / TOKEN_FOLDER_NAME

    if not token_folder.exists():
        print(f"ERROR: Token folder not found: {token_folder}")
        print(f"Looking for folder named: '{TOKEN_FOLDER_NAME}'")
        print(f"In parent directory: {parent_dir}")
        return None

    # Find file starting with github_pat
    for file in token_folder.iterdir():
        if file.is_file() and file.name.startswith(TOKEN_FILE_PATTERN):
            token = file.read_text().strip()
            print(f"Found token file: {file.name}")
            return token

    print(f"ERROR: No file starting with '{TOKEN_FILE_PATTERN}' found in {token_folder}")
    return None

def trigger_workflow(token, zip_path, modrinth_url, max_parallel="all"):
    """Trigger GitHub Actions workflow via API."""
    url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/actions/workflows/{WORKFLOW_NAME}/dispatches"

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json"
    }

    data = {
        "ref": "main",
        "inputs": {
            "zip_path": zip_path,
            "modrinth_project_url": modrinth_url,
            "max_parallel": max_parallel
        }
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers=headers,
        method='POST'
    )

    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 204:
                print("\n✓ Workflow triggered successfully!")
                print(f"  Zip: {zip_path}")
                print(f"  Modrinth: {modrinth_url}")
                print(f"  Max parallel: {max_parallel}")
                print(f"\nCheck progress at:")
                print(f"  https://github.com/{REPO_OWNER}/{REPO_NAME}/actions")
                return True
            else:
                print(f"Unexpected status: {response.status}")
                return False
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code}: {e.reason}")
        try:
            body = e.read().decode('utf-8')
            error_data = json.loads(body)
            print(f"Message: {error_data.get('message', 'Unknown error')}")
        except:
            pass
        return False
    except Exception as e:
        print(f"Error: {e}")
        return False

def main():
    print("=" * 60)
    print("GitHub Actions Workflow Trigger")
    print("=" * 60)

    # Find token
    print("\n[1] Looking for GitHub token...")
    token = find_github_token()
    if not token:
        sys.exit(1)
    print(f"  Token found (length: {len(token)} chars)")

    # Default values for heartsystem build
    zip_path = "incoming/heartsystem-all-versions.zip"
    modrinth_url = "https://modrinth.com/mod/lifesteal-parrot-mod"
    max_parallel = "all"

    print(f"\n[2] Configuration:")
    print(f"  Zip path: {zip_path}")
    print(f"  Modrinth URL: {modrinth_url}")
    print(f"  Max parallel: {max_parallel}")

    print(f"\n[3] Triggering workflow...")
    if trigger_workflow(token, zip_path, modrinth_url, max_parallel):
        print("\n" + "=" * 60)
        print("Done! The build will run on GitHub Actions.")
        print("=" * 60)
    else:
        print("\n" + "=" * 60)
        print("Failed to trigger workflow.")
        print("=" * 60)
        sys.exit(1)

if __name__ == "__main__":
    main()
