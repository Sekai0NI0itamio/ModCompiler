#!/usr/bin/env python3
"""
Search Minecraft source code using GitHub Actions workflow.
This triggers the grep-minecraft-source workflow and downloads results.
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run_gh_command(args, capture=True):
    """Run a gh CLI command."""
    cmd = ["gh"] + args
    if capture:
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
        if result.returncode != 0:
            print(f"Error running: {' '.join(cmd)}", file=sys.stderr)
            print(result.stderr, file=sys.stderr)
            sys.exit(1)
        return result.stdout.strip()
    else:
        result = subprocess.run(cmd, cwd=ROOT)
        if result.returncode != 0:
            sys.exit(1)

def trigger_workflow(version, loader, query, file_pattern="*.java", context_lines=5):
    """Trigger the grep-minecraft-source workflow."""
    print(f"Triggering source search workflow...")
    print(f"  Version: {version}")
    print(f"  Loader: {loader}")
    print(f"  Query: {query}")
    print(f"  File Pattern: {file_pattern}")
    print(f"  Context: {context_lines} lines")
    print()
    
    # Trigger workflow
    run_gh_command([
        "workflow", "run", "grep-minecraft-source.yml",
        "-f", f"minecraft_version={version}",
        "-f", f"loader={loader}",
        "-f", f"query={query}",
        "-f", f"file_pattern={file_pattern}",
        "-f", f"context_lines={context_lines}"
    ], capture=False)
    
    print("\nWorkflow triggered! Waiting for run to start...")
    time.sleep(5)
    
    # Get the latest run
    runs_json = run_gh_command([
        "run", "list",
        "--workflow=grep-minecraft-source.yml",
        "--limit=1",
        "--json=databaseId,status,conclusion,createdAt"
    ])
    
    runs = json.loads(runs_json)
    if not runs:
        print("Error: Could not find workflow run", file=sys.stderr)
        sys.exit(1)
    
    run_id = runs[0]["databaseId"]
    print(f"Run ID: {run_id}")
    print(f"URL: https://github.com/{get_repo()}/actions/runs/{run_id}")
    
    return run_id

def get_repo():
    """Get the repository name."""
    remote = run_gh_command(["repo", "view", "--json=nameWithOwner", "-q", ".nameWithOwner"])
    return remote

def wait_for_completion(run_id, timeout=1800):
    """Wait for workflow run to complete."""
    print("\nWaiting for workflow to complete...")
    start_time = time.time()
    
    while True:
        if time.time() - start_time > timeout:
            print(f"\nTimeout after {timeout}s", file=sys.stderr)
            sys.exit(1)
        
        # Check run status
        run_json = run_gh_command([
            "run", "view", str(run_id),
            "--json=status,conclusion"
        ])
        
        run_data = json.loads(run_json)
        status = run_data["status"]
        conclusion = run_data.get("conclusion")
        
        if status == "completed":
            print(f"\nWorkflow completed with conclusion: {conclusion}")
            return conclusion == "success"
        
        # Show progress
        elapsed = int(time.time() - start_time)
        print(f"\r[{elapsed:04d}s] Status: {status}...", end="", flush=True)
        
        time.sleep(10)

def download_results(run_id, output_dir):
    """Download search results artifact."""
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\nDownloading search results to: {output_path}")
    
    # Download artifact
    run_gh_command([
        "run", "download", str(run_id),
        "-n", "minecraft-source-search-results",
        "-D", str(output_path)
    ], capture=False)
    
    # Read and display results
    results_file = output_path / "results.txt"
    summary_file = output_path / "summary.txt"
    
    if summary_file.exists():
        print("\n" + "="*60)
        print("SEARCH SUMMARY")
        print("="*60)
        print(summary_file.read_text())
        print("="*60)
    
    if results_file.exists():
        print("\nSEARCH RESULTS:")
        print("="*60)
        results = results_file.read_text()
        print(results)
        print("="*60)
        print(f"\nFull results saved to: {results_file}")
        return True
    else:
        print("No results file found", file=sys.stderr)
        return False

def main():
    parser = argparse.ArgumentParser(
        description="Search Minecraft source code using GitHub Actions",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Search for SavedDataType in Forge 1.21.5
  python3 scripts/grep_minecraft_source.py --version 1.21.5 --loader forge --query "SavedDataType"
  
  # Search for computeIfAbsent in DimensionDataStorage
  python3 scripts/grep_minecraft_source.py --version 1.21.5 --loader forge \\
    --query "computeIfAbsent" --file-pattern "*DimensionDataStorage.java"
  
  # Search for SavedData usage with more context
  python3 scripts/grep_minecraft_source.py --version 1.21.5 --loader forge \\
    --query "class.*extends SavedData" --context 10
        """
    )
    
    parser.add_argument("--version", required=True,
                       help="Minecraft version (e.g., 1.21.5)")
    parser.add_argument("--loader", required=True, choices=["forge", "fabric", "neoforge"],
                       help="Mod loader")
    parser.add_argument("--query", required=True,
                       help="Search query (regex pattern)")
    parser.add_argument("--file-pattern", default="*.java",
                       help="File pattern to search (default: *.java)")
    parser.add_argument("--context", type=int, default=5,
                       help="Lines of context around matches (default: 5)")
    parser.add_argument("--output-dir", default=None,
                       help="Output directory for results (default: MinecraftSourceSearch/run-TIMESTAMP)")
    parser.add_argument("--no-wait", action="store_true",
                       help="Don't wait for completion, just trigger and exit")
    
    args = parser.parse_args()
    
    # Check if gh CLI is installed
    try:
        subprocess.run(["gh", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("Error: GitHub CLI (gh) is not installed or not in PATH", file=sys.stderr)
        print("Install from: https://cli.github.com/", file=sys.stderr)
        sys.exit(1)
    
    # Trigger workflow
    run_id = trigger_workflow(
        args.version,
        args.loader,
        args.query,
        args.file_pattern,
        args.context
    )
    
    if args.no_wait:
        print(f"\nWorkflow triggered (Run ID: {run_id})")
        print("Use --output-dir to download results later:")
        print(f"  gh run download {run_id} -n minecraft-source-search-results")
        return
    
    # Wait for completion
    success = wait_for_completion(run_id)
    
    if not success:
        print("\nWorkflow failed. Check logs:")
        print(f"  gh run view {run_id} --log")
        sys.exit(1)
    
    # Download results
    if args.output_dir:
        output_dir = args.output_dir
    else:
        timestamp = time.strftime("%Y%m%d-%H%M%S")
        output_dir = ROOT / "MinecraftSourceSearch" / f"run-{timestamp}"
    
    if download_results(run_id, output_dir):
        print("\n✓ Search complete!")
    else:
        print("\n✗ Failed to download results", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
