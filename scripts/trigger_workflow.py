#!/usr/bin/env python3
"""
Trigger a GitHub Actions workflow, wait for completion, and download artifacts.

Uses the `gh` CLI (must be authenticated) or GitHub API with a token.

Usage:
    python3 trigger_workflow.py <workflow_file> [--inputs KEY=VALUE ...] [--ref REF]
                                 [--poll-interval SECONDS] [--timeout MINUTES]
                                 [--download-artifacts] [--artifact-dir DIR]
                                 [--repo OWNER/REPO] [--token TOKEN]

Examples:
    # Trigger build.yml, wait for completion, download artifacts
    python3 trigger_workflow.py build.yml \
        --inputs zip_path=incoming/my-fix.zip max_parallel=all \
        --download-artifacts

    # Trigger diagnosis, just watch logs
    python3 trigger_workflow.py ModrinthProjectDiagnosis.yml \
        --inputs modrinth_project_url=https://modrinth.com/mod/pingfix

    # Trigger with GitHub token (fallback if gh CLI not available)
    python3 trigger_workflow.py build.yml \
        --inputs zip_path=incoming/my-fix.zip \
        --token ghp_xxxxxxxxxxxx
"""

import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional


def get_repo() -> str:
    """Get repo from git remote."""
    result = subprocess.run(
        ["git", "remote", "get-url", "origin"],
        capture_output=True, text=True, cwd=Path(__file__).resolve().parent.parent
    )
    url = result.stdout.strip()
    # Extract owner/repo from URL
    for prefix in ["https://github.com/", "git@github.com:"]:
        if prefix in url:
            path = url.split(prefix)[1]
            if path.endswith(".git"):
                path = path[:-4]
            return path
    raise RuntimeError(f"Cannot parse repo from URL: {url}")


def trigger_via_gh_cli(workflow_file: str, inputs: dict, ref: str, repo: str) -> str:
    """Trigger workflow via gh CLI. Returns the run URL."""
    cmd = [
        "gh", "workflow", "run", workflow_file,
        "--repo", repo,
        "--ref", ref or "main",
    ]
    for key, value in inputs.items():
        cmd.extend(["-f", f"{key}={value}"])

    print(f"[trigger] Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        print(f"[trigger] stderr: {result.stderr}")
        raise RuntimeError(f"Failed to trigger workflow: {result.stderr.strip()}")

    print(f"[trigger] {result.stdout.strip()}")
    return result.stdout.strip()


def get_latest_run_id(repo: str, workflow_file: str, branch: str) -> str:
    """Get the latest run ID for a workflow on a branch."""
    result = subprocess.run(
        ["gh", "run", "list", "--repo", repo, "--workflow", workflow_file,
         "--branch", branch, "--limit", "1", "--json", "databaseId,status",
         "--jq", ".[0].databaseId"],
        capture_output=True, text=True, timeout=15
    )
    run_id = result.stdout.strip()
    if not run_id or not run_id.isdigit():
        raise RuntimeError(f"Could not find run for {workflow_file} on {branch}")
    return run_id


def watch_run(repo: str, run_id: str, poll_interval: int, timeout_minutes: int):
    """Watch a run and stream logs until completion."""
    print(f"\n[watch] Watching run {run_id} (poll every {poll_interval}s, timeout {timeout_minutes}m)")
    print(f"[watch] Run URL: https://github.com/{repo}/actions/runs/{run_id}")
    print()

    start = time.time()
    timeout_seconds = timeout_minutes * 60
    last_log_position = 0
    last_job_count = 0

    while True:
        elapsed = time.time() - start
        if elapsed > timeout_seconds:
            print(f"\n[watch] TIMEOUT after {timeout_minutes} minutes")
            sys.exit(1)

        # Get run status
        result = subprocess.run(
            ["gh", "run", "view", run_id, "--repo", repo,
             "--json", "status,conclusion,jobs",
             "--jq", '{status: .status, conclusion: .conclusion, jobs: [.jobs[] | {name: .name, status: .status, conclusion: .conclusion}]}'],
            capture_output=True, text=True, timeout=15
        )

        try:
            data = json.loads(result.stdout)
        except json.JSONDecodeError:
            time.sleep(poll_interval)
            continue

        status = data.get("status", "unknown")
        conclusion = data.get("conclusion", "")
        jobs = data.get("jobs", [])

        # Print new jobs or job status changes
        if len(jobs) != last_job_count:
            for job in jobs:
                name = job.get("name", "?")
                js = job.get("status", "?")
                jc = job.get("conclusion", "")
                icon = ""
                if jc == "success":
                    icon = " [OK]"
                elif jc == "failure":
                    icon = " [FAIL]"
                elif jc == "cancelled":
                    icon = " [CANCEL]"
                elif jc == "skipped":
                    icon = " [SKIP]"
                status_text = f"{js}"
                if jc:
                    status_text += f" -> {jc}"
                if jc != "success" or last_job_count == 0:
                    print(f"  [{status_text}] {name}{icon}")
            last_job_count = len(jobs)

        # Check if done
        if status == "completed":
            print(f"\n[watch] Run completed with conclusion: {conclusion}")
            print(f"[watch] Elapsed: {elapsed:.0f}s")
            return conclusion

        time.sleep(poll_interval)


def get_run_logs(repo: str, run_id: str):
    """Get logs for failed jobs."""
    result = subprocess.run(
        ["gh", "run", "view", run_id, "--repo", repo, "--log-failed"],
        capture_output=True, text=True, timeout=30
    )
    if result.stdout.strip():
        print("\n" + "=" * 60)
        print("FAILED JOB LOGS")
        print("=" * 60)
        # Limit to last 200 lines
        lines = result.stdout.strip().split("\n")
        for line in lines[-200:]:
            print(line)


def list_artifacts(repo: str, run_id: str) -> list[dict]:
    """List all artifacts for a run via GitHub API."""
    result = subprocess.run(
        ["gh", "api", f"/repos/{repo}/actions/runs/{run_id}/artifacts",
         "--jq", ".artifacts"],
        capture_output=True, text=True, timeout=30
    )
    if result.returncode != 0:
        print(f"[artifacts] WARNING: Failed to list artifacts: {result.stderr.strip()}")
        return []
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        return []


def _download_single_artifact(repo: str, run_id: str, name: str, dest_dir: str):
    """Download a single artifact by name."""
    dest = Path(dest_dir) / name
    dest.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        ["gh", "run", "download", run_id, "--repo", repo,
         "-n", name, "--dir", str(dest)],
        capture_output=True, text=True, timeout=120
    )
    if result.returncode != 0:
        return (name, False, result.stderr.strip())
    file_count = len(list(dest.rglob("*")))
    return (name, True, f"{file_count} files")


def download_artifacts(repo: str, run_id: str, artifact_dir: str, max_workers: int = 5):
    """Download all artifacts from a run concurrently."""
    dest = Path(artifact_dir)
    dest.mkdir(parents=True, exist_ok=True)

    # List artifacts first
    print(f"\n[artifacts] Listing artifacts for run {run_id}...")
    artifacts = list_artifacts(repo, run_id)

    if not artifacts:
        print("[artifacts] No artifacts found or failed to list. Falling back to sequential download.")
        result = subprocess.run(
            ["gh", "run", "download", run_id, "--repo", repo, "--dir", str(dest)],
            capture_output=True, text=True, timeout=120
        )
        if result.returncode != 0:
            print(f"[artifacts] WARNING: {result.stderr.strip()}")
        else:
            artifact_count = len([p for p in dest.iterdir() if p.is_dir()])
            print(f"[artifacts] Downloaded {artifact_count} artifact(s) to {dest}")
        return

    # Filter out expired artifacts
    downloadable = [a for a in artifacts if not a.get("expired", False)]
    expired = len(artifacts) - len(downloadable)
    if expired:
        print(f"[artifacts] Skipping {expired} expired artifact(s)")

    if not downloadable:
        print("[artifacts] No downloadable artifacts found.")
        return

    print(f"[artifacts] Downloading {len(downloadable)} artifact(s) concurrently (max_workers={max_workers})...")

    # Download concurrently
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = {
            executor.submit(
                _download_single_artifact, repo, run_id, a["name"], str(dest)
            ): a["name"]
            for a in downloadable
        }
        for future in concurrent.futures.as_completed(futures):
            name, ok, info = future.result()
            if ok:
                print(f"  [OK] {name} ({info})")
            else:
                print(f"  [FAIL] {name}: {info[:100]}")
            results.append((name, ok, info))

    success_count = sum(1 for _, ok, _ in results if ok)
    print(f"[artifacts] Downloaded {success_count}/{len(downloadable)} artifact(s) to {dest}")


def trigger_via_api(workflow_file: str, inputs: dict, ref: str, repo: str, token: str):
    """Trigger workflow via GitHub REST API (fallback)."""
    import urllib.request
    import urllib.error

    url = f"https://api.github.com/repos/{repo}/actions/workflows/{workflow_file}/dispatches"
    payload = json.dumps({"ref": ref or "main", "inputs": inputs}).encode()
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
    }

    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            print(f"[trigger] API response: {resp.status}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        raise RuntimeError(f"API trigger failed: {e.code} {body}")

    # Wait for the run to appear
    time.sleep(3)
    return get_latest_run_id(repo, workflow_file, ref or "main")


def main():
    parser = argparse.ArgumentParser(description="Trigger a GitHub Actions workflow and wait for completion")
    parser.add_argument("workflow_file", help="Workflow file name (e.g., build.yml)")
    parser.add_argument("--inputs", nargs="*", default=[],
                        help="Workflow inputs as KEY=VALUE pairs")
    parser.add_argument("--ref", default=None,
                        help="Git ref to run on (default: current branch)")
    parser.add_argument("--repo", default=None,
                        help="GitHub repo as OWNER/REPO (default: from git remote)")
    parser.add_argument("--token", default=None,
                        help="GitHub token (falls back to gh CLI if not provided)")
    parser.add_argument("--poll-interval", type=int, default=15,
                        help="Seconds between status polls (default: 15)")
    parser.add_argument("--timeout", type=int, default=60,
                        help="Timeout in minutes (default: 60)")
    parser.add_argument("--download-artifacts", action="store_true",
                        help="Download artifacts after completion")
    parser.add_argument("--artifact-dir", default=".workflow_downloads",
                        help="Directory for downloaded artifacts")
    parser.add_argument("--max-workers", type=int, default=5,
                        help="Max concurrent artifact downloads (default: 5)")
    parser.add_argument("--show-logs", action="store_true", default=True,
                        help="Show failed job logs after completion (default: true)")

    args = parser.parse_args()

    # Parse inputs
    inputs = {}
    for item in args.inputs:
        if "=" in item:
            key, value = item.split("=", 1)
            inputs[key] = value
        else:
            print(f"WARNING: Ignoring malformed input: {item}")

    # Determine repo
    repo = args.repo or get_repo()
    ref = args.ref or subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        capture_output=True, text=True, cwd=Path(__file__).resolve().parent.parent
    ).stdout.strip()

    print(f"[trigger_workflow] Repo: {repo}")
    print(f"[trigger_workflow] Ref: {ref}")
    print(f"[trigger_workflow] Workflow: {args.workflow_file}")
    print(f"[trigger_workflow] Inputs: {inputs}")

    # Trigger the workflow
    token = args.token or os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    use_gh = not token  # Prefer gh CLI if no explicit token

    if use_gh:
        trigger_via_gh_cli(args.workflow_file, inputs, ref, repo)
        # Wait a moment for the run to appear
        time.sleep(3)
        run_id = get_latest_run_id(repo, args.workflow_file, ref)
    else:
        run_id = trigger_via_api(args.workflow_file, inputs, ref, repo, token)

    print(f"[trigger_workflow] Run ID: {run_id}")

    # Watch and wait
    conclusion = watch_run(repo, run_id, args.poll_interval, args.timeout)

    # Show logs if failed
    if args.show_logs and conclusion != "success":
        get_run_logs(repo, run_id)

    # Download artifacts
    if args.download_artifacts:
        download_artifacts(repo, run_id, args.artifact_dir, args.max_workers)

    # Exit with appropriate code
    if conclusion == "success":
        print("\n[trigger_workflow] SUCCESS")
        sys.exit(0)
    elif conclusion == "failure":
        print("\n[trigger_workflow] FAILED")
        sys.exit(1)
    else:
        print(f"\n[trigger_workflow] Completed with conclusion: {conclusion}")
        sys.exit(1 if conclusion != "success" else 0)


if __name__ == "__main__":
    main()