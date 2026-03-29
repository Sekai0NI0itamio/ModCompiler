from __future__ import annotations

import argparse
import base64
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import textwrap
import threading
import time
import traceback
import urllib.error
import urllib.request
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from modcompiler.common import ModCompilerError, copy_file, copy_tree, load_json, safe_rmtree, write_json
from modcompiler.decompile import inspect_mod_jar
from modcompiler.modrinth import (
    ModrinthClient,
    build_modrinth_user_agent,
    encode_multipart_form_data,
    guess_content_type,
)


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT_DIR = "ToBeUploaded"
DEFAULT_OUTPUT_DIR = "AutoCreateModrinthBundles"
DEFAULT_MANIFEST = "version-manifest.json"
DEFAULT_TEMPLATE_DIR = str(REPO_ROOT / "templatecreatemod")
DEFAULT_C05_URL = "http://localhost:8129"
DEFAULT_C05_HOSTER = "scitely"
DEFAULT_C05_MODEL = "deepseek-v3.2"
DEFAULT_REASONING_EFFORT = "low"
DEFAULT_PROJECT_STATUS = "draft"
DEFAULT_VERSION_STATUS = "draft"
DEFAULT_VERSION_TYPE = "release"
DEFAULT_MAX_WORKERS = 4
DEFAULT_PROMPT_PROJECTINFO_CHAR_LIMIT = 24_000
DEFAULT_PUBLISH_VIA = "auto"
REMOTE_DECOMPILE_WORKFLOW_ID = "jar-decompile.yml"
REMOTE_DECOMPILE_ARTIFACT_NAME = "jar-decompile-output"
REMOTE_PUBLISH_WORKFLOW_ID = "publish-auto-create-modrinth.yml"
REMOTE_PUBLISH_ARTIFACT_NAME = "auto-create-modrinth-publish-output"
SOURCE_SUFFIXES = {".java", ".kt"}
MAX_PROJECTINFO_TREE_LINES = 160
MAX_PROJECTINFO_EXCERPTS = 5
PROJECTINFO_EXCERPT_MAX_LINES = 120
PROJECTINFO_EXCERPT_MAX_CHARS = 4_000
SUPPORT_SOURCE_MARKERS = ("proxy/", "clientproxy", "commonproxy")
TECHNICAL_DESCRIPTION_MARKERS = (
    "reflection",
    "custom damage source",
    "damage source",
    "internal cooldown",
    "cooldown field",
    "tick event",
    "world tick",
    "event bus",
    "event handler",
    "subscribeevent",
    "tile entit",
    "field access",
    "internal field",
    "package name",
    "class name",
    "method name",
    "internal api",
    "model loader",
)
GENERIC_COMPATIBILITY_MARKERS = (
    "requires forge",
    "requires fabric",
    "requires neoforge",
    "this is a forge mod",
    "this is a fabric mod",
    "this is a neoforge mod",
    "designed for minecraft",
)
MODRINTH_CATEGORY_ALIASES = {
    "adventure": "adventure",
    "food": "food",
    "minigame": "minigame",
    "technology": "technology",
    "cursed": "cursed",
    "game mechanics": "game-mechanics",
    "game-mechanics": "game-mechanics",
    "mobs": "mobs",
    "transportation": "transportation",
    "decoration": "decoration",
    "library": "library",
    "optimization": "optimization",
    "utility": "utility",
    "economy": "economy",
    "magic": "magic",
    "social": "social",
    "world generation": "worldgen",
    "world-generation": "worldgen",
    "worldgen": "worldgen",
    "equipment": "equipment",
    "management": "management",
    "storage": "storage",
}
AI_LICENSE_CHOICES = (
    "CC0-1.0",
    "CC-BY-4.0",
    "Apache-2.0",
    "BSD-2-Clause",
    "BSD-3-Clause",
)
DEFAULT_ALL_RIGHTS_RESERVED_LICENSE_ID = "LicenseRef-All-Rights-Reserved"
DEFAULT_CUSTOM_LICENSE_ID = "LicenseRef-Custom"
OPTIONAL_ICON_FILENAMES = (
    "icon.png",
    "icon.jpg",
    "icon.jpeg",
    "icon.bmp",
    "icon.gif",
    "icon.webp",
    "icon.svg",
    "icon.svgz",
    "icon.rgb",
)
ANSI_GREEN = "\033[92m"
ANSI_RED = "\033[91m"
ANSI_YELLOW = "\033[93m"
ANSI_RESET = "\033[0m"
PRINT_LOCK = threading.Lock()
REMOTE_DISPATCH_LOCK = threading.Lock()
CLAIMED_REMOTE_RUN_IDS: set[int] = set()


@dataclass(frozen=True)
class GenerateOptions:
    input_dir: Path
    output_dir: Path
    manifest_path: Path
    manifest: dict[str, Any]
    c05_url: str
    c05_hoster: str
    c05_model: str
    reasoning_effort: str
    temperature: float
    max_tokens: int
    timeout_seconds: int
    force: bool
    prompt_projectinfo_char_limit: int
    project_status: str
    version_status: str
    version_type: str
    categories: list[str]
    additional_categories: list[str]
    client_side: str
    server_side: str
    issues_url: str
    source_url: str
    wiki_url: str
    discord_url: str
    license_id: str
    license_url: str
    template_examples: dict[str, str]
    github_token: str
    github_repo: str
    github_branch: str
    remote_jar_paths: dict[str, str]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Local-only workflow that decompiles jars from ToBeUploaded, asks C05 Local AI "
            "(scitely/deepseek-v3.2) to draft a Modrinth listing, then publishes only bundles "
            "whose verify.txt contains 'verified'."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate_parser = subparsers.add_parser(
        "generate",
        help="Create one review bundle per jar in ToBeUploaded.",
    )
    generate_parser.add_argument("--input-dir", default=DEFAULT_INPUT_DIR)
    generate_parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    generate_parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    generate_parser.add_argument("--c05-url", default=DEFAULT_C05_URL)
    generate_parser.add_argument("--c05-hoster", default=DEFAULT_C05_HOSTER)
    generate_parser.add_argument("--c05-model", default=DEFAULT_C05_MODEL)
    generate_parser.add_argument("--reasoning-effort", default=DEFAULT_REASONING_EFFORT)
    generate_parser.add_argument("--temperature", type=float, default=0.2)
    generate_parser.add_argument("--max-tokens", type=int, default=1_400)
    generate_parser.add_argument("--timeout-seconds", type=int, default=300)
    generate_parser.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS)
    generate_parser.add_argument("--force", action="store_true")
    generate_parser.add_argument(
        "--prompt-projectinfo-char-limit",
        type=int,
        default=DEFAULT_PROMPT_PROJECTINFO_CHAR_LIMIT,
        help="Maximum projectinfo characters to embed into the DeepSeek user message.",
    )
    generate_parser.add_argument("--project-status", default=DEFAULT_PROJECT_STATUS)
    generate_parser.add_argument("--version-status", default=DEFAULT_VERSION_STATUS)
    generate_parser.add_argument("--version-type", default=DEFAULT_VERSION_TYPE)
    generate_parser.add_argument("--categories", default="")
    generate_parser.add_argument("--additional-categories", default="")
    generate_parser.add_argument("--client-side", default="optional")
    generate_parser.add_argument("--server-side", default="optional")
    generate_parser.add_argument("--issues-url", default="")
    generate_parser.add_argument("--source-url", default="")
    generate_parser.add_argument("--wiki-url", default="")
    generate_parser.add_argument("--discord-url", default="")
    generate_parser.add_argument("--license-id", default="")
    generate_parser.add_argument("--license-url", default="")
    generate_parser.add_argument("--template-dir", default=DEFAULT_TEMPLATE_DIR)

    publish_parser = subparsers.add_parser(
        "publish",
        help="Create Modrinth projects and upload jars for verified bundles only.",
    )
    publish_parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    publish_parser.add_argument("--modrinth-token", default="")
    publish_parser.add_argument("--dry-run", action="store_true")
    publish_parser.add_argument("--only-bundle", default="")
    publish_parser.add_argument("--publish-via", default=DEFAULT_PUBLISH_VIA)

    args = parser.parse_args(argv)
    try:
        if args.command == "generate":
            return command_generate(args)
        if args.command == "publish":
            return command_publish(args)
    except ModCompilerError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 1


def command_generate(args: argparse.Namespace) -> int:
    input_dir = Path(args.input_dir)
    output_dir = Path(args.output_dir)
    manifest_path = Path(args.manifest)

    if not manifest_path.is_absolute():
        manifest_path = Path.cwd() / manifest_path
    if not manifest_path.exists():
        raise ModCompilerError(f"Manifest file does not exist: {manifest_path}")

    if not input_dir.is_absolute():
        input_dir = Path.cwd() / input_dir
    if not input_dir.exists():
        input_dir.mkdir(parents=True, exist_ok=True)
        raise ModCompilerError(
            f"Input folder did not exist, so it was created for you: {input_dir}\n"
            "Place one or more .jar files there and run the script again."
        )

    jars = sorted(path for path in input_dir.iterdir() if path.is_file() and path.suffix.lower() == ".jar")
    if not jars:
        raise ModCompilerError(f"No .jar files were found in {input_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = load_json(manifest_path)
    template_examples = load_template_examples(Path(args.template_dir))
    github_token = discover_github_token()
    github_repo = discover_github_repo()
    current_branch = discover_current_branch()
    github_branch, remote_jar_paths = prepare_remote_decompile_inputs(
        jars=jars,
        github_token=github_token,
        github_repo=github_repo,
        github_base_branch=current_branch,
    )

    options = GenerateOptions(
        input_dir=input_dir,
        output_dir=output_dir,
        manifest_path=manifest_path,
        manifest=manifest,
        c05_url=str(args.c05_url).strip().rstrip("/"),
        c05_hoster=str(args.c05_hoster).strip().lower() or DEFAULT_C05_HOSTER,
        c05_model=str(args.c05_model).strip() or DEFAULT_C05_MODEL,
        reasoning_effort=str(args.reasoning_effort).strip() or DEFAULT_REASONING_EFFORT,
        temperature=float(args.temperature),
        max_tokens=int(args.max_tokens),
        timeout_seconds=int(args.timeout_seconds),
        force=bool(args.force),
        prompt_projectinfo_char_limit=max(10_000, int(args.prompt_projectinfo_char_limit)),
        project_status=str(args.project_status).strip() or DEFAULT_PROJECT_STATUS,
        version_status=str(args.version_status).strip() or DEFAULT_VERSION_STATUS,
        version_type=str(args.version_type).strip() or DEFAULT_VERSION_TYPE,
        categories=parse_csv_items(args.categories),
        additional_categories=parse_csv_items(args.additional_categories),
        client_side=normalize_side_value(args.client_side),
        server_side=normalize_side_value(args.server_side),
        issues_url=str(args.issues_url).strip(),
        source_url=str(args.source_url).strip(),
        wiki_url=str(args.wiki_url).strip(),
        discord_url=str(args.discord_url).strip(),
        license_id=str(args.license_id).strip(),
        license_url=str(args.license_url).strip(),
        template_examples=template_examples,
        github_token=github_token,
        github_repo=github_repo,
        github_branch=github_branch,
        remote_jar_paths=remote_jar_paths,
    )

    max_workers = max(1, min(int(args.max_workers), len(jars)))
    print(
        f"Generating {len(jars)} Modrinth bundle(s) from {input_dir} with {max_workers} worker(s).",
        flush=True,
    )

    results: list[dict[str, Any]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_map = {executor.submit(generate_bundle_for_jar, jar_path, options): jar_path for jar_path in jars}
        for future in concurrent.futures.as_completed(future_map):
            jar_path = future_map[future]
            try:
                result = future.result()
            except Exception as error:  # pragma: no cover - defensive fallback
                result = {
                    "jar_name": jar_path.name,
                    "bundle_slug": slugify_text(jar_path.stem),
                    "status": "failed",
                    "error": f"{type(error).__name__}: {error}",
                }
            results.append(result)

    summary = {
        "generated_at": now_iso(),
        "input_dir": str(input_dir),
        "output_dir": str(output_dir),
        "hoster": options.c05_hoster,
        "model": options.c05_model,
        "results": sorted(results, key=lambda item: (item.get("status", ""), item.get("jar_name", ""))),
    }
    write_json(output_dir / "generate-summary.json", summary)
    (output_dir / "GENERATE_SUMMARY.md").write_text(render_generate_summary_markdown(summary), encoding="utf-8")

    failed = sum(1 for result in results if result.get("status") == "failed")
    skipped = sum(1 for result in results if result.get("status") == "skipped")
    ready = sum(1 for result in results if result.get("status") == "ready_for_verification")
    print(
        f"Finished generation: {ready} ready, {skipped} skipped, {failed} failed. "
        f"Review each bundle's verify.txt before publishing.",
        flush=True,
    )
    return 0 if failed == 0 else 1


def command_publish(args: argparse.Namespace) -> int:
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = Path.cwd() / output_dir
    if not output_dir.exists():
        raise ModCompilerError(f"Output bundle directory does not exist: {output_dir}")

    bundle_dirs = discover_bundle_dirs(output_dir)
    if args.only_bundle:
        target = output_dir / str(args.only_bundle).strip()
        bundle_dirs = [path for path in bundle_dirs if path.resolve() == target.resolve()]
    if not bundle_dirs:
        raise ModCompilerError(f"No bundle directories were found under {output_dir}")

    token = str(args.modrinth_token).strip() or os.environ.get("MODRINTH_TOKEN", "").strip()
    publish_via = normalize_publish_via(args.publish_via)
    if args.dry_run:
        publish_via = "local"
    elif publish_via == "auto":
        publish_via = "local" if token else "github"

    if publish_via == "github":
        return command_publish_via_github(args=args, output_dir=output_dir, bundle_dirs=bundle_dirs)

    if not args.dry_run and not token:
        raise ModCompilerError(
            "A Modrinth token is required for local publish. "
            "Pass --modrinth-token, set MODRINTH_TOKEN, or use --publish-via github."
        )

    client = ModrinthClient(
        token=token if not args.dry_run else "",
        user_agent=build_modrinth_user_agent(),
    )

    results: list[dict[str, Any]] = []
    for bundle_dir in bundle_dirs:
        results.append(publish_bundle(bundle_dir=bundle_dir, client=client, dry_run=bool(args.dry_run)))

    summary = {
        "published_at": now_iso(),
        "output_dir": str(output_dir),
        "dry_run": bool(args.dry_run),
        "results": results,
    }
    write_json(output_dir / "publish-summary.json", summary)
    (output_dir / "PUBLISH_SUMMARY.md").write_text(render_publish_summary_markdown(summary), encoding="utf-8")

    failures = sum(1 for result in results if result.get("status") == "failed")
    print(
        f"Finished publish scan: {len(results)} bundle(s) checked, {failures} failure(s).",
        flush=True,
    )
    return 0 if failures == 0 else 1


def command_publish_via_github(
    *,
    args: argparse.Namespace,
    output_dir: Path,
    bundle_dirs: list[Path],
) -> int:
    github_token = discover_github_token()
    github_repo = discover_github_repo()
    current_branch = discover_current_branch()
    dispatch_branch, remote_output_dir = prepare_remote_publish_inputs(
        bundle_dirs=bundle_dirs,
        output_dir=output_dir,
        github_token=github_token,
        github_repo=github_repo,
        github_base_branch=current_branch,
    )

    results: list[dict[str, Any]] = []
    for bundle_dir in bundle_dirs:
        results.append(
            publish_bundle_via_github_actions(
                bundle_dir=bundle_dir,
                output_dir=output_dir,
                remote_output_dir=remote_output_dir,
                github_repo=github_repo,
                github_branch=dispatch_branch,
                github_token=github_token,
            )
        )

    summary = {
        "published_at": now_iso(),
        "output_dir": str(output_dir),
        "dry_run": False,
        "publish_via": "github",
        "results": results,
    }
    write_json(output_dir / "publish-summary.json", summary)
    (output_dir / "PUBLISH_SUMMARY.md").write_text(render_publish_summary_markdown(summary), encoding="utf-8")

    failures = sum(1 for result in results if result.get("status") == "failed")
    print(
        f"Finished publish scan via GitHub: {len(results)} bundle(s) checked, {failures} failure(s).",
        flush=True,
    )
    return 0 if failures == 0 else 1


def discover_github_token() -> str:
    for env_name in ("GH_TOKEN", "GITHUB_TOKEN"):
        token = os.environ.get(env_name, "").strip()
        if token:
            return token

    parent = Path.cwd().parent
    matches = sorted(
        path
        for path in parent.iterdir()
        if path.is_dir() and path.name.startswith("github_pat_")
    )
    if matches:
        return matches[0].name

    raise ModCompilerError(
        "Could not find a GitHub token. Set GH_TOKEN/GITHUB_TOKEN or create ../github_pat_* as you described."
    )


def discover_github_repo() -> str:
    remote_url = run_subprocess(["git", "remote", "get-url", "origin"]).strip()
    repo = parse_github_repo_from_remote(remote_url)
    if not repo:
        raise ModCompilerError(f"Could not determine owner/repo from origin remote: {remote_url}")
    return repo


def parse_github_repo_from_remote(remote_url: str) -> str:
    raw = str(remote_url or "").strip()
    if raw.startswith("git@github.com:"):
        tail = raw.split(":", 1)[1]
    elif raw.startswith("https://github.com/"):
        tail = raw.split("https://github.com/", 1)[1]
    elif raw.startswith("http://github.com/"):
        tail = raw.split("http://github.com/", 1)[1]
    else:
        return ""
    tail = tail.strip("/")
    if tail.endswith(".git"):
        tail = tail[:-4]
    parts = [part for part in tail.split("/") if part]
    if len(parts) < 2:
        return ""
    return f"{parts[0]}/{parts[1]}"


def discover_current_branch() -> str:
    branch = run_subprocess(["git", "rev-parse", "--abbrev-ref", "HEAD"]).strip()
    if not branch or branch == "HEAD":
        raise ModCompilerError("The current git checkout is detached. Switch to a branch before running generate.")
    return branch


def relative_to_repo_root(path: Path) -> Path:
    repo_root = Path.cwd().resolve()
    resolved = path.resolve()
    try:
        return resolved.relative_to(repo_root)
    except ValueError:
        raise ModCompilerError(
            f"{resolved} is outside the repository root ({repo_root}). "
            "Remote GitHub workflow publishing only supports bundle directories inside this repo."
        ) from None


def prepare_remote_decompile_inputs(
    *,
    jars: list[Path],
    github_token: str,
    github_repo: str,
    github_base_branch: str,
) -> tuple[str, dict[str, str]]:
    ensure_command_available("gh")
    ensure_command_available("git")

    session_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]
    dispatch_branch = f"auto-create-modrinth-decompile-{session_id}"
    remote_root = Path("To Be Decompiled") / "auto-create-modrinth" / session_id
    remote_jar_paths: dict[str, str] = {}

    with tempfile.TemporaryDirectory(prefix="auto-create-remote-branch-") as temp_dir:
        worktree_dir = Path(temp_dir) / "worktree"
        run_subprocess(["git", "worktree", "add", "--detach", str(worktree_dir), "HEAD"])
        try:
            run_subprocess(["git", "checkout", "-b", dispatch_branch], cwd=worktree_dir)

            staged_paths: list[Path] = []
            for jar_path in jars:
                remote_path = remote_root / jar_path.name
                repo_file_path = worktree_dir / remote_path
                repo_file_path.parent.mkdir(parents=True, exist_ok=True)
                copy_file(jar_path, repo_file_path)
                staged_paths.append(remote_path)
                remote_jar_paths[str(jar_path.resolve())] = str(remote_path).replace(os.sep, "/")

            run_subprocess(["git", "add", "--"] + [str(path) for path in staged_paths], cwd=worktree_dir)
            staged_output = run_subprocess(
                ["git", "diff", "--cached", "--name-only", "--"] + [str(path) for path in staged_paths],
                cwd=worktree_dir,
            )
            if not staged_output.strip():
                raise ModCompilerError("Failed to stage the remote decompile jar files for commit.")

            commit_message = f"Add auto-create decompile jars ({session_id})"
            run_subprocess(
                [
                    "git",
                    "-c",
                    "user.name=Codex Auto Create",
                    "-c",
                    "user.email=codex-auto-create@example.com",
                    "commit",
                    "-m",
                    commit_message,
                    "--",
                    *[str(path) for path in staged_paths],
                ],
                cwd=worktree_dir,
            )
            push_branch_with_token(
                branch=dispatch_branch,
                github_token=github_token,
                cwd=worktree_dir,
            )
        finally:
            try:
                run_subprocess(["git", "worktree", "remove", "--force", str(worktree_dir)])
            except ModCompilerError:
                pass

    _print_status(
        f"Uploaded {len(jars)} jar(s) to GitHub on temporary branch {dispatch_branch} "
        f"(base: {github_base_branch}) for remote decompilation in {github_repo}."
    )
    return dispatch_branch, remote_jar_paths


def prepare_remote_publish_inputs(
    *,
    bundle_dirs: list[Path],
    output_dir: Path,
    github_token: str,
    github_repo: str,
    github_base_branch: str,
) -> tuple[str, str]:
    ensure_command_available("gh")
    ensure_command_available("git")

    session_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]
    dispatch_branch = f"auto-create-modrinth-publish-{session_id}"
    relative_output_dir = relative_to_repo_root(output_dir)

    with tempfile.TemporaryDirectory(prefix="auto-create-remote-publish-branch-") as temp_dir:
        worktree_dir = Path(temp_dir) / "worktree"
        run_subprocess(["git", "worktree", "add", "--detach", str(worktree_dir), "HEAD"])
        try:
            run_subprocess(["git", "checkout", "-b", dispatch_branch], cwd=worktree_dir)

            staged_paths: list[Path] = []
            for bundle_dir in bundle_dirs:
                relative_bundle = relative_to_repo_root(bundle_dir)
                destination = worktree_dir / relative_bundle
                copy_tree(bundle_dir, destination)
                staged_paths.append(relative_bundle)

            for runtime_path in remote_publish_runtime_paths():
                destination = worktree_dir / runtime_path
                destination.parent.mkdir(parents=True, exist_ok=True)
                copy_file(Path.cwd() / runtime_path, destination)
                staged_paths.append(runtime_path)

            run_subprocess(["git", "add", "--"] + [str(path) for path in staged_paths], cwd=worktree_dir)
            staged_output = run_subprocess(
                ["git", "diff", "--cached", "--name-only", "--"] + [str(path) for path in staged_paths],
                cwd=worktree_dir,
            )
            if not staged_output.strip():
                raise ModCompilerError("Failed to stage the bundle files for remote publish.")

            commit_message = f"Add auto-create publish bundle ({session_id})"
            run_subprocess(
                [
                    "git",
                    "-c",
                    "user.name=Codex Auto Create",
                    "-c",
                    "user.email=codex-auto-create@example.com",
                    "commit",
                    "-m",
                    commit_message,
                    "--",
                    *[str(path) for path in staged_paths],
                ],
                cwd=worktree_dir,
            )
            push_branch_with_token(
                branch=dispatch_branch,
                github_token=github_token,
                cwd=worktree_dir,
            )
        finally:
            try:
                run_subprocess(["git", "worktree", "remove", "--force", str(worktree_dir)])
            except ModCompilerError:
                pass

    _print_status(
        f"Uploaded {len(bundle_dirs)} bundle(s) to GitHub on temporary branch {dispatch_branch} "
        f"(base: {github_base_branch}) for remote Modrinth publish in {github_repo}."
    )
    return dispatch_branch, str(relative_output_dir).replace(os.sep, "/")


def remote_publish_runtime_paths() -> list[Path]:
    required = [
        Path(".github/workflows/publish-auto-create-modrinth.yml"),
        Path("scripts/auto_create_modrinth_page_and_publish_mod.py"),
        Path("modcompiler/auto_create_modrinth.py"),
    ]
    missing = [path for path in required if not (Path.cwd() / path).exists()]
    if missing:
        raise ModCompilerError(
            "Remote publish is missing required runtime files: "
            + ", ".join(str(path) for path in missing)
        )
    return required


def remote_decompile_jar_via_github_actions(
    *,
    jar_path: Path,
    decompiled_dir: Path,
    options: GenerateOptions,
) -> dict[str, Any]:
    remote_jar_path = options.remote_jar_paths.get(str(jar_path.resolve()), "").strip()
    if not remote_jar_path:
        raise ModCompilerError(f"No remote decompile path was prepared for {jar_path}")

    _print_status(f"Dispatching GitHub decompile workflow for {jar_path.name}...")
    run_id = dispatch_remote_decompile_run(
        github_repo=options.github_repo,
        github_branch=options.github_branch,
        github_token=options.github_token,
        remote_jar_path=remote_jar_path,
    )
    run_info = wait_for_remote_run_completion(
        github_repo=options.github_repo,
        github_token=options.github_token,
        run_id=run_id,
    )
    with tempfile.TemporaryDirectory(prefix="auto-create-remote-artifact-") as temp_dir:
        artifact_root = download_remote_decompile_artifact(
            github_repo=options.github_repo,
            github_token=options.github_token,
            run_id=run_id,
            download_root=Path(temp_dir) / "artifact-download",
        )
        result_path = find_first_file_named(artifact_root, "result.json")
        if result_path is None:
            raise ModCompilerError(
                f"GitHub decompile run {run_id} completed but no result.json was found in the artifact."
            )

        metadata = load_json(result_path)
        metadata["github_run_id"] = run_id
        metadata["github_run_url"] = str(run_info.get("url", "") or "")
        metadata["github_remote_jar_path"] = remote_jar_path

        if str(metadata.get("status", "")).strip().lower() != "success":
            warnings = metadata.get("warnings", []) or []
            warning_text = " | ".join(str(item) for item in warnings if str(item).strip())
            if not warning_text and run_info.get("conclusion"):
                warning_text = f"Workflow conclusion: {run_info['conclusion']}"
            raise ModCompilerError(
                f"GitHub decompile failed for {jar_path.name}. {warning_text or 'See the GitHub workflow run for details.'}"
            )

        package_zip = find_first_zip_file(artifact_root)
        if package_zip is None:
            raise ModCompilerError(
                f"GitHub decompile for {jar_path.name} succeeded but no packaged source zip was found in the artifact."
            )

        safe_rmtree(decompiled_dir)
        decompiled_dir.mkdir(parents=True, exist_ok=True)

        extracted_root = Path(temp_dir) / "extracted"
        extracted_root.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(package_zip) as archive:
            archive.extractall(extracted_root)

        src_root = find_first_dir_named(extracted_root, "src")
        if src_root is None:
            raise ModCompilerError(
                f"GitHub decompile for {jar_path.name} succeeded but the packaged zip did not contain src/."
            )
        copy_tree(src_root, decompiled_dir / "src")

        mod_info_path = find_first_file_named(artifact_root, "mod_info.txt")
        if mod_info_path is not None:
            copy_file(mod_info_path, decompiled_dir / "mod_info.txt")

        return metadata


def publish_bundle_via_github_actions(
    *,
    bundle_dir: Path,
    output_dir: Path,
    remote_output_dir: str,
    github_repo: str,
    github_branch: str,
    github_token: str,
) -> dict[str, Any]:
    bundle_slug = bundle_dir.name
    verify_path = bundle_dir / "verify.txt"
    state_path = bundle_dir / "publish_state.json"
    project_path = bundle_dir / "modrinth.project.json"
    version_path = bundle_dir / "modrinth.version.json"

    result = {
        "bundle": bundle_slug,
        "bundle_slug": bundle_slug,
        "status": "skipped",
        "note": "",
        "project_id": "",
        "project_slug": "",
        "version_id": "",
    }

    if not project_path.exists() or not version_path.exists():
        result["status"] = "failed"
        result["note"] = "Bundle is missing modrinth.project.json or modrinth.version.json."
        return result

    if not is_bundle_verified(verify_path):
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        if state_path.exists():
            publish_state = load_json(state_path)
            publish_state["status"] = "ready_for_verification"
            publish_state["verified"] = False
            write_json(state_path, publish_state)
        return result

    validate_project_payload(load_json(project_path), project_path)
    validate_version_payload(load_json(version_path), version_path)

    _print_status(f"Dispatching GitHub publish workflow for {bundle_slug}...")
    run_id = dispatch_remote_publish_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        remote_output_dir=remote_output_dir,
        bundle_slug=bundle_slug,
    )
    run_info = wait_for_remote_run_completion(
        github_repo=github_repo,
        github_token=github_token,
        run_id=run_id,
    )

    with tempfile.TemporaryDirectory(prefix="auto-create-remote-publish-artifact-") as temp_dir:
        artifact_root = download_run_artifact(
            github_repo=github_repo,
            github_token=github_token,
            run_id=run_id,
            artifact_name=REMOTE_PUBLISH_ARTIFACT_NAME,
            download_root=Path(temp_dir) / "artifact-download",
        )
        remote_bundle_dir = find_first_dir_named(artifact_root, bundle_slug)
        if remote_bundle_dir is None:
            raise ModCompilerError(
                f"GitHub publish run {run_id} completed but no updated bundle directory for {bundle_slug} was found."
            )
        copy_tree(remote_bundle_dir, bundle_dir)

    publish_state = load_json(state_path) if state_path.exists() else {}
    result["status"] = str(publish_state.get("status", "") or "failed")
    result["project_id"] = str(publish_state.get("project_id", "") or "")
    result["project_slug"] = str(publish_state.get("project_slug", "") or "")
    result["version_id"] = str(publish_state.get("version_id", "") or "")

    if result["status"] == "published":
        result["note"] = f"Published via GitHub workflow run {run_id}."
        return result
    if result["status"] == "ready_for_verification":
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        return result

    run_url = str(run_info.get("url", "") or "")
    state_error = str(publish_state.get("last_error", "") or "").strip()
    conclusion = str(run_info.get("conclusion", "") or "").strip()
    detail = state_error or (f"GitHub workflow conclusion: {conclusion}" if conclusion else "Remote publish failed.")
    if run_url:
        detail = f"{detail} Run: {run_url}"
    result["status"] = "failed"
    result["note"] = detail
    return result


def dispatch_remote_decompile_run(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    remote_jar_path: str,
) -> int:
    return dispatch_workflow_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        workflow_id=REMOTE_DECOMPILE_WORKFLOW_ID,
        fields={"jar_path": remote_jar_path},
        not_found_message=(
            f"GitHub accepted the Jar Decompile dispatch for {remote_jar_path}, but no new run could be located."
        ),
    )


def dispatch_remote_publish_run(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    remote_output_dir: str,
    bundle_slug: str,
) -> int:
    return dispatch_workflow_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        workflow_id=REMOTE_PUBLISH_WORKFLOW_ID,
        fields={
            "output_dir": remote_output_dir,
            "bundle_slug": bundle_slug,
        },
        not_found_message=(
            f"GitHub accepted the publish dispatch for {bundle_slug}, but no new run could be located."
        ),
    )


def list_remote_decompile_runs(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
) -> list[dict[str, Any]]:
    return list_workflow_runs(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        workflow_id=REMOTE_DECOMPILE_WORKFLOW_ID,
    )


def dispatch_workflow_run(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    workflow_id: str,
    fields: dict[str, str],
    not_found_message: str,
) -> int:
    with REMOTE_DISPATCH_LOCK:
        before_ids = {
            int(item["databaseId"])
            for item in list_workflow_runs(
                github_repo=github_repo,
                github_branch=github_branch,
                github_token=github_token,
                workflow_id=workflow_id,
            )
        }
        command = [
            "gh",
            "workflow",
            "run",
            workflow_id,
            "-R",
            github_repo,
            "--ref",
            github_branch,
        ]
        for key, value in fields.items():
            command.extend(["-f", f"{key}={value}"])
        run_subprocess(command, env=github_cli_env(github_token))

        deadline = time.time() + 180
        while time.time() < deadline:
            for item in list_workflow_runs(
                github_repo=github_repo,
                github_branch=github_branch,
                github_token=github_token,
                workflow_id=workflow_id,
            ):
                run_id = int(item["databaseId"])
                if run_id in before_ids or run_id in CLAIMED_REMOTE_RUN_IDS:
                    continue
                CLAIMED_REMOTE_RUN_IDS.add(run_id)
                return run_id
            time.sleep(5)

    raise ModCompilerError(not_found_message)


def list_workflow_runs(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    workflow_id: str,
) -> list[dict[str, Any]]:
    try:
        output = run_subprocess(
            [
                "gh",
                "run",
                "list",
                "-R",
                github_repo,
                "-w",
                workflow_id,
                "-b",
                github_branch,
                "-e",
                "workflow_dispatch",
                "--json",
                "databaseId,status,conclusion,createdAt,url,headBranch,workflowName,displayTitle",
                "-L",
                "20",
            ],
            env=github_cli_env(github_token),
        )
    except ModCompilerError as error:
        text = str(error)
        if "not found on the default branch" in text:
            raise ModCompilerError(
                f"GitHub cannot manually dispatch `{workflow_id}` yet because that workflow file is not on the default branch.\n"
                "GitHub's `workflow_dispatch` only works for workflows that already exist on the repository's default branch.\n"
                f"Next step: commit and push `{Path('.github/workflows') / workflow_id}` to `main`, then run `publish` again.\n"
                "If you do not want to commit that workflow yet, the other option is to publish locally with `--modrinth-token`."
            ) from None
        raise
    parsed = json.loads(output or "[]")
    return parsed if isinstance(parsed, list) else []


def wait_for_remote_run_completion(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    timeout_seconds: int = 3600,
) -> dict[str, Any]:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        output = run_subprocess(
            [
                "gh",
                "run",
                "view",
                str(run_id),
                "-R",
                github_repo,
                "--json",
                "status,conclusion,url,workflowName",
            ],
            env=github_cli_env(github_token),
        )
        parsed = json.loads(output or "{}")
        if isinstance(parsed, dict) and parsed.get("status") == "completed":
            return parsed
        time.sleep(10)

    raise ModCompilerError(f"Timed out waiting for GitHub run {run_id} to finish.")


def download_remote_decompile_artifact(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    download_root: Path,
) -> Path:
    return download_run_artifact(
        github_repo=github_repo,
        github_token=github_token,
        run_id=run_id,
        artifact_name=REMOTE_DECOMPILE_ARTIFACT_NAME,
        download_root=download_root,
    )


def download_run_artifact(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    artifact_name: str,
    download_root: Path,
) -> Path:
    safe_rmtree(download_root)
    download_root.mkdir(parents=True, exist_ok=True)

    last_error = ""
    for _attempt in range(5):
        try:
            run_subprocess(
                [
                    "gh",
                    "run",
                    "download",
                    str(run_id),
                    "-R",
                    github_repo,
                    "-n",
                    artifact_name,
                    "-D",
                    str(download_root),
                ],
                env=github_cli_env(github_token),
            )
            break
        except ModCompilerError as error:
            last_error = str(error)
            time.sleep(3)
    else:
        raise ModCompilerError(
            f"Failed to download the {artifact_name} artifact for run {run_id}. {last_error}"
        )

    return download_root


def ensure_command_available(command_name: str) -> None:
    try:
        run_subprocess([command_name, "--version"])
    except ModCompilerError as error:
        raise ModCompilerError(f"Required command '{command_name}' is not available: {error}") from None


def github_cli_env(github_token: str) -> dict[str, str]:
    env = os.environ.copy()
    env["GH_TOKEN"] = github_token
    env["GITHUB_TOKEN"] = github_token
    return env


def push_branch_with_token(*, branch: str, github_token: str, cwd: Path | None = None) -> None:
    basic_value = base64.b64encode(f"x-access-token:{github_token}".encode("utf-8")).decode("ascii")
    try:
        run_subprocess(
            [
                "git",
                "-c",
                f"http.https://github.com/.extraheader=AUTHORIZATION: basic {basic_value}",
                "push",
                "origin",
                f"HEAD:refs/heads/{branch}",
            ],
            cwd=cwd,
        )
    except ModCompilerError as error:
        text = str(error)
        if "Permission to" in text and "403" in text:
            raise ModCompilerError(
                "GitHub rejected the push with HTTP 403. "
                "Your token is valid for gh auth, but it does not have enough repository write access for git push.\n"
                "If this is a fine-grained PAT, grant it:\n"
                "- Repository access to this repo\n"
                "- Contents: Read and write\n"
                "- Actions: Read and write (or write)\n"
                "If this is a classic PAT, it usually needs:\n"
                "- repo\n"
                "- workflow\n"
                f"\nOriginal error:\n{text}"
            ) from None
        if "[rejected]" in text and "fetch first" in text:
            raise ModCompilerError(
                "Git rejected the push because the target branch already exists and has moved ahead. "
                "This should be rare for the temporary decompile branch path. "
                "Retry the command so the script can generate a fresh temporary branch name.\n"
                f"\nOriginal error:\n{text}"
            ) from None
        raise


def run_subprocess(
    args: list[str],
    *,
    cwd: Path | None = None,
    env: dict[str, str] | None = None,
) -> str:
    try:
        completed = subprocess.run(
            args,
            cwd=str(cwd) if cwd else None,
            env=env,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
    except subprocess.CalledProcessError as error:
        stderr = (error.stderr or "").strip()
        stdout = (error.stdout or "").strip()
        detail = stderr or stdout or f"exit code {error.returncode}"
        raise ModCompilerError(f"Command failed: {' '.join(args)}\n{detail}") from None
    return completed.stdout


def find_first_file_named(root: Path, filename: str) -> Path | None:
    for path in sorted(root.rglob(filename)):
        if path.is_file():
            return path
    return None


def find_first_zip_file(root: Path) -> Path | None:
    for path in sorted(root.rglob("*.zip")):
        if path.is_file():
            return path
    return None


def find_first_dir_named(root: Path, dirname: str) -> Path | None:
    for path in sorted(root.rglob(dirname)):
        if path.is_dir():
            return path
    return None


def generate_bundle_for_jar(jar_path: Path, options: GenerateOptions) -> dict[str, Any]:
    bundle_slug = slugify_text(jar_path.stem) or slugify_text(jar_path.name) or f"bundle-{uuid.uuid4().hex[:8]}"
    bundle_dir = options.output_dir / bundle_slug

    if bundle_dir.exists() and not options.force:
        _print_status(
            colorize(
                "yellow",
                f"Skipped {jar_path.name}: bundle already exists at {bundle_dir}. Use --force to regenerate.",
            )
        )
        return {
            "jar_name": jar_path.name,
            "bundle_slug": bundle_slug,
            "bundle_dir": str(bundle_dir),
            "status": "skipped",
            "error": "",
        }

    safe_rmtree(bundle_dir)
    bundle_dir.mkdir(parents=True, exist_ok=True)

    input_dir = bundle_dir / "input"
    decompiled_dir = bundle_dir / "decompiled"
    jar_copy_path = input_dir / jar_path.name
    input_dir.mkdir(parents=True, exist_ok=True)
    copy_file(jar_path, jar_copy_path)

    ai_response_text = ""
    prompt_text = ""
    metadata = inspect_mod_jar(jar_path, options.manifest)
    try:
        metadata = remote_decompile_jar_via_github_actions(
            jar_path=jar_path,
            decompiled_dir=decompiled_dir,
            options=options,
        )

        projectinfo_text = build_projectinfo_text(
            jar_path=jar_path,
            metadata=metadata,
            decompiled_dir=decompiled_dir,
        )
        (bundle_dir / "projectinfo.txt").write_text(projectinfo_text, encoding="utf-8")

        prompt_text = build_ai_user_message(
            projectinfo_text=projectinfo_text,
            prompt_projectinfo_char_limit=options.prompt_projectinfo_char_limit,
            template_examples=options.template_examples,
        )
        (bundle_dir / "ai_request_user_message.txt").write_text(prompt_text, encoding="utf-8")

        ai_response_text = stream_local_c05_chat(
            base_url=options.c05_url,
            hoster=options.c05_hoster,
            model=options.c05_model,
            user_prompt=prompt_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=options.max_tokens,
            timeout_seconds=options.timeout_seconds,
            session_id=f"auto-create-{bundle_slug}-{uuid.uuid4().hex[:8]}",
            app_id="auto-create-modrinth-page-and-publish-mod",
        )
        (bundle_dir / "ai_response.txt").write_text(ai_response_text, encoding="utf-8")

        ai_result = parse_ai_listing_response(ai_response_text)
        listing = finalize_generated_listing(
            listing=ai_result,
            metadata=metadata,
            jar_path=jar_path,
        )
        write_json(bundle_dir / "listing.json", listing)

        bundle_metadata = build_bundle_metadata(
            jar_path=jar_path,
            bundle_slug=bundle_slug,
            metadata=metadata,
            options=options,
            listing=listing,
        )
        write_json(bundle_dir / "bundle_metadata.json", bundle_metadata)

        project_draft = build_project_draft(metadata=metadata, listing=listing, options=options, ai_result=ai_result)
        version_draft = build_version_draft(metadata=metadata, listing=listing, options=options)
        write_json(bundle_dir / "modrinth.project.json", project_draft)
        write_json(bundle_dir / "modrinth.version.json", version_draft)

        verify_path = bundle_dir / "verify.txt"
        verify_path.write_text(render_verify_template(), encoding="utf-8")

        publish_state = {
            "status": "ready_for_verification",
            "verified": False,
            "generated_at": now_iso(),
            "project_id": "",
            "project_slug": "",
            "version_id": "",
            "last_error": "",
        }
        write_json(bundle_dir / "publish_state.json", publish_state)
        (bundle_dir / "SUMMARY.md").write_text(
            render_bundle_summary_markdown(bundle_metadata=bundle_metadata, listing=listing, publish_state=publish_state),
            encoding="utf-8",
        )

        _print_status(
            colorize(
                "green",
                f"Complete: {jar_path.name} -> {bundle_dir} (review verify.txt before publishing)",
            )
        )
        return {
            "jar_name": jar_path.name,
            "bundle_slug": bundle_slug,
            "bundle_dir": str(bundle_dir),
            "status": "ready_for_verification",
            "error": "",
        }
    except Exception as error:
        publish_state = {
            "status": "failed_generation",
            "verified": False,
            "generated_at": now_iso(),
            "project_id": "",
            "project_slug": "",
            "version_id": "",
            "last_error": f"{type(error).__name__}: {error}",
        }
        write_json(bundle_dir / "publish_state.json", publish_state)
        write_json(
            bundle_dir / "bundle_metadata.json",
            build_bundle_metadata(
                jar_path=jar_path,
                bundle_slug=bundle_slug,
                metadata=metadata,
                options=options,
                listing=None,
            ),
        )
        if prompt_text and not (bundle_dir / "ai_request_user_message.txt").exists():
            (bundle_dir / "ai_request_user_message.txt").write_text(prompt_text, encoding="utf-8")
        if ai_response_text and not (bundle_dir / "ai_response.txt").exists():
            (bundle_dir / "ai_response.txt").write_text(ai_response_text, encoding="utf-8")
        (bundle_dir / "error.txt").write_text(
            f"{type(error).__name__}: {error}\n\n{traceback.format_exc()}",
            encoding="utf-8",
        )
        if not (bundle_dir / "projectinfo.txt").exists():
            (bundle_dir / "projectinfo.txt").write_text(
                build_projectinfo_text(
                    jar_path=jar_path,
                    metadata=metadata,
                    decompiled_dir=decompiled_dir if decompiled_dir.exists() else None,
                ),
                encoding="utf-8",
            )
        (bundle_dir / "SUMMARY.md").write_text(
            render_failed_bundle_summary_markdown(
                jar_name=jar_path.name,
                bundle_dir=bundle_dir,
                error=f"{type(error).__name__}: {error}",
            ),
            encoding="utf-8",
        )
        _print_status(colorize("red", f"Failed: {jar_path.name} -> {error}"))
        return {
            "jar_name": jar_path.name,
            "bundle_slug": bundle_slug,
            "bundle_dir": str(bundle_dir),
            "status": "failed",
            "error": f"{type(error).__name__}: {error}",
        }


def publish_bundle(*, bundle_dir: Path, client: ModrinthClient, dry_run: bool) -> dict[str, Any]:
    metadata_path = bundle_dir / "bundle_metadata.json"
    project_path = bundle_dir / "modrinth.project.json"
    version_path = bundle_dir / "modrinth.version.json"
    state_path = bundle_dir / "publish_state.json"
    verify_path = bundle_dir / "verify.txt"

    result = {
        "bundle": bundle_dir.name,
        "bundle_dir": str(bundle_dir),
        "status": "skipped",
        "note": "",
        "project_id": "",
        "project_slug": "",
        "version_id": "",
    }

    if not metadata_path.exists():
        result["status"] = "failed"
        result["note"] = "bundle_metadata.json is missing."
        return result
    if not project_path.exists() or not version_path.exists():
        result["status"] = "failed"
        result["note"] = "Modrinth draft files are missing."
        return result
    if not state_path.exists():
        result["status"] = "failed"
        result["note"] = "publish_state.json is missing."
        return result

    bundle_metadata = load_json(metadata_path)
    publish_state = load_json(state_path)
    result["project_id"] = str(publish_state.get("project_id", "") or "")
    result["project_slug"] = str(publish_state.get("project_slug", "") or "")
    result["version_id"] = str(publish_state.get("version_id", "") or "")

    if not is_bundle_verified(verify_path):
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        return result

    if publish_state.get("version_id"):
        result["status"] = "skipped"
        result["note"] = f"Version already uploaded as {publish_state['version_id']}."
        return result

    project_payload = clean_modrinth_payload(load_json(project_path))
    version_payload = clean_modrinth_payload(load_json(version_path))
    validate_project_payload(project_payload, project_path)
    validate_version_payload(version_payload, version_path)

    jar_name = str(bundle_metadata.get("jar_name", "")).strip()
    jar_path = bundle_dir / "input" / jar_name
    if not jar_name or not jar_path.exists():
        result["status"] = "failed"
        result["note"] = f"Input jar is missing: {jar_path}"
        return result

    project_id = str(version_payload.get("project_id", "") or publish_state.get("project_id", "")).strip()
    project_slug = str(project_payload.get("slug", "")).strip()

    try:
        if not project_id:
            if dry_run:
                result["status"] = "dry_run"
                result["note"] = "Would create project, then upload version."
                return result

            icon_path = find_bundle_icon(bundle_dir)
            project_response = create_modrinth_project(
                client=client,
                payload=project_payload,
                icon_path=icon_path,
            )
            project_id = str(project_response.get("id", "")).strip()
            project_slug = str(project_response.get("slug", "")).strip() or project_slug
            if not project_id:
                raise ModCompilerError("Modrinth returned a project response without an id.")
            publish_state["project_id"] = project_id
            publish_state["project_slug"] = project_slug
            result["project_id"] = project_id
            result["project_slug"] = project_slug
            write_json(state_path, publish_state)

        version_payload["project_id"] = project_id

        existing = None
        loaders = version_payload.get("loaders") or []
        game_versions = version_payload.get("game_versions") or []
        version_number = str(version_payload.get("version_number", "")).strip()
        if loaders and game_versions and version_number:
            try:
                existing = client.find_existing_version(
                    project_id=project_id,
                    loader=str(loaders[0]),
                    minecraft_version=str(game_versions[0]),
                    version_number=version_number,
                    jar_name=jar_path.name,
                )
            except ModCompilerError:
                existing = None
        if existing is not None:
            publish_state["status"] = "published"
            publish_state["verified"] = True
            publish_state["version_id"] = existing.get("id", "")
            publish_state["last_error"] = ""
            write_json(state_path, publish_state)
            result["status"] = "skipped"
            result["version_id"] = existing.get("id", "")
            result["note"] = f"Version already exists on Modrinth as {existing.get('id', '-')}"
            return result

        if dry_run:
            result["status"] = "dry_run"
            result["note"] = "Would upload version to the verified bundle's Modrinth project."
            result["project_id"] = project_id
            result["project_slug"] = project_slug
            return result

        created_version = client.create_version(payload=version_payload, jar_path=jar_path)
        version_id = str(created_version.get("id", "")).strip()
        publish_state["status"] = "published"
        publish_state["verified"] = True
        publish_state["version_id"] = version_id
        publish_state["last_error"] = ""
        write_json(state_path, publish_state)

        result["status"] = "published"
        result["note"] = "Project created/uploaded successfully." if not result["project_id"] else "Version uploaded."
        result["project_id"] = project_id
        result["project_slug"] = project_slug
        result["version_id"] = version_id
        return result
    except Exception as error:
        publish_state["status"] = "publish_failed"
        publish_state["verified"] = True
        publish_state["last_error"] = f"{type(error).__name__}: {error}"
        write_json(state_path, publish_state)
        result["status"] = "failed"
        result["note"] = f"{type(error).__name__}: {error}"
        result["project_id"] = project_id
        result["project_slug"] = project_slug
        return result


def build_projectinfo_text(
    *,
    jar_path: Path,
    metadata: dict[str, Any],
    decompiled_dir: Path | None,
) -> str:
    details = metadata.get("metadata", {})
    source_roots = resolve_decompiled_source_roots(decompiled_dir)
    source_files = list_source_files(source_roots)
    packages = sorted({extract_package_name(path) for path in source_files if extract_package_name(path)})

    lines = [
        "# Project Info",
        "",
        f"generated_at={now_iso()}",
        f"jar_absolute_path={jar_path.resolve()}",
        f"jar_size_bytes={jar_path.stat().st_size if jar_path.exists() else 0}",
        f"jar_sha1={sha1_file(jar_path) if jar_path.exists() else ''}",
        "",
        "## Detected Metadata",
        "",
        *render_projectinfo_metadata_lines(metadata),
        "",
        "## Source Summary",
        "",
    ]

    if len(source_roots) == 1:
        lines.append(f"source_root={source_roots[0].resolve()}")
    else:
        lines.append(
            f"source_roots={','.join(str(path.resolve()) for path in source_roots) if source_roots else 'missing'}"
        )
    lines.extend(
        [
            f"source_file_count={len(source_files)}",
            f"package_count={len(packages)}",
            f"packages={','.join(packages)}",
            "",
            "## Source Tree",
            "",
        ]
    )

    if source_roots:
        tree_lines = [source_path_label(path, source_roots) for path in source_files]
        lines.extend(tree_lines[:MAX_PROJECTINFO_TREE_LINES] or ["(no files found under source root)"])
        if len(tree_lines) > MAX_PROJECTINFO_TREE_LINES:
            lines.append(f"... truncated {len(tree_lines) - MAX_PROJECTINFO_TREE_LINES} additional paths ...")
    else:
        lines.append("(decompiled sources are missing)")

    lines.extend(["", "## Key Source Excerpts", ""])
    excerpt_paths = select_interesting_source_files(source_roots, source_files, metadata)
    if not excerpt_paths:
        lines.append("(no source excerpts available)")
    else:
        for source_path in excerpt_paths:
            excerpt_text = build_source_excerpt(
                source_path,
                max_lines=PROJECTINFO_EXCERPT_MAX_LINES,
                max_chars=PROJECTINFO_EXCERPT_MAX_CHARS,
            )
            lines.append(f"### {source_path_label(source_path, source_roots)}")
            lines.append("```")
            lines.append(excerpt_text.rstrip())
            lines.append("```")
            lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def build_ai_user_message(
    *,
    projectinfo_text: str,
    prompt_projectinfo_char_limit: int,
    template_examples: dict[str, str] | None = None,
) -> str:
    trimmed_projectinfo = truncate_text_block(projectinfo_text, prompt_projectinfo_char_limit)
    template = json.dumps(
        {
            "name": "Short memorable mod name",
            "short_description": "One accurate sentence under 96 characters.",
            "long_description": "Player-facing Markdown that matches the perfect template style.",
            "categories": ["utility"],
            "additional_categories": [],
            "client_side": "optional",
            "server_side": "required",
            "license_id": "",
            "license_url": "",
        },
        indent=2,
    )
    template_examples = template_examples or load_template_examples()
    categories_list = ", ".join(sorted(set(MODRINTH_CATEGORY_ALIASES.values())))
    license_list = ", ".join(item or "empty-string-when-unknown" for item in AI_LICENSE_CHOICES)

    prompt = f"""
Generate Modrinth listing copy for one Minecraft mod.

Use only the facts found in PROJECT INFO below.
Do not invent features, commands, compatibility, authors, loaders, game versions, or licensing facts.
Use the perfect template examples below for tone, quality, and formatting only.
Do not copy the example mod's features unless PROJECT INFO proves them.
Lead with the main player-facing feature, not the internal implementation.
If the jar metadata already contains a solid name, keep it very close to that name.
Keep the short description to one accurate sentence under 96 characters.
Make the long description feel like a polished manual Modrinth page.
You may use Markdown headings and bullet lists when they improve clarity.
Mention secondary features only if they are clearly present and worth surfacing.
Avoid implementation details like reflection, event hooks, custom damage sources,
cooldown fields, package names, class names, or internal APIs.
Avoid filler compatibility lines unless PROJECT INFO makes them unusually important.
Choose categories only from this list: {categories_list}
Choose client_side and server_side only from: required, optional, unsupported, unknown
Choose license_id only from this list when PROJECT INFO clearly supports it: {license_list}
If the license is custom and PROJECT INFO provides a license link, return an empty license_id and include the license_url.
If the license is all-rights-reserved or unclear, return an empty license_id and license_url. The script will apply a safe fallback.
Return only valid JSON that matches this template exactly:

{template}

PERFECT TEMPLATE EXAMPLES

modname.txt
{template_examples["modname"]}

summary.txt
{template_examples["summary"]}

description.txt
{template_examples["description"]}

PROJECT INFO:
{trimmed_projectinfo}
"""
    return textwrap.dedent(prompt).strip() + "\n"


def parse_ai_listing_response(raw_text: str) -> dict[str, Any]:
    data = extract_json_object(raw_text)
    name = normalize_single_line(data.get("name") or data.get("title") or "")
    short_description = normalize_single_line(
        data.get("short_description")
        or data.get("shortDescription")
        or data.get("description")
        or ""
    )
    long_description = str(
        data.get("long_description")
        or data.get("longDescription")
        or data.get("body")
        or ""
    ).strip()

    if not name:
        raise ModCompilerError("AI response did not contain a usable 'name'.")
    if not short_description:
        raise ModCompilerError("AI response did not contain a usable 'short_description'.")
    if not long_description:
        raise ModCompilerError("AI response did not contain a usable 'long_description'.")

    return {
        "name": name,
        "short_description": short_description,
        "long_description": long_description,
        "categories": normalize_category_values(data.get("categories")),
        "additional_categories": normalize_category_values(data.get("additional_categories")),
        "client_side": normalize_side_hint(data.get("client_side")),
        "server_side": normalize_side_hint(data.get("server_side")),
        "license_id": normalize_ai_license_id(data.get("license_id")),
        "license_url": normalize_single_line(data.get("license_url") or ""),
    }


def finalize_generated_listing(
    *,
    listing: dict[str, str],
    metadata: dict[str, Any],
    jar_path: Path,
) -> dict[str, str]:
    details = metadata.get("metadata", {})
    detected_name = normalize_single_line(details.get("name") or "")
    fallback_name = humanize_name(str(details.get("mod_id") or jar_path.stem))
    short_description = normalize_short_description(
        listing.get("short_description", ""),
        fallback=details.get("description") or "",
    )
    long_description = normalize_long_description(
        listing.get("long_description", ""),
        short_description=short_description,
    )

    name = normalize_single_line(listing.get("name") or "")
    if not name:
        name = detected_name or fallback_name
    elif detected_name and comparable_text(name) == comparable_text(detected_name):
        name = name or detected_name

    return {
        "name": name or fallback_name or jar_path.stem,
        "short_description": short_description,
        "long_description": long_description,
    }


def extract_json_object(raw_text: str) -> dict[str, Any]:
    stripped = str(raw_text or "").strip()
    if not stripped:
        raise ModCompilerError("AI response was empty.")

    for candidate in json_candidates_from_text(stripped):
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            return parsed

    raise ModCompilerError(
        "Could not parse a JSON object from the AI response. "
        "Check ai_response.txt in the bundle for the raw model output."
    )


def render_projectinfo_metadata_lines(metadata: dict[str, Any]) -> list[str]:
    details = metadata.get("metadata", {})
    warnings = [normalize_single_line(item) for item in metadata.get("warnings", []) if normalize_single_line(item)]
    values: list[tuple[str, str]] = [
        ("jar_name", normalize_single_line(metadata.get("jar_name"))),
        ("loader", normalize_single_line(metadata.get("loader"))),
        ("supported_minecraft", normalize_single_line(metadata.get("supported_minecraft"))),
        ("primary_mod_id", normalize_single_line(metadata.get("primary_mod_id"))),
        ("name", normalize_single_line(details.get("name"))),
        ("mod_version", normalize_single_line(details.get("mod_version"))),
        ("description", projectinfo_value(details.get("description"))),
        ("authors", ",".join(str(item).strip() for item in details.get("authors", []) if str(item).strip())),
        ("license", normalize_single_line(details.get("license"))),
    ]

    optional_values = [
        ("homepage", normalize_single_line(details.get("homepage"))),
        ("sources", normalize_single_line(details.get("sources"))),
        ("issues", normalize_single_line(details.get("issues"))),
        ("entrypoint_class", normalize_single_line(details.get("entrypoint_class"))),
        ("group", normalize_single_line(details.get("group"))),
        ("warnings", " | ".join(warnings)),
    ]
    values.extend((key, value) for key, value in optional_values if value)
    return [f"{key}={value}" for key, value in values]


def projectinfo_value(value: Any) -> str:
    return normalize_single_line(str(value or "").replace("\n", "\\n"))


def normalize_short_description(raw_text: str, *, fallback: str) -> str:
    text = normalize_single_line(raw_text)
    if not text:
        text = normalize_single_line(fallback) or "A Minecraft mod."
    if len(text) <= 96:
        return text

    sentences = split_sentences(text)
    for sentence in sentences:
        normalized = normalize_single_line(sentence)
        if normalized and len(normalized) <= 96:
            return normalized

    for divider in (". ", "; ", " - ", ", "):
        head = text.split(divider, 1)[0].strip()
        if head and len(head) <= 96:
            return head.rstrip(".,;:") + "."
    return text[:93].rstrip() + "..."


def normalize_long_description(raw_text: str, *, short_description: str) -> str:
    cleaned = str(raw_text or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not cleaned:
        return ensure_terminal_punctuation(short_description)

    short_key = comparable_text(short_description)
    blocks = [block for block in re.split(r"\n\s*\n", cleaned) if block.strip()]
    kept_blocks: list[str] = []

    for block in blocks:
        raw_lines = [line.rstrip() for line in block.splitlines() if line.strip()]
        kept_lines: list[str] = []
        for line in raw_lines:
            stripped_markdown = strip_markdown_prefix(line)
            comparable = comparable_text(stripped_markdown)
            if comparable and comparable == short_key:
                continue
            if line.lstrip().startswith(("#", "-", "*")):
                if is_overly_technical_sentence(stripped_markdown) or is_generic_compatibility_sentence(stripped_markdown):
                    continue
                kept_lines.append(line.strip())
                continue

            sentences = split_sentences(stripped_markdown)
            filtered = [
                ensure_terminal_punctuation(sentence)
                for sentence in sentences
                if comparable_text(sentence) != short_key
                and not is_overly_technical_sentence(sentence)
                and not is_generic_compatibility_sentence(sentence)
            ]
            if filtered:
                kept_lines.append(" ".join(filtered))

        if kept_lines:
            kept_blocks.append("\n".join(kept_lines))

    if not kept_blocks:
        return ensure_terminal_punctuation(short_description)

    result = "\n\n".join(kept_blocks[:8]).strip()
    if len(result) > 2_500:
        result = result[:2_497].rstrip() + "..."
    return result


def split_sentences(text: str) -> list[str]:
    normalized = normalize_single_line(text)
    if not normalized:
        return []
    parts = re.split(r"(?<=[.!?])\s+(?=[A-Z0-9])", normalized)
    return [part.strip() for part in parts if part.strip()]


def is_overly_technical_sentence(sentence: str) -> bool:
    lowered = sentence.lower()
    return any(marker in lowered for marker in TECHNICAL_DESCRIPTION_MARKERS)


def is_generic_compatibility_sentence(sentence: str) -> bool:
    lowered = sentence.lower()
    return any(marker in lowered for marker in GENERIC_COMPATIBILITY_MARKERS)


def ensure_terminal_punctuation(text: str) -> str:
    stripped = normalize_single_line(text)
    if not stripped:
        return ""
    if stripped[-1] in ".!?":
        return stripped
    return stripped + "."


def comparable_text(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", str(value or "").lower())


def humanize_name(value: str) -> str:
    text = re.sub(r"[_\-]+", " ", str(value or "")).strip()
    text = re.sub(r"\s+", " ", text)
    return text.title()


def json_candidates_from_text(text: str) -> list[str]:
    candidates: list[str] = [text]
    fenced = re.findall(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.DOTALL | re.IGNORECASE)
    candidates.extend(fenced)

    brace_starts = [match.start() for match in re.finditer(r"\{", text)]
    for start in brace_starts:
        depth = 0
        in_string = False
        escaped = False
        for index in range(start, len(text)):
            char = text[index]
            if escaped:
                escaped = False
                continue
            if char == "\\":
                escaped = True
                continue
            if char == '"':
                in_string = not in_string
                continue
            if in_string:
                continue
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    candidates.append(text[start : index + 1])
                    break
    return candidates


def stream_local_c05_chat(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    reasoning_effort: str,
    temperature: float,
    max_tokens: int,
    timeout_seconds: int,
    session_id: str,
    app_id: str,
) -> str:
    payload: dict[str, Any] = {
        "user_prompt": user_prompt,
        "model": model,
        "hoster": hoster,
        "app_id": app_id,
        "session_id": session_id,
        "include_history": False,
        "reasoning_effort": reasoning_effort,
        "extra_body": {
            "temperature": temperature,
            "max_tokens": max_tokens,
        },
    }
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/chat",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/x-ndjson"},
    )

    chunks: list[str] = []
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            for raw_line in response:
                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                event = json.loads(line)
                if event.get("event") == "content":
                    chunks.append(str(event.get("content", "")))
                    continue
                if event.get("error"):
                    last_error = str(event.get("last_error", "")).strip()
                    detail = str(event["error"]).strip()
                    if last_error:
                        detail = f"{detail} Last error: {last_error}"
                    raise ModCompilerError(f"C05 Local AI stream reported an error: {detail}")
                if event.get("status") == "end":
                    break
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace").strip()
        raise ModCompilerError(f"C05 Local AI request failed with HTTP {error.code}. {detail}") from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Could not reach C05 Local AI at {base_url}: {error.reason}") from None

    text = "".join(chunks).strip()
    if not text:
        raise ModCompilerError("C05 Local AI returned no content for the listing generation request.")
    return text


def build_bundle_metadata(
    *,
    jar_path: Path,
    bundle_slug: str,
    metadata: dict[str, Any],
    options: GenerateOptions,
    listing: dict[str, str] | None,
) -> dict[str, Any]:
    return {
        "bundle_slug": bundle_slug,
        "generated_at": now_iso(),
        "jar_name": jar_path.name,
        "jar_path": str(jar_path.resolve()),
        "loader": metadata.get("loader", ""),
        "supported_minecraft": metadata.get("supported_minecraft", ""),
        "resolved_game_versions": expand_supported_versions(options.manifest, metadata),
        "primary_mod_id": metadata.get("primary_mod_id", ""),
        "metadata": metadata.get("metadata", {}),
        "warnings": list(metadata.get("warnings", [])),
        "local_ai": {
            "url": options.c05_url,
            "hoster": options.c05_hoster,
            "model": options.c05_model,
            "reasoning_effort": options.reasoning_effort,
        },
        "listing": listing or {},
    }


def build_project_draft(
    *,
    metadata: dict[str, Any],
    listing: dict[str, str],
    options: GenerateOptions,
    ai_result: dict[str, Any] | None = None,
) -> dict[str, Any]:
    details = metadata.get("metadata", {})
    ai_result = ai_result or {}
    categories = dedupe_preserve_order(
        options.categories
        or normalize_category_values(ai_result.get("categories"))
        or default_categories_for_loader(str(metadata.get("loader", "")).strip())
    )
    if not categories and metadata.get("loader"):
        categories = [str(metadata["loader"]).strip()]

    detected_license = normalize_single_line(details.get("license") or "")
    license_id = normalize_single_line(options.license_id or detected_license or ai_result.get("license_id") or "")
    license_url = empty_to_none(options.license_url or ai_result.get("license_url"))
    additional_categories = dedupe_preserve_order(
        options.additional_categories or normalize_category_values(ai_result.get("additional_categories"))
    )
    client_side = options.client_side
    server_side = options.server_side
    ai_client_side = normalize_side_hint(ai_result.get("client_side"))
    ai_server_side = normalize_side_hint(ai_result.get("server_side"))
    if client_side == "optional" and ai_client_side:
        client_side = ai_client_side
    if server_side == "optional" and ai_server_side:
        server_side = ai_server_side
    project = {
        "slug": slugify_text(listing["name"]) or slugify_text(details.get("mod_id", "")),
        "title": listing["name"],
        "description": listing["short_description"],
        "categories": categories,
        "client_side": client_side,
        "server_side": server_side,
        "body": listing["long_description"],
        "status": options.project_status,
        "requested_status": None,
        "additional_categories": additional_categories,
        "issues_url": options.issues_url or empty_to_none(details.get("issues")),
        "source_url": options.source_url or empty_to_none(details.get("sources")),
        "wiki_url": options.wiki_url or empty_to_none(details.get("homepage")),
        "discord_url": empty_to_none(options.discord_url),
        "donation_urls": [],
        "license_id": license_id,
        "license_url": license_url,
        "project_type": "mod",
    }
    return clean_modrinth_payload(project, keep_empty_arrays={"donation_urls", "additional_categories"})


def build_version_draft(
    *,
    metadata: dict[str, Any],
    listing: dict[str, str],
    options: GenerateOptions,
) -> dict[str, Any]:
    details = metadata.get("metadata", {})
    game_versions = expand_supported_versions(options.manifest, metadata)
    loader = str(metadata.get("loader", "")).strip()
    version_number = normalize_single_line(details.get("mod_version") or "") or "1.0.0"
    readable_versions = ", ".join(game_versions) if game_versions else (metadata.get("supported_minecraft", "") or "unknown")

    version = {
        "name": f"{listing['name']} {version_number}",
        "version_number": version_number,
        "changelog": "\n".join(
            [
                "Auto-created from a verified local jar bundle.",
                "",
                f"Project: {listing['name']}",
                f"Jar: {metadata.get('jar_name', '') or details.get('mod_id', '')}",
                f"Loader: {loader or 'unknown'}",
                f"Detected versions: {readable_versions}",
            ]
        ),
        "dependencies": [],
        "game_versions": game_versions,
        "version_type": options.version_type,
        "loaders": [loader] if loader else [],
        "featured": False,
        "project_id": "",
        "file_parts": ["file"],
        "status": options.version_status,
    }
    return clean_modrinth_payload(version, keep_empty_arrays={"dependencies"})


def expand_supported_versions(manifest: dict[str, Any], metadata: dict[str, Any]) -> list[str]:
    folders = list(metadata.get("resolved_range_folders", []) or [])
    loader = str(metadata.get("loader", "")).strip()
    versions: list[str] = []
    for folder in folders:
        range_entry = next((entry for entry in manifest.get("ranges", []) if entry.get("folder") == folder), None)
        if not range_entry:
            continue
        loader_entry = (range_entry.get("loaders") or {}).get(loader) or {}
        supported_versions = loader_entry.get("supported_versions")
        if isinstance(supported_versions, list) and supported_versions:
            for version in supported_versions:
                version_text = str(version).strip()
                if version_text:
                    versions.append(version_text)
            continue
        anchor_version = str(loader_entry.get("anchor_version", "")).strip()
        if anchor_version:
            versions.append(anchor_version)
            continue
        min_version = str(range_entry.get("min_version", "")).strip()
        max_version = str(range_entry.get("max_version", "")).strip()
        if min_version and min_version == max_version:
            versions.append(min_version)
        elif max_version:
            versions.append(max_version)

    if versions:
        return dedupe_preserve_order(versions)

    supported_minecraft = str(metadata.get("supported_minecraft", "")).strip()
    if re.fullmatch(r"\d+\.\d+(?:\.\d+)?", supported_minecraft):
        return [supported_minecraft]
    return []


def validate_project_payload(payload: dict[str, Any], path: Path) -> None:
    required_non_empty = ("slug", "title", "description", "categories", "client_side", "server_side", "body", "project_type")
    for key in required_non_empty:
        value = payload.get(key)
        if isinstance(value, list) and value:
            continue
        if isinstance(value, str) and value.strip():
            continue
        raise ModCompilerError(f"{path}: required Modrinth project field '{key}' is missing or empty.")


def validate_version_payload(payload: dict[str, Any], path: Path) -> None:
    required_non_empty = ("name", "version_number", "game_versions", "version_type", "loaders", "file_parts")
    for key in required_non_empty:
        value = payload.get(key)
        if isinstance(value, list) and value:
            continue
        if isinstance(value, str) and value.strip():
            continue
        raise ModCompilerError(f"{path}: required Modrinth version field '{key}' is missing or empty.")


def create_modrinth_project(
    *,
    client: ModrinthClient,
    payload: dict[str, Any],
    icon_path: Path | None,
) -> dict[str, Any]:
    create_payload = build_project_create_payload(payload)
    files: list[tuple[str, str, bytes, str]] = []
    if icon_path is not None:
        files.append(("icon", icon_path.name, icon_path.read_bytes(), guess_content_type(icon_path.name)))
    body, content_type = encode_multipart_form_data(
        fields={"data": json.dumps(create_payload)},
        files=files,
    )
    response = client.request_json(
        "POST",
        "/project",
        body=body,
        extra_headers={"Content-Type": content_type},
    )
    if not isinstance(response, dict):
        raise ModCompilerError("Modrinth returned an invalid project creation response.")
    return response


def build_project_create_payload(payload: dict[str, Any]) -> dict[str, Any]:
    create_payload = dict(payload)
    create_payload["status"] = "draft"
    create_payload.setdefault("initial_versions", [])
    create_payload.setdefault("is_draft", True)
    raw_license_id = normalize_single_line(create_payload.get("license_id", ""))
    raw_license_url = normalize_single_line(create_payload.get("license_url", ""))
    if raw_license_id:
        lowered = raw_license_id.lower()
        if lowered in {"all rights reserved", "all rights reserved/no license", "no license", "arr"}:
            create_payload["license_id"] = DEFAULT_ALL_RIGHTS_RESERVED_LICENSE_ID
        elif lowered == "custom":
            create_payload["license_id"] = DEFAULT_CUSTOM_LICENSE_ID
        else:
            create_payload["license_id"] = raw_license_id
    elif raw_license_url:
        create_payload["license_id"] = DEFAULT_CUSTOM_LICENSE_ID
    else:
        create_payload["license_id"] = DEFAULT_ALL_RIGHTS_RESERVED_LICENSE_ID
    return create_payload


def clean_modrinth_payload(
    payload: dict[str, Any],
    *,
    keep_empty_arrays: set[str] | None = None,
) -> dict[str, Any]:
    keep_empty_arrays = keep_empty_arrays or set()
    cleaned: dict[str, Any] = {}
    for key, value in payload.items():
        if value is None:
            continue
        if isinstance(value, str):
            trimmed = value.strip()
            if not trimmed:
                continue
            cleaned[key] = trimmed
            continue
        if isinstance(value, list):
            items = []
            for item in value:
                if isinstance(item, str):
                    trimmed = item.strip()
                    if trimmed:
                        items.append(trimmed)
                    continue
                if item is None:
                    continue
                items.append(item)
            if items or key in keep_empty_arrays:
                cleaned[key] = items
            continue
        cleaned[key] = value
    return cleaned


def is_bundle_verified(verify_path: Path) -> bool:
    if not verify_path.exists():
        return False
    for raw_line in verify_path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        return line.lower() == "verified"
    return False


def discover_bundle_dirs(output_dir: Path) -> list[Path]:
    return sorted(
        path
        for path in output_dir.iterdir()
        if path.is_dir() and (path / "bundle_metadata.json").exists()
    )


def resolve_decompiled_source_roots(decompiled_dir: Path | None) -> list[Path]:
    if decompiled_dir is None or not decompiled_dir.exists():
        return []

    candidates = [
        decompiled_dir / "java",
        decompiled_dir / "kotlin",
        decompiled_dir / "src" / "main" / "java",
        decompiled_dir / "src" / "main" / "kotlin",
        decompiled_dir / "src" / "client" / "java",
        decompiled_dir / "src" / "client" / "kotlin",
    ]

    roots: list[Path] = []
    seen: set[Path] = set()
    for path in candidates:
        if not path.exists() or not path.is_dir():
            continue
        resolved = path.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        roots.append(path)
    return roots


def list_source_files(source_roots: list[Path]) -> list[Path]:
    source_files: list[Path] = []
    seen: set[Path] = set()
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
                continue
            resolved = path.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            source_files.append(path)
    return source_files


def extract_package_name(source_path: Path) -> str:
    try:
        text = source_path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    match = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;", text, flags=re.MULTILINE)
    return match.group(1).strip() if match else ""


def select_interesting_source_files(
    source_roots: list[Path],
    source_files: list[Path],
    metadata: dict[str, Any],
) -> list[Path]:
    if not source_roots or not source_files:
        return []

    details = metadata.get("metadata", {})
    ranked: list[tuple[int, Path]] = []
    entrypoint = str(details.get("entrypoint_class", "")).strip()
    mod_id = str(details.get("mod_id", "")).strip().lower()

    for path in source_files:
        relative = source_path_label(path, source_roots).lower()
        score = 0
        if entrypoint:
            expected = entrypoint.replace(".", "/").lower()
            if relative.endswith(expected + path.suffix.lower()):
                score += 500
        if mod_id and mod_id in relative:
            score += 90
        for keyword, weight in (
            ("event", 110),
            ("handler", 90),
            ("behavior", 85),
            ("mod", 80),
            ("main", 70),
            ("client", 40),
            ("server", 35),
            ("command", 40),
            ("config", 35),
            ("screen", 30),
            ("gui", 30),
            ("mixin", 25),
            ("block", 25),
            ("item", 25),
            ("entity", 20),
        ):
            if keyword in relative:
                score += weight
        for keyword, penalty in (
            ("clientproxy", 120),
            ("commonproxy", 120),
            ("proxy/", 80),
        ):
            if keyword in relative:
                score -= penalty
        ranked.append((score, path))

    ranked.sort(key=lambda item: (-item[0], len(str(item[1]))))
    selected: list[Path] = []
    seen: set[Path] = set()
    deferred_support: list[Path] = []
    for _score, path in ranked:
        if path in seen:
            continue
        seen.add(path)
        if is_support_source_path(path, source_roots):
            deferred_support.append(path)
            continue
        selected.append(path)
        if len(selected) >= MAX_PROJECTINFO_EXCERPTS:
            break
    for path in deferred_support:
        if len(selected) >= MAX_PROJECTINFO_EXCERPTS:
            break
        selected.append(path)
    return selected


def source_path_label(source_path: Path, source_roots: list[Path]) -> str:
    for root in source_roots:
        try:
            return str(source_path.relative_to(root))
        except ValueError:
            continue
    return source_path.name


def build_source_excerpt(source_path: Path, *, max_lines: int = 220, max_chars: int = 10_000) -> str:
    text = source_path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    excerpt = "\n".join(lines[:max_lines])
    if len(excerpt) > max_chars:
        excerpt = excerpt[:max_chars].rstrip() + "\n... truncated ..."
    elif len(lines) > max_lines:
        excerpt = excerpt.rstrip() + "\n... truncated ..."
    return excerpt


def is_support_source_path(source_path: Path, source_roots: list[Path]) -> bool:
    relative = source_path_label(source_path, source_roots).lower()
    return any(marker in relative for marker in SUPPORT_SOURCE_MARKERS)


def render_verify_template() -> str:
    return textwrap.dedent(
        """
        pending

        Replace the first line with:
        verified

        Recommended checks before you do that:
        - title and short description are accurate
        - long description does not invent features
        - categories, side support, and license look correct in modrinth.project.json
        - game_versions and loader look correct in modrinth.version.json
        - add an icon.* file to this bundle if you want the project created with an icon
        """
    ).strip() + "\n"


def render_bundle_summary_markdown(
    *,
    bundle_metadata: dict[str, Any],
    listing: dict[str, str],
    publish_state: dict[str, Any],
) -> str:
    return "\n".join(
        [
            "# Auto Create Modrinth Bundle",
            "",
            f"- Status: `{publish_state.get('status', '-')}`",
            f"- Bundle slug: `{bundle_metadata.get('bundle_slug', '-')}`",
            f"- Jar: `{bundle_metadata.get('jar_name', '-')}`",
            f"- Loader: `{bundle_metadata.get('loader', '-')}`",
            f"- Detected game versions: `{', '.join(bundle_metadata.get('resolved_game_versions', [])) or '-'}`",
            "",
            "## Listing Preview",
            "",
            f"- Name: `{listing.get('name', '-')}`",
            f"- Short description: `{listing.get('short_description', '-')}`",
            "",
            "## Next Step",
            "",
            "Open `verify.txt`, replace `pending` with `verified` after review, then run the `publish` command.",
            "",
        ]
    )


def render_failed_bundle_summary_markdown(*, jar_name: str, bundle_dir: Path, error: str) -> str:
    return "\n".join(
        [
            "# Auto Create Modrinth Bundle",
            "",
            f"- Status: `failed_generation`",
            f"- Jar: `{jar_name}`",
            f"- Bundle dir: `{bundle_dir}`",
            "",
            "## Error",
            "",
            error,
            "",
        ]
    )


def render_generate_summary_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# Generate Summary",
        "",
        f"- Generated at: `{summary.get('generated_at', '-')}`",
        f"- Input dir: `{summary.get('input_dir', '-')}`",
        f"- Output dir: `{summary.get('output_dir', '-')}`",
        f"- Local AI hoster/model: `{summary.get('hoster', '-')}` / `{summary.get('model', '-')}`",
        "",
        "| Jar | Bundle | Status | Error |",
        "| --- | --- | --- | --- |",
    ]
    for result in summary.get("results", []):
        lines.append(
            "| "
            + " | ".join(
                [
                    result.get("jar_name", "-"),
                    result.get("bundle_slug", "-"),
                    result.get("status", "-"),
                    (result.get("error", "") or "-").replace("\n", " "),
                ]
            )
            + " |"
        )
    lines.append("")
    return "\n".join(lines)


def render_publish_summary_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# Publish Summary",
        "",
        f"- Published at: `{summary.get('published_at', '-')}`",
        f"- Dry run: `{summary.get('dry_run', False)}`",
        "",
        "| Bundle | Status | Project | Version | Note |",
        "| --- | --- | --- | --- | --- |",
    ]
    for result in summary.get("results", []):
        lines.append(
            "| "
            + " | ".join(
                [
                    result.get("bundle", "-"),
                    result.get("status", "-"),
                    result.get("project_id", "-") or "-",
                    result.get("version_id", "-") or "-",
                    (result.get("note", "") or "-").replace("\n", " "),
                ]
            )
            + " |"
        )
    lines.append("")
    return "\n".join(lines)


def load_template_examples(template_dir: Path | None = None) -> dict[str, str]:
    template_dir = (template_dir or Path(DEFAULT_TEMPLATE_DIR)).resolve()

    def read_or_fallback(filename: str, fallback: str) -> str:
        path = template_dir / filename
        if not path.exists():
            return fallback
        return path.read_text(encoding="utf-8", errors="replace").strip() or fallback

    return {
        "modname": read_or_fallback("modname.txt", "Example Mod"),
        "summary": read_or_fallback("summary.txt", "A short accurate Modrinth description."),
        "description": read_or_fallback("description.txt", "A polished long Markdown description for the Modrinth page."),
    }


def default_categories_for_loader(loader: str) -> list[str]:
    normalized = loader.strip().lower()
    return [normalized] if normalized else []


def find_bundle_icon(bundle_dir: Path) -> Path | None:
    for filename in OPTIONAL_ICON_FILENAMES:
        candidate = bundle_dir / filename
        if candidate.exists() and candidate.is_file():
            return candidate
    return None


def parse_csv_items(raw: str) -> list[str]:
    return [item.strip() for item in str(raw or "").split(",") if item.strip()]


def dedupe_preserve_order(items: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for item in items:
        key = item.strip()
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(key)
    return out


def normalize_category_values(raw: Any) -> list[str]:
    if raw is None:
        return []
    if isinstance(raw, str):
        items = re.split(r"[,/\n|]+", raw)
    elif isinstance(raw, list):
        items = [str(item) for item in raw]
    else:
        return []

    normalized: list[str] = []
    for item in items:
        key = normalize_single_line(item).lower()
        if not key:
            continue
        if key in MODRINTH_CATEGORY_ALIASES:
            normalized.append(MODRINTH_CATEGORY_ALIASES[key])
            continue
        key = key.replace("_", "-")
        if key in MODRINTH_CATEGORY_ALIASES.values():
            normalized.append(key)
    return dedupe_preserve_order(normalized)


def normalize_publish_via(raw: str) -> str:
    value = normalize_single_line(raw).lower() or DEFAULT_PUBLISH_VIA
    if value not in {"auto", "local", "github"}:
        raise ModCompilerError(f"Publish mode must be one of auto, local, github; got '{raw}'.")
    return value


def normalize_side_value(raw: str) -> str:
    value = str(raw or "").strip().lower() or "optional"
    if value not in {"required", "optional", "unsupported", "unknown"}:
        raise ModCompilerError(
            f"Side value must be one of required, optional, unsupported, unknown; got '{raw}'."
        )
    return value


def normalize_side_hint(raw: Any) -> str:
    text = normalize_single_line(raw).lower()
    if not text:
        return ""
    if text in {"required", "optional", "unsupported", "unknown"}:
        return text
    return ""


def normalize_ai_license_id(raw: Any) -> str:
    text = normalize_single_line(raw)
    canonical = {item.lower(): item for item in AI_LICENSE_CHOICES}
    if text.lower() in canonical:
        return canonical[text.lower()]
    return ""


def truncate_text_block(text: str, limit: int) -> str:
    stripped = str(text or "").strip()
    if len(stripped) <= limit:
        return stripped
    tail = f"\n\n... truncated {len(stripped) - limit} characters from projectinfo.txt ..."
    return stripped[: max(0, limit - len(tail))].rstrip() + tail


def normalize_single_line(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def strip_markdown_prefix(text: str) -> str:
    stripped = str(text or "").strip()
    stripped = re.sub(r"^#{1,6}\s*", "", stripped)
    stripped = re.sub(r"^[-*+]\s+", "", stripped)
    stripped = re.sub(r"^\d+\.\s+", "", stripped)
    return normalize_single_line(stripped)


def empty_to_none(value: Any) -> str | None:
    text = normalize_single_line(value)
    return text or None


def slugify_text(value: str) -> str:
    slug = re.sub(r"[^a-z0-9._-]+", "-", str(value or "").strip().lower())
    slug = slug.strip("-._")
    if len(slug) < 3:
        return ""
    return slug[:64]


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1_048_576), b""):
            digest.update(chunk)
    return digest.hexdigest()


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def colorize(color: str, text: str) -> str:
    if not sys.stdout.isatty():
        return text
    prefix = {
        "green": ANSI_GREEN,
        "red": ANSI_RED,
        "yellow": ANSI_YELLOW,
    }.get(color, "")
    return f"{prefix}{text}{ANSI_RESET}" if prefix else text


def _print_status(text: str) -> None:
    with PRINT_LOCK:
        print(text, flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
