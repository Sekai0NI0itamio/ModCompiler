#!/usr/bin/env python3
"""Auto-commit and push changes cleanly.

Two modes of operation:

1. Interactive (no arguments):
   - Shows git status
   - Pulls latest from remote to stay up to date
   - Displays all pending changes
   - Lets the user exclude files/folders by adding them to .gitignore
   - Asks for a commit message
   - Commits and pushes

2. AI agent mode (with -m flag):
   - Stages specified files (or all changes)
   - Commits with the given message
   - Pushes to remote

Usage:
    python3 commit_and_push.py                     # Interactive mode
    python3 commit_and_push.py -m "message"        # AI agent mode
    python3 commit_and_push.py --files a.py b.py -m "message"
    python3 commit_and_push.py --dry-run -m "message"
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


def run(cmd: list[str], *, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, capture_output=capture, text=True)
    if check and result.returncode != 0:
        stderr = result.stderr.strip() if result.stderr else ""
        raise SystemExit(f"Command failed: {' '.join(cmd)}\n{stderr}")
    return result


def get_current_branch() -> str:
    result = run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    return result.stdout.strip()


def get_status() -> str:
    result = run(["git", "status", "--porcelain"])
    return result.stdout.strip()


def get_diff_stat() -> str:
    result = run(["git", "diff", "--cached", "--stat"])
    return result.stdout.strip()


def find_gitignore() -> Path:
    """Find or create .gitignore at the repo root."""
    root = run(["git", "rev-parse", "--show-toplevel"]).stdout.strip()
    return Path(root) / ".gitignore"


def read_gitignore(path: Path) -> list[str]:
    if path.exists():
        return path.read_text(encoding="utf-8").splitlines()
    return []


def write_gitignore(path: Path, lines: list[str]) -> None:
    content = "\n".join(lines)
    if not content.endswith("\n"):
        content += "\n"
    path.write_text(content, encoding="utf-8")


def add_to_gitignore(gitignore_path: Path, entry: str) -> None:
    """Add an entry to .gitignore if not already present."""
    lines = read_gitignore(gitignore_path)
    entry = entry.strip()
    if entry and entry not in lines:
        lines.append(entry)
        write_gitignore(gitignore_path, lines)
        print(f"  Added '{entry}' to .gitignore")
    elif entry in lines:
        print(f"  '{entry}' is already in .gitignore")


def get_working_diff() -> str:
    """Get the full diff of unstaged changes in tracked files."""
    result = run(["git", "diff"], check=False)
    return result.stdout.strip()


def get_status_text() -> str:
    """Get a human-readable summary of pending changes."""
    result = run(["git", "status", "--short"], check=False)
    return result.stdout.strip()


def call_c05localai(system_prompt: str, user_prompt: str, timeout: int = 30) -> str:
    """Call the local c05localai server to generate a commit message."""
    url = "http://localhost:8129/chat"
    payload = {
        "model": "nvidia/nemotron-3-nano-30b-a3b",
        "hoster": "nvidia",
        "response_mode": "direct",
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            response_text = response.read().decode("utf-8")
    except urllib.error.URLError as e:
        raise SystemExit(
            f"  Error: Could not reach c05localai server at {url}.\n"
            f"  Make sure it's running (see C05LocalAi/ for setup).\n"
            f"  Details: {e.reason}"
        )
    except TimeoutError:
        raise SystemExit(
            f"  Error: c05localai server timed out after {timeout}s.\n"
            f"  Try again or enter a commit message manually."
        )

    # Parse the SSE-like line-delimited JSON response from the /chat endpoint.
    # The response consists of lines like:
    #   {"event": "start", ...}
    #   {"event": "content", "content": "..."}
    #   {"status": "end", "content": "...", ...}
    full_content = ""
    for line in response_text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        # Capture content from content events
        if obj.get("event") == "content":
            chunk = obj.get("content", "")
            if chunk:
                full_content += chunk
        # Also capture from the final end event
        elif obj.get("status") == "end":
            final_content = obj.get("content", "")
            if final_content:
                # If we didn't get it via content events, use this
                if not full_content:
                    full_content = final_content
    return full_content.strip()


def generate_ai_commit_message() -> str:
    """Generate a commit message from the AI based on the working diff and status."""
    print("  Generating commit message with AI (c05localai)...")

    # Gather context about changes
    status_summary = get_status_text()
    diff_context = get_working_diff()

    if not diff_context and not status_summary:
        raise SystemExit("  Error: No changes detected to generate a commit message.")

    branch = get_current_branch()

    system_prompt = (
        "You are a helpful assistant that writes concise Git commit messages. "
        "Given the git diff, status summary, and branch name, write a clear commit message. "
        "Follow conventional commit format: type(scope): description. "
        "Use types like feat, fix, refactor, chore, docs, test, style, perf, ci. "
        "Keep the description under 72 characters. Do not add any extra commentary or explanation."
    )

    user_prompt = (
        f"Branch: {branch}\n\n"
        f"Changes summary:\n{status_summary}\n\n"
        f"Diff:\n{diff_context}\n"
    )

    print("  Contacting c05localai (NVIDIA Nemotron)...")
    message = call_c05localai(system_prompt, user_prompt)

    if not message:
        raise SystemExit("  AI returned an empty commit message. Try again or enter one manually.")

    return message


def parse_status_files(status_output: str) -> list[dict]:
    """Parse git status --porcelain output into structured entries."""
    entries = []
    for line in status_output.splitlines():
        if not line:
            continue
        status_code = line[:2]
        filepath = line[3:]
        # Handle renamed files (has -> in path)
        if " -> " in filepath:
            filepath = filepath.split(" -> ", 1)[1]
        entries.append({"status": status_code.strip(), "path": filepath})
    return entries


def display_changes(entries: list[dict]) -> None:
    """Display pending changes in a readable format."""
    if not entries:
        print("  No changes detected.")
        return

    status_map = {
        "M": "modified",
        "A": "added",
        "D": "deleted",
        "R": "renamed",
        "??": "untracked",
        "MM": "modified",
        "AM": "added+modified",
    }

    # Group by directory for readability
    print(f"\n  {'Status':<12} {'Path'}")
    print(f"  {'-' * 10}   {'-' * 50}")
    for entry in entries:
        label = status_map.get(entry["status"], entry["status"])
        print(f"  {label:<12} {entry['path']}")
    print()


def interactive_mode() -> None:
    """Run in interactive mode: show status, allow gitignore, commit, push."""
    print("=" * 60)
    print("  commit_and_push.py — Interactive Mode")
    print("=" * 60)

    # Check we're in a git repo
    run(["git", "rev-parse", "--is-inside-work-tree"])

    branch = get_current_branch()
    print(f"\n  Branch: {branch}")

    # Pull latest to stay up to date
    print("\n  Syncing with remote...")
    pull_result = run(["git", "pull", "--rebase", "origin", branch], check=False)
    if pull_result.returncode != 0:
        # May fail if there are unstaged changes; that's fine, we'll push after commit
        if "unstaged changes" in (pull_result.stderr or ""):
            print("  Skipping pull (unstaged changes present, will rebase after commit).")
        elif "Could not read from remote" in (pull_result.stderr or ""):
            print("  Warning: could not reach remote. Continuing offline.")
        else:
            print(f"  Pull note: {(pull_result.stderr or '').strip()[:200]}")
    else:
        print("  Up to date with remote.")

    # Show current status
    status = get_status()
    if not status:
        print("\n  Working tree is clean. Nothing to commit.")
        sys.exit(0)

    entries = parse_status_files(status)
    print(f"\n  Pending changes ({len(entries)} files):")
    display_changes(entries)

    # Gitignore loop
    gitignore_path = find_gitignore()
    while True:
        answer = input("  Add any files/folders to .gitignore? (enter path, or 'n' to skip): ").strip()
        if answer.lower() in ("n", "no", ""):
            break

        add_to_gitignore(gitignore_path, answer)

        # Remove from git tracking if already tracked
        run(["git", "rm", "-r", "--cached", answer], check=False, capture=True)

        # Refresh status
        status = get_status()
        entries = parse_status_files(status)
        print(f"\n  Updated pending changes ({len(entries)} files):")
        display_changes(entries)

    # Refresh final status
    status = get_status()
    if not status:
        print("\n  All changes were gitignored. Nothing to commit.")
        sys.exit(0)

    entries = parse_status_files(status)
    print(f"  Final changes to commit ({len(entries)} files):")
    display_changes(entries)

    # Confirm
    confirm = input("  Proceed with commit? (y/n): ").strip().lower()
    if confirm not in ("y", "yes"):
        print("  Aborted.")
        sys.exit(0)

    # Get commit message
    message = input("  Commit message: ").strip()
    if not message:
        print("  Error: commit message cannot be empty.")
        sys.exit(1)

    # Stage all remaining changes
    print("\n  Staging all changes...")
    run(["git", "add", "-A"])

    staged = get_diff_stat()
    if not staged:
        print("  Nothing staged to commit.")
        sys.exit(0)

    print(f"\n{staged}\n")

    # Commit
    print(f"  Committing: {message}")
    run(["git", "commit", "-m", message])

    # Push (with rebase if needed)
    print(f"  Pushing to origin/{branch}...")
    push_result = run(["git", "push", "origin", branch], check=False)
    if push_result.returncode != 0:
        # Remote may have advanced; try pull --rebase then push again
        print("  Remote has new commits, rebasing...")
        rebase_result = run(["git", "pull", "--rebase", "origin", branch], check=False)
        if rebase_result.returncode != 0:
            stderr = rebase_result.stderr.strip() if rebase_result.stderr else ""
            raise SystemExit(f"  Rebase failed:\n{stderr}")
        push_result = run(["git", "push", "origin", branch], check=False)
        if push_result.returncode != 0:
            # Try setting upstream
            push_result = run(["git", "push", "--set-upstream", "origin", branch], check=False)
            if push_result.returncode != 0:
                stderr = push_result.stderr.strip() if push_result.stderr else ""
                raise SystemExit(f"  Push failed:\n{stderr}")

    print(f"\n  Done. Committed and pushed to origin/{branch}.")


def agent_mode(args: argparse.Namespace) -> None:
    """Run in AI agent mode: non-interactive commit and push."""
    message = args.message
    if not message:
        raise SystemExit("Error: commit message is required.")

    # Check we're in a git repo
    run(["git", "rev-parse", "--is-inside-work-tree"])

    # Check there are changes to commit
    status = get_status()
    if not status:
        print("Nothing to commit - working tree is clean.")
        sys.exit(0)

    # Stage files
    if args.files:
        print(f"Staging specified files: {args.files}")
        run(["git", "add", "--"] + args.files)
    else:
        print("Staging all changes...")
        run(["git", "add", "-A"])

    # Check if anything was staged
    staged = get_diff_stat()
    if not staged:
        print("Nothing staged to commit after add.")
        sys.exit(0)

    print(f"\nStaged changes:\n{staged}\n")

    if args.dry_run:
        print("[DRY RUN] Would commit with message:")
        print(f"  {message}")
        print("\n[DRY RUN] Would push to remote.")
        run(["git", "reset", "HEAD"], check=False)
        sys.exit(0)

    # Commit
    print(f"Committing: {message}")
    run(["git", "commit", "-m", message])

    # Determine remote and branch
    branch = get_current_branch()
    print(f"Pushing branch '{branch}' to origin...")

    # Push (set upstream if needed)
    push_result = run(["git", "push", "origin", branch], check=False)
    if push_result.returncode != 0:
        # Try pull --rebase then push
        rebase_result = run(["git", "pull", "--rebase", "origin", branch], check=False)
        if rebase_result.returncode == 0:
            push_result = run(["git", "push", "origin", branch], check=False)
        if push_result.returncode != 0:
            push_result = run(["git", "push", "--set-upstream", "origin", branch], check=False)
            if push_result.returncode != 0:
                stderr = push_result.stderr.strip() if push_result.stderr else ""
                raise SystemExit(f"Push failed:\n{stderr}")

    print(f"\nDone. Committed and pushed to origin/{branch}.")


def main() -> None:
    # If no arguments at all, run interactive mode
    if len(sys.argv) == 1:
        interactive_mode()
        return

    parser = argparse.ArgumentParser(description="Auto-commit and push changes cleanly.")
    parser.add_argument("-m", "--message", default=None, help="Commit message (enables agent mode)")
    parser.add_argument("--files", nargs="*", default=None,
                        help="Specific files to stage. If omitted, stages all changes.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show what would be committed without doing it.")
    args, _ = parser.parse_known_args()

    if args.message:
        agent_mode(args)
    else:
        interactive_mode()


if __name__ == "__main__":
    main()
