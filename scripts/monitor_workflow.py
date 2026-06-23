#!/usr/bin/env python3
"""Minimal GitHub Actions workflow monitor.

Polls a workflow run until it finishes, then downloads all logs.

Usage:
    python3 scripts/monitor_workflow.py <run-id> [--owner OWNER] [--repo REPO] [--out DIR]

Environment:
    GITHUB_TOKEN  GitHub personal access token (required for private repos).
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path


def get_repo():
    """Derive owner/repo from the local git remote, if any."""
    try:
        result = subprocess.run(
            ["git", "remote", "get-url", "origin"],
            capture_output=True, text=True, timeout=10, check=True,
        )
        url = result.stdout.strip()
        if url.startswith("https://github.com/"):
            parts = url.replace("https://github.com/", "").replace(".git", "").rstrip("/").split("/")
            return "/".join(parts[:2])
        if url.startswith("git@github.com:"):
            parts = url.replace("git@github.com:", "").replace(".git", "").rstrip("/").split("/")
            return "/".join(parts[:2])
    except Exception:
        pass
    return None


def api_request(url, token):
    headers = {"User-Agent": "ModCompiler-Monitor/1.0", "Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def download_file(url, dest, token):
    headers = {"User-Agent": "ModCompiler-Monitor/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as r:
        dest.write_bytes(r.read())


def main():
    parser = argparse.ArgumentParser(description="Monitor a GitHub Actions run and download logs.")
    parser.add_argument("run_id", help="GitHub Actions run ID (numeric)")
    parser.add_argument("--owner", help="Repository owner")
    parser.add_argument("--repo", help="Repository name")
    parser.add_argument("--out", default="workflow_logs", help="Directory to save logs")
    parser.add_argument("--poll", type=int, default=30, help="Polling interval in seconds")
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        try:
            result = subprocess.run(
                ["gh", "auth", "token"],
                capture_output=True, text=True, timeout=10, check=True,
            )
            token = result.stdout.strip()
        except Exception:
            token = ""

    repo = None
    if args.owner and args.repo:
        repo = f"{args.owner}/{args.repo}"
    else:
        repo = get_repo()

    if not repo:
        print("ERROR: Could not determine owner/repo. Use --owner and --repo.", file=sys.stderr)
        sys.exit(1)

    run_url = f"https://api.github.com/repos/{repo}/actions/runs/{args.run_id}"
    jobs_url = f"https://api.github.com/repos/{repo}/actions/runs/{args.run_id}/jobs"
    logs_url = f"https://api.github.com/repos/{repo}/actions/runs/{args.run_id}/logs"

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Monitoring run {args.run_id} in {repo}")
    print(f"Logs will be saved to: {out_dir.resolve()}")

    while True:
        try:
            run_data = api_request(run_url, token)
            status = run_data.get("status", "unknown")
            conclusion = run_data.get("conclusion") or "in_progress"
            jobs_data = api_request(jobs_url, token)
            jobs = jobs_data.get("jobs", [])
            total = len(jobs)
            completed = sum(1 for j in jobs if j.get("status") == "completed")
            print(f"[{time.strftime('%H:%M:%S')}] status={status} conclusion={conclusion} jobs={completed}/{total}", flush=True)
            if status == "completed":
                break
        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] ERROR polling: {e}", flush=True)
        time.sleep(args.poll)

    print("Run completed. Downloading logs...")
    zip_path = out_dir / f"run-{args.run_id}-logs.zip"
    try:
        download_file(logs_url, zip_path, token)
        print(f"Downloaded logs: {zip_path} ({zip_path.stat().st_size / 1024:.0f} KB)")
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(out_dir)
        print(f"Extracted logs to: {out_dir}")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print("Logs not available yet (404). They may appear shortly.")
        else:
            print(f"ERROR downloading logs: {e}")
    except Exception as e:
        print(f"ERROR downloading logs: {e}")


if __name__ == "__main__":
    main()
