#!/usr/bin/env python3
"""Auto-commit and push only the changes made in the working tree.

Usage:
    python3 commit_and_push.py "Your commit message here"
    python3 commit_and_push.py -m "Your commit message here"
    python3 commit_and_push.py --message "Your commit message here"

Options:
    -m, --message   Commit message (required)
    --files         Specific files to stage (space-separated). If omitted,
                    all modified/deleted/new tracked changes are staged.
    --dry-run       Show what would be committed without actually doing it.

Designed for AI agent workflows: stages only dirty files, commits, and
pushes to the current branch's upstream in one clean operation.
"""

import argparse
import subprocess
import sys


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


def main() -> None:
    parser = argparse.ArgumentParser(description="Auto-commit and push changes cleanly.")
    parser.add_argument("-m", "--message", required=True, help="Commit message")
    parser.add_argument("--files", nargs="*", default=None,
                        help="Specific files to stage. If omitted, stages all changes.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show what would be committed without doing it.")
    # Support positional message as first arg for convenience
    args, unknown = parser.parse_known_args()

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
        # Stage all modified and deleted tracked files, plus new untracked files
        # that aren't ignored
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
        # Unstage since this is a dry run
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
        # Try setting upstream
        push_result = run(["git", "push", "--set-upstream", "origin", branch], check=False)
        if push_result.returncode != 0:
            stderr = push_result.stderr.strip() if push_result.stderr else ""
            raise SystemExit(f"Push failed:\n{stderr}")

    print(f"\nDone. Committed and pushed to origin/{branch}.")


if __name__ == "__main__":
    main()
