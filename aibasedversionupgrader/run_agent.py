#!/usr/bin/env python3
"""
run_agent.py — Single entry point for the AI Mod Version Converter
===================================================================
Runs Phase 1 (project info + missing version detection) then Phase 2
(AI compilation loop) all in one command.

Local usage:
    python3 aibasedversionupgrader/run_agent.py https://modrinth.com/mod/snow-accumulation

GitHub Actions usage (same script, keys come from NVIDIA_API_KEYS secret):
    python3 aibasedversionupgrader/run_agent.py <modrinth_url>

Options:
    --artifact-dir DIR    Output artifact directory (default: ai-built-versions/)
    --sandbox-dir DIR     Sandbox working directory (default: ai-sandbox/)
    --max-iterations N    Maximum agent iterations (default: 200)
    --skip-phase1         Skip Phase 1 and reuse existing project_info/ + target_list.json
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from aibasedversionupgrader.agent import AgentConfig, ModVersionConverterAgent

LOCAL_NVIDIA_KEY_FILE = Path(
    "/Users/stevennovak/Desktop/Important/Assistant/C05LocalAi/keys/nvidia.txt"
)


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="AI Mod Version Converter — compile missing mod versions using an AI agent",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 aibasedversionupgrader/run_agent.py https://modrinth.com/mod/snow-accumulation
  python3 aibasedversionupgrader/run_agent.py snow-accumulation --skip-phase1
        """,
    )
    p.add_argument(
        "modrinth_url",
        help="Modrinth project URL, slug, or project ID",
    )
    p.add_argument(
        "--artifact-dir", default="ai-built-versions/",
        help="Output artifact directory (default: ai-built-versions/)",
    )
    p.add_argument(
        "--max-iterations", type=int, default=200,
        help="Maximum agent iterations (default: 200)",
    )
    p.add_argument(
        "--skip-phase1", action="store_true",
        help="Skip Phase 1 and reuse existing project_info/ + target_list.json",
    )
    return p.parse_args()


# ---------------------------------------------------------------------------
# Key loading
# ---------------------------------------------------------------------------

def load_nvidia_keys() -> list:
    """Load NVIDIA API keys from env var or local key file (local dev only)."""
    raw = os.environ.get("NVIDIA_API_KEYS", "").strip()
    if not raw:
        single = os.environ.get("NVIDIA_API_KEY", "").strip()
        if single:
            return [single]
        if LOCAL_NVIDIA_KEY_FILE.exists():
            raw = LOCAL_NVIDIA_KEY_FILE.read_text(encoding="utf-8").strip()
            if raw:
                print(f"[INFO] Loaded NVIDIA keys from {LOCAL_NVIDIA_KEY_FILE}")
    if not raw:
        return []
    return [
        line.strip()
        for line in raw.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]


# ---------------------------------------------------------------------------
# Phase 1 helpers (inline — no subprocess needed)
# ---------------------------------------------------------------------------

def run_phase1_project_info(modrinth_url: str) -> None:
    """Run Phase 1a inline by temporarily patching sys.argv."""
    print("\n" + "─" * 60)
    print("PHASE 1a: Fetching project info...")
    print("─" * 60)
    orig_argv = sys.argv[:]
    sys.argv = ["phase1_project_info.py", modrinth_url]
    try:
        from aibasedversionupgrader.phase1_project_info import main as _pi_main
        _pi_main()
    finally:
        sys.argv = orig_argv


def run_phase1_missing_versions(modrinth_url: str) -> None:
    """Run Phase 1b inline by temporarily patching sys.argv."""
    print("\n" + "─" * 60)
    print("PHASE 1b: Detecting missing versions...")
    print("─" * 60)
    orig_argv = sys.argv[:]
    sys.argv = ["phase1_missing_versions.py", modrinth_url]
    try:
        from aibasedversionupgrader.phase1_missing_versions import main as _mv_main
        _mv_main()
    finally:
        sys.argv = orig_argv


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    args = parse_args()
    modrinth_url = args.modrinth_url.strip()
    is_local = not os.environ.get("GITHUB_ACTIONS")

    # ── Header ──────────────────────────────────────────────────────────────
    print("=" * 70)
    print("AI MOD VERSION CONVERTER")
    print("=" * 70)
    print(f"Project:  {modrinth_url}")
    print(f"Mode:     {'Local' if is_local else 'GitHub Actions'}")
    print("=" * 70)

    # ── Load keys ───────────────────────────────────────────────────────────
    nvidia_keys = load_nvidia_keys()
    if not nvidia_keys:
        if is_local:
            print(
                "\nWARNING: No NVIDIA API keys found.\n"
                f"  Add keys to {LOCAL_NVIDIA_KEY_FILE}\n"
                "  or set NVIDIA_API_KEYS env var.\n"
                "  LLM calls will fail without keys.\n"
            )
        else:
            print("ERROR: NVIDIA_API_KEYS secret is not set.", file=sys.stderr)
            sys.exit(1)

    modrinth_token = os.environ.get("MODRINTH_TOKEN", "").strip()

    # ── Phase 1 ─────────────────────────────────────────────────────────────
    if args.skip_phase1:
        print("\n[INFO] --skip-phase1 set — reusing existing project_info/ and target_list.json")
    else:
        # Auto-skip Phase 1 if data already exists for the same project
        project_json = Path("project_info") / "project.json"
        target_json = Path("target_list.json")
        if project_json.exists() and target_json.exists():
            try:
                existing = json.loads(project_json.read_text(encoding="utf-8"))
                existing_slug = existing.get("slug", "")
                from aibasedversionupgrader.phase1_project_info import normalize_project_ref
                requested_slug = normalize_project_ref(modrinth_url)
                if existing_slug and existing_slug == requested_slug:
                    print(f"\n[INFO] Phase 1 data already exists for '{existing_slug}' — skipping re-fetch.")
                    print("[INFO] Use --skip-phase1 to always skip, or delete project_info/ to force re-fetch.")
                else:
                    run_phase1_project_info(modrinth_url)
                    run_phase1_missing_versions(modrinth_url)
            except Exception:
                run_phase1_project_info(modrinth_url)
                run_phase1_missing_versions(modrinth_url)
        else:
            run_phase1_project_info(modrinth_url)
            run_phase1_missing_versions(modrinth_url)

    # ── Check target list ───────────────────────────────────────────────────
    target_list_path = Path("target_list.json")
    if not target_list_path.exists():
        print("\nERROR: target_list.json not found after Phase 1.", file=sys.stderr)
        sys.exit(1)

    target_list = json.loads(target_list_path.read_text(encoding="utf-8"))
    if not target_list:
        print("\n✅ All versions are already present on Modrinth. Nothing to build.")
        sys.exit(0)

    print(f"\n[INFO] {len(target_list)} target(s) to build — starting AI agent...")

    # ── Phase 2 config ──────────────────────────────────────────────────────
    summary_file = None
    summary_env = os.environ.get("GITHUB_STEP_SUMMARY", "")
    if summary_env:
        summary_file = Path(summary_env)

    log_file = Path("local_run_log.txt") if is_local else None

    if is_local:
        print(f"\nSessions:  ai-sessions/<session-id>/")
        print(f"Artifacts: {args.artifact_dir}")
        print(f"Keys:      {len(nvidia_keys)} NVIDIA API key(s)")
        print()

    config = AgentConfig(
        nvidia_keys=nvidia_keys or ["dummy-key-for-local-testing"],
        modrinth_token=modrinth_token,
        target_list_path=target_list_path,
        project_info_dir=Path("project_info/"),
        sessions_base_dir=Path("ai-sessions/"),
        artifact_dir=Path(args.artifact_dir),
        repo_root=ROOT,
        is_local=is_local,
        max_iterations=args.max_iterations,
        summary_file=summary_file,
        log_file=log_file,
    )

    # ── Run agent ───────────────────────────────────────────────────────────
    print("─" * 60)
    print("PHASE 2: AI Compilation Loop")
    print("─" * 60)
    agent = ModVersionConverterAgent(config)
    result = asyncio.run(agent.run())

    # ── Final summary ───────────────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("FINAL RESULTS")
    print("=" * 70)
    print(f"Total targets:      {result.total_targets}")
    print(f"Successfully built: {len(result.success_list)}")
    print(f"Failed/skipped:     {len(result.failed_list)}")
    print(f"Iterations used:    {result.iterations}")

    if result.success_list:
        print("\n✅ Successfully built:")
        for s in result.success_list:
            print(f"   {s['minecraft_version']}/{s['loader']}")

    if result.failed_list:
        print("\n❌ Failed/not built:")
        for f in result.failed_list:
            print(f"   {f['minecraft_version']}/{f['loader']}")

    if result.error:
        print(f"\nError: {result.error}")

    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as f:
            f.write("\n## AI Mod Version Converter Results\n\n")
            f.write(f"- **Project:** `{modrinth_url}`\n")
            f.write(f"- **Total targets:** {result.total_targets}\n")
            f.write(f"- **Successfully built:** {len(result.success_list)}\n")
            f.write(f"- **Failed:** {len(result.failed_list)}\n")
            f.write(f"- **Iterations:** {result.iterations}\n\n")
            if result.success_list:
                f.write("### ✅ Built\n")
                for s in result.success_list:
                    f.write(f"- `{s['minecraft_version']}/{s['loader']}`\n")
                f.write("\n")
            if result.failed_list:
                f.write("### ❌ Failed\n")
                for fl in result.failed_list:
                    f.write(f"- `{fl['minecraft_version']}/{fl['loader']}`\n")
                f.write("\n")

    if is_local and result.session_dir:
        print(f"\nSession dir: {result.session_dir}")
        print(f"Agent log:   {result.session_dir / 'agent_log.txt'}")

    sys.exit(1 if (result.failed_list and not result.success_list) else 0)


if __name__ == "__main__":
    main()
