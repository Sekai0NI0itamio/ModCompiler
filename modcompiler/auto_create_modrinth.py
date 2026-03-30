from __future__ import annotations

import argparse
import base64
import html as html_lib
import hashlib
import io
import json
import os
import re
import socket
import subprocess
import sys
import tempfile
import textwrap
import threading
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from PIL import Image, ImageDraw, ImageEnhance, ImageFont, ImageOps
except ModuleNotFoundError:  # pragma: no cover - optional at import time for non-generate commands
    Image = None  # type: ignore[assignment]
    ImageDraw = None  # type: ignore[assignment]
    ImageEnhance = None  # type: ignore[assignment]
    ImageFont = None  # type: ignore[assignment]
    ImageOps = None  # type: ignore[assignment]

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
DEFAULT_BACKGROUND_IMAGES_DIR = str(REPO_ROOT / "BackgroundImages")
DEFAULT_MANIFEST = "version-manifest.json"
DEFAULT_TEMPLATE_DIR = str(REPO_ROOT / "templatecreatemod")
DEFAULT_C05_URL = "http://localhost:8129"
DEFAULT_C05_HOSTER = "scitely"
DEFAULT_C05_MODEL = "deepseek-v3.2"
DEFAULT_IMAGE_C05_HOSTER = "aihorde"
DEFAULT_IMAGE_C05_MODEL = ""
DEFAULT_IMAGE_TIMEOUT_SECONDS = 600
DEFAULT_IMAGE_POLL_INTERVAL_SECONDS = 4
DEFAULT_REASONING_EFFORT = "low"
DEFAULT_PROJECT_STATUS = "draft"
DEFAULT_VERSION_STATUS = "listed"
DEFAULT_VERSION_TYPE = "release"
DEFAULT_MAX_WORKERS = 1
DEFAULT_PROMPT_PROJECTINFO_CHAR_LIMIT = 24_000
DEFAULT_PUBLISH_VIA = "auto"
PRIMARY_CATEGORY_LIMIT = 3
REMOTE_DECOMPILE_WORKFLOW_ID = "jar-decompile.yml"
REMOTE_DECOMPILE_ARTIFACT_NAME = "jar-decompile-output"
REMOTE_PUBLISH_WORKFLOW_ID = "publish-auto-create-modrinth.yml"
REMOTE_PUBLISH_ARTIFACT_NAME = "auto-create-modrinth-draft-output"
DRAFT_STATE_FILENAME = "draft_state.json"
LEGACY_PUBLISH_STATE_FILENAME = "publish_state.json"
DRAFT_SUMMARY_JSON = "draft-upload-summary.json"
LEGACY_PUBLISH_SUMMARY_JSON = "publish-summary.json"
DRAFT_SUMMARY_MD = "DRAFT_UPLOAD_SUMMARY.md"
LEGACY_PUBLISH_SUMMARY_MD = "PUBLISH_SUMMARY.md"
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
# Official Modrinth mod categories verified against /v2/tag/category on 2026-03-29.
# The hint text below is inferred classifier guidance because the API currently exposes names,
# icons, and headers for categories but not per-category prose descriptions.
MODRINTH_MOD_CATEGORY_HINTS = {
    "adventure": "Exploration, progression, quests, dungeons, or travel-driven gameplay.",
    "food": "Cooking, crops, meals, hunger systems, or edible content.",
    "minigame": "Self-contained game modes, arenas, or score-based side activities.",
    "technology": "Machines, automation, engineering systems, power, or technical progression.",
    "cursed": "Intentionally weird, chaotic, trollish, or unsettling gameplay changes.",
    "game-mechanics": "Core rule changes, balance tweaks, combat rules, or vanilla behavior changes.",
    "mobs": "Adds, changes, or heavily focuses on creatures, NPCs, or mob behavior.",
    "transportation": "Movement, vehicles, teleportation, rails, portals, or travel helpers.",
    "decoration": "Building aesthetics, furniture, cosmetics, or decorative blocks/items.",
    "library": "Shared APIs, framework code, dependencies, or developer-facing support mods.",
    "optimization": "Performance, FPS, memory, loading, server tick, or lag reduction.",
    "utility": "General quality-of-life features, helpers, tools, or convenience tweaks.",
    "economy": "Currency, shops, trading, markets, or resource-value systems.",
    "magic": "Spells, mana, rituals, enchantment-focused systems, or mystical progression.",
    "social": "Multiplayer interactions, chat, parties, friends, sharing, or community features.",
    "worldgen": "Biomes, structures, terrain, ore generation, or world creation changes.",
    "equipment": "Armor, weapons, tools, trinkets, or gear-focused progression.",
    "management": "Admin tools, automation control panels, config-heavy oversight, or organization.",
    "storage": "Inventories, containers, item transport, sorting, logistics, or storage systems.",
}
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
ART_DIRNAME = "art"
ART_METADATA_FILENAME = "art/assets.json"
VISUAL_BACKGROUND_FILENAME = "background.jpg"
BACKGROUND_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
VISUAL_DESIGN_SIZE = 1024
VISUAL_RENDER_SIZE = 2048
DESCRIPTION_IMAGE_SIZE = (1024, 576)
LOGO_IMAGE_SIZE = (1024, 1024)
DESCRIPTION_POSTER_IMAGE_SIZE = (1536, 1536)
DESCRIPTION_POSTER_SLICE_HEIGHT = 768
MODRINTH_ICON_MAX_BYTES = 256 * 1024
TITLE_OVERLAY_DIM_FACTOR = 0.8
ANSI_GREEN = "\033[92m"
ANSI_RED = "\033[91m"
ANSI_YELLOW = "\033[93m"
ANSI_RESET = "\033[0m"
PRINT_LOCK = threading.Lock()
REMOTE_DISPATCH_LOCK = threading.Lock()
IMAGE_GENERATION_LOCK = threading.Lock()
VISUAL_DESIGN_LOCK = threading.Lock()
CLAIMED_REMOTE_RUN_IDS: set[int] = set()


@dataclass(frozen=True)
class BackgroundImageChoice:
    path: Path
    relative_name: str
    label: str


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
    image_timeout_seconds: int
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
    background_images: tuple[BackgroundImageChoice, ...]
    github_token: str
    github_repo: str
    github_branch: str
    remote_jar_paths: dict[str, str]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Local-first workflow that decompiles jars from ToBeUploaded, asks C05 Local AI "
            "(scitely/deepseek-v3.2 by default) to draft Modrinth listing copy, then creates full "
            "Modrinth draft projects and versions for bundles whose verify.txt contains "
            "'verified'."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate_parser = subparsers.add_parser(
        "generate",
        help="Create one review bundle per jar in ToBeUploaded.",
    )
    generate_parser.add_argument("--input-dir", default=DEFAULT_INPUT_DIR)
    generate_parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    generate_parser.add_argument("--background-dir", default=DEFAULT_BACKGROUND_IMAGES_DIR)
    generate_parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    generate_parser.add_argument("--c05-url", default=DEFAULT_C05_URL)
    generate_parser.add_argument("--c05-hoster", default=DEFAULT_C05_HOSTER)
    generate_parser.add_argument("--c05-model", default=DEFAULT_C05_MODEL)
    generate_parser.add_argument("--reasoning-effort", default=DEFAULT_REASONING_EFFORT)
    generate_parser.add_argument("--temperature", type=float, default=0.2)
    generate_parser.add_argument("--max-tokens", type=int, default=1_400)
    generate_parser.add_argument("--timeout-seconds", type=int, default=300)
    generate_parser.add_argument(
        "--image-timeout-seconds",
        type=int,
        default=DEFAULT_IMAGE_TIMEOUT_SECONDS,
        help="Local HTML render timeout in seconds for logo and description art generation.",
    )
    generate_parser.add_argument(
        "--max-workers",
        type=int,
        default=DEFAULT_MAX_WORKERS,
        help="Compatibility flag only. Generation now runs sequentially to avoid provider rate limits.",
    )
    generate_parser.add_argument("--force", action="store_true")
    generate_parser.add_argument(
        "--prompt-projectinfo-char-limit",
        type=int,
        default=DEFAULT_PROMPT_PROJECTINFO_CHAR_LIMIT,
        help="Maximum projectinfo characters to embed into the DeepSeek user message.",
    )
    generate_parser.add_argument(
        "--project-status",
        default=DEFAULT_PROJECT_STATUS,
        help="Kept for compatibility. The generated Modrinth project draft is always created as draft.",
    )
    generate_parser.add_argument(
        "--version-status",
        default=DEFAULT_VERSION_STATUS,
        help="Version status to store in modrinth.version.json and send during Modrinth version creation.",
    )
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
        "create-drafts",
        aliases=["publish"],
        help="Create verified Modrinth draft projects and upload their jar versions.",
    )
    publish_parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    publish_parser.add_argument("--modrinth-token", default="")
    publish_parser.add_argument("--dry-run", action="store_true")
    publish_parser.add_argument("--only-bundle", default="")
    publish_parser.add_argument(
        "--verified",
        action="store_true",
        help="Bypass verify.txt checks and treat the selected bundles as already approved for draft creation.",
    )
    publish_parser.add_argument("--create-via", "--publish-via", dest="publish_via", default=DEFAULT_PUBLISH_VIA)

    args = parser.parse_args(argv)
    try:
        if args.command == "generate":
            return command_generate(args)
        if args.command in {"create-drafts", "publish"}:
            return command_publish(args)
    except ModCompilerError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 1


def command_generate(args: argparse.Namespace) -> int:
    require_pillow_for_generate()
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
    background_images = discover_background_images(Path(args.background_dir))
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
        image_timeout_seconds=max(60, min(int(args.image_timeout_seconds), 1_170)),
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
        background_images=background_images,
        github_token=github_token,
        github_repo=github_repo,
        github_branch=github_branch,
        remote_jar_paths=remote_jar_paths,
    )

    max_workers = max(1, min(int(args.max_workers), len(jars)))
    if max_workers != 1:
        print(
            f"Sequential generation mode is enabled to avoid AI/provider rate limits, so --max-workers={max_workers} will be ignored.",
            flush=True,
        )
    print(
        f"Generating {len(jars)} Modrinth bundle(s) from {input_dir} one by one in sequential mode.",
        flush=True,
    )

    results: list[dict[str, Any]] = []
    for jar_path in jars:
        try:
            result = generate_bundle_for_jar(jar_path, options)
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
        f"Review each bundle's verify.txt before creating the Modrinth draft.",
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
            "A Modrinth token is required for local draft creation. "
            "Pass --modrinth-token, set MODRINTH_TOKEN, or use --create-via github."
        )

    client = ModrinthClient(
        token=token if not args.dry_run else "",
        user_agent=build_modrinth_user_agent(),
    )

    results: list[dict[str, Any]] = []
    for bundle_dir in bundle_dirs:
        results.append(
            publish_bundle(
                bundle_dir=bundle_dir,
                client=client,
                dry_run=bool(args.dry_run),
                assume_verified=bool(args.verified),
            )
        )

    summary = {
        "drafted_at": now_iso(),
        "output_dir": str(output_dir),
        "dry_run": bool(args.dry_run),
        "results": results,
    }
    write_draft_summary_outputs(output_dir, summary)

    failures = summarize_result_counts(results)["failed"]
    print_result_overview(results, via_github=False)
    print_result_links(results)
    return 0 if failures == 0 else 1


def command_publish_via_github(
    *,
    args: argparse.Namespace,
    output_dir: Path,
    bundle_dirs: list[Path],
) -> int:
    prechecked_results: list[dict[str, Any]] = []
    verified_bundle_dirs: list[Path] = []
    if args.verified:
        verified_bundle_dirs = list(bundle_dirs)
    else:
        for bundle_dir in bundle_dirs:
            if is_bundle_approved_for_draft(bundle_dir / "verify.txt", assume_verified=False):
                verified_bundle_dirs.append(bundle_dir)
            else:
                prechecked_results.append(build_unverified_bundle_result(bundle_dir))

    remote_results: list[dict[str, Any]] = []
    if verified_bundle_dirs:
        github_token = discover_github_token()
        github_repo = discover_github_repo()
        current_branch = discover_current_branch()
        dispatch_branch, remote_output_dir = prepare_remote_publish_inputs(
            bundle_dirs=verified_bundle_dirs,
            output_dir=output_dir,
            github_token=github_token,
            github_repo=github_repo,
            github_base_branch=current_branch,
            assume_verified=bool(args.verified),
        )

        for bundle_dir in verified_bundle_dirs:
            remote_results.append(
                publish_bundle_via_github_actions(
                    bundle_dir=bundle_dir,
                    output_dir=output_dir,
                    remote_output_dir=remote_output_dir,
                    github_repo=github_repo,
                    github_branch=dispatch_branch,
                    github_token=github_token,
                    assume_verified=bool(args.verified),
                )
            )

    results = prechecked_results + remote_results
    results.sort(key=lambda item: str(item.get("bundle", "")))

    summary = {
        "drafted_at": now_iso(),
        "output_dir": str(output_dir),
        "dry_run": False,
        "create_via": "github",
        "results": results,
    }
    write_draft_summary_outputs(output_dir, summary)

    failures = summarize_result_counts(results)["failed"]
    print_result_overview(results, via_github=True)
    print_result_links(results)
    return 0 if failures == 0 else 1


def build_unverified_bundle_result(bundle_dir: Path) -> dict[str, Any]:
    state = load_bundle_state(bundle_dir)
    if state:
        state["status"] = "ready_for_verification"
        state["verified"] = False
        write_bundle_state(bundle_dir, state)
        sync_bundle_summary(bundle_dir)

    return {
        "bundle": bundle_dir.name,
        "bundle_slug": bundle_dir.name,
        "status": "skipped",
        "note": "verify.txt does not contain 'verified'.",
        "project_id": str(state.get("project_id", "") or ""),
        "project_slug": str(state.get("project_slug", "") or ""),
        "project_url": str(state.get("project_url", "") or ""),
        "version_id": str(state.get("version_id", "") or ""),
        "version_url": str(state.get("version_url", "") or ""),
    }


def summarize_result_counts(results: list[dict[str, Any]]) -> dict[str, int]:
    return {
        "created": sum(1 for result in results if result.get("status") == "draft_created"),
        "submitted": sum(1 for result in results if result.get("status") == "submitted"),
        "skipped": sum(1 for result in results if result.get("status") == "skipped"),
        "dry_run": sum(1 for result in results if result.get("status") == "dry_run"),
        "failed": sum(1 for result in results if result.get("status") == "failed"),
    }


def print_result_overview(results: list[dict[str, Any]], *, via_github: bool) -> None:
    counts = summarize_result_counts(results)
    prefix = "Finished draft creation scan via GitHub" if via_github else "Finished draft creation scan"
    print(
        f"{prefix}: {len(results)} bundle(s) checked, "
        f"{counts['created']} created, {counts['submitted']} submitted, {counts['skipped']} skipped, "
        f"{counts['dry_run']} dry run, {counts['failed']} failure(s).",
        flush=True,
    )
    if counts["created"] == 0 and counts["skipped"] > 0:
        skipped = [
            str(result.get("bundle", "") or result.get("bundle_slug", "") or "-")
            for result in results
            if result.get("status") == "skipped"
        ]
        print(
            "No Modrinth drafts were created yet. These bundles are still waiting for you to change "
            f"`verify.txt` from `pending` to `verified`: {', '.join(skipped)}",
            flush=True,
        )
        print(
            "After that, rerun `python3 scripts/auto_create_modrinth_draft_projects.py create-drafts`.",
            flush=True,
        )
    elif counts["created"] == 0 and counts["failed"] == 0 and counts["dry_run"] > 0:
        print("No drafts were created because this was a dry run.", flush=True)
    return


def require_pillow_for_generate() -> None:
    if all(module is not None for module in (Image, ImageDraw, ImageEnhance, ImageFont, ImageOps)):
        return
    raise ModCompilerError(
        "Pillow is required for the `generate` command because it renders the draft art assets. "
        "Install it with `python3 -m pip install Pillow`."
    )


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
            "Remote GitHub workflow draft creation only supports bundle directories inside this repo."
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
    assume_verified: bool,
) -> tuple[str, str]:
    ensure_command_available("gh")
    ensure_command_available("git")

    session_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]
    dispatch_branch = f"auto-create-modrinth-draft-{session_id}"
    relative_output_dir = relative_to_repo_root(output_dir)

    with tempfile.TemporaryDirectory(prefix="auto-create-remote-draft-branch-") as temp_dir:
        worktree_dir = Path(temp_dir) / "worktree"
        run_subprocess(["git", "worktree", "add", "--detach", str(worktree_dir), "HEAD"])
        try:
            run_subprocess(["git", "checkout", "-b", dispatch_branch], cwd=worktree_dir)

            staged_paths: list[Path] = []
            for bundle_dir in bundle_dirs:
                relative_bundle = relative_to_repo_root(bundle_dir)
                destination = worktree_dir / relative_bundle
                copy_tree(bundle_dir, destination)
                if assume_verified:
                    set_verify_file_verified(destination / "verify.txt")
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
                raise ModCompilerError("Failed to stage the bundle files for remote draft creation.")

            commit_message = f"Add auto-create draft bundle ({session_id})"
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
        f"(base: {github_base_branch}) for remote Modrinth draft creation in {github_repo}."
    )
    return dispatch_branch, str(relative_output_dir).replace(os.sep, "/")


def remote_publish_runtime_paths() -> list[Path]:
    required = [
        Path(".github/workflows/publish-auto-create-modrinth.yml"),
        Path("scripts/auto_create_modrinth_draft_projects.py"),
        Path("scripts/auto_create_modrinth_page_and_publish_mod.py"),
        Path("modcompiler/auto_create_modrinth.py"),
        Path("modcompiler/modrinth.py"),
        Path("modcompiler/common.py"),
        Path("modcompiler/decompile.py"),
    ]
    missing = [path for path in required if not (Path.cwd() / path).exists()]
    if missing:
        raise ModCompilerError(
            "Remote draft creation is missing required runtime files: "
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
    assume_verified: bool,
) -> dict[str, Any]:
    bundle_slug = bundle_dir.name
    verify_path = bundle_dir / "verify.txt"
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
        "project_url": "",
        "version_url": "",
    }

    if not project_path.exists() or not version_path.exists():
        result["status"] = "failed"
        result["note"] = "Bundle is missing modrinth.project.json or modrinth.version.json."
        return result

    if not is_bundle_approved_for_draft(verify_path, assume_verified=assume_verified):
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        publish_state = load_bundle_state(bundle_dir)
        if publish_state:
            publish_state["status"] = "ready_for_verification"
            publish_state["verified"] = False
            write_bundle_state(bundle_dir, publish_state)
        return result

    validate_project_payload(load_json(project_path), project_path)
    validate_version_payload(load_json(version_path), version_path)

    _print_status(f"Dispatching GitHub draft workflow for {bundle_slug}...")
    run_id = dispatch_remote_publish_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        remote_output_dir=remote_output_dir,
        bundle_slug=bundle_slug,
        assume_verified=assume_verified,
    )
    run_info = wait_for_remote_run_completion(
        github_repo=github_repo,
        github_token=github_token,
        run_id=run_id,
    )

    with tempfile.TemporaryDirectory(prefix="auto-create-remote-draft-artifact-") as temp_dir:
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
                f"GitHub draft run {run_id} completed but no updated bundle directory for {bundle_slug} was found."
            )
        copy_tree(remote_bundle_dir, bundle_dir)

    publish_state = load_bundle_state(bundle_dir)
    result["status"] = str(publish_state.get("status", "") or "failed")
    result["project_id"] = str(publish_state.get("project_id", "") or "")
    result["project_slug"] = str(publish_state.get("project_slug", "") or "")
    result["version_id"] = str(publish_state.get("version_id", "") or "")
    result["project_url"] = str(publish_state.get("project_url", "") or "")
    result["version_url"] = str(publish_state.get("version_url", "") or "")
    warnings = [str(item) for item in (publish_state.get("warnings", []) or []) if str(item).strip()]
    warnings = [str(item) for item in (publish_state.get("warnings", []) or []) if str(item).strip()]

    if result["status"] == "draft_created":
        result["note"] = f"Draft created via GitHub workflow run {run_id}."
        if warnings:
            result["note"] = f"{result['note']} Warning: {' | '.join(dedupe_preserve_order(warnings))}"
        return result
    if result["status"] == "ready_for_verification":
        run_url = str(run_info.get("url", "") or "").strip()
        conclusion = str(run_info.get("conclusion", "") or "").strip()
        if conclusion and conclusion.lower() != "success":
            detail = f"GitHub workflow conclusion: {conclusion}"
            if run_url:
                detail = f"{detail}. Run: {run_url}"
            result["status"] = "failed"
            result["note"] = detail
            return result
        if assume_verified:
            result["status"] = "submitted"
            result["note"] = (
                f"GitHub draft workflow run {run_id} was dispatched with --verified; "
                "ignoring the returned ready_for_verification status."
            )
            if run_url:
                result["note"] = f"{result['note']} Run: {run_url}"
            return result
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        return result

    run_url = str(run_info.get("url", "") or "")
    state_error = str(publish_state.get("last_error", "") or "").strip()
    conclusion = str(run_info.get("conclusion", "") or "").strip()
    detail = state_error or (f"GitHub workflow conclusion: {conclusion}" if conclusion else "Remote draft creation failed.")
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
    assume_verified: bool,
) -> int:
    return dispatch_workflow_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        workflow_id=REMOTE_PUBLISH_WORKFLOW_ID,
        fields={
            "output_dir": remote_output_dir,
            "bundle_slug": bundle_slug,
            "verified": "true" if assume_verified else "false",
        },
        not_found_message=(
            f"GitHub accepted the draft-creation dispatch for {bundle_slug}, but no new run could be located."
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
                f"Next step: commit and push `{Path('.github/workflows') / workflow_id}` to `main`, then run `create-drafts` again.\n"
                "If you do not want to commit that workflow yet, the other option is to create drafts locally with `--modrinth-token`."
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
    listing_ai_usage = {"hoster": options.c05_hoster, "model": options.c05_model}
    metadata = inspect_mod_jar(jar_path, options.manifest)
    visual_assets: dict[str, Any] | None = None
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
            app_id="auto-create-modrinth-draft-projects",
        )
        listing_ai_usage = {"hoster": options.c05_hoster, "model": options.c05_model}
        (bundle_dir / "ai_response.txt").write_text(ai_response_text, encoding="utf-8")

        ai_result = parse_ai_listing_response(ai_response_text)
        listing = finalize_generated_listing(
            listing=ai_result,
            metadata=metadata,
            jar_path=jar_path,
        )
        write_json(bundle_dir / "listing.json", listing)

        try:
            visual_assets = generate_bundle_visual_assets(
                bundle_dir=bundle_dir,
                listing=listing,
                metadata=metadata,
                options=options,
            )
        except Exception as visual_error:
            visual_assets = {
                "generated_at": now_iso(),
                "render_engine": "html+qlmanage",
                "render_timeout_seconds": options.image_timeout_seconds,
                "background_file": "",
                "logo_file": "",
                "description_image_file": "",
                "icon_file": "",
                "warnings": [f"Visual asset generation failed: {type(visual_error).__name__}: {visual_error}"],
            }
            save_bundle_art_metadata(bundle_dir, visual_assets)

        bundle_metadata = build_bundle_metadata(
            jar_path=jar_path,
            bundle_slug=bundle_slug,
            metadata=metadata,
            options=options,
            listing=listing,
            listing_ai_usage=listing_ai_usage,
            visual_assets=visual_assets,
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
            "description_image_url": "",
            "description_image_urls": [],
            "last_error": "",
            "warnings": [],
        }
        write_bundle_state(bundle_dir, publish_state)
        (bundle_dir / "SUMMARY.md").write_text(
            render_bundle_summary_markdown(bundle_metadata=bundle_metadata, listing=listing, publish_state=publish_state),
            encoding="utf-8",
        )
        art_status = (
            "art ready"
            if (visual_assets or {}).get("logo_file") and (visual_assets or {}).get("description_image_file")
            else "art issue; see art/assets.json"
        )

        _print_status(
            colorize(
                "green",
                f"Complete: {jar_path.name} -> {bundle_dir} ({art_status}; review verify.txt before creating the Modrinth draft)",
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
            "description_image_url": "",
            "description_image_urls": [],
            "last_error": f"{type(error).__name__}: {error}",
            "warnings": [],
        }
        write_bundle_state(bundle_dir, publish_state)
        write_json(
            bundle_dir / "bundle_metadata.json",
            build_bundle_metadata(
                jar_path=jar_path,
                bundle_slug=bundle_slug,
                metadata=metadata,
                options=options,
                listing=None,
                visual_assets=visual_assets,
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


def publish_bundle(*, bundle_dir: Path, client: ModrinthClient, dry_run: bool, assume_verified: bool) -> dict[str, Any]:
    metadata_path = bundle_dir / "bundle_metadata.json"
    project_path = bundle_dir / "modrinth.project.json"
    version_path = bundle_dir / "modrinth.version.json"
    verify_path = bundle_dir / "verify.txt"

    result = {
        "bundle": bundle_dir.name,
        "bundle_dir": str(bundle_dir),
        "status": "skipped",
        "note": "",
        "project_id": "",
        "project_slug": "",
        "version_id": "",
        "project_url": "",
        "version_url": "",
    }

    if not metadata_path.exists():
        result["status"] = "failed"
        result["note"] = "bundle_metadata.json is missing."
        return result
    if not project_path.exists() or not version_path.exists():
        result["status"] = "failed"
        result["note"] = "Modrinth draft files are missing."
        return result
    bundle_metadata = load_json(metadata_path)
    publish_state = load_bundle_state(bundle_dir)
    if not publish_state:
        result["status"] = "failed"
        result["note"] = f"{DRAFT_STATE_FILENAME} is missing."
        return result
    result["project_id"] = str(publish_state.get("project_id", "") or "")
    result["project_slug"] = str(publish_state.get("project_slug", "") or "")
    result["version_id"] = str(publish_state.get("version_id", "") or "")
    result["project_url"] = str(publish_state.get("project_url", "") or "")
    result["version_url"] = str(publish_state.get("version_url", "") or "")

    if not is_bundle_approved_for_draft(verify_path, assume_verified=assume_verified):
        result["status"] = "skipped"
        result["note"] = "verify.txt does not contain 'verified'."
        return result

    project_payload = clean_modrinth_payload(load_json(project_path))
    version_payload = clean_modrinth_payload(load_json(version_path), keep_empty_arrays={"dependencies"})
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
    existing_version_id = str(publish_state.get("version_id", "") or "").strip()
    created_project = False
    warnings: list[str] = [str(item) for item in (publish_state.get("warnings", []) or []) if str(item).strip()]

    try:
        if existing_version_id:
            if dry_run:
                result["status"] = "dry_run"
                result["note"] = f"Would reuse existing Modrinth draft version {existing_version_id}."
                return result

            publish_state["status"] = "draft_created"
            publish_state["verified"] = True
            publish_state["last_error"] = ""
            promote_published_bundle_visibility(
                client=client,
                project_id=project_id,
                version_id=existing_version_id,
                project_payload=project_payload,
                version_payload=version_payload,
                project_path=project_path,
                version_path=version_path,
            )
            warnings.extend(
                sync_project_description_image(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                    project_payload=project_payload,
                    project_path=project_path,
                    publish_state=publish_state,
                )
            )
            publish_state["warnings"] = dedupe_preserve_order(warnings)
            write_bundle_state(bundle_dir, publish_state)
            sync_bundle_summary(bundle_dir)
            result["status"] = "draft_created"
            result["note"] = f"Existing Modrinth draft version {existing_version_id} reused."
            if warnings:
                result["note"] = f"{result['note']} Warning: {' | '.join(dedupe_preserve_order(warnings))}"
            result["version_id"] = existing_version_id
            result["project_url"] = str(publish_state.get("project_url", "") or "")
            result["version_url"] = str(publish_state.get("version_url", "") or "")
            return result

        if project_id or project_slug:
            resolved_project_id, resolved_project_slug = resolve_modrinth_project_identity(
                client=client,
                project_id=project_id,
                project_slug=project_slug,
            )
            if resolved_project_id:
                if resolved_project_id != project_id or resolved_project_slug != project_slug:
                    project_id = resolved_project_id
                    project_slug = resolved_project_slug or project_slug
                    publish_state["project_id"] = project_id
                    publish_state["project_slug"] = project_slug
                    write_bundle_state(bundle_dir, publish_state)
                    sync_bundle_summary(bundle_dir)
            elif project_id:
                warnings.append(
                    "Saved Modrinth draft project id could not be resolved; creating a fresh draft project."
                )
                project_id = ""
                publish_state["project_id"] = ""
                publish_state["project_slug"] = project_slug
                write_bundle_state(bundle_dir, publish_state)
                sync_bundle_summary(bundle_dir)

        if not project_id:
            if dry_run:
                result["status"] = "dry_run"
                result["note"] = "Would create a Modrinth draft project, then upload the jar version."
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
            resolved_project_id, resolved_project_slug = resolve_modrinth_project_identity(
                client=client,
                project_id=project_id,
                project_slug=project_slug,
            )
            if resolved_project_id:
                project_id = resolved_project_id
                project_slug = resolved_project_slug or project_slug
            created_project = True
            publish_state["project_id"] = project_id
            publish_state["project_slug"] = project_slug
            result["project_id"] = project_id
            result["project_slug"] = project_slug
            result["project_url"] = build_modrinth_project_url(project_slug=project_slug, project_id=project_id)
            write_bundle_state(bundle_dir, publish_state)
            sync_bundle_summary(bundle_dir)

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
            existing_version_id = str(existing.get("id", "") or "")
            publish_state["status"] = "draft_created"
            publish_state["verified"] = True
            publish_state["version_id"] = existing_version_id
            publish_state["last_error"] = ""
            promote_published_bundle_visibility(
                client=client,
                project_id=project_id,
                version_id=existing_version_id,
                project_payload=project_payload,
                version_payload=version_payload,
                project_path=project_path,
                version_path=version_path,
            )
            warnings.extend(
                sync_project_description_image(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                    project_payload=project_payload,
                    project_path=project_path,
                    publish_state=publish_state,
                )
            )
            publish_state["warnings"] = dedupe_preserve_order(warnings)
            write_bundle_state(bundle_dir, publish_state)
            sync_bundle_summary(bundle_dir)
            result["status"] = "draft_created"
            result["version_id"] = existing_version_id
            result["note"] = f"Version already existed on Modrinth as {existing.get('id', '-')}; draft state reused."
            result["project_url"] = str(publish_state.get("project_url", "") or "")
            result["version_url"] = str(publish_state.get("version_url", "") or "")
            return result

        if dry_run:
            result["status"] = "dry_run"
            result["note"] = "Would upload the version to the verified bundle's Modrinth draft project."
            result["project_id"] = project_id
            result["project_slug"] = project_slug
            result["project_url"] = build_modrinth_project_url(project_slug=project_slug, project_id=project_id)
            return result

        created_version = client.create_version(payload=build_version_create_payload(version_payload), jar_path=jar_path)
        version_id = str(created_version.get("id", "")).strip()
        publish_state["status"] = "draft_created"
        publish_state["verified"] = True
        publish_state["version_id"] = version_id
        publish_state["last_error"] = ""
        promote_published_bundle_visibility(
            client=client,
            project_id=project_id,
            version_id=version_id,
            project_payload=project_payload,
            version_payload=version_payload,
            project_path=project_path,
            version_path=version_path,
        )
        warnings.extend(
            sync_project_description_image(
                client=client,
                project_id=project_id,
                bundle_dir=bundle_dir,
                project_payload=project_payload,
                project_path=project_path,
                publish_state=publish_state,
            )
        )
        publish_state["warnings"] = dedupe_preserve_order(warnings)
        write_bundle_state(bundle_dir, publish_state)
        sync_bundle_summary(bundle_dir)

        result["status"] = "draft_created"
        result["note"] = "Draft project created and version uploaded." if created_project else "Draft version uploaded."
        if warnings:
            result["note"] = f"{result['note']} Warning: {' | '.join(dedupe_preserve_order(warnings))}"
        result["project_id"] = project_id
        result["project_slug"] = project_slug
        result["version_id"] = version_id
        result["project_url"] = str(publish_state.get("project_url", "") or "")
        result["version_url"] = str(publish_state.get("version_url", "") or "")
        return result
    except Exception as error:
        publish_state["status"] = "draft_create_failed"
        publish_state["verified"] = True
        publish_state["last_error"] = f"{type(error).__name__}: {error}"
        publish_state["warnings"] = dedupe_preserve_order(warnings)
        write_bundle_state(bundle_dir, publish_state)
        sync_bundle_summary(bundle_dir)
        result["status"] = "failed"
        result["note"] = f"{type(error).__name__}: {error}"
        result["project_id"] = project_id
        result["project_slug"] = project_slug
        result["project_url"] = str(publish_state.get("project_url", "") or "")
        result["version_url"] = str(publish_state.get("version_url", "") or "")
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
            "categories": ["utility", "management"],
            "additional_categories": ["storage", "technology"],
            "client_side": "optional",
            "server_side": "required",
            "license_id": "",
            "license_url": "",
        },
        indent=2,
    )
    template_examples = template_examples or load_template_examples()
    categories_list = ", ".join(sorted(MODRINTH_MOD_CATEGORY_HINTS))
    category_guide = build_modrinth_category_guidance()
    license_list = ", ".join(item or "empty-string-when-unknown" for item in AI_LICENSE_CHOICES)

    prompt = f"""
Generate Modrinth listing copy for one Minecraft mod.

Use only the facts found in PROJECT INFO below.
Do not invent features, commands, compatibility, authors, loaders, game versions, or licensing facts.
Use the perfect template examples below for tone, quality, and formatting only.
Do not copy the example mod's features unless PROJECT INFO proves them.
Lead with the main player-facing feature, not the internal implementation.
If the jar metadata already contains a solid name, keep it very close to that name.
Compare the raw jar filename, jar_display_name, metadata name, and primary_mod_id before choosing the final name.
If one candidate has an obvious typo and another candidate is clearly the corrected human-readable name, use the corrected one.
Prefer fixing obvious spelling mistakes like "most" to "mobs" when the jar naming proves the intended public name.
Keep the short description to one accurate sentence under 96 characters.
Make the long description feel like a polished manual Modrinth page.
You may use Markdown headings and bullet lists when they improve clarity.
Mention secondary features only if they are clearly present and worth surfacing.
Avoid implementation details like reflection, event hooks, custom damage sources,
cooldown fields, package names, class names, or internal APIs.
Avoid filler compatibility lines unless PROJECT INFO makes them unusually important.
Choose 1 to {PRIMARY_CATEGORY_LIMIT} most relevant primary categories for the categories array.
Put any other clearly relevant search tags into additional_categories.
Do not repeat a category across both arrays.
Do not use loader names like forge, fabric, quilt, or neoforge as categories.
Use only these official Modrinth mod category slugs: {categories_list}
These classifier hints are inferred from Modrinth's official category names because the API does not return prose descriptions:
{category_guide}
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


def build_visual_prompt_user_message(
    *,
    listing: dict[str, str],
    metadata: dict[str, Any],
    template_examples: dict[str, str] | None = None,
    background_images: tuple[BackgroundImageChoice, ...] = (),
) -> str:
    details = metadata.get("metadata", {})
    template_examples = template_examples or {}
    title = listing.get("name", "") or details.get("name") or "Unknown Mod"
    summary = listing.get("short_description", "") or details.get("description", "")
    body = truncate_text_block(listing.get("long_description", ""), 2_000)
    template = json.dumps(
        {
            "accent_color": "#ff7f2f",
            "background_image": background_images[0].relative_name if background_images else "background.jpg",
            "logo": {
                "eyebrow": "Utility Mod",
                "mark": "TD",
                "title": "TNT Duper",
                "subtitle": "A sharper industrial identity built around duplication, ignition, and redstone-scale impact.",
                "rail_left": "Redstone Ready",
                "rail_right": "Infinite Payload",
            },
            "description": {
                "kicker": "Utility Mod",
                "title": "TNT Duper",
                "tagline": "Launch primed TNT from dispensers without draining your stock, wrapped in a sharper industrial presentation built for automated chaos.",
                "chips": ["Infinite Dispense", "Redstone Driven", "Forge 1.12.2"],
                "stats": [
                    {
                        "value": "0",
                        "label": "Blocks Lost",
                        "note": "Dispensers keep firing primed TNT while the source stack stays untouched.",
                    },
                    {
                        "value": "24/7",
                        "label": "Automation",
                        "note": "Fits looping redstone systems, testing rigs, and high-volume explosive contraptions.",
                    },
                ],
            },
        },
        indent=2,
    )
    background_choices = render_background_image_choices(background_images) if background_images else "- background.jpg -> Scenic Background"
    prompt = f"""
Generate compact visual content data for one mod page.

Do not write full HTML documents, CSS, or explanations.
The script will render your content through an approved handcrafted template locally on macOS.
Your job is to provide the words, labels, short phrases, and one accent color that fit that template.

Return only valid JSON with this exact shape:

{template}

Important rules:
- Keep all strings concise and polished.
- `accent_color` must be a single hex color like `#ff7f2f`.
- `background_image` must be one exact filename from BACKGROUND IMAGE OPTIONS.
- `logo.mark` should be a short monogram or abbreviation, usually 2 to 4 characters.
- `description.chips` should contain 2 to 4 short labels.
- `description.stats` should contain exactly 2 items.
- Each `stats.note` should be one short sentence.
- Keep the style modern, premium, and non-Minecraft-themed.
- Match the level of polish, layout discipline, and typography hierarchy from the approved examples below.
- Reuse the approved example's composition language, but adapt the actual words to this mod.

BACKGROUND IMAGE OPTIONS
Choose the single background file whose filename mood best fits this mod:
{background_choices}

APPROVED LOGO HTML EXAMPLE:
```html
{template_examples.get("visual_logo_html", "")}
```

APPROVED DESCRIPTION HTML EXAMPLE:
```html
{template_examples.get("visual_description_html", "")}
```

MOD TITLE:
{title}

SHORT SUMMARY:
{summary}

LONG DESCRIPTION CONTEXT:
{body}
"""
    return textwrap.dedent(prompt).strip() + "\n"


def extract_html_document_fragments(raw_html: Any) -> tuple[str, str]:
    text = str(raw_html or "").strip()
    if not text:
        return "", ""
    text = re.sub(r"(?is)<script\b.*?</script>", "", text)
    styles = re.findall(r"(?is)<style\b[^>]*>(.*?)</style>", text)
    css = "\n\n".join(part.strip() for part in styles if part.strip())
    body_match = re.search(r"(?is)<body\b[^>]*>(.*?)</body>", text)
    body_html = body_match.group(1).strip() if body_match else text
    body_html = re.sub(r"(?is)</?(?:!doctype|html|head|body|style|meta|title|link)\b[^>]*>", "", body_html).strip()
    return css, body_html


def normalize_visual_css_fragment(value: Any) -> str:
    css = str(value or "").strip()
    if not css:
        return ""
    css = re.sub(r"(?is)<style\b[^>]*>", "", css)
    css = re.sub(r"(?is)</style>", "", css)
    css = re.sub(r"(?is)@import\s+url\((.*?)\)\s*;?", "", css)
    css = re.sub(r"(?i)url\((['\"]?)(https?:)?//.*?\1\)", "none", css)
    return css.strip()


def normalize_visual_body_html(value: Any) -> str:
    html = str(value or "").strip()
    if not html:
        return ""
    html = re.sub(r"(?is)<script\b.*?</script>", "", html)
    html = re.sub(r"(?is)</?(?:html|head|body)\b[^>]*>", "", html)
    return html.strip()


def normalize_hex_color(value: Any, *, fallback: str = "#ff7f2f") -> str:
    text = normalize_single_line(value)
    if re.fullmatch(r"#[0-9a-fA-F]{6}", text):
        return text.lower()
    return fallback


def default_visual_mark(title: str) -> str:
    words = [word for word in re.findall(r"[A-Za-z0-9]+", title) if word]
    if not words:
        return "MC"
    initials = "".join(word[0] for word in words[:3]).upper()
    if 2 <= len(initials) <= 4:
        return initials
    compact = re.sub(r"[^A-Za-z0-9]+", "", title).upper()
    return (compact[:3] or "MC")


def normalize_visual_design_response(
    data: dict[str, Any],
    *,
    title: str,
    summary: str,
    background_images: tuple[BackgroundImageChoice, ...] = (),
) -> dict[str, Any]:
    logo_data = data.get("logo", {}) if isinstance(data.get("logo"), dict) else {}
    description_data = data.get("description", {}) if isinstance(data.get("description"), dict) else {}

    chips_raw = description_data.get("chips")
    chips = normalize_string_list(chips_raw)[:4]
    if len(chips) < 2:
        chips = dedupe_preserve_order(chips + ["Draft Ready", "Manual Review"])

    stats_raw = description_data.get("stats")
    stats: list[dict[str, str]] = []
    if isinstance(stats_raw, list):
        for item in stats_raw:
            if not isinstance(item, dict):
                continue
            value = normalize_single_line(item.get("value"))
            label = normalize_single_line(item.get("label"))
            note = normalize_single_line(item.get("note"))
            if value and label and note:
                stats.append({"value": value, "label": label, "note": note})
            if len(stats) >= 2:
                break
    while len(stats) < 2:
        stats.append(
            {
                "value": "Ready" if len(stats) == 0 else "Review",
                "label": "Draft State" if len(stats) == 0 else "Next Step",
                "note": "Generated from the approved visual template." if len(stats) == 0 else "Open the draft on Modrinth and review the final layout.",
            }
        )

    clean_title = normalize_single_line(title) or "Example Mod"
    clean_summary = normalize_single_line(summary) or "A polished mod page presentation."
    return {
        "accent_color": normalize_hex_color(data.get("accent_color")),
        "background_image": normalize_background_choice(data.get("background_image"), background_images=background_images),
        "logo": {
            "eyebrow": normalize_single_line(logo_data.get("eyebrow")) or "Utility Mod",
            "mark": normalize_single_line(logo_data.get("mark")) or default_visual_mark(clean_title),
            "title": normalize_single_line(logo_data.get("title")) or clean_title,
            "subtitle": normalize_single_line(logo_data.get("subtitle")) or clean_summary,
            "rail_left": normalize_single_line(logo_data.get("rail_left")) or "Draft Ready",
            "rail_right": normalize_single_line(logo_data.get("rail_right")) or "Manual Review",
        },
        "description": {
            "kicker": normalize_single_line(description_data.get("kicker")) or "Utility Mod",
            "title": normalize_single_line(description_data.get("title")) or clean_title,
            "tagline": normalize_single_line(description_data.get("tagline")) or clean_summary,
            "chips": chips[:4],
            "stats": stats[:2],
        },
    }


def parse_visual_prompt_response(raw_text: str) -> dict[str, str]:
    data = extract_json_object(raw_text)
    return data if isinstance(data, dict) else {}


def finalize_generated_listing(
    *,
    listing: dict[str, str],
    metadata: dict[str, Any],
    jar_path: Path,
) -> dict[str, str]:
    details = metadata.get("metadata", {})
    detected_name = normalize_single_line(details.get("name") or "")
    jar_display_name = humanize_name(jar_path.stem)
    mod_id_display_name = humanize_name(str(details.get("mod_id") or ""))
    fallback_name = mod_id_display_name or jar_display_name or humanize_name(str(jar_path.stem))
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
        name = choose_reference_display_name(
            detected_name=detected_name,
            jar_display_name=jar_display_name,
            mod_id_display_name=mod_id_display_name,
        )
    else:
        name = correct_generated_display_name(
            generated_name=name,
            detected_name=detected_name,
            jar_display_name=jar_display_name,
            mod_id_display_name=mod_id_display_name,
        )

    final_name = name or fallback_name or jar_path.stem
    long_description = align_long_description_heading(long_description, final_name)

    return {
        "name": final_name,
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
    jar_name = normalize_single_line(metadata.get("jar_name"))
    jar_display_name = humanize_name(Path(jar_name).stem if jar_name else "")
    values: list[tuple[str, str]] = [
        ("jar_name", jar_name),
        ("jar_display_name", jar_display_name),
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


TRAILING_NAME_NOISE_TOKENS = {"forge", "fabric", "quilt", "neoforge"}


def split_display_name_tokens(value: str) -> list[str]:
    text = str(value or "").strip()
    if not text:
        return []
    text = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", text)
    text = re.sub(r"[_\-]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text.split(" ") if text else []


def is_probable_version_token(token: str) -> bool:
    lowered = token.lower().strip()
    if not lowered:
        return False
    if re.fullmatch(r"v?\d+(?:\.\d+){1,4}[a-z0-9._-]*", lowered):
        return True
    if re.fullmatch(r"mc\d+(?:\.\d+){1,4}", lowered):
        return True
    if re.fullmatch(r"(?:alpha|beta|rc)\d*", lowered):
        return True
    return False


def format_display_name_token(token: str) -> str:
    stripped = token.strip()
    if not stripped:
        return ""
    if stripped.isdigit() or re.fullmatch(r"\d+(?:\.\d+)+", stripped):
        return stripped
    if stripped.upper() == stripped and any(char.isalpha() for char in stripped):
        return stripped
    if re.fullmatch(r"[a-z]{2,4}", stripped) and not any(char in "aeiou" for char in stripped):
        return stripped.upper()
    return stripped.capitalize()


def humanize_name(value: str) -> str:
    tokens = split_display_name_tokens(str(value or ""))
    while tokens and is_probable_version_token(tokens[-1]):
        tokens.pop()
    while tokens and tokens[-1].lower() in TRAILING_NAME_NOISE_TOKENS and len(tokens) > 1:
        tokens.pop()
    if not tokens:
        tokens = split_display_name_tokens(str(value or ""))
    return " ".join(part for part in (format_display_name_token(token) for token in tokens) if part)


def discover_background_images(background_dir: Path | None = None) -> tuple[BackgroundImageChoice, ...]:
    root = (background_dir or Path(DEFAULT_BACKGROUND_IMAGES_DIR)).resolve()
    if not root.exists():
        raise ModCompilerError(f"Background image folder does not exist: {root}")
    if not root.is_dir():
        raise ModCompilerError(f"Background image path is not a folder: {root}")

    paths = sorted(
        path
        for path in root.rglob("*")
        if path.is_file()
        and not path.name.startswith(".")
        and path.suffix.lower() in BACKGROUND_IMAGE_EXTENSIONS
    )
    if not paths:
        raise ModCompilerError(
            f"No usable background images were found in {root}. "
            f"Supported extensions: {', '.join(sorted(BACKGROUND_IMAGE_EXTENSIONS))}"
        )

    return tuple(
        BackgroundImageChoice(
            path=path.resolve(),
            relative_name=path.relative_to(root).as_posix(),
            label=humanize_name(path.stem) or path.stem,
        )
        for path in paths
    )


def render_background_image_choices(background_images: tuple[BackgroundImageChoice, ...]) -> str:
    return "\n".join(
        f"- {choice.relative_name} -> {choice.label}"
        for choice in background_images
    )


def normalize_background_choice(
    value: Any,
    *,
    background_images: tuple[BackgroundImageChoice, ...],
) -> str:
    if not background_images:
        return ""

    raw = normalize_single_line(value).replace("\\", "/")
    if raw:
        lowered = raw.lower()
        for choice in background_images:
            if lowered == choice.relative_name.lower() or lowered == choice.path.name.lower():
                return choice.relative_name
        raw_comp = comparable_text(raw)
        for choice in background_images:
            if raw_comp in {
                comparable_text(choice.relative_name),
                comparable_text(choice.path.name),
                comparable_text(choice.label),
            }:
                return choice.relative_name

    return background_images[0].relative_name


def resolve_background_image_choice(
    selected_name: str,
    *,
    background_images: tuple[BackgroundImageChoice, ...],
) -> BackgroundImageChoice:
    normalized_name = normalize_background_choice(selected_name, background_images=background_images)
    for choice in background_images:
        if choice.relative_name == normalized_name:
            return choice
    return background_images[0]


def tokenize_name_words(value: str) -> list[str]:
    return re.findall(r"[a-z0-9]+", normalize_single_line(value).lower())


def bounded_levenshtein_distance(left: str, right: str, *, max_distance: int = 2) -> int | None:
    if left == right:
        return 0
    if abs(len(left) - len(right)) > max_distance:
        return None
    previous = list(range(len(right) + 1))
    for left_index, left_char in enumerate(left, start=1):
        current = [left_index]
        row_min = current[0]
        for right_index, right_char in enumerate(right, start=1):
            substitution_cost = 0 if left_char == right_char else 1
            current.append(
                min(
                    previous[right_index] + 1,
                    current[right_index - 1] + 1,
                    previous[right_index - 1] + substitution_cost,
                )
            )
            row_min = min(row_min, current[-1])
        if row_min > max_distance:
            return None
        previous = current
    distance = previous[-1]
    return distance if distance <= max_distance else None


def names_have_obvious_single_token_typo(left: str, right: str) -> bool:
    left_tokens = tokenize_name_words(left)
    right_tokens = tokenize_name_words(right)
    if len(left_tokens) != len(right_tokens) or len(left_tokens) < 2:
        return False
    mismatches = [(left_token, right_token) for left_token, right_token in zip(left_tokens, right_tokens) if left_token != right_token]
    if len(mismatches) != 1:
        return False
    left_token, right_token = mismatches[0]
    if min(len(left_token), len(right_token)) < 3:
        return False
    return bounded_levenshtein_distance(left_token, right_token, max_distance=2) is not None


def choose_reference_display_name(
    *,
    detected_name: str,
    jar_display_name: str,
    mod_id_display_name: str,
) -> str:
    if detected_name and jar_display_name and names_have_obvious_single_token_typo(detected_name, jar_display_name):
        return jar_display_name
    return detected_name or jar_display_name or mod_id_display_name


def correct_generated_display_name(
    *,
    generated_name: str,
    detected_name: str,
    jar_display_name: str,
    mod_id_display_name: str,
) -> str:
    reference_name = choose_reference_display_name(
        detected_name=detected_name,
        jar_display_name=jar_display_name,
        mod_id_display_name=mod_id_display_name,
    )
    if not reference_name:
        return generated_name
    if comparable_text(generated_name) == comparable_text(reference_name):
        return reference_name
    if names_have_obvious_single_token_typo(generated_name, reference_name):
        return reference_name
    return generated_name


def align_long_description_heading(body: str, title: str) -> str:
    lines = body.splitlines()
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("# "):
            lines[index] = f"# {title}"
            return "\n".join(lines).strip()
        break
    return body


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


def extract_image_urls_from_chat_parts(parts: Any) -> list[str]:
    if isinstance(parts, dict):
        parts = [parts]
    if not isinstance(parts, list):
        return []

    urls: list[str] = []
    for part in parts:
        if not isinstance(part, dict):
            continue
        image_url = part.get("image_url")
        if isinstance(image_url, dict):
            url = normalize_single_line(image_url.get("url"))
            if url:
                urls.append(url)
    return dedupe_preserve_order(urls)


def request_local_c05_chat(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    reasoning_effort: str = "",
    extra_body: dict[str, Any] | None = None,
    timeout_seconds: int,
    session_id: str,
    app_id: str,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "user_prompt": user_prompt,
        "model": model,
        "hoster": hoster,
        "app_id": app_id,
        "session_id": session_id,
        "include_history": False,
    }
    if reasoning_effort:
        payload["reasoning_effort"] = reasoning_effort
    if extra_body:
        payload["extra_body"] = extra_body
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/chat",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/x-ndjson"},
    )

    chunks: list[str] = []
    image_urls: list[str] = []
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
                if event.get("event") == "content_part":
                    image_urls.extend(extract_image_urls_from_chat_parts(event.get("content")))
                    continue
                if event.get("event") == "content_parts":
                    image_urls.extend(extract_image_urls_from_chat_parts(event.get("content")))
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

    return {
        "text": "".join(chunks).strip(),
        "image_urls": dedupe_preserve_order(image_urls),
    }


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
    response = request_local_c05_chat(
        base_url=base_url,
        hoster=hoster,
        model=model,
        user_prompt=user_prompt,
        reasoning_effort=reasoning_effort,
        extra_body={
            "temperature": temperature,
            "max_tokens": max_tokens,
        },
        timeout_seconds=timeout_seconds,
        session_id=session_id,
        app_id=app_id,
    )
    text = str(response.get("text", "")).strip()
    if not text:
        raise ModCompilerError("C05 Local AI returned no content for the listing generation request.")
    return text


def request_visual_design_response(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    reasoning_effort: str,
    temperature: float,
    max_tokens: int,
    timeout_seconds: int,
    session_id_prefix: str,
    title: str,
    summary: str,
    background_images: tuple[BackgroundImageChoice, ...] = (),
) -> tuple[str, dict[str, Any], str, str]:
    response_text = stream_local_c05_chat(
        base_url=base_url,
        hoster=hoster,
        model=model,
        user_prompt=user_prompt,
        reasoning_effort=reasoning_effort,
        temperature=temperature,
        max_tokens=max_tokens,
        timeout_seconds=timeout_seconds,
        session_id=f"{session_id_prefix}-{uuid.uuid4().hex[:8]}",
        app_id="auto-create-modrinth-visual-prompts",
    )
    try:
        parsed = normalize_visual_design_response(
            parse_visual_prompt_response(response_text),
            title=title,
            summary=summary,
            background_images=background_images,
        )
    except Exception as error:
        raise ModCompilerError(f"Visual design response could not be parsed: {error}") from None
    return response_text, parsed, hoster, model


def request_local_c05_provider_api(
    *,
    base_url: str,
    hoster: str,
    operation: str,
    params: dict[str, Any],
    timeout_seconds: int,
) -> dict[str, Any]:
    payload = {
        "operation": operation,
        "params": params,
    }
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/hosters/{urllib.parse.quote(hoster, safe='')}/api/request",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            raw = response.read().decode("utf-8", errors="replace").strip()
    except (TimeoutError, socket.timeout):
        raise ModCompilerError(
            f"C05 Local AI {hoster} operation '{operation}' timed out after {timeout_seconds} seconds."
        ) from None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace").strip()
        raise ModCompilerError(
            f"C05 Local AI {hoster} operation '{operation}' failed with HTTP {error.code}. {detail}"
        ) from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Could not reach C05 Local AI at {base_url}: {error.reason}") from None

    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ModCompilerError(
            f"C05 Local AI {hoster} operation '{operation}' returned invalid JSON: {error}"
        ) from None
    if isinstance(data, dict) and data.get("error"):
        detail = normalize_single_line(data.get("message") or data.get("detail") or data.get("error"))
        raise ModCompilerError(f"C05 Local AI {hoster} operation '{operation}' failed: {detail}")
    if not isinstance(data, dict):
        raise ModCompilerError(
            f"C05 Local AI {hoster} operation '{operation}' returned an unexpected response shape."
        )
    return data


def build_bundle_metadata(
    *,
    jar_path: Path,
    bundle_slug: str,
    metadata: dict[str, Any],
    options: GenerateOptions,
    listing: dict[str, str] | None,
    listing_ai_usage: dict[str, str] | None = None,
    visual_assets: dict[str, Any] | None = None,
) -> dict[str, Any]:
    metadata_warnings = list(metadata.get("warnings", []))
    if visual_assets:
        metadata_warnings.extend(str(item) for item in visual_assets.get("warnings", []) or [] if str(item).strip())
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
        "warnings": metadata_warnings,
        "local_ai": {
            "url": options.c05_url,
            "primary_hoster": options.c05_hoster,
            "primary_model": options.c05_model,
            "listing_hoster": (listing_ai_usage or {}).get("hoster", options.c05_hoster),
            "listing_model": (listing_ai_usage or {}).get("model", options.c05_model),
            "reasoning_effort": options.reasoning_effort,
        },
        "listing": listing or {},
        "visual_assets": visual_assets or {},
    }


def normalize_image_extension(value: str) -> str:
    ext = str(value or "").strip().lower().lstrip(".")
    if ext == "jpg":
        return "jpeg"
    if ext in {"png", "jpeg", "bmp", "gif", "webp", "svg", "svgz", "rgb"}:
        return ext
    return "png"


def normalize_visual_prompt_text(value: Any, *, max_words: int = 24, max_chars: int = 180) -> str:
    text = normalize_single_line(value)
    if not text:
        return ""
    text = re.sub(r"\s+", " ", text).strip()
    first_sentence = re.split(r"(?<=[.!?])\s+", text, maxsplit=1)[0].strip()
    if first_sentence:
        text = first_sentence
    words = text.split()
    if len(words) > max_words:
        text = " ".join(words[:max_words]).rstrip(",;:-")
    if len(text) > max_chars:
        text = text[:max_chars].rstrip(",;:- ")
    text = re.sub(r"(?i)\bthe text\b.*", "", text).strip(" ,;:-")
    text = re.sub(r"(?i)\btitle\b.*", "", text).strip(" ,;:-")
    text = re.sub(r"(?i)\btypography\b.*", "", text).strip(" ,;:-")
    if text and text[-1] not in ".!?":
        text += "."
    return text


def resolve_overlay_font_paths() -> list[Path]:
    return [
        Path("/System/Library/Fonts/HelveticaNeue.ttc"),
        Path("/System/Library/Fonts/Avenir Next Condensed.ttc"),
        Path("/System/Library/Fonts/SFCompact.ttf"),
        Path("/System/Library/Fonts/LucidaGrande.ttc"),
        Path("/Library/Fonts/Arial.ttf"),
    ]


def load_overlay_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in resolve_overlay_font_paths():
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size=size)
            except Exception:
                continue
    return ImageFont.load_default()


def wrap_title_lines(
    *,
    draw: ImageDraw.ImageDraw,
    title: str,
    font: ImageFont.ImageFont,
    max_width: int,
) -> list[str]:
    words = [word for word in title.split() if word.strip()]
    if not words:
        return [title]
    lines: list[str] = []
    current = words[0]
    for word in words[1:]:
        candidate = f"{current} {word}"
        bbox = draw.textbbox((0, 0), candidate, font=font)
        if (bbox[2] - bbox[0]) <= max_width:
            current = candidate
            continue
        lines.append(current)
        current = word
    lines.append(current)
    return lines


def render_title_overlay_on_image(
    *,
    image_path: Path,
    title: str,
    dim_factor: float = TITLE_OVERLAY_DIM_FACTOR,
) -> None:
    clean_title = normalize_single_line(title)
    if not clean_title:
        return

    with Image.open(image_path) as opened:
        image = opened.convert("RGBA")
        image = ImageEnhance.Brightness(image).enhance(dim_factor)
        draw = ImageDraw.Draw(image)
        width, height = image.size
        target_width = int(width * 0.84)
        max_height = int(height * 0.58)
        font_size = max(24, int(min(width, height) * 0.22))
        lines: list[str] = [clean_title]
        font: ImageFont.ImageFont = load_overlay_font(font_size)

        while font_size >= 18:
            font = load_overlay_font(font_size)
            lines = wrap_title_lines(draw=draw, title=clean_title, font=font, max_width=target_width)
            line_heights: list[int] = []
            text_width = 0
            for line in lines:
                bbox = draw.textbbox((0, 0), line, font=font)
                text_width = max(text_width, bbox[2] - bbox[0])
                line_heights.append(bbox[3] - bbox[1])
            spacing = max(6, font_size // 7)
            total_height = sum(line_heights) + spacing * max(0, len(lines) - 1)
            if text_width <= target_width and total_height <= max_height:
                break
            font_size -= 2

        spacing = max(6, font_size // 7)
        line_metrics: list[tuple[str, tuple[int, int, int, int]]] = [
            (line, draw.textbbox((0, 0), line, font=font))
            for line in lines
        ]
        total_height = sum(metric[1][3] - metric[1][1] for metric in line_metrics) + spacing * max(0, len(lines) - 1)
        y = (height - total_height) / 2
        shadow_offset = max(2, font_size // 18)
        for line, bbox in line_metrics:
            line_width = bbox[2] - bbox[0]
            line_height = bbox[3] - bbox[1]
            x = (width - line_width) / 2
            draw.text((x + shadow_offset, y + shadow_offset), line, font=font, fill=(0, 0, 0, 140))
            draw.text((x, y), line, font=font, fill=(255, 255, 255, 255))
            y += line_height + spacing

        save_image = image.convert("RGB") if image_path.suffix.lower() in {".jpg", ".jpeg", ".bmp"} else image
        save_image.save(image_path)


def detect_image_extension_from_bytes(data: bytes, *, fallback: str = "png") -> str:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "gif"
    if data.startswith(b"BM"):
        return "bmp"
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    stripped = data.lstrip()
    if stripped.startswith(b"<?xml") or stripped.startswith(b"<svg"):
        return "svg"
    return normalize_image_extension(fallback)


def detect_image_extension(*, url: str, content_type: str) -> str:
    content_type = str(content_type or "").strip().lower()
    if "/" in content_type:
        subtype = content_type.split("/", 1)[1].split(";", 1)[0].strip()
        if subtype:
            return normalize_image_extension(subtype)
    parsed = urllib.parse.urlparse(url)
    suffix = Path(parsed.path).suffix.lower().lstrip(".")
    return normalize_image_extension(suffix)


def decode_base64_image_payload(image_base64: str) -> tuple[bytes, str]:
    raw = str(image_base64 or "").strip()
    if not raw:
        raise ModCompilerError("Generated image payload was empty.")

    content_type = ""
    if raw.startswith("data:"):
        header, separator, encoded = raw.partition(",")
        if separator:
            content_type = normalize_single_line(header[5:].split(";", 1)[0])
            raw = encoded.strip()

    padding = "=" * ((4 - len(raw) % 4) % 4)
    try:
        data = base64.b64decode(raw + padding, validate=False)
    except (ValueError, TypeError) as error:
        raise ModCompilerError(f"Could not decode generated image payload: {error}") from None
    if not data:
        raise ModCompilerError("Generated image payload decoded to empty bytes.")
    return data, content_type


def extract_aihorde_generated_images(payload: Any) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    seen: set[tuple[str, str, str]] = set()

    def append_candidate(image_url: str, image_base64: str, content_type: str) -> None:
        clean_url = normalize_single_line(image_url)
        clean_base64 = str(image_base64 or "").strip()
        clean_content_type = normalize_single_line(content_type)
        if not clean_url and not clean_base64:
            return
        dedupe_key = (clean_url, clean_base64[:80], clean_content_type)
        if dedupe_key in seen:
            return
        seen.add(dedupe_key)
        results.append(
            {
                "image_url": clean_url,
                "image_base64": clean_base64,
                "content_type": clean_content_type,
            }
        )

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            image_url = node.get("image_url")
            image_base64 = node.get("image_base64")
            if not image_base64 and isinstance(node.get("img"), str):
                image_base64 = node.get("img")
            content_type = node.get("content_type") or node.get("mime_type") or node.get("mime")
            if isinstance(image_url, str) or isinstance(image_base64, str):
                append_candidate(
                    str(image_url or ""),
                    str(image_base64 or ""),
                    str(content_type or ""),
                )
            for value in node.values():
                walk(value)
            return
        if isinstance(node, list):
            for item in node:
                walk(item)

    walk(payload)
    return results


def extract_aihorde_job_id(payload: Any) -> str:
    def walk(node: Any) -> str:
        if isinstance(node, dict):
            for key in ("job_id", "id"):
                value = normalize_single_line(node.get(key))
                if value:
                    return value
            for value in node.values():
                found = walk(value)
                if found:
                    return found
            return ""
        if isinstance(node, list):
            for item in node:
                found = walk(item)
                if found:
                    return found
        return ""

    return walk(payload)


def build_aihorde_image_submit_params(
    *,
    prompt: str,
    generation_params: dict[str, Any] | None = None,
    image_model: str = "",
) -> dict[str, Any]:
    payload = {
        "prompt": prompt,
        "r2": True,
        "params": {
            "steps": 15,
            "n": 1,
        },
    }
    if generation_params:
        payload["params"].update(generation_params)
    clean_image_model = normalize_single_line(image_model)
    if clean_image_model:
        payload["models"] = [clean_image_model]
    return payload


def download_binary_url(url: str, *, timeout_seconds: int = 300) -> tuple[bytes, str]:
    request = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            return response.read(), str(response.headers.get_content_type() or "")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace").strip()
        raise ModCompilerError(f"Failed to download generated image ({error.code}): {detail}") from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Could not download generated image: {error.reason}") from None


def generate_image_asset_via_c05(
    *,
    base_url: str,
    prompt: str,
    output_path_stem: Path,
    timeout_seconds: int,
    generation_params: dict[str, Any] | None = None,
) -> dict[str, str]:
    provider_timeout_seconds = max(60, min(int(timeout_seconds), 1_170))
    request_timeout_seconds = min(provider_timeout_seconds + 90, 1_260)
    request_params = build_aihorde_image_submit_params(
        prompt=prompt,
        generation_params=generation_params,
        image_model=DEFAULT_IMAGE_C05_MODEL,
    )
    request_params["timeout_seconds"] = provider_timeout_seconds
    request_params["poll_interval_seconds"] = DEFAULT_IMAGE_POLL_INTERVAL_SECONDS

    response = request_local_c05_provider_api(
        base_url=base_url,
        hoster=DEFAULT_IMAGE_C05_HOSTER,
        operation="generate_image_and_wait",
        params=request_params,
        timeout_seconds=request_timeout_seconds,
    )

    candidates = extract_aihorde_generated_images(response)
    if not candidates:
        raise ModCompilerError("C05 AI Horde returned no generated image output.")

    image_url = candidates[0]["image_url"]
    content_type = candidates[0]["content_type"]
    if image_url:
        data, downloaded_content_type = download_binary_url(image_url, timeout_seconds=provider_timeout_seconds)
        if downloaded_content_type:
            content_type = downloaded_content_type
        extension = detect_image_extension(url=image_url, content_type=content_type)
    else:
        data, inline_content_type = decode_base64_image_payload(candidates[0]["image_base64"])
        if inline_content_type:
            content_type = inline_content_type
        extension = detect_image_extension_from_bytes(data, fallback=content_type or "webp")

    output_path = output_path_stem.with_suffix(f".{extension}")
    output_path.write_bytes(data)
    return {
        "file": output_path.name,
        "source_url": image_url,
        "content_type": content_type,
    }


def copy_visual_background_asset(art_dir: Path, source: Path) -> Path:
    with Image.open(source) as opened:
        prepared = ImageOps.fit(
            opened.convert("RGB"),
            (VISUAL_RENDER_SIZE, VISUAL_RENDER_SIZE),
            Image.Resampling.LANCZOS,
            centering=(0.5, 0.5),
        )
    return save_image_with_size_constraints(
        image=prepared,
        output_path_stem=art_dir / Path(VISUAL_BACKGROUND_FILENAME).stem,
    )


def build_visual_html_document(
    *,
    title: str,
    body_html: str,
    css: str,
    variant: str,
    background_filename: str = VISUAL_BACKGROUND_FILENAME,
) -> str:
    safe_title = html_lib.escape(normalize_single_line(title) or "Mod")
    body_class = "modcompiler-description" if variant == "description" else "modcompiler-logo"
    body_opacity = "0.80" if variant == "description" else "1"
    safe_background_filename = html_lib.escape(background_filename)
    return textwrap.dedent(
        f"""
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>{safe_title}</title>
          <style>
            :root {{
              color-scheme: dark;
            }}

            * {{
              box-sizing: border-box;
            }}

            html, body {{
              margin: 0;
              width: {VISUAL_DESIGN_SIZE}px;
              height: {VISUAL_DESIGN_SIZE}px;
              overflow: hidden;
              background: #000;
            }}

            body {{
              position: relative;
              font-family: "Helvetica Neue", "Avenir Next", "SF Pro Display", Arial, sans-serif;
              color: #fff;
              isolation: isolate;
            }}

            body::before {{
              content: "";
              position: absolute;
              inset: 0;
              background:
                linear-gradient(180deg, rgba(0, 0, 0, 0.18), rgba(0, 0, 0, 0.72)),
                url("{safe_background_filename}") center center / cover no-repeat;
              filter: saturate(1.05) brightness(0.68);
              transform: scale(1.06);
              transform-origin: center;
            }}

            body::after {{
              content: "";
              position: absolute;
              inset: 0;
              background:
                radial-gradient(circle at top left, rgba(255,255,255,0.12), transparent 38%),
                radial-gradient(circle at bottom right, rgba(255,255,255,0.10), transparent 34%);
              mix-blend-mode: screen;
              pointer-events: none;
            }}

            #modcompiler-root {{
              position: relative;
              z-index: 1;
              width: 100%;
              height: 100%;
              opacity: {body_opacity};
            }}

            {css}
          </style>
        </head>
        <body class="{body_class}">
          <div id="modcompiler-root">
            {body_html}
          </div>
        </body>
        </html>
        """
    ).strip() + "\n"


def css_variable_style(variables: dict[str, str]) -> str:
    return "; ".join(f"{key}: {value}" for key, value in variables.items() if str(value or "").strip())


def compute_logo_layout_variables(title: str) -> dict[str, str]:
    clean_title = normalize_single_line(title)
    words = re.findall(r"[A-Za-z0-9]+", clean_title)
    char_count = len(clean_title)
    max_word_length = max((len(word) for word in words), default=0)
    single_word = len(words) == 1

    if char_count >= 28 or max_word_length >= 13 or (single_word and max_word_length >= 12):
        return {
            "--frame-inset": "26px",
            "--bracket-inset": "56px",
            "--content-pad-top": "84px",
            "--content-pad-x": "48px",
            "--content-pad-bottom": "72px",
            "--eyebrow-size": "14px",
            "--eyebrow-spacing": "0.20em",
            "--mark-size": "230px",
            "--title-size": "44px",
            "--title-line-height": "0.96",
            "--title-max-width": "100%",
            "--sub-size": "19px",
            "--sub-max-width": "100%",
            "--rail-inset": "54px",
            "--rail-size": "13px",
            "--rail-spacing": "0.18em",
        }
    if char_count >= 20 or max_word_length >= 10 or (single_word and max_word_length >= 9):
        return {
            "--frame-inset": "40px",
            "--bracket-inset": "78px",
            "--content-pad-top": "92px",
            "--content-pad-x": "70px",
            "--content-pad-bottom": "82px",
            "--eyebrow-size": "15px",
            "--eyebrow-spacing": "0.24em",
            "--mark-size": "270px",
            "--title-size": "56px",
            "--title-line-height": "0.95",
            "--title-max-width": "100%",
            "--sub-size": "21px",
            "--sub-max-width": "100%",
            "--rail-inset": "72px",
            "--rail-size": "15px",
            "--rail-spacing": "0.22em",
        }
    if char_count >= 15 or max_word_length >= 8:
        return {
            "--frame-inset": "52px",
            "--bracket-inset": "98px",
            "--content-pad-top": "102px",
            "--content-pad-x": "90px",
            "--content-pad-bottom": "88px",
            "--eyebrow-size": "17px",
            "--eyebrow-spacing": "0.28em",
            "--mark-size": "300px",
            "--title-size": "66px",
            "--title-line-height": "0.94",
            "--title-max-width": "94%",
            "--sub-size": "22px",
            "--sub-max-width": "720px",
            "--rail-inset": "92px",
            "--rail-size": "17px",
            "--rail-spacing": "0.24em",
        }
    return {
        "--frame-inset": "62px",
        "--bracket-inset": "112px",
        "--content-pad-top": "110px",
        "--content-pad-x": "110px",
        "--content-pad-bottom": "94px",
        "--eyebrow-size": "18px",
        "--eyebrow-spacing": "0.32em",
        "--mark-size": "320px",
        "--title-size": "74px",
        "--title-line-height": "0.94",
        "--title-max-width": "88%",
        "--sub-size": "24px",
        "--sub-max-width": "660px",
        "--rail-inset": "110px",
        "--rail-size": "18px",
        "--rail-spacing": "0.26em",
    }


def compute_description_layout_variables(title: str) -> dict[str, str]:
    clean_title = normalize_single_line(title)
    words = re.findall(r"[A-Za-z0-9]+", clean_title)
    char_count = len(clean_title)
    max_word_length = max((len(word) for word in words), default=0)
    single_word = len(words) == 1

    if char_count >= 30 or max_word_length >= 13 or (single_word and max_word_length >= 12):
        return {
            "--root-pad-top": "112px",
            "--root-pad-bottom": "112px",
            "--root-pad-x": "54px",
            "--layout-gap": "22px",
            "--side-width": "188px",
            "--hero-pad-y": "34px",
            "--hero-pad-x": "38px",
            "--kicker-size": "13px",
            "--kicker-spacing": "0.18em",
            "--description-title-size": "62px",
            "--description-title-line-height": "0.96",
            "--tagline-size": "20px",
            "--tagline-max-width": "100%",
            "--chip-size": "13px",
            "--stat-value-size": "42px",
            "--stat-label-size": "13px",
            "--stat-note-size": "14px",
            "--hero-stack-gap": "12px",
            "--meta-padding-top": "16px",
        }
    if char_count >= 22 or max_word_length >= 10 or (single_word and max_word_length >= 9):
        return {
            "--root-pad-top": "126px",
            "--root-pad-bottom": "126px",
            "--root-pad-x": "68px",
            "--layout-gap": "28px",
            "--side-width": "214px",
            "--hero-pad-y": "40px",
            "--hero-pad-x": "46px",
            "--kicker-size": "14px",
            "--kicker-spacing": "0.20em",
            "--description-title-size": "74px",
            "--description-title-line-height": "0.95",
            "--tagline-size": "22px",
            "--tagline-max-width": "100%",
            "--chip-size": "14px",
            "--stat-value-size": "48px",
            "--stat-label-size": "14px",
            "--stat-note-size": "15px",
            "--hero-stack-gap": "14px",
            "--meta-padding-top": "18px",
        }
    if char_count >= 16 or max_word_length >= 8:
        return {
            "--root-pad-top": "140px",
            "--root-pad-bottom": "140px",
            "--root-pad-x": "82px",
            "--layout-gap": "34px",
            "--side-width": "238px",
            "--hero-pad-y": "48px",
            "--hero-pad-x": "56px",
            "--kicker-size": "15px",
            "--kicker-spacing": "0.22em",
            "--description-title-size": "88px",
            "--description-title-line-height": "0.94",
            "--tagline-size": "25px",
            "--tagline-max-width": "100%",
            "--chip-size": "15px",
            "--stat-value-size": "52px",
            "--stat-label-size": "15px",
            "--stat-note-size": "16px",
            "--hero-stack-gap": "16px",
            "--meta-padding-top": "20px",
        }
    return {
        "--root-pad-top": "154px",
        "--root-pad-bottom": "154px",
        "--root-pad-x": "94px",
        "--layout-gap": "42px",
        "--side-width": "260px",
        "--hero-pad-y": "54px",
        "--hero-pad-x": "68px",
        "--kicker-size": "16px",
        "--kicker-spacing": "0.24em",
        "--description-title-size": "118px",
        "--description-title-line-height": "0.9",
        "--tagline-size": "30px",
        "--tagline-max-width": "680px",
        "--chip-size": "16px",
        "--stat-value-size": "58px",
        "--stat-label-size": "15px",
        "--stat-note-size": "16px",
        "--hero-stack-gap": "18px",
        "--meta-padding-top": "22px",
    }


def build_logo_template_body_html(*, visual_data: dict[str, Any]) -> str:
    logo = visual_data["logo"]
    eyebrow = html_lib.escape(logo["eyebrow"])
    mark = html_lib.escape(logo["mark"])
    title = html_lib.escape(logo["title"])
    subtitle = html_lib.escape(logo["subtitle"])
    rail_left = html_lib.escape(logo["rail_left"])
    rail_right = html_lib.escape(logo["rail_right"])
    frame_style = html_lib.escape(css_variable_style(compute_logo_layout_variables(logo["title"])))
    return (
        f'<div class="frame" style="{frame_style}">'
        '<div class="rings"><div class="ring one"></div><div class="ring two"></div><div class="ring three"></div></div>'
        '<div class="brackets"><span class="tl"></span><span class="tr"></span><span class="bl"></span><span class="br"></span></div>'
        '<div class="content">'
        f'<div class="eyebrow">{eyebrow}</div>'
        f'<div class="mark">{mark}</div>'
        f'<div class="title">{title}</div>'
        f'<div class="sub">{subtitle}</div>'
        '</div>'
        f'<div class="rail"><span>{rail_left}</span><span class="warn">{rail_right}</span></div>'
        '</div>'
    )


def build_logo_template_css(*, accent_color: str) -> str:
    return textwrap.dedent(
        f"""
        .frame {{
          position: absolute;
          inset: var(--frame-inset, 62px);
          border-radius: 42px;
          background:
            linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.015)),
            rgba(14, 14, 16, 0.64);
          border: 1px solid rgba(255, 255, 255, 0.12);
          box-shadow:
            0 35px 100px rgba(0, 0, 0, 0.55),
            inset 0 1px 0 rgba(255, 255, 255, 0.08),
            inset 0 -24px 40px rgba(0, 0, 0, 0.35);
          backdrop-filter: blur(14px);
          overflow: hidden;
        }}
        .frame::before,
        .frame::after {{
          content: "";
          position: absolute;
          width: 320px;
          height: 320px;
          border-radius: 999px;
          filter: blur(18px);
          opacity: 0.72;
        }}
        .frame::before {{
          top: -96px;
          left: -82px;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 75%, white 25%), transparent 72%);
        }}
        .frame::after {{
          right: -100px;
          bottom: -120px;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 68%, black 12%), transparent 72%);
        }}
        .rings {{
          position: absolute;
          inset: 0;
          display: grid;
          place-items: center;
          pointer-events: none;
        }}
        .ring {{
          position: absolute;
          border-radius: 50%;
          border: 1px solid rgba(255, 255, 255, 0.12);
          box-shadow: 0 0 0 1px color-mix(in srgb, {accent_color} 22%, transparent) inset;
        }}
        .ring.one {{ width: 610px; height: 610px; }}
        .ring.two {{ width: 470px; height: 470px; }}
        .ring.three {{ width: 310px; height: 310px; }}
        .brackets {{
          position: absolute;
          inset: var(--bracket-inset, 112px);
          pointer-events: none;
        }}
        .brackets span {{
          position: absolute;
          width: 92px;
          height: 92px;
          border-color: rgba(255, 255, 255, 0.24);
          border-style: solid;
          opacity: 0.88;
        }}
        .brackets .tl {{ top: 0; left: 0; border-width: 2px 0 0 2px; }}
        .brackets .tr {{ top: 0; right: 0; border-width: 2px 2px 0 0; }}
        .brackets .bl {{ bottom: 0; left: 0; border-width: 0 0 2px 2px; }}
        .brackets .br {{ bottom: 0; right: 0; border-width: 0 2px 2px 0; }}
        .content {{
          position: relative;
          z-index: 2;
          height: 100%;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          min-width: 0;
          padding:
            var(--content-pad-top, 110px)
            var(--content-pad-x, 110px)
            var(--content-pad-bottom, 94px);
          text-align: center;
        }}
        .eyebrow {{
          margin-bottom: 26px;
          padding: 10px 18px 11px;
          border-radius: 999px;
          border: 1px solid rgba(255, 255, 255, 0.14);
          background: rgba(255, 255, 255, 0.05);
          font-size: var(--eyebrow-size, 18px);
          font-weight: 700;
          letter-spacing: var(--eyebrow-spacing, 0.32em);
          text-transform: uppercase;
          color: rgba(255, 255, 255, 0.82);
          box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
        }}
        .mark {{
          position: relative;
          line-height: 0.82;
          font-size: var(--mark-size, 320px);
          font-weight: 900;
          letter-spacing: -0.08em;
          text-transform: uppercase;
          color: #f4f7fb;
          text-shadow:
            0 0 18px rgba(255, 255, 255, 0.28),
            0 0 46px rgba(255, 255, 255, 0.18),
            0 26px 65px rgba(0, 0, 0, 0.48);
        }}
        .mark::after {{
          content: "";
          position: absolute;
          left: 6%;
          right: 6%;
          top: 54%;
          height: 18px;
          border-radius: 999px;
          background: linear-gradient(90deg, transparent, {accent_color}, transparent);
          box-shadow: 0 0 18px color-mix(in srgb, {accent_color} 82%, transparent);
          transform: skewX(-18deg);
        }}
        .title {{
          margin-top: 18px;
          max-width: var(--title-max-width, 88%);
          font-size: var(--title-size, 74px);
          line-height: var(--title-line-height, 0.94);
          font-weight: 900;
          letter-spacing: -0.04em;
          text-transform: uppercase;
          color: white;
          text-wrap: balance;
          overflow-wrap: normal;
          word-break: keep-all;
          hyphens: none;
        }}
        .sub {{
          margin-top: 22px;
          max-width: var(--sub-max-width, 660px);
          font-size: var(--sub-size, 24px);
          line-height: 1.45;
          color: rgba(255, 255, 255, 0.74);
          letter-spacing: 0.02em;
          text-wrap: pretty;
        }}
        .rail {{
          position: absolute;
          left: var(--rail-inset, 110px);
          right: var(--rail-inset, 110px);
          bottom: 72px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 16px;
          font-size: var(--rail-size, 18px);
          font-weight: 700;
          letter-spacing: var(--rail-spacing, 0.26em);
          text-transform: uppercase;
          color: rgba(255, 255, 255, 0.56);
        }}
        .rail span {{
          min-width: 0;
        }}
        .rail::before {{
          content: "";
          position: absolute;
          left: 0;
          right: 0;
          top: -20px;
          height: 1px;
          background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.10), transparent);
        }}
        .rail .warn {{
          color: color-mix(in srgb, {accent_color} 76%, white 24%);
        }}
        """
    ).strip()


def build_description_template_body_html(*, visual_data: dict[str, Any]) -> str:
    description = visual_data["description"]
    kicker = html_lib.escape(description["kicker"])
    title = html_lib.escape(description["title"])
    tagline = html_lib.escape(description["tagline"])
    chips_html = "".join(f'<div class="chip">{html_lib.escape(chip)}</div>' for chip in description["chips"])
    stats_html = "".join(
        (
            '<section class="stat">'
            f'<div class="value">{html_lib.escape(stat["value"])}</div>'
            f'<div class="label">{html_lib.escape(stat["label"])}</div>'
            f'<div class="note">{html_lib.escape(stat["note"])}</div>'
            '</section>'
        )
        for stat in description["stats"]
    )
    root_style = html_lib.escape(css_variable_style(compute_description_layout_variables(description["title"])))
    return (
        f'<div id="root" style="{root_style}"><div class="layout">'
        '<section class="hero">'
        f'<div class="kicker">{kicker}</div>'
        f'<h1>{title}</h1>'
        f'<p class="tagline">{tagline}</p>'
        f'<div class="meta">{chips_html}</div>'
        '</section>'
        f'<aside class="side">{stats_html}</aside>'
        '</div></div>'
    )


def build_description_template_css(*, accent_color: str) -> str:
    return textwrap.dedent(
        f"""
        #root {{
          position: relative;
          z-index: 1;
          width: 100%;
          height: 100%;
          opacity: 0.8;
          padding:
            var(--root-pad-top, 170px)
            var(--root-pad-x, 94px)
            var(--root-pad-bottom, 172px);
        }}
        .layout {{
          height: 100%;
          display: grid;
          grid-template-columns: minmax(0, 1fr) var(--side-width, 260px);
          gap: var(--layout-gap, 42px);
          align-items: stretch;
          min-height: 0;
        }}
        .hero {{
          position: relative;
          display: flex;
          flex-direction: column;
          justify-content: flex-start;
          gap: var(--hero-stack-gap, 18px);
          min-width: 0;
          padding: var(--hero-pad-y, 62px) var(--hero-pad-x, 68px);
          border-radius: 34px;
          background:
            linear-gradient(180deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02)),
            rgba(10, 10, 12, 0.46);
          border: 1px solid rgba(255, 255, 255, 0.14);
          box-shadow:
            0 30px 90px rgba(0, 0, 0, 0.45),
            inset 0 1px 0 rgba(255, 255, 255, 0.1);
          backdrop-filter: blur(14px);
          overflow: hidden;
        }}
        .hero::before {{
          content: "";
          position: absolute;
          top: -80px;
          left: -80px;
          width: 260px;
          height: 260px;
          border-radius: 50%;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 46%, transparent), transparent 72%);
          filter: blur(8px);
        }}
        .hero::after {{
          content: "";
          position: absolute;
          right: -120px;
          bottom: -120px;
          width: 300px;
          height: 300px;
          border-radius: 50%;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 38%, transparent), transparent 72%);
          filter: blur(16px);
        }}
        .kicker {{
          position: relative;
          display: inline-flex;
          width: fit-content;
          align-items: center;
          gap: 12px;
          padding: 11px 16px;
          border-radius: 999px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.14);
          color: rgba(255,255,255,0.82);
          font-size: var(--kicker-size, 16px);
          font-weight: 700;
          letter-spacing: var(--kicker-spacing, 0.24em);
          text-transform: uppercase;
        }}
        .kicker::before {{
          content: "";
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background: {accent_color};
          box-shadow: 0 0 14px color-mix(in srgb, {accent_color} 92%, transparent);
        }}
        h1 {{
          position: relative;
          margin: 0;
          max-width: 100%;
          font-size: var(--description-title-size, 118px);
          line-height: var(--description-title-line-height, 0.9);
          letter-spacing: -0.06em;
          font-weight: 900;
          color: white;
          text-wrap: balance;
          overflow-wrap: normal;
          word-break: keep-all;
          hyphens: none;
        }}
        .tagline {{
          position: relative;
          max-width: var(--tagline-max-width, 680px);
          margin: 0;
          font-size: var(--tagline-size, 30px);
          line-height: 1.45;
          color: rgba(244, 247, 251, 0.92);
          font-weight: 500;
          text-wrap: pretty;
        }}
        .meta {{
          position: relative;
          display: flex;
          gap: 14px;
          flex-wrap: wrap;
          margin-top: auto;
          padding-top: var(--meta-padding-top, 22px);
        }}
        .chip {{
          padding: 12px 16px;
          border-radius: 14px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.12);
          color: rgba(244, 247, 251, 0.74);
          font-size: var(--chip-size, 16px);
          font-weight: 700;
          letter-spacing: 0.08em;
          text-transform: uppercase;
        }}
        .side {{
          display: grid;
          grid-template-rows: 1fr 1fr;
          gap: 20px;
          min-height: 0;
        }}
        .stat {{
          position: relative;
          padding: 28px 24px;
          border-radius: 26px;
          background:
            linear-gradient(180deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02)),
            rgba(9, 9, 10, 0.48);
          border: 1px solid rgba(255,255,255,0.12);
          box-shadow: inset 0 1px 0 rgba(255,255,255,0.08);
          backdrop-filter: blur(12px);
          display: flex;
          flex-direction: column;
          justify-content: flex-end;
          min-height: 0;
          overflow: hidden;
        }}
        .stat::before {{
          content: "";
          position: absolute;
          left: 20px;
          right: 20px;
          top: 20px;
          height: 2px;
          background: linear-gradient(90deg, rgba(255,255,255,0.05), {accent_color}, rgba(255,255,255,0.05));
        }}
        .stat .value {{
          position: relative;
          font-size: var(--stat-value-size, 58px);
          line-height: 0.92;
          font-weight: 900;
          letter-spacing: -0.04em;
          color: white;
        }}
        .stat .label {{
          position: relative;
          margin-top: 10px;
          font-size: var(--stat-label-size, 15px);
          font-weight: 700;
          letter-spacing: 0.18em;
          text-transform: uppercase;
          color: rgba(255,255,255,0.62);
        }}
        .stat .note {{
          position: relative;
          margin-top: 16px;
          font-size: var(--stat-note-size, 16px);
          line-height: 1.45;
          color: rgba(255,255,255,0.74);
        }}
        """
    ).strip()


def render_html_preview_via_qlmanage(
    *,
    html_path: Path,
    render_size: int,
    timeout_seconds: int,
) -> bytes:
    qlmanage_path = Path("/usr/bin/qlmanage")
    if not qlmanage_path.exists():
        raise ModCompilerError("HTML visual rendering requires /usr/bin/qlmanage on macOS.")

    with tempfile.TemporaryDirectory(prefix="modcompiler-html-render-") as temp_dir:
        command = [
            str(qlmanage_path),
            "-t",
            "-s",
            str(render_size),
            "-o",
            temp_dir,
            str(html_path),
        ]
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=max(20, min(int(timeout_seconds), 180)),
            )
        except subprocess.TimeoutExpired:
            raise ModCompilerError(f"HTML render timed out for {html_path.name}.") from None
        if completed.returncode != 0:
            detail = (completed.stderr or completed.stdout or "").strip()
            raise ModCompilerError(f"qlmanage failed while rendering {html_path.name}: {detail}")

        output_path = Path(temp_dir) / f"{html_path.name}.png"
        if not output_path.exists():
            raise ModCompilerError(f"qlmanage did not produce a PNG for {html_path.name}.")
        return output_path.read_bytes()


def save_image_with_size_constraints(
    *,
    image: Image.Image,
    output_path_stem: Path,
    max_bytes: int | None = None,
) -> Path:
    working = image.convert("RGB")
    if max_bytes is None:
        buffer = io.BytesIO()
        working.save(buffer, format="WEBP", lossless=True, quality=100, method=6)
        output_path = output_path_stem.with_suffix(".webp")
        output_path.write_bytes(buffer.getvalue())
        return output_path

    for quality in (92, 88, 84, 80, 76, 72, 68, 64, 60):
        buffer = io.BytesIO()
        working.save(buffer, format="WEBP", quality=quality, method=6)
        data = buffer.getvalue()
        if max_bytes is None or len(data) <= max_bytes:
            output_path = output_path_stem.with_suffix(".webp")
            output_path.write_bytes(data)
            return output_path

    if max_bytes is not None:
        width, height = working.size
        for scale in (0.92, 0.86, 0.80, 0.74, 0.68, 0.62):
            resized = working.resize(
                (max(128, int(width * scale)), max(128, int(height * scale))),
                Image.Resampling.LANCZOS,
            )
            for quality in (72, 66, 60, 54):
                buffer = io.BytesIO()
                resized.save(buffer, format="WEBP", quality=quality, method=6)
                data = buffer.getvalue()
                if len(data) <= max_bytes:
                    output_path = output_path_stem.with_suffix(".webp")
                    output_path.write_bytes(data)
                    return output_path

    output_path = output_path_stem.with_suffix(".png")
    working.save(output_path, format="PNG", optimize=True)
    return output_path


def render_html_document_to_asset(
    *,
    html_path: Path,
    output_path_stem: Path,
    target_size: tuple[int, int],
    timeout_seconds: int,
) -> dict[str, str]:
    preview_bytes = render_html_preview_via_qlmanage(
        html_path=html_path,
        render_size=VISUAL_RENDER_SIZE,
        timeout_seconds=timeout_seconds,
    )
    with Image.open(io.BytesIO(preview_bytes)) as preview:
        fitted = ImageOps.fit(preview.convert("RGB"), target_size, Image.Resampling.LANCZOS, centering=(0.5, 0.5))
        output_path = save_image_with_size_constraints(
            image=fitted,
            output_path_stem=output_path_stem,
        )
    return {
        "file": output_path.name,
        "content_type": guess_content_type(output_path.name),
    }


def render_html_document_to_image(
    *,
    html_path: Path,
    timeout_seconds: int,
    target_size: tuple[int, int] | None = None,
) -> Image.Image:
    preview_bytes = render_html_preview_via_qlmanage(
        html_path=html_path,
        render_size=VISUAL_RENDER_SIZE,
        timeout_seconds=timeout_seconds,
    )
    with Image.open(io.BytesIO(preview_bytes)) as preview:
        image = preview.convert("RGB")
        if target_size:
            image = ImageOps.fit(image, target_size, Image.Resampling.LANCZOS, centering=(0.5, 0.5))
        return image.copy()


def save_vertical_poster_slices(
    *,
    image: Image.Image,
    output_dir: Path,
    file_stem_prefix: str,
    max_slice_height: int = DESCRIPTION_POSTER_SLICE_HEIGHT,
) -> list[str]:
    width, height = image.size
    if width <= 0 or height <= 0:
        return []

    slice_height = max(128, int(max_slice_height))
    file_names: list[str] = []
    top = 0
    index = 1
    while top < height:
        bottom = min(height, top + slice_height)
        panel = image.crop((0, top, width, bottom))
        path = save_image_with_size_constraints(
            image=panel,
            output_path_stem=output_dir / f"{file_stem_prefix}-{index:02d}",
        )
        file_names.append(path.name)
        top = bottom
        index += 1
    return file_names


def resolve_description_image_files_for_upload(art_metadata: dict[str, Any]) -> list[str]:
    poster_file = normalize_single_line(art_metadata.get("description_poster_file", "") or "")
    if poster_file:
        return [poster_file]

    image_files = normalize_string_list(art_metadata.get("description_image_files"))
    if image_files:
        return image_files

    legacy_image_file = normalize_single_line(art_metadata.get("description_image_file", "") or "")
    if legacy_image_file:
        return [legacy_image_file]
    return []


def prepare_generated_logo_icon(bundle_dir: Path, logo_path: Path) -> tuple[str, str]:
    with Image.open(logo_path) as opened:
        icon_image = ImageOps.fit(opened.convert("RGB"), (512, 512), Image.Resampling.LANCZOS)
        icon_path = save_image_with_size_constraints(
            image=icon_image,
            output_path_stem=bundle_dir / "icon",
            max_bytes=MODRINTH_ICON_MAX_BYTES,
        )
    if icon_path.stat().st_size > MODRINTH_ICON_MAX_BYTES:
        return "", (
            f"Generated icon stayed above Modrinth's {MODRINTH_ICON_MAX_BYTES} byte icon limit "
            f"after optimization ({icon_path.stat().st_size} bytes)."
        )
    return icon_path.name, ""


def load_bundle_art_metadata(bundle_dir: Path) -> dict[str, Any]:
    path = bundle_dir / ART_METADATA_FILENAME
    if not path.exists():
        return {}
    return load_json(path)


def save_bundle_art_metadata(bundle_dir: Path, payload: dict[str, Any]) -> None:
    path = bundle_dir / ART_METADATA_FILENAME
    path.parent.mkdir(parents=True, exist_ok=True)
    write_json(path, payload)


def generate_bundle_visual_assets(
    *,
    bundle_dir: Path,
    listing: dict[str, str],
    metadata: dict[str, Any],
    options: GenerateOptions,
) -> dict[str, Any]:
    art_dir = bundle_dir / ART_DIRNAME
    art_dir.mkdir(parents=True, exist_ok=True)

    request_text = build_visual_prompt_user_message(
        listing=listing,
        metadata=metadata,
        template_examples=options.template_examples,
        background_images=options.background_images,
    )
    (art_dir / "visual-html.request.txt").write_text(request_text, encoding="utf-8")
    with VISUAL_DESIGN_LOCK:
        response_text, prompts, visual_hoster, visual_model = request_visual_design_response(
            base_url=options.c05_url,
            hoster=options.c05_hoster,
            model=options.c05_model,
            user_prompt=request_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=min(options.max_tokens, 700),
            timeout_seconds=options.timeout_seconds,
            session_id_prefix=f"auto-create-art-prompts-{bundle_dir.name}",
            title=listing.get("name", ""),
            summary=listing.get("short_description", ""),
            background_images=options.background_images,
        )
    (art_dir / "visual-html.response.txt").write_text(response_text, encoding="utf-8")
    write_json(art_dir / "visual-html.json", prompts)

    warnings: list[str] = []
    background_choice = resolve_background_image_choice(
        prompts.get("background_image", ""),
        background_images=options.background_images,
    )
    prompts["background_image"] = background_choice.relative_name
    write_json(art_dir / "visual-html.json", prompts)
    background_path = copy_visual_background_asset(art_dir, background_choice.path)
    accent_color = prompts["accent_color"]
    logo_css = build_logo_template_css(accent_color=accent_color)
    logo_body_html = build_logo_template_body_html(visual_data=prompts)
    description_css = build_description_template_css(accent_color=accent_color)
    description_body_html = build_description_template_body_html(visual_data=prompts)

    write_json(
        art_dir / "visual-fragments.json",
        {
            "accent_color": accent_color,
            "logo_css": logo_css,
            "logo_body_html": logo_body_html,
            "description_css": description_css,
            "description_body_html": description_body_html,
            "visual_content": prompts,
        },
    )

    logo_html = build_visual_html_document(
        title=listing.get("name", ""),
        body_html=logo_body_html,
        css=logo_css,
        variant="logo",
        background_filename=background_path.name,
    )
    description_html = build_visual_html_document(
        title=listing.get("name", ""),
        body_html=description_body_html,
        css=description_css,
        variant="description",
        background_filename=background_path.name,
    )
    (art_dir / "logo.render.html").write_text(logo_html, encoding="utf-8")
    (art_dir / "description-image.render.html").write_text(description_html, encoding="utf-8")
    with IMAGE_GENERATION_LOCK:
        logo_asset = render_html_document_to_asset(
            html_path=art_dir / "logo.render.html",
            output_path_stem=art_dir / "logo",
            target_size=LOGO_IMAGE_SIZE,
            timeout_seconds=options.image_timeout_seconds,
        )

        description_poster_image = render_html_document_to_image(
            html_path=art_dir / "description-image.render.html",
            timeout_seconds=options.image_timeout_seconds,
            target_size=DESCRIPTION_POSTER_IMAGE_SIZE,
        )
        description_poster_path = save_image_with_size_constraints(
            image=description_poster_image,
            output_path_stem=art_dir / "description-image",
        )
        description_panel_files = save_vertical_poster_slices(
            image=description_poster_image,
            output_dir=art_dir,
            file_stem_prefix="description-image-panel",
        )
        description_asset = {
            "file": description_poster_path.name,
            "files": [description_poster_path.name],
            "panel_files": description_panel_files,
            "poster_file": description_poster_path.name,
        }

    icon_file, icon_warning = prepare_generated_logo_icon(bundle_dir, art_dir / logo_asset["file"])
    if icon_warning:
        warnings.append(icon_warning)

    asset_metadata = {
        "generated_at": now_iso(),
        "render_engine": "html+qlmanage",
        "render_timeout_seconds": options.image_timeout_seconds,
        "text_hoster": visual_hoster,
        "text_model": visual_model,
        "background_source_name": background_choice.relative_name,
        "background_file": f"{ART_DIRNAME}/{background_path.name}",
        "accent_color": accent_color,
        "logo_css": logo_css,
        "description_css": description_css,
        "logo_file": f"{ART_DIRNAME}/{logo_asset['file']}",
        "description_poster_file": f"{ART_DIRNAME}/{description_asset['poster_file']}",
        "description_image_file": f"{ART_DIRNAME}/{description_asset['file']}" if description_asset["file"] else "",
        "description_image_files": [f"{ART_DIRNAME}/{name}" for name in description_asset["files"]],
        "description_panel_files": [f"{ART_DIRNAME}/{name}" for name in description_asset["panel_files"]],
        "icon_file": icon_file,
        "warnings": warnings,
    }
    save_bundle_art_metadata(bundle_dir, asset_metadata)
    return asset_metadata


def build_project_draft(
    *,
    metadata: dict[str, Any],
    listing: dict[str, str],
    options: GenerateOptions,
    ai_result: dict[str, Any] | None = None,
) -> dict[str, Any]:
    details = metadata.get("metadata", {})
    ai_result = ai_result or {}
    detected_license = normalize_single_line(details.get("license") or "")
    license_id = normalize_single_line(options.license_id or detected_license or ai_result.get("license_id") or "")
    license_url = empty_to_none(options.license_url or ai_result.get("license_url"))
    categories, additional_categories = finalize_category_selection(
        primary_candidates=options.categories or normalize_category_values(ai_result.get("categories")),
        additional_candidates=options.additional_categories or normalize_category_values(ai_result.get("additional_categories")),
        metadata=metadata,
    )
    client_side = options.client_side
    server_side = options.server_side
    ai_client_side = normalize_side_hint(ai_result.get("client_side"))
    ai_server_side = normalize_side_hint(ai_result.get("server_side"))
    if client_side == "optional" and ai_client_side:
        client_side = ai_client_side
    if server_side == "optional" and ai_server_side:
        server_side = ai_server_side
    project_status = "draft"

    project = {
        "slug": slugify_text(listing["name"]) or slugify_text(details.get("mod_id", "")),
        "title": listing["name"],
        "description": listing["short_description"],
        "categories": categories,
        "client_side": client_side,
        "server_side": server_side,
        "body": listing["long_description"],
        "status": project_status,
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


def render_description_image_markdown(*, title: str, image_url: str, index: int = 1, total: int = 1) -> str:
    if total > 1:
        return f"![{title} poster section {index} of {total}]({image_url})"
    return f"![{title} cover image]({image_url})"


def inject_description_image_into_body(*, body: str, title: str, image_url: str) -> str:
    return inject_description_images_into_body(body=body, title=title, image_urls=[image_url] if image_url else [])


def inject_description_images_into_body(*, body: str, title: str, image_urls: list[str]) -> str:
    clean_body = str(body or "").strip()
    urls = [normalize_single_line(url) for url in image_urls if normalize_single_line(url)]
    if not urls:
        return clean_body
    if all(url in clean_body for url in urls):
        return clean_body

    image_blocks = [
        render_description_image_markdown(title=title, image_url=url, index=index + 1, total=len(urls))
        for index, url in enumerate(urls)
    ]
    image_markdown = "\n\n".join(image_blocks)

    lines = clean_body.splitlines()
    if lines and lines[0].lstrip().startswith("#"):
        heading = lines[0].rstrip()
        rest = "\n".join(lines[1:]).lstrip("\n")
        parts = [heading, "", image_markdown]
        if rest:
            parts.extend(["", rest])
        return "\n".join(parts).strip() + "\n"

    parts = [f"# {title}", "", image_markdown]
    if clean_body:
        parts.extend(["", clean_body])
    return "\n".join(parts).strip() + "\n"


def normalize_string_list(values: Any) -> list[str]:
    if isinstance(values, list):
        return [normalize_single_line(value) for value in values if normalize_single_line(value)]
    value = normalize_single_line(values)
    return [value] if value else []


def resolve_gallery_image_urls_by_titles(*, project_data: dict[str, Any], expected_titles: list[str]) -> list[str]:
    gallery = project_data.get("gallery", []) or []
    urls_by_title: dict[str, str] = {}
    for item in gallery:
        if not isinstance(item, dict):
            continue
        title = normalize_single_line(item.get("title"))
        url = normalize_single_line(item.get("url"))
        if title and url and title not in urls_by_title:
            urls_by_title[title] = url
    return [urls_by_title.get(title, "") for title in expected_titles]


def resolve_new_gallery_image_url(
    *,
    before_urls: set[str],
    project_data: dict[str, Any],
    expected_title: str,
) -> str:
    gallery = project_data.get("gallery", []) or []
    for item in gallery:
        if not isinstance(item, dict):
            continue
        url = normalize_single_line(item.get("url"))
        title = normalize_single_line(item.get("title"))
        if url and title == expected_title and url not in before_urls:
            return url
    for item in gallery:
        if not isinstance(item, dict):
            continue
        url = normalize_single_line(item.get("url"))
        if url and url not in before_urls:
            return url
    for item in gallery:
        if not isinstance(item, dict):
            continue
        url = normalize_single_line(item.get("url"))
        title = normalize_single_line(item.get("title"))
        if url and title == expected_title:
            return url
    return ""


def sync_project_description_image(
    *,
    client: ModrinthClient,
    project_id: str,
    bundle_dir: Path,
    project_payload: dict[str, Any],
    project_path: Path,
    publish_state: dict[str, Any],
) -> list[str]:
    art_metadata = load_bundle_art_metadata(bundle_dir)
    image_files = resolve_description_image_files_for_upload(art_metadata)
    if not image_files:
        return []

    image_paths = [bundle_dir / image_file for image_file in image_files]
    missing_paths = [str(path) for path in image_paths if not path.exists()]
    if missing_paths:
        return [f"Generated description image files were missing: {', '.join(missing_paths)}"]

    title = normalize_single_line(project_payload.get("title", "") or bundle_dir.name)
    warnings: list[str] = []
    stored_urls = normalize_string_list(publish_state.get("description_image_urls"))
    if not stored_urls:
        legacy_url = normalize_single_line(publish_state.get("description_image_url", ""))
        if legacy_url:
            stored_urls = [legacy_url]
    gallery_titles = [
        f"{title} Cover Image" if len(image_paths) == 1 else f"{title} Poster {index + 1}"
        for index in range(len(image_paths))
    ]

    try:
        before_project = client.get_project(project_ref=project_id)
        existing_urls = resolve_gallery_image_urls_by_titles(
            project_data=before_project,
            expected_titles=gallery_titles,
        )
        if all(existing_urls):
            stored_urls = existing_urls
            publish_state["description_image_urls"] = stored_urls
            publish_state["description_image_url"] = stored_urls[0] if stored_urls else ""
            write_bundle_state(bundle_dir, publish_state)

        if len(stored_urls) != len(image_paths) or any(not url for url in stored_urls):
            before_urls = {
                normalize_single_line(item.get("url"))
                for item in (before_project.get("gallery", []) or [])
                if isinstance(item, dict)
            }
            for index, image_path in enumerate(image_paths):
                if existing_urls[index]:
                    continue
                client.add_gallery_image(
                    project_ref=project_id,
                    image_path=image_path,
                    featured=index == 0,
                    title=gallery_titles[index],
                    description=(
                        f"Generated description image for {title}."
                        if len(image_paths) == 1
                        else f"Generated poster section {index + 1} for {title}."
                    ),
                    ordering=index,
                )
            after_project = client.get_project(project_ref=project_id)
            resolved_urls = resolve_gallery_image_urls_by_titles(
                project_data=after_project,
                expected_titles=gallery_titles,
            )
            if not all(resolved_urls):
                resolved_urls = [
                    resolve_new_gallery_image_url(
                        before_urls=before_urls,
                        project_data=after_project,
                        expected_title=gallery_title,
                    )
                    for gallery_title in gallery_titles
                ]
            if not all(resolved_urls):
                raise ModCompilerError("Modrinth gallery upload completed but not all poster section URLs could be found.")
            stored_urls = resolved_urls
            publish_state["description_image_urls"] = stored_urls
            publish_state["description_image_url"] = stored_urls[0] if stored_urls else ""
            write_bundle_state(bundle_dir, publish_state)

        updated_body = inject_description_images_into_body(
            body=str(project_payload.get("body", "") or ""),
            title=title,
            image_urls=stored_urls,
        )
        if updated_body != str(project_payload.get("body", "") or ""):
            client.modify_project(project_ref=project_id, payload={"body": updated_body})
            project_payload["body"] = updated_body
            write_json(project_path, project_payload)
    except ModCompilerError as error:
        warnings.append(
            "Generated description poster could not be uploaded/embedded automatically. "
            f"You can still use `{', '.join(image_files)}` manually. {error}"
        )
    return warnings


def build_project_create_payload(payload: dict[str, Any]) -> dict[str, Any]:
    create_payload = dict(payload)
    create_payload["status"] = "draft"
    create_payload.pop("requested_status", None)
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


def build_version_create_payload(payload: dict[str, Any]) -> dict[str, Any]:
    create_payload = dict(payload)
    create_payload.setdefault("dependencies", [])
    file_parts = normalize_string_list(create_payload.get("file_parts"))
    if not file_parts:
        file_parts = ["file"]
    create_payload["file_parts"] = file_parts
    primary_file = normalize_single_line(create_payload.get("primary_file", "")) or file_parts[0]
    if primary_file not in file_parts:
        primary_file = file_parts[0]
    create_payload["primary_file"] = primary_file
    return create_payload


def resolve_modrinth_project_identity(
    *,
    client: ModrinthClient,
    project_id: str,
    project_slug: str,
) -> tuple[str, str]:
    normalized_id = normalize_single_line(project_id)
    normalized_slug = normalize_single_line(project_slug)
    refs: list[str] = []
    if normalized_id:
        refs.append(normalized_id)
    if normalized_slug and normalized_slug not in refs:
        refs.append(normalized_slug)

    for ref in refs:
        try:
            project = client.get_project(project_ref=ref)
        except ModCompilerError:
            continue
        resolved_id = normalize_single_line(project.get("id"))
        resolved_slug = normalize_single_line(project.get("slug")) or normalized_slug
        if resolved_id or resolved_slug:
            return resolved_id or normalized_id, resolved_slug or normalized_slug
    return "", normalized_slug


def resolve_project_publish_target(payload: dict[str, Any]) -> str:
    requested = normalize_single_line(payload.get("requested_status", "")).lower()
    if requested in {"approved", "archived", "unlisted", "private", "draft"}:
        return requested
    status = normalize_single_line(payload.get("status", "")).lower()
    if status in {"approved", "archived", "private"}:
        return status
    if status == "unlisted":
        return "approved"
    if status == "draft":
        return "draft"
    return DEFAULT_PROJECT_STATUS if DEFAULT_PROJECT_STATUS != "draft" else "unlisted"


def resolve_version_publish_target(payload: dict[str, Any]) -> str:
    requested = normalize_single_line(payload.get("requested_status", "")).lower()
    if requested in {"listed", "archived", "draft", "unlisted"}:
        return requested
    status = normalize_single_line(payload.get("status", "")).lower()
    if status in {"listed", "archived", "unlisted"}:
        return status
    return DEFAULT_VERSION_STATUS if DEFAULT_VERSION_STATUS != "draft" else "listed"


def build_project_visibility_patch(target: str) -> dict[str, Any]:
    normalized = normalize_single_line(target).lower()
    if normalized == "approved":
        return {"status": "draft", "requested_status": "approved"}
    if normalized in {"archived", "draft", "private", "unlisted"}:
        return {"status": normalized}
    return {"status": DEFAULT_PROJECT_STATUS if DEFAULT_PROJECT_STATUS != "draft" else "unlisted"}


def build_version_visibility_patch(target: str) -> dict[str, Any]:
    normalized = normalize_single_line(target).lower()
    if normalized in {"listed", "archived", "draft", "unlisted"}:
        return {"status": normalized}
    return {"status": DEFAULT_VERSION_STATUS if DEFAULT_VERSION_STATUS != "draft" else "listed"}


def bundle_state_paths(bundle_dir: Path) -> list[Path]:
    return [
        bundle_dir / DRAFT_STATE_FILENAME,
        bundle_dir / LEGACY_PUBLISH_STATE_FILENAME,
    ]


def load_bundle_state(bundle_dir: Path) -> dict[str, Any]:
    for path in bundle_state_paths(bundle_dir):
        if path.exists():
            return apply_modrinth_urls(load_json(path))
    return {}


def write_bundle_state(bundle_dir: Path, state: dict[str, Any]) -> None:
    state = apply_modrinth_urls(state)
    for path in bundle_state_paths(bundle_dir):
        write_json(path, state)


def write_draft_summary_outputs(output_dir: Path, summary: dict[str, Any]) -> None:
    write_json(output_dir / DRAFT_SUMMARY_JSON, summary)
    write_json(output_dir / LEGACY_PUBLISH_SUMMARY_JSON, summary)
    rendered = render_draft_upload_summary_markdown(summary)
    (output_dir / DRAFT_SUMMARY_MD).write_text(rendered, encoding="utf-8")
    (output_dir / LEGACY_PUBLISH_SUMMARY_MD).write_text(rendered, encoding="utf-8")


def build_modrinth_project_url(*, project_slug: str = "", project_id: str = "") -> str:
    project_ref = normalize_single_line(project_slug) or normalize_single_line(project_id)
    if not project_ref:
        return ""
    return f"https://modrinth.com/mod/{urllib.parse.quote(project_ref, safe='')}"


def build_modrinth_version_url(*, project_slug: str = "", project_id: str = "", version_id: str = "") -> str:
    project_url = build_modrinth_project_url(project_slug=project_slug, project_id=project_id)
    version_ref = normalize_single_line(version_id)
    if not project_url or not version_ref:
        return ""
    return f"{project_url}/version/{urllib.parse.quote(version_ref, safe='')}"


def apply_modrinth_urls(state: dict[str, Any]) -> dict[str, Any]:
    updated = dict(state)
    project_id = str(updated.get("project_id", "") or "")
    project_slug = str(updated.get("project_slug", "") or "")
    version_id = str(updated.get("version_id", "") or "")
    updated["project_url"] = build_modrinth_project_url(project_slug=project_slug, project_id=project_id)
    updated["version_url"] = build_modrinth_version_url(
        project_slug=project_slug,
        project_id=project_id,
        version_id=version_id,
    )
    return updated


def sync_bundle_summary(bundle_dir: Path) -> None:
    metadata_path = bundle_dir / "bundle_metadata.json"
    listing_path = bundle_dir / "listing.json"
    if not metadata_path.exists() or not listing_path.exists():
        return
    bundle_metadata = load_json(metadata_path)
    listing = load_json(listing_path)
    draft_state = load_bundle_state(bundle_dir)
    if not draft_state:
        return
    (bundle_dir / "SUMMARY.md").write_text(
        render_bundle_summary_markdown(
            bundle_metadata=bundle_metadata,
            listing=listing,
            publish_state=draft_state,
        ),
        encoding="utf-8",
    )


def promote_published_bundle_visibility(
    *,
    client: ModrinthClient,
    project_id: str,
    version_id: str,
    project_payload: dict[str, Any],
    version_payload: dict[str, Any],
    project_path: Path,
    version_path: Path,
) -> tuple[str, str]:
    project_target = resolve_project_publish_target(project_payload)
    version_target = resolve_version_publish_target(version_payload)

    if version_id and version_target != "draft":
        client.modify_version(version_id=version_id, payload=build_version_visibility_patch(version_target))
        version_payload["status"] = version_target
        version_payload.pop("requested_status", None)
        write_json(version_path, version_payload)

    if project_id and project_target != "draft":
        client.modify_project(project_ref=project_id, payload=build_project_visibility_patch(project_target))
        if project_target == "approved":
            project_payload["status"] = "draft"
            project_payload["requested_status"] = "approved"
        else:
            project_payload["status"] = project_target
            project_payload.pop("requested_status", None)
        write_json(project_path, project_payload)

    return project_target, version_target


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


def set_verify_file_verified(verify_path: Path) -> None:
    existing_lines: list[str] = []
    if verify_path.exists():
        existing_lines = verify_path.read_text(encoding="utf-8", errors="replace").splitlines()

    updated_lines: list[str] = []
    replaced = False
    for raw_line in existing_lines:
        line = raw_line.strip()
        if not replaced and line and not line.startswith("#"):
            updated_lines.append("verified")
            replaced = True
            continue
        updated_lines.append(raw_line)

    if not replaced:
        updated_lines.insert(0, "verified")

    verify_path.parent.mkdir(parents=True, exist_ok=True)
    verify_path.write_text("\n".join(updated_lines).rstrip() + "\n", encoding="utf-8")


def is_bundle_approved_for_draft(verify_path: Path, *, assume_verified: bool) -> bool:
    return bool(assume_verified) or is_bundle_verified(verify_path)


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
        - generated logo and description image look good in art/
        - categories, side support, and license look correct in modrinth.project.json
        - game_versions and loader look correct in modrinth.version.json
        - add an icon.* file to this bundle if you want the draft project created with an icon
        """
    ).strip() + "\n"


def render_bundle_summary_markdown(
    *,
    bundle_metadata: dict[str, Any],
    listing: dict[str, str],
    publish_state: dict[str, Any],
) -> str:
    status = str(publish_state.get("status", "-") or "-")
    warning_lines = [str(item) for item in (bundle_metadata.get("warnings", []) or []) if str(item).strip()]
    state_warnings = [str(item) for item in (publish_state.get("warnings", []) or []) if str(item).strip()]
    next_step_lines = [
        "## Next Step",
        "",
    ]
    if status == "ready_for_verification":
        next_step_lines.extend(
            [
                "Open `verify.txt`, replace `pending` with `verified` after review, then run the `create-drafts` command.",
            ]
        )
    elif status in {"draft_created", "published"}:
        project_url = str(publish_state.get("project_url", "") or "").strip()
        version_url = str(publish_state.get("version_url", "") or "").strip()
        next_step_lines.extend(
            [
                f"Draft created on Modrinth project `{publish_state.get('project_slug', '-') or '-'}` "
                f"(`{publish_state.get('project_id', '-') or '-'}`), "
                f"version `{publish_state.get('version_id', '-') or '-'}`. Review it on Modrinth and submit it manually when ready.",
            ]
        )
        if project_url:
            next_step_lines.append(f"Project link: {project_url}")
        if version_url:
            next_step_lines.append(f"Version link: {version_url}")
    elif status in {"draft_create_failed", "publish_failed"}:
        next_step_lines.extend(
            [
                f"Inspect `{DRAFT_STATE_FILENAME}` for the latest error, then fix the payload and run `create-drafts` again.",
            ]
        )
    else:
        next_step_lines.extend(
            [
                "Review the bundle files and rerun the appropriate workflow step if needed.",
            ]
        )
    next_step_lines.append("")

    return "\n".join(
        [
            "# Auto Create Modrinth Draft Bundle",
            "",
            f"- Status: `{status}`",
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
            *next_step_lines,
        ]
        + (
            ["## Warnings", "", *dedupe_preserve_order(warning_lines + state_warnings), ""]
            if warning_lines or state_warnings
            else []
        )
    )


def render_failed_bundle_summary_markdown(*, jar_name: str, bundle_dir: Path, error: str) -> str:
    return "\n".join(
        [
            "# Auto Create Modrinth Draft Bundle",
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


def print_result_links(results: list[dict[str, Any]]) -> None:
    viewable = [
        result for result in results
        if str(result.get("project_url", "")).strip()
        and str(result.get("status", "")).strip() in {"draft_created", "dry_run"}
    ]
    if not viewable:
        return

    print("Modrinth links:", flush=True)
    for result in viewable:
        bundle = str(result.get("bundle", "") or result.get("bundle_slug", "") or "-")
        project_url = str(result.get("project_url", "") or "").strip()
        version_url = str(result.get("version_url", "") or "").strip()
        print(f"- {bundle}: {project_url}", flush=True)
        if version_url:
            print(f"  version: {version_url}", flush=True)


def render_draft_upload_summary_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# Draft Upload Summary",
        "",
        f"- Drafted at: `{summary.get('drafted_at', '-')}`",
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
    links = [
        result for result in summary.get("results", [])
        if str(result.get("project_url", "") or "").strip()
    ]
    if links:
        lines.extend(
            [
                "## Modrinth Links",
                "",
            ]
        )
        for result in links:
            bundle = result.get("bundle", "-")
            project_url = result.get("project_url", "-") or "-"
            version_url = result.get("version_url", "") or ""
            lines.append(f"- `{bundle}` project: {project_url}")
            if version_url:
                lines.append(f"- `{bundle}` version: {version_url}")
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
        "visual_logo_html": read_or_fallback("visual-logo-template.html", ""),
        "visual_description_html": read_or_fallback("visual-description-template.html", ""),
    }


def build_modrinth_category_guidance() -> str:
    return "\n".join(
        f"- {category}: {hint}"
        for category, hint in MODRINTH_MOD_CATEGORY_HINTS.items()
    )


def default_categories_for_metadata(metadata: dict[str, Any]) -> list[str]:
    details = metadata.get("metadata", {}) if isinstance(metadata, dict) else {}
    haystack = " ".join(
        normalize_single_line(value)
        for value in (
            details.get("name"),
            details.get("description"),
            details.get("mod_id"),
            details.get("entrypoint_class"),
            details.get("group"),
        )
    ).lower()

    keyword_groups = (
        ("library", ("library", "api", "framework", "dependency", "core", "common")),
        ("optimization", ("optimiz", "performance", "fps", "lag", "memory", "tick")),
        ("storage", ("storage", "inventory", "hopper", "chest", "sorting", "logistics")),
        ("technology", ("machine", "automation", "energy", "power", "factory", "tech")),
        ("worldgen", ("worldgen", "world generation", "biome", "terrain", "ore", "structure")),
        ("mobs", ("mob", "entity", "creature", "npc")),
        ("equipment", ("weapon", "armor", "tool", "gear", "equipment")),
        ("magic", ("magic", "spell", "mana", "ritual", "arcane")),
        ("food", ("food", "meal", "crop", "hunger", "kitchen")),
        ("transportation", ("transport", "travel", "teleport", "vehicle", "rail", "portal")),
        ("management", ("admin", "command", "manage", "manager", "moderation", "configuration")),
        ("social", ("social", "chat", "party", "friend", "multiplayer", "community")),
        ("economy", ("economy", "currency", "shop", "trade", "market")),
        ("decoration", ("decorate", "decoration", "furniture", "building", "cosmetic")),
        ("minigame", ("minigame", "arena", "score", "game mode")),
        ("cursed", ("cursed", "meme", "troll", "chaos")),
        ("adventure", ("adventure", "quest", "dungeon", "explor", "progression")),
        ("game-mechanics", ("mechanic", "gameplay", "combat", "rule", "vanilla")),
    )
    matches = [
        category
        for category, keywords in keyword_groups
        if any(keyword in haystack for keyword in keywords)
    ]
    return matches[:PRIMARY_CATEGORY_LIMIT] or ["utility"]


def finalize_category_selection(
    *,
    primary_candidates: list[str],
    additional_candidates: list[str],
    metadata: dict[str, Any],
) -> tuple[list[str], list[str]]:
    primary = dedupe_preserve_order(primary_candidates)
    additional = dedupe_preserve_order(additional_candidates)

    if not primary and additional:
        primary = additional[:PRIMARY_CATEGORY_LIMIT]
        additional = additional[PRIMARY_CATEGORY_LIMIT:]
    if not primary:
        primary = default_categories_for_metadata(metadata)

    overflow = primary[PRIMARY_CATEGORY_LIMIT:]
    primary = primary[:PRIMARY_CATEGORY_LIMIT]
    additional = dedupe_preserve_order(overflow + additional)
    additional = [item for item in additional if item not in primary]
    return primary, additional


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
        raise ModCompilerError(f"Draft creation mode must be one of auto, local, github; got '{raw}'.")
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
