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
import os
import subprocess
import sys
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
