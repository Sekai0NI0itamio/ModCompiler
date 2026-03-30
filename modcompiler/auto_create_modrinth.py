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
DEFAULT_C05_HOSTER = "nvidia"
DEFAULT_C05_MODEL = "nvidia/nemotron-3-nano-30b-a3b"
DEFAULT_VISUAL_C05_HOSTER = DEFAULT_C05_HOSTER
DEFAULT_VISUAL_C05_MODEL = DEFAULT_C05_MODEL
DEFAULT_IMAGE_C05_HOSTER = "aihorde"
DEFAULT_IMAGE_C05_MODEL = ""
DEFAULT_IMAGE_TIMEOUT_SECONDS = 600
DEFAULT_IMAGE_POLL_INTERVAL_SECONDS = 4
DEFAULT_REASONING_EFFORT = "high"
DEFAULT_TEXT_MAX_TOKENS = 3_200
GITHUB_CLI_MAX_RETRIES = 4
GITHUB_CLI_RETRY_DELAY_SECONDS = 3.0
DEFAULT_PROJECT_STATUS = "draft"
DEFAULT_VERSION_STATUS = "listed"
DEFAULT_VERSION_TYPE = "release"
DEFAULT_MAX_WORKERS = 1
DEFAULT_PROMPT_PROJECTINFO_CHAR_LIMIT = 24_000
DEFAULT_PUBLISH_VIA = "auto"
TEXT_CHAT_APP_ID = "auto-create-modrinth-text"
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
EXTERNAL_LINKS_FILENAME = "external_links.json"
MANAGED_GITHUB_MARKER_FILENAME = ".modcompiler-managed.json"
MANAGED_GITHUB_WIKI_DEFAULT_BRANCH = "master"
MANAGED_GITHUB_ART_DIR = ".modcompiler-art"
SOURCE_SUFFIXES = {".java", ".kt"}
MAX_PROJECTINFO_TREE_LINES = 160
MAX_PROJECTINFO_EXCERPTS = 5
PROJECTINFO_EXCERPT_MAX_LINES = 120
PROJECTINFO_EXCERPT_MAX_CHARS = 4_000
LISTING_SYSTEM_PROMPT = (
    "You are generating a Modrinth listing payload. "
    "Return only one complete valid JSON object that fully matches the requested schema. "
    "Do not include commentary, markdown fences, or reasoning. "
    "Keep long_description polished but reasonably compact so the full JSON completes in one response."
)
SUMMARY_SYSTEM_PROMPT = (
    "You are generating a Modrinth summary. "
    "Return exactly one plain text summary line only. "
    "Do not include JSON, bullets, quotes, commentary, reasoning, counting, or step-by-step work."
)
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
GENERIC_FILLER_DESCRIPTION_MARKERS = (
    "works in both single-player and multiplayer",
    "works in both single player and multiplayer",
    "works on any world",
    "including existing saves",
    "existing saves",
    "the mod is lightweight",
    "lightweight and only requires",
    "encouraging players to stay inside at night",
    "build shelter",
    "simple, natural challenge",
)
UNVERIFIED_DESCRIPTION_MARKER_GROUPS: tuple[tuple[str, ...], ...] = (
    ("singleplayer", "single-player", "multiplayer", "client", "server worlds", "client and server"),
    ("overworld", "nether", "end", "dimension", "dimensions"),
    ("helmet", "helmets"),
    ("commands", "command", "config", "configuration"),
    ("cheats", "op status", "operator"),
    ("tactical advantage", "more challenge", "perfect for survival worlds", "sunny areas"),
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
VISUAL_RENDER_SIZE = 2048
VISUAL_BACKGROUND_MAX_SIDE = 3072
SHORT_DESCRIPTION_MAX_CHARS = 160
DESCRIPTION_IMAGE_SIZE = (1600, 900)
DESCRIPTION_LAYOUT_BASE_SIZE = (1024, 1024)
LOGO_IMAGE_SIZE = (1024, 1024)
DESCRIPTION_POSTER_IMAGE_SIZE = DESCRIPTION_IMAGE_SIZE
DESCRIPTION_POSTER_SLICE_HEIGHT = 768
MODRINTH_ICON_MAX_BYTES = 256 * 1024
MODRINTH_ICON_TARGET_BYTES = MODRINTH_ICON_MAX_BYTES - 4 * 1024
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
class GenerateInputBundle:
    entry_dir: Path
    jar_path: Path
    source_dir: Path

    @property
    def bundle_slug(self) -> str:
        return slugify_text(self.jar_path.stem) or slugify_text(self.jar_path.name) or self.entry_dir.name


@dataclass(frozen=True)
class GenerateOptions:
    input_dir: Path
    output_dir: Path
    manifest_path: Path
    manifest: dict[str, Any]
    c05_url: str
    c05_hoster: str
    c05_model: str
    visual_c05_hoster: str
    visual_c05_model: str
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
    nolinks: bool
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
    github_owner: str
    github_branch: str
    remote_jar_paths: dict[str, str]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Local-first workflow that reads paired upload bundles from ToBeUploaded, where each "
            "bundle folder contains one mod .jar and one real source-code folder, asks C05 Local "
            "AI (hoster `nvidia`, model `nvidia/nemotron-3-nano-30b-a3b` by default) to draft Modrinth listing copy, then creates full "
            "Modrinth draft projects and versions for bundles whose verify.txt contains 'verified'."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate_parser = subparsers.add_parser(
        "generate",
        help="Create one review bundle per upload folder in ToBeUploaded.",
    )
    generate_parser.add_argument("--input-dir", default=DEFAULT_INPUT_DIR)
    generate_parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    generate_parser.add_argument("--background-dir", default=DEFAULT_BACKGROUND_IMAGES_DIR)
    generate_parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    generate_parser.add_argument("--c05-url", default=DEFAULT_C05_URL)
    generate_parser.add_argument("--c05-hoster", default=DEFAULT_C05_HOSTER)
    generate_parser.add_argument("--c05-model", default=DEFAULT_C05_MODEL)
    generate_parser.add_argument("--visual-c05-hoster", default=DEFAULT_VISUAL_C05_HOSTER)
    generate_parser.add_argument("--visual-c05-model", default=DEFAULT_VISUAL_C05_MODEL)
    generate_parser.add_argument("--reasoning-effort", default=DEFAULT_REASONING_EFFORT)
    generate_parser.add_argument("--temperature", type=float, default=0.2)
    generate_parser.add_argument("--max-tokens", type=int, default=DEFAULT_TEXT_MAX_TOKENS)
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
        "--only-bundle",
        default="",
        help="Generate only the jar whose bundle slug, jar stem, or jar filename matches this value.",
    )
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
    generate_parser.add_argument(
        "--nolinks",
        action="store_true",
        help="Skip GitHub repo/wiki/issues link generation and strip external links from the Modrinth draft payload.",
    )
    generate_parser.add_argument("--issues-url", default="")
    generate_parser.add_argument("--source-url", default="")
    generate_parser.add_argument("--wiki-url", default="")
    generate_parser.add_argument("--discord-url", default="")
    generate_parser.add_argument(
        "--github-owner",
        default="",
        help="GitHub user or org that should own generated public per-mod repositories. Defaults to the current origin owner.",
    )
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
    publish_parser.add_argument(
        "--nolinks",
        action="store_true",
        help="Strip issues/source/wiki/discord/donation links from the Modrinth draft payload before upload.",
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
            "Place one bundle folder per mod inside it, with exactly one .jar and one source-code folder, then run the script again."
        )

    input_bundles = discover_generate_input_bundles(input_dir)
    if not input_bundles:
        raise ModCompilerError(
            f"No upload bundles were found in {input_dir}. "
            "Each bundle folder must contain one .jar file and one source-code folder."
        )
    input_bundles = filter_generate_input_bundles(
        bundles=input_bundles,
        only_bundle=str(args.only_bundle),
        input_dir=input_dir,
    )
    jars = [bundle.jar_path for bundle in input_bundles]

    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = load_json(manifest_path)
    template_examples = load_template_examples(Path(args.template_dir))
    background_images = discover_background_images(Path(args.background_dir))
    github_token = discover_github_token()
    github_repo = discover_github_repo()
    github_owner = normalize_single_line(args.github_owner) or github_repo.split("/", 1)[0]
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
        visual_c05_hoster=str(args.visual_c05_hoster).strip().lower() or DEFAULT_VISUAL_C05_HOSTER,
        visual_c05_model=str(args.visual_c05_model).strip() or DEFAULT_VISUAL_C05_MODEL,
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
        nolinks=bool(args.nolinks),
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
        github_owner=github_owner,
        github_branch=github_branch,
        remote_jar_paths=remote_jar_paths,
    )

    max_workers = max(1, min(int(args.max_workers), len(input_bundles)))
    if max_workers != 1:
        print(
            f"Sequential generation mode is enabled to avoid AI/provider rate limits, so --max-workers={max_workers} will be ignored.",
            flush=True,
        )
    print(
        f"Generating {len(input_bundles)} Modrinth bundle(s) from {input_dir} one by one in sequential mode.",
        flush=True,
    )

    results: list[dict[str, Any]] = []
    for input_bundle in input_bundles:
        try:
            result = generate_bundle_for_input_bundle(input_bundle, options)
        except Exception as error:  # pragma: no cover - defensive fallback
            result = {
                "jar_name": input_bundle.jar_path.name,
                "bundle_slug": input_bundle.bundle_slug,
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


def directory_contains_source_files(root: Path) -> bool:
    if not root.exists() or not root.is_dir():
        return False
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in SOURCE_SUFFIXES:
            return True
    return False


def discover_generate_input_bundles(input_dir: Path) -> list[GenerateInputBundle]:
    bundle_dirs = sorted(
        path for path in input_dir.iterdir() if path.is_dir() and not path.name.startswith(".")
    )
    bundles: list[GenerateInputBundle] = []
    problems: list[str] = []

    for entry_dir in bundle_dirs:
        jar_candidates = sorted(
            path for path in entry_dir.iterdir() if path.is_file() and path.suffix.lower() == ".jar"
        )
        source_candidates = sorted(
            path
            for path in entry_dir.iterdir()
            if path.is_dir() and not path.name.startswith(".") and directory_contains_source_files(path)
        )

        if len(jar_candidates) != 1:
            problems.append(
                f"{entry_dir}: expected exactly 1 top-level .jar file, found {len(jar_candidates)}."
            )
            continue
        if len(source_candidates) != 1:
            problems.append(
                f"{entry_dir}: expected exactly 1 top-level source folder with .java/.kt files, found {len(source_candidates)}."
            )
            continue

        bundles.append(
            GenerateInputBundle(
                entry_dir=entry_dir,
                jar_path=jar_candidates[0],
                source_dir=source_candidates[0],
            )
        )

    if bundle_dirs and not bundles and problems:
        raise ModCompilerError(
            "Could not discover any valid upload bundles.\n" + "\n".join(problems)
        )
    if problems:
        raise ModCompilerError(
            "Some upload bundles are invalid.\n" + "\n".join(problems)
        )
    return bundles


def filter_generate_input_bundles(
    *,
    bundles: list[GenerateInputBundle],
    only_bundle: str,
    input_dir: Path,
) -> list[GenerateInputBundle]:
    target = normalize_single_line(only_bundle)
    if not target:
        return bundles

    target_slug = slugify_text(target)
    selected: list[GenerateInputBundle] = []
    for bundle in bundles:
        jar_name = normalize_single_line(bundle.jar_path.name)
        jar_stem = normalize_single_line(bundle.jar_path.stem)
        source_name = normalize_single_line(bundle.source_dir.name)
        bundle_dir_name = normalize_single_line(bundle.entry_dir.name)
        bundle_slug = bundle.bundle_slug
        if (
            target in {jar_name, jar_stem, source_name, bundle_dir_name}
            or (target_slug and target_slug in {bundle_slug, slugify_text(source_name), slugify_text(bundle_dir_name)})
        ):
            selected.append(bundle)

    if selected:
        return selected

    available = ", ".join(bundle.bundle_slug for bundle in bundles)
    raise ModCompilerError(
        f"No upload bundle in {input_dir} matched --only-bundle '{target}'. Available bundle slugs: {available}"
    )


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
                disable_links=bool(args.nolinks),
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
                    disable_links=bool(args.nolinks),
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
    disable_links: bool,
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
        disable_links=disable_links,
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
    disable_links: bool,
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
            "nolinks": "true" if disable_links else "false",
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
        run_github_cli(command, github_token=github_token)

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
        output = run_github_cli(
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
            github_token=github_token,
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
        output = run_github_cli(
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
            github_token=github_token,
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
            run_github_cli(
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
                github_token=github_token,
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


def is_transient_github_cli_error(text: str) -> bool:
    lowered = str(text or "").lower()
    transient_markers = (
        "connection reset by peer",
        "tls handshake timeout",
        "i/o timeout",
        "timeout awaiting headers",
        "connection timed out",
        "unexpected eof",
        "http2: client connection lost",
        "use of closed network connection",
        "server closed idle connection",
        "temporary failure in name resolution",
        "no such host",
        "connection refused",
        "net/http: timeout awaiting response headers",
    )
    return any(marker in lowered for marker in transient_markers)


def run_github_cli(
    args: list[str],
    *,
    github_token: str,
    cwd: Path | None = None,
    retries: int = GITHUB_CLI_MAX_RETRIES,
    retry_delay_seconds: float = GITHUB_CLI_RETRY_DELAY_SECONDS,
) -> str:
    last_error: ModCompilerError | None = None
    attempts = max(1, int(retries))
    for attempt in range(1, attempts + 1):
        try:
            return run_subprocess(args, cwd=cwd, env=github_cli_env(github_token))
        except ModCompilerError as error:
            last_error = error
            if attempt >= attempts or not is_transient_github_cli_error(str(error)):
                raise
            time.sleep(retry_delay_seconds * attempt)
    if last_error is not None:
        raise last_error
    raise ModCompilerError("GitHub CLI command failed for an unknown reason.")


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
        command_text = sanitize_sensitive_subprocess_text(" ".join(args))
        detail_text = sanitize_sensitive_subprocess_text(detail)
        raise ModCompilerError(f"Command failed: {command_text}\n{detail_text}") from None
    return completed.stdout


def sanitize_sensitive_subprocess_text(text: str) -> str:
    sanitized = str(text or "")
    sanitized = re.sub(
        r"(http\.https://github\.com/\.extraheader=AUTHORIZATION:\s+basic\s+)\S+",
        r"\1<redacted>",
        sanitized,
        flags=re.IGNORECASE,
    )
    sanitized = re.sub(r"(x-access-token:)[^\s@]+", r"\1<redacted>", sanitized, flags=re.IGNORECASE)
    sanitized = re.sub(r"github_pat_[A-Za-z0-9_]+", "github_pat_<redacted>", sanitized)
    return sanitized


def github_api_request_json(
    method: str,
    endpoint: str,
    *,
    github_token: str,
    payload: dict[str, Any] | None = None,
    allow_not_found: bool = False,
) -> Any:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"https://api.github.com{endpoint}",
        data=body,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {github_token}",
            "User-Agent": build_modrinth_user_agent(),
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        if allow_not_found and error.code == 404:
            return None
        try:
            detail = error.read().decode("utf-8")
        except Exception:
            detail = str(error)
        raise ModCompilerError(
            f"GitHub API request failed: {method} {endpoint}\nHTTP {error.code}: {detail.strip() or error.reason}"
        ) from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Could not reach GitHub API for {method} {endpoint}: {error}") from None
    if not raw.strip():
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"raw": raw}


def build_github_repo_url(owner: str, repo_name: str) -> str:
    safe_owner = urllib.parse.quote(owner, safe="")
    safe_repo = urllib.parse.quote(repo_name, safe="")
    return f"https://github.com/{safe_owner}/{safe_repo}"


def build_github_blob_file_url(owner: str, repo_name: str, branch: str, path: str | Path) -> str:
    safe_owner = urllib.parse.quote(owner, safe="")
    safe_repo = urllib.parse.quote(repo_name, safe="")
    safe_branch = urllib.parse.quote(normalize_single_line(branch) or "main", safe="")
    relative_path = str(path).strip().replace(os.sep, "/").lstrip("/")
    safe_path = urllib.parse.quote(relative_path, safe="/")
    return f"https://github.com/{safe_owner}/{safe_repo}/blob/{safe_branch}/{safe_path}"


def build_github_raw_file_url(owner: str, repo_name: str, branch: str, path: str | Path) -> str:
    safe_owner = urllib.parse.quote(owner, safe="")
    safe_repo = urllib.parse.quote(repo_name, safe="")
    safe_branch = urllib.parse.quote(normalize_single_line(branch) or "main", safe="")
    relative_path = str(path).strip().replace(os.sep, "/").lstrip("/")
    safe_path = urllib.parse.quote(relative_path, safe="/")
    return f"https://raw.githubusercontent.com/{safe_owner}/{safe_repo}/{safe_branch}/{safe_path}"


def build_github_issues_url(owner: str, repo_name: str) -> str:
    return f"{build_github_repo_url(owner, repo_name)}/issues"


def build_github_wiki_url(owner: str, repo_name: str) -> str:
    return f"{build_github_repo_url(owner, repo_name)}/wiki"


def build_github_clone_url(owner: str, repo_name: str, *, wiki: bool = False) -> str:
    suffix = ".wiki.git" if wiki else ".git"
    return f"{build_github_repo_url(owner, repo_name)}{suffix}"


def git_command_with_token(*, github_token: str, command: list[str]) -> list[str]:
    basic_value = base64.b64encode(f"x-access-token:{github_token}".encode("utf-8")).decode("ascii")
    return [
        "git",
        "-c",
        f"http.https://github.com/.extraheader=AUTHORIZATION: basic {basic_value}",
        *command,
    ]


def configure_git_commit_identity(cwd: Path) -> None:
    run_subprocess(["git", "config", "user.name", "ModCompiler"], cwd=cwd)
    run_subprocess(["git", "config", "user.email", "modcompiler@users.noreply.github.com"], cwd=cwd)


def checkout_has_managed_marker(root: Path) -> bool:
    return (root / MANAGED_GITHUB_MARKER_FILENAME).exists()


def checkout_is_effectively_empty(root: Path) -> bool:
    for path in root.iterdir():
        if path.name == ".git":
            continue
        return False
    return True


def remove_path_if_exists(path: Path) -> None:
    if path.is_dir():
        safe_rmtree(path)
    elif path.exists():
        path.unlink()


def discover_checkout_branch(cwd: Path, *, fallback: str) -> str:
    try:
        branch = run_subprocess(["git", "branch", "--show-current"], cwd=cwd).strip()
    except ModCompilerError:
        branch = ""
    return branch or fallback


def write_text_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def write_managed_marker(path: Path, payload: dict[str, Any]) -> None:
    write_json(path, payload)


def clear_managed_checkout(checkout_dir: Path) -> None:
    for path in checkout_dir.iterdir():
        if path.name == ".git":
            continue
        remove_path_if_exists(path)


def copy_directory_contents(source_dir: Path, destination_dir: Path) -> None:
    destination_dir.mkdir(parents=True, exist_ok=True)
    for child in sorted(source_dir.iterdir()):
        if child.name == ".git":
            continue
        destination = destination_dir / child.name
        if child.is_dir():
            copy_tree(child, destination)
        else:
            copy_file(child, destination)


def copy_managed_repository_artifacts(*, checkout_dir: Path, bundle_dir: Path) -> list[dict[str, str]]:
    art_metadata = load_bundle_art_metadata(bundle_dir)
    candidates = [
        ("description_image_url", normalize_string_list(art_metadata.get("description_poster_file"))),
        ("logo_image_url", normalize_string_list(art_metadata.get("logo_file"))),
        ("icon_image_url", normalize_string_list(art_metadata.get("icon_file"))),
    ]
    copied: list[dict[str, str]] = []
    target_dir = checkout_dir / MANAGED_GITHUB_ART_DIR
    remove_path_if_exists(target_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    for key, rel_paths in candidates:
        if not rel_paths:
            continue
        source_path = bundle_dir / rel_paths[0]
        if not source_path.exists():
            continue
        destination = target_dir / source_path.name
        copy_file(source_path, destination)
        copied.append(
            {
                "state_key": key,
                "repo_path": f"{MANAGED_GITHUB_ART_DIR}/{destination.name}",
            }
        )
    if not copied:
        remove_path_if_exists(target_dir)
    return copied


def normalize_repo_description(value: str) -> str:
    clean = normalize_single_line(value)
    if len(clean) <= 160:
        return clean
    return clean[:157].rstrip() + "..."


def ensure_public_github_repo(
    *,
    owner: str,
    repo_name: str,
    github_token: str,
    description: str,
) -> tuple[dict[str, Any], bool]:
    repo = github_api_request_json(
        "GET",
        f"/repos/{urllib.parse.quote(owner, safe='')}/{urllib.parse.quote(repo_name, safe='')}",
        github_token=github_token,
        allow_not_found=True,
    )
    created = False
    if repo is None:
        owner_info = github_api_request_json(
            "GET",
            f"/users/{urllib.parse.quote(owner, safe='')}",
            github_token=github_token,
        )
        owner_type = str((owner_info or {}).get("type", "") or "")
        if owner_type.lower() == "organization":
            endpoint = f"/orgs/{urllib.parse.quote(owner, safe='')}/repos"
        else:
            user_info = github_api_request_json("GET", "/user", github_token=github_token)
            login = str((user_info or {}).get("login", "") or "")
            if login.lower() != owner.lower():
                raise ModCompilerError(
                    f"GitHub token is authenticated as {login or 'unknown'}, so it cannot create a user repo under {owner}."
                )
            endpoint = "/user/repos"
        repo = github_api_request_json(
            "POST",
            endpoint,
            github_token=github_token,
            payload={
                "name": repo_name,
                "description": normalize_repo_description(description),
                "private": False,
                "has_issues": True,
                "has_wiki": True,
                "auto_init": False,
            },
        )
        created = True

    patch_payload: dict[str, Any] = {}
    if bool(repo.get("private")):
        patch_payload["private"] = False
    if not bool(repo.get("has_issues", True)):
        patch_payload["has_issues"] = True
    if not bool(repo.get("has_wiki", True)):
        patch_payload["has_wiki"] = True
    desired_description = normalize_repo_description(description)
    if desired_description and normalize_single_line(repo.get("description")) != desired_description:
        patch_payload["description"] = desired_description
    if patch_payload:
        repo = github_api_request_json(
            "PATCH",
            f"/repos/{urllib.parse.quote(owner, safe='')}/{urllib.parse.quote(repo_name, safe='')}",
            github_token=github_token,
            payload=patch_payload,
        )
    return dict(repo or {}), created


def clone_existing_git_repository(*, remote_url: str, destination: Path, github_token: str) -> None:
    run_subprocess(
        git_command_with_token(
            github_token=github_token,
            command=["clone", "--depth", "1", remote_url, str(destination)],
        )
    )


def initialize_git_repository(*, destination: Path, remote_url: str, branch: str) -> None:
    run_subprocess(["git", "init", "-b", branch], cwd=destination)
    run_subprocess(["git", "remote", "add", "origin", remote_url], cwd=destination)


def git_checkout_has_changes(cwd: Path) -> bool:
    return bool(run_subprocess(["git", "status", "--porcelain"], cwd=cwd).strip())


def commit_and_push_git_checkout(
    *,
    cwd: Path,
    branch: str,
    github_token: str,
    message: str,
) -> bool:
    if not git_checkout_has_changes(cwd):
        return False
    run_subprocess(["git", "add", "-A"], cwd=cwd)
    run_subprocess(["git", "commit", "-m", message], cwd=cwd)
    push_branch_with_token(branch=branch, github_token=github_token, cwd=cwd)
    return True


def build_generated_repo_readme(
    *,
    listing: dict[str, str],
    metadata: dict[str, Any],
) -> str:
    body = str(listing.get("long_description", "") or "").strip()
    if not body.startswith("# "):
        body = f"# {listing.get('name', 'Mod')}\n\n{body}".strip()
    credit_line = "This github repository is auto created using itamio's Mod Compiler repository workflow."
    return f"{body}\n\n---\n\n{credit_line}\n"


def build_generated_bug_issue_template(*, listing: dict[str, str], metadata: dict[str, Any]) -> str:
    name = listing.get("name", "Mod")
    loader = normalize_single_line(metadata.get("loader")) or "unknown"
    versions = ", ".join(expand_supported_versions(load_json(REPO_ROOT / DEFAULT_MANIFEST), metadata)) or "unknown"
    return textwrap.dedent(
        f"""
        ---
        name: Bug report
        about: Report a problem with {name}
        title: "[Bug] "
        labels: bug
        ---

        ## Environment

        - Mod: {name}
        - Loader: {loader}
        - Minecraft version: {versions}

        ## What happened?

        Describe the problem clearly.

        ## Steps to reproduce

        1. 
        2. 
        3. 

        ## Expected behavior

        What should have happened instead?

        ## Logs / screenshots

        Paste logs or screenshots here if you have them.
        """
    ).strip() + "\n"


def build_generated_feature_issue_template(*, listing: dict[str, str]) -> str:
    name = listing.get("name", "Mod")
    return textwrap.dedent(
        f"""
        ---
        name: Feature request
        about: Suggest an improvement for {name}
        title: "[Feature] "
        labels: enhancement
        ---

        ## Request

        Describe the feature or improvement you want.

        ## Why it helps

        Explain what pain point this would solve.

        ## Extra context

        Add screenshots, examples, or related mods if useful.
        """
    ).strip() + "\n"


def build_generated_wiki_pages(*, listing: dict[str, str], metadata: dict[str, Any]) -> dict[str, str]:
    name = listing.get("name", "Mod")
    summary = get_listing_summary(listing)
    long_description = str(listing.get("long_description", "") or "").strip()
    if not long_description.startswith("# "):
        long_description = f"# {name}\n\n{long_description}".strip()
    loader = normalize_single_line(metadata.get("loader")) or "unknown"
    versions = ", ".join(expand_supported_versions(load_json(REPO_ROOT / DEFAULT_MANIFEST), metadata)) or (
        str(metadata.get("supported_minecraft", "")).strip() or "unknown"
    )
    jar_name = normalize_single_line(metadata.get("jar_name")) or "unknown"
    installation = textwrap.dedent(
        f"""
        # Installation

        - Loader: `{loader}`
        - Supported Minecraft versions: `{versions}`
        - Source jar: `{jar_name}`

        ## Steps

        1. Install the correct loader version.
        2. Download the jar built for your Minecraft version.
        3. Drop the jar into your `mods` folder.
        4. Launch the game and confirm the mod loads successfully.
        """
    ).strip()
    troubleshooting = textwrap.dedent(
        f"""
        # Troubleshooting

        ## The mod does not load

        - Confirm you are using the correct loader.
        - Confirm your Minecraft version is one of: `{versions}`.
        - Remove duplicate or conflicting jars before retrying.

        ## The game crashes on startup

        - Check `latest.log` and crash reports.
        - Verify you are not mixing unsupported loader or Minecraft versions.
        - Re-test with only `{name}` enabled to isolate conflicts.

        ## Need help?

        Use the GitHub issue tracker for reproducible bugs and attach logs when possible.
        """
    ).strip()
    sidebar = textwrap.dedent(
        """
        * [Home](Home)
        * [Installation](Installation)
        * [Troubleshooting](Troubleshooting)
        """
    ).strip()
    home = "\n\n".join(
        [
            long_description,
            "## Quick Facts",
            "",
            f"- Summary: {summary or '-'}",
            f"- Loader: `{loader}`",
            f"- Supported Minecraft versions: `{versions}`",
        ]
    ).strip()
    return {
        "Home.md": home + "\n",
        "Installation.md": installation + "\n",
        "Troubleshooting.md": troubleshooting + "\n",
        "_Sidebar.md": sidebar + "\n",
    }


def build_generated_docs_pages(*, listing: dict[str, str], metadata: dict[str, Any]) -> dict[str, str]:
    wiki_pages = build_generated_wiki_pages(listing=listing, metadata=metadata)
    docs_pages = {
        "docs/Home.md": wiki_pages["Home.md"],
        "docs/Installation.md": wiki_pages["Installation.md"],
        "docs/Troubleshooting.md": wiki_pages["Troubleshooting.md"],
        "docs/README.md": wiki_pages["Home.md"],
    }
    return docs_pages


def sync_managed_repository_contents(
    *,
    checkout_dir: Path,
    listing: dict[str, str],
    metadata: dict[str, Any],
    decompiled_dir: Path,
    bundle_dir: Path,
    bundle_slug: str,
) -> list[dict[str, str]]:
    clear_managed_checkout(checkout_dir)

    provided_source_dir = bundle_dir / "source"
    if provided_source_dir.exists():
        copy_directory_contents(provided_source_dir, checkout_dir)
    else:
        src_root = decompiled_dir / "src"
        if src_root.exists():
            copy_tree(src_root, checkout_dir / "src")
    mod_info_path = decompiled_dir / "mod_info.txt"
    if mod_info_path.exists() and not (checkout_dir / "mod_info.txt").exists():
        copy_file(mod_info_path, checkout_dir / "mod_info.txt")

    write_managed_marker(
        checkout_dir / MANAGED_GITHUB_MARKER_FILENAME,
        {
            "managed_by": "modcompiler",
            "managed_kind": "repository",
            "bundle_slug": bundle_slug,
            "generated_at": now_iso(),
        },
    )
    write_text_file(
        checkout_dir / "README.md",
        build_generated_repo_readme(
            listing=listing,
            metadata=metadata,
        ),
    )
    for relative_path, content in build_generated_docs_pages(listing=listing, metadata=metadata).items():
        write_text_file(checkout_dir / relative_path, content)
    issue_dir = checkout_dir / ".github" / "ISSUE_TEMPLATE"
    write_text_file(
        issue_dir / "bug_report.md",
        build_generated_bug_issue_template(listing=listing, metadata=metadata),
    )
    write_text_file(
        issue_dir / "feature_request.md",
        build_generated_feature_issue_template(listing=listing),
    )
    write_text_file(
        issue_dir / "config.yml",
        "blank_issues_enabled: true\ncontact_links: []\n",
    )
    return copy_managed_repository_artifacts(checkout_dir=checkout_dir, bundle_dir=bundle_dir)


def sync_managed_wiki_contents(
    *,
    checkout_dir: Path,
    listing: dict[str, str],
    metadata: dict[str, Any],
    bundle_slug: str,
) -> None:
    for relative_path in (
        Path("Home.md"),
        Path("Installation.md"),
        Path("Troubleshooting.md"),
        Path("_Sidebar.md"),
        Path(MANAGED_GITHUB_MARKER_FILENAME),
    ):
        remove_path_if_exists(checkout_dir / relative_path)

    write_managed_marker(
        checkout_dir / MANAGED_GITHUB_MARKER_FILENAME,
        {
            "managed_by": "modcompiler",
            "managed_kind": "wiki",
            "bundle_slug": bundle_slug,
            "generated_at": now_iso(),
        },
    )
    for filename, content in build_generated_wiki_pages(listing=listing, metadata=metadata).items():
        write_text_file(checkout_dir / filename, content)


def sync_bundle_github_links(
    *,
    bundle_dir: Path,
    listing: dict[str, str],
    metadata: dict[str, Any],
    options: GenerateOptions,
) -> dict[str, Any]:
    repo_name = slugify_text(listing.get("name", "")) or slugify_text(metadata.get("primary_mod_id", "")) or bundle_dir.name
    repo_url = build_github_repo_url(options.github_owner, repo_name)
    issues_url = build_github_issues_url(options.github_owner, repo_name)
    wiki_url = build_github_wiki_url(options.github_owner, repo_name)
    docs_home_path = Path("docs") / "Home.md"
    state: dict[str, Any] = {
        "enabled": True,
        "owner": options.github_owner,
        "repo_name": repo_name,
        "default_branch": "",
        "repo_url": repo_url,
        "issues_url": issues_url,
        "source_url": repo_url,
        "wiki_url": wiki_url,
        "description_image_url": "",
        "description_image_urls": [],
        "logo_image_url": "",
        "icon_image_url": "",
        "warnings": [],
        "repo_created": False,
        "repo_updated": False,
        "wiki_updated": False,
    }

    repo_info, created_repo = ensure_public_github_repo(
        owner=options.github_owner,
        repo_name=repo_name,
        github_token=options.github_token,
        description=get_listing_summary(listing) or listing.get("name", ""),
    )
    state["repo_created"] = created_repo
    default_branch = normalize_single_line(repo_info.get("default_branch")) or "main"
    state["default_branch"] = default_branch

    with tempfile.TemporaryDirectory(prefix="modcompiler-repo-sync-") as temp_dir:
        checkout_dir = Path(temp_dir) / "repo"
        remote_url = build_github_clone_url(options.github_owner, repo_name)
        if created_repo:
            checkout_dir.mkdir(parents=True, exist_ok=True)
            initialize_git_repository(destination=checkout_dir, remote_url=remote_url, branch=default_branch)
            configure_git_commit_identity(checkout_dir)
            repo_is_managed = True
        else:
            clone_existing_git_repository(remote_url=remote_url, destination=checkout_dir, github_token=options.github_token)
            configure_git_commit_identity(checkout_dir)
            repo_is_managed = checkout_has_managed_marker(checkout_dir) or checkout_is_effectively_empty(checkout_dir)

        if repo_is_managed:
            repo_branch = discover_checkout_branch(checkout_dir, fallback=default_branch)
            artifact_entries = sync_managed_repository_contents(
                checkout_dir=checkout_dir,
                listing=listing,
                metadata=metadata,
                decompiled_dir=bundle_dir / "decompiled",
                bundle_dir=bundle_dir,
                bundle_slug=bundle_dir.name,
            )
            state["repo_updated"] = commit_and_push_git_checkout(
                cwd=checkout_dir,
                branch=repo_branch,
                github_token=options.github_token,
                message=f"Auto-sync {listing.get('name', bundle_dir.name)} source snapshot",
            )
            for entry in artifact_entries:
                url = build_github_raw_file_url(
                    options.github_owner,
                    repo_name,
                    repo_branch,
                    entry["repo_path"],
                )
                state[entry["state_key"]] = url
            if state.get("description_image_url"):
                state["description_image_urls"] = [state["description_image_url"]]
            docs_home_url = build_github_blob_file_url(
                options.github_owner,
                repo_name,
                repo_branch,
                docs_home_path,
            )
        else:
            repo_branch = default_branch
            docs_home_url = build_github_blob_file_url(
                options.github_owner,
                repo_name,
                repo_branch,
                docs_home_path,
            )
            state["warnings"].append(
                f"GitHub repo {options.github_owner}/{repo_name} already exists and is not ModCompiler-managed, so its files were left unchanged."
            )

    with tempfile.TemporaryDirectory(prefix="modcompiler-wiki-sync-") as temp_dir:
        checkout_dir = Path(temp_dir) / "wiki"
        remote_url = build_github_clone_url(options.github_owner, repo_name, wiki=True)
        try:
            try:
                clone_existing_git_repository(remote_url=remote_url, destination=checkout_dir, github_token=options.github_token)
                configure_git_commit_identity(checkout_dir)
                wiki_branch = discover_checkout_branch(checkout_dir, fallback=MANAGED_GITHUB_WIKI_DEFAULT_BRANCH)
                wiki_is_managed = checkout_has_managed_marker(checkout_dir) or checkout_is_effectively_empty(checkout_dir)
            except ModCompilerError as error:
                text = str(error).lower()
                if "repository not found" in text or "not found" in text or "could not read from remote repository" in text:
                    checkout_dir.mkdir(parents=True, exist_ok=True)
                    initialize_git_repository(
                        destination=checkout_dir,
                        remote_url=remote_url,
                        branch=MANAGED_GITHUB_WIKI_DEFAULT_BRANCH,
                    )
                    configure_git_commit_identity(checkout_dir)
                    wiki_branch = MANAGED_GITHUB_WIKI_DEFAULT_BRANCH
                    wiki_is_managed = True
                else:
                    raise

            if wiki_is_managed:
                sync_managed_wiki_contents(
                    checkout_dir=checkout_dir,
                    listing=listing,
                    metadata=metadata,
                    bundle_slug=bundle_dir.name,
                )
                state["wiki_updated"] = commit_and_push_git_checkout(
                    cwd=checkout_dir,
                    branch=wiki_branch,
                    github_token=options.github_token,
                    message=f"Auto-sync {listing.get('name', bundle_dir.name)} wiki",
                )
            else:
                state["warnings"].append(
                    f"GitHub wiki for {options.github_owner}/{repo_name} already exists and is not ModCompiler-managed, so it was left unchanged."
                )
        except Exception as error:
            state["wiki_updated"] = False
            state["wiki_url"] = docs_home_url
            state["warnings"].append(
                f"GitHub wiki sync failed: {type(error).__name__}: {error}"
            )
            state["warnings"].append(
                f"Falling back to repository docs page for wiki link: {docs_home_url}"
            )

    return state


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


def generate_bundle_for_input_bundle(input_bundle: GenerateInputBundle, options: GenerateOptions) -> dict[str, Any]:
    jar_path = input_bundle.jar_path
    bundle_slug = input_bundle.bundle_slug or f"bundle-{uuid.uuid4().hex[:8]}"
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
    provided_source_dir = bundle_dir / "source"
    decompiled_dir = bundle_dir / "decompiled"
    jar_copy_path = input_dir / jar_path.name
    input_dir.mkdir(parents=True, exist_ok=True)
    copy_file(jar_path, jar_copy_path)
    copy_tree(input_bundle.source_dir, provided_source_dir)

    ai_response_text = ""
    prompt_text = ""
    clarification_prompt_text = ""
    clarification_response_text = ""
    summary_prompt_text = ""
    summary_correction_prompt_text = ""
    summary_response_text = ""
    summary_retry_response_text = ""
    listing_ai_usage = {"hoster": options.c05_hoster, "model": options.c05_model}
    metadata = inspect_mod_jar(jar_path, options.manifest)
    visual_assets: dict[str, Any] | None = None
    external_links: dict[str, Any] = {
        "enabled": not options.nolinks,
        "owner": options.github_owner,
        "repo_name": "",
        "repo_url": "",
        "issues_url": "",
        "source_url": "",
        "wiki_url": "",
        "warnings": [],
        "repo_created": False,
        "repo_updated": False,
        "wiki_updated": False,
    }
    try:
        metadata = remote_decompile_jar_via_github_actions(
            jar_path=jar_path,
            decompiled_dir=decompiled_dir,
            options=options,
        )

        projectinfo_text = build_projectinfo_text(
            jar_path=jar_path,
            metadata=metadata,
            source_dir=provided_source_dir,
            decompiled_dir=decompiled_dir,
        )
        (bundle_dir / "projectinfo.txt").write_text(projectinfo_text, encoding="utf-8")

        source_context_text = build_source_clarification_context_text(source_dir=provided_source_dir)
        (bundle_dir / "source_clarification_context.txt").write_text(source_context_text, encoding="utf-8")
        clarification_prompt_text = build_source_clarification_user_message(
            source_context_text=source_context_text,
            prompt_projectinfo_char_limit=options.prompt_projectinfo_char_limit,
        )
        (bundle_dir / "ai_clarification_request_user_message.txt").write_text(
            clarification_prompt_text,
            encoding="utf-8",
        )
        text_session_id = f"auto-create-text-{bundle_slug}-{uuid.uuid4().hex[:8]}"
        clarification_response_text = stream_local_c05_chat(
            base_url=options.c05_url,
            hoster=options.c05_hoster,
            model=options.c05_model,
            system_prompt="",
            user_prompt=clarification_prompt_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=None,
            timeout_seconds=options.timeout_seconds,
            session_id=text_session_id,
            app_id=TEXT_CHAT_APP_ID,
            include_history=True,
            request_label="source clarification",
        )
        (bundle_dir / "ai_clarification_response.txt").write_text(
            clarification_response_text,
            encoding="utf-8",
        )

        prompt_text = build_ai_user_message(
            projectinfo_text=projectinfo_text,
            source_verified_text=clarification_response_text,
            prompt_projectinfo_char_limit=options.prompt_projectinfo_char_limit,
            template_examples=options.template_examples,
        )
        (bundle_dir / "ai_request_user_message.txt").write_text(prompt_text, encoding="utf-8")

        ai_response_text = stream_local_c05_chat(
            base_url=options.c05_url,
            hoster=options.c05_hoster,
            model=options.c05_model,
            system_prompt="",
            user_prompt=prompt_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=None,
            timeout_seconds=options.timeout_seconds,
            session_id=text_session_id,
            app_id=TEXT_CHAT_APP_ID,
            include_history=True,
        )
        listing_ai_usage = {"hoster": options.c05_hoster, "model": options.c05_model}
        (bundle_dir / "ai_response.txt").write_text(ai_response_text, encoding="utf-8")

        ai_result = parse_ai_listing_response(ai_response_text)
        preliminary_listing = finalize_generated_listing(
            listing=ai_result,
            summary_text="",
            metadata=metadata,
            jar_path=jar_path,
            verified_facts_text=clarification_response_text,
        )
        summary_prompt_text = build_summary_user_message(
            projectinfo_text=projectinfo_text,
            source_verified_text=clarification_response_text,
            name=preliminary_listing["name"],
            long_description=preliminary_listing["long_description"],
            prompt_projectinfo_char_limit=options.prompt_projectinfo_char_limit,
            template_examples=options.template_examples,
        )
        (bundle_dir / "ai_summary_request_user_message.txt").write_text(summary_prompt_text, encoding="utf-8")
        summary_response_text = stream_local_c05_chat(
            base_url=options.c05_url,
            hoster=options.c05_hoster,
            model=options.c05_model,
            system_prompt="",
            user_prompt=summary_prompt_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=None,
            timeout_seconds=options.timeout_seconds,
            session_id=text_session_id,
            app_id=TEXT_CHAT_APP_ID,
            include_history=True,
            request_label="summary generation",
        )
        (bundle_dir / "ai_summary_response.txt").write_text(summary_response_text, encoding="utf-8")
        summary_correction_prompt_text = build_summary_correction_user_message(
            summary_prompt_text,
            summary_response_text,
        )
        (bundle_dir / "ai_summary_retry_request_user_message.txt").write_text(
            summary_correction_prompt_text,
            encoding="utf-8",
        )
        try:
            summary_retry_response_text = stream_local_c05_chat(
                base_url=options.c05_url,
                hoster=options.c05_hoster,
                model=options.c05_model,
                system_prompt="",
                user_prompt=summary_correction_prompt_text,
                reasoning_effort=options.reasoning_effort,
                temperature=options.temperature,
                max_tokens=None,
                timeout_seconds=options.timeout_seconds,
                session_id=text_session_id,
                app_id=TEXT_CHAT_APP_ID,
                include_history=True,
                request_label="summary correction generation",
            )
            (bundle_dir / "ai_summary_retry_response.txt").write_text(summary_retry_response_text, encoding="utf-8")
        except Exception:
            summary_retry_response_text = ""

        summary_candidates: list[str] = []
        summary_parse_errors: list[str] = []
        for candidate_text in (summary_response_text, summary_retry_response_text):
            if not str(candidate_text or "").strip():
                continue
            try:
                summary_candidates.append(parse_ai_summary_response(candidate_text))
            except ModCompilerError as summary_error:
                summary_parse_errors.append(str(summary_error))
        summary_text = choose_best_summary_candidate(summary_candidates)
        if not summary_text:
            detail = summary_parse_errors[-1] if summary_parse_errors else "AI summary response was empty."
            raise ModCompilerError(detail)
        listing = finalize_generated_listing(
            listing=ai_result,
            summary_text=summary_text,
            metadata=metadata,
            jar_path=jar_path,
            verified_facts_text=clarification_response_text,
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

        if not options.nolinks:
            try:
                external_links = sync_bundle_github_links(
                    bundle_dir=bundle_dir,
                    listing=listing,
                    metadata=metadata,
                    options=options,
                )
            except Exception as link_error:
                external_links = {
                    **external_links,
                    "warnings": [f"GitHub link generation failed: {type(link_error).__name__}: {link_error}"],
                }
        write_json(bundle_dir / EXTERNAL_LINKS_FILENAME, external_links)

        bundle_metadata = build_bundle_metadata(
            jar_path=jar_path,
            bundle_slug=bundle_slug,
            source_dir=provided_source_dir,
            input_entry_dir=input_bundle.entry_dir,
            metadata=metadata,
            options=options,
            listing=listing,
            listing_ai_usage=listing_ai_usage,
            visual_assets=visual_assets,
        )
        bundle_metadata["external_links"] = external_links
        if external_links.get("warnings"):
            bundle_metadata["warnings"] = dedupe_preserve_order(
                [*bundle_metadata.get("warnings", []), *external_links.get("warnings", [])]
            )
        write_json(bundle_dir / "bundle_metadata.json", bundle_metadata)

        project_draft = build_project_draft(
            metadata=metadata,
            listing=listing,
            options=options,
            ai_result=ai_result,
            generated_links=external_links,
            include_links=not options.nolinks,
        )
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
                source_dir=provided_source_dir,
                input_entry_dir=input_bundle.entry_dir,
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
        if clarification_prompt_text and not (bundle_dir / "ai_clarification_request_user_message.txt").exists():
            (bundle_dir / "ai_clarification_request_user_message.txt").write_text(
                clarification_prompt_text,
                encoding="utf-8",
            )
        if clarification_response_text and not (bundle_dir / "ai_clarification_response.txt").exists():
            (bundle_dir / "ai_clarification_response.txt").write_text(
                clarification_response_text,
                encoding="utf-8",
            )
        if summary_prompt_text and not (bundle_dir / "ai_summary_request_user_message.txt").exists():
            (bundle_dir / "ai_summary_request_user_message.txt").write_text(summary_prompt_text, encoding="utf-8")
        if summary_response_text and not (bundle_dir / "ai_summary_response.txt").exists():
            (bundle_dir / "ai_summary_response.txt").write_text(summary_response_text, encoding="utf-8")
        (bundle_dir / "error.txt").write_text(
            f"{type(error).__name__}: {error}\n\n{traceback.format_exc()}",
            encoding="utf-8",
        )
        if not (bundle_dir / "projectinfo.txt").exists():
            (bundle_dir / "projectinfo.txt").write_text(
                build_projectinfo_text(
                    jar_path=jar_path,
                    metadata=metadata,
                    source_dir=provided_source_dir if provided_source_dir.exists() else None,
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


def strip_project_external_links(payload: dict[str, Any]) -> dict[str, Any]:
    updated = dict(payload)
    for key in ("issues_url", "source_url", "wiki_url", "discord_url", "donation_urls"):
        updated.pop(key, None)
    return updated


def publish_bundle(
    *,
    bundle_dir: Path,
    client: ModrinthClient,
    dry_run: bool,
    assume_verified: bool,
    disable_links: bool,
) -> dict[str, Any]:
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
    if disable_links:
        project_payload = clean_modrinth_payload(strip_project_external_links(project_payload))
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
                sync_project_icon(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                )
            )
            warnings.extend(
                sync_project_description_image(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                    project_payload=project_payload,
                    project_path=project_path,
                    publish_state=publish_state,
                    allow_external_urls=not disable_links,
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
                sync_project_icon(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                )
            )
            warnings.extend(
                sync_project_description_image(
                    client=client,
                    project_id=project_id,
                    bundle_dir=bundle_dir,
                    project_payload=project_payload,
                    project_path=project_path,
                    publish_state=publish_state,
                    allow_external_urls=not disable_links,
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
            sync_project_icon(
                client=client,
                project_id=project_id,
                bundle_dir=bundle_dir,
            )
        )
        warnings.extend(
            sync_project_description_image(
                client=client,
                project_id=project_id,
                bundle_dir=bundle_dir,
                project_payload=project_payload,
                project_path=project_path,
                publish_state=publish_state,
                allow_external_urls=not disable_links,
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
    source_dir: Path | None,
    decompiled_dir: Path | None,
) -> str:
    details = metadata.get("metadata", {})
    source_origin = "provided" if source_dir and source_dir.exists() else "decompiled"
    source_base_dir = source_dir if source_dir and source_dir.exists() else decompiled_dir
    source_roots = resolve_source_roots(source_base_dir)
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

    lines.append(f"source_origin={source_origin}")
    if source_dir and source_dir.exists():
        lines.append(f"provided_source_dir={source_dir.resolve()}")
    if decompiled_dir and decompiled_dir.exists():
        lines.append(f"decompiled_dir={decompiled_dir.resolve()}")

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
        lines.append("(source files are missing)")

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


def build_source_clarification_context_text(*, source_dir: Path | None) -> str:
    source_roots = resolve_source_roots(source_dir)
    source_files = list_source_files(source_roots)
    packages = sorted({extract_package_name(path) for path in source_files if extract_package_name(path)})

    lines = [
        "# Original Source Context",
        "",
        f"generated_at={now_iso()}",
        f"provided_source_dir={source_dir.resolve() if source_dir and source_dir.exists() else 'missing'}",
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
        lines.append("(source files are missing)")

    lines.extend(["", "## Key Source Excerpts", ""])
    excerpt_paths = select_interesting_source_files(source_roots, source_files, {})
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


def build_source_clarification_user_message(
    *,
    source_context_text: str,
    prompt_projectinfo_char_limit: int,
) -> str:
    trimmed_source_context = truncate_text_block(source_context_text, prompt_projectinfo_char_limit)
    prompt = f"""
Look at this original Minecraft mod source folder and explain, in simple everyday language, what this mod appears to do for players.

Use only the ORIGINAL SOURCE CONTEXT below.
Do not use jar metadata, decompiled output, old summaries, or guesses from past mods.
If something is uncertain, say that it is uncertain.
Keep the wording plain and direct.
Avoid polished or dramatic phrases like "ignite", "deadly zones", "streamlines", or "player-facing utility" unless the source itself really demands that wording.
Prefer leaving details out over guessing.

Return plain text with exactly these sections:
GUARANTEED PLAYER-VISIBLE FACTS
- short bullets with only things the source clearly proves will happen in-game

DO NOT CLAIM
- short bullets for things that are unclear, not proven, or should be left out

Rules:
- Lead with the main thing the player will notice in-game.
- Mention commands, mobs, blocks, items, or rules only when the source clearly proves them.
- Prefer concrete wording over fancy wording.
- Do not return JSON.

ORIGINAL SOURCE CONTEXT:
{trimmed_source_context}
"""
    return textwrap.dedent(prompt).strip() + "\n"


def build_ai_user_message(
    *,
    projectinfo_text: str,
    source_verified_text: str,
    prompt_projectinfo_char_limit: int,
    template_examples: dict[str, str] | None = None,
) -> str:
    trimmed_projectinfo = truncate_text_block(projectinfo_text, prompt_projectinfo_char_limit)
    trimmed_verified = truncate_text_block(source_verified_text, 4_000)
    template = json.dumps(
        {
            "name": "Short memorable mod name",
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

You already reviewed the original source folder earlier in this chat.
Use that source-based understanding as the main truth for what the mod actually does.
Use only the facts found in PROJECT INFO below.
Use SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT as the highest-trust description of what the mod actually does.
If PROJECT INFO, code comments, or your own instincts suggest extra claims that are not clearly supported by SOURCE-VERIFIED NOTES, leave them out.
Do not invent features, commands, compatibility, authors, loaders, game versions, or licensing facts.
Use the perfect template examples below for tone, quality, and formatting only.
Do not copy the example mod's features unless PROJECT INFO proves them.
Lead with the main player-facing feature, not the internal implementation.
If the jar metadata already contains a solid name, keep it very close to that name.
Compare the raw jar filename, jar_display_name, metadata name, and primary_mod_id before choosing the final name.
If one candidate has an obvious typo and another candidate is clearly the corrected human-readable name, use the corrected one.
Prefer fixing obvious spelling mistakes like "most" to "mobs" when the jar naming proves the intended public name.
Make the long description feel like a real human-written Modrinth page in simple everyday wording.
You may use Markdown headings and bullet lists when they improve clarity.
Mention secondary features only if they are clearly present and worth surfacing.
Avoid implementation details like reflection, event hooks, custom damage sources,
cooldown fields, package names, class names, or internal APIs.
Avoid filler compatibility lines unless PROJECT INFO makes them unusually important.
Avoid overly advanced or dramatic wording like "ignite", "deadly zones", "seamless", or "streamlines" when simpler wording would be more natural.
Do not pad the description with generic filler like "works in single-player and multiplayer", "works on existing saves",
"lightweight", or broad "why use it" claims unless PROJECT INFO clearly proves them.
Reuse the concrete nouns and behavior already present in PROJECT INFO instead of broadening them into extra gameplay claims.
Small helper phrases are good when they make the writing feel more human, like "so", "well", "basically", "which means", or "that means".
Slightly imperfect or casual grammar is okay if it makes the explanation easier to read and closer to the examples.
Do not over-polish the wording into marketing copy or textbook English.
Keep long_description focused and compact enough to complete cleanly in a single JSON response.
Usually 3 to 7 short paragraphs or bullets is enough.
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

description.txt
{template_examples["description"]}

SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:
{trimmed_verified}

PROJECT INFO:
{trimmed_projectinfo}
"""
    return textwrap.dedent(prompt).strip() + "\n"


def build_summary_user_message(
    *,
    projectinfo_text: str,
    source_verified_text: str,
    name: str,
    long_description: str,
    prompt_projectinfo_char_limit: int,
    template_examples: dict[str, str] | None = None,
) -> str:
    trimmed_projectinfo = truncate_text_block(projectinfo_text, prompt_projectinfo_char_limit)
    trimmed_verified = truncate_text_block(source_verified_text, 4_000)
    trimmed_long_description = truncate_text_block(long_description, 1_500)
    template_examples = template_examples or load_template_examples()
    specific_guidance = build_summary_specific_guidance(
        name=name,
        long_description=trimmed_long_description,
        projectinfo_text=trimmed_projectinfo,
        source_verified_text=trimmed_verified,
    )
    prompt = f"""
Write only the Modrinth summary for one Minecraft mod.

You already reviewed the original source folder earlier in this chat.
Use that source-based understanding first, then use the chosen name and long description below only to stay aligned.
Use SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT as the highest-trust fact list.
Return plain text only.
Do not return JSON.
Do not return markdown bullets, headings, explanations, quotes, or multiple options.
Focus only on producing one strong player-facing summary line.

Summary rules:
- Usually 1 sentence, or 2 very short sentences.
- Write naturally. The script will shorten the final summary later if needed.
- Sound like a real human mod page, not corporate marketing copy.
- Prefer an accurate complaint/problem -> payoff cadence when the mod naturally fits it.
- Match the rhythm, wording, and sentence structure of the examples very closely.
- It is okay to copy the example structure almost directly and just swap in the real mod details.
- Do not try to be original if the examples already fit. Reuse their shape.
- When it fits naturally, strongly prefer starting with a player frustration hook like "Hate...", "Tired of...", or "Wanting...".
- Be specific about the actual player-visible benefit.
- Keep the wording simple and casual rather than polished or dramatic.
- Small helper words and bridge phrases are good, like "well", "now", "so", "basically", or "which means", when they make it sound more human.
- Slightly imperfect grammar is okay if it sounds clearer, more casual, and closer to the examples.
- Do not mention internal implementation details, package names, event names, hooks, APIs, or metadata trivia.
- Do not add compatibility or command claims unless the project info clearly proves them.
- Do not exaggerate.
- Never count characters, words, or tokens out loud.
- Never show your steps, analysis, or any 'Count:' style output.
- Avoid generic openings like "Hostile mobs..." or "This mod..." when a sharper player-facing hook would fit better.
- Avoid advanced wording like "ignite", "deadly zones", or "roaming safely in daylight" when simpler wording would sound more human.
- Avoid clever rewrites, slogans, or edgy phrases like "free-range mob-killer".

Important:
- Treat the examples below like fill-in-the-blank templates.
- Copy their sentence pattern very closely when the mod fits.
- Prefer sounding a lot like the examples over sounding polished.
- Reuse phrases like "Hate that...", "Hate the...", "This mod makes it so that...", "Wanting to...", and "Well this mod cancels that pain..." when they fit.
- Do not paraphrase the examples into brand new wording if the example phrasing already works.
- Do not invent a fake second complaint just to fit Template A. If only one real complaint is clearly supported, use Template C.
- Reuse the concrete nouns already present in PROJECT INFO. Do not swap in new problems like raids, bases, or travel unless PROJECT INFO really proves them.
- If the verified notes only prove one clear complaint, stick to one clear complaint.

Preferred reusable templates:
- Template A: Hate that [annoying thing]? Hate the [second annoying thing]? This mod makes it so that [main payoff].
- Template B: Wanting to [goal]? Tired of [pain]? Well this mod cancels that pain, you can [main payoff].
- Template C: Hate when [problem]? This mod [simple payoff].

SPECIFIC GUIDANCE FOR THIS MOD:
{specific_guidance}

Good style examples:
{template_examples["summary"]}

Chosen mod name:
{name}

Chosen long description:
{trimmed_long_description}

SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:
{trimmed_verified}

PROJECT INFO:
{trimmed_projectinfo}
"""
    return textwrap.dedent(prompt).strip() + "\n"


def build_summary_correction_user_message(summary_prompt_text: str, previous_summary_text: str) -> str:
    return (
        "Wrong your generated summary is incorrect, see my instructions and examples again.\n"
        + "Look back at the earlier history in this chat and follow those examples and verified facts more closely.\n"
        + "Previous generated summary:\n"
        + normalize_single_line(previous_summary_text)
        + "\nYour previous summary did not copy the example structure closely enough.\n"
        + "Rewrite it by following the earlier examples much more directly.\n"
        + "Only use things that the verified facts clearly prove.\n"
        + "Do not invent a fake second complaint if the project only clearly supports one main annoyance.\n"
        + "Use small helper words like well, so, or now if that makes it sound more natural.\n"
        + "Slightly imperfect grammar is okay if it reads more like a real person wrote it.\n"
        + "Return only the corrected summary line.\n"
    )


def build_summary_specific_guidance(*, name: str, long_description: str, projectinfo_text: str, source_verified_text: str) -> str:
    combined = "\n".join(str(item or "") for item in (name, long_description, projectinfo_text, source_verified_text)).lower()
    looks_like_daylight_burn_mod = (
        ("daylight" in combined or "sun" in combined)
        and "burn" in combined
        and ("hostile mobs" in combined or "mobs" in combined)
    )
    if looks_like_daylight_burn_mod:
        return textwrap.dedent(
            """
            - Use Template C for this mod.
            - The one clear complaint is that hostile mobs do not burn in daylight.
            - Stay concrete and close to this exact shape:
              Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.
            - Do not use vague complaints like "wander around", "ignore the sun", "keep walking at noon", or "unscathed".
            """
        ).strip()
    return "- Use the template that best fits the concrete, proven annoyance for this mod."


def parse_ai_listing_response(raw_text: str) -> dict[str, Any]:
    data = extract_json_object(raw_text)
    name = normalize_single_line(data.get("name") or data.get("title") or "")
    long_description = str(
        data.get("long_description")
        or data.get("longDescription")
        or data.get("body")
        or ""
    ).strip()

    if not name:
        raise ModCompilerError("AI response did not contain a usable 'name'.")
    if not long_description:
        raise ModCompilerError("AI response did not contain a usable 'long_description'.")

    return {
        "name": name,
        "long_description": long_description,
        "categories": normalize_category_values(data.get("categories")),
        "additional_categories": normalize_category_values(data.get("additional_categories")),
        "client_side": normalize_side_hint(data.get("client_side")),
        "server_side": normalize_side_hint(data.get("server_side")),
        "license_id": normalize_ai_license_id(data.get("license_id")),
        "license_url": normalize_single_line(data.get("license_url") or ""),
    }


def parse_ai_summary_response(raw_text: str) -> str:
    stripped = str(raw_text or "").strip()
    if not stripped:
        raise ModCompilerError("AI summary response was empty.")

    try:
        data = extract_json_object(stripped)
    except ModCompilerError:
        data = None

    if isinstance(data, dict):
        candidate = normalize_single_line(
            data.get("summary")
            or data.get("short_description")
            or data.get("shortDescription")
            or data.get("description")
            or ""
        )
        if candidate and not summary_text_looks_glitched(candidate):
            return candidate

    stripped = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", stripped)
    stripped = re.sub(r"\s*```$", "", stripped)
    recovered = extract_best_answer_candidate_text(stripped)
    if recovered:
        stripped = recovered
    stripped = normalize_single_line(stripped.strip().strip('"').strip("'"))
    if not stripped:
        raise ModCompilerError("AI summary response did not contain usable text.")
    if summary_text_looks_glitched(stripped):
        raise ModCompilerError("AI summary response did not contain usable summary text.")
    return stripped


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
    summary = get_listing_summary(listing) or details.get("description", "")
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
    recommended_backgrounds = recommend_background_images(
        background_images,
        title=title,
        summary=summary,
        long_description=body,
        limit=3,
    )
    recommended_background_choices = (
        render_background_image_choices(recommended_backgrounds)
        if recommended_backgrounds
        else ""
    )
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
- Make every visible label about the mod's real gameplay behavior, features, or player-facing effect.
- Never mention draft status, review status, workflow steps, bundle processing, templates, uploads, approvals, or Modrinth administration.
- `logo.rail_left`, `logo.rail_right`, `description.chips`, and `description.stats` must describe actual mod behavior, not project status.

BACKGROUND IMAGE OPTIONS
Choose the single background file whose filename mood best fits this mod:
{background_choices}

RECOMMENDED BACKGROUND OPTIONS
Strongly prefer one of these if it clearly matches the mod better than the rest:
{recommended_background_choices or "- No strong recommendation. Choose the most relevant non-generic background."}

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

SUMMARY:
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


VISUAL_ADMIN_MARKERS = (
    "draft",
    "review",
    "verify",
    "verified",
    "workflow",
    "next step",
    "manual review",
    "approved",
    "template",
    "upload",
    "bundle",
    "project status",
    "draft state",
    "modrinth",
)
VISUAL_GENERIC_EYEBROWS = {
    "utility mod",
    "game mechanics",
    "content mod",
    "minecraft mod",
}
BACKGROUND_THEME_RULES: tuple[dict[str, Any], ...] = (
    {
        "choice_markers": ("fire", "burn", "burning", "lava"),
        "text_markers": ("fire", "burn", "burning", "sun", "daylight", "ignite", "ignites", "tnt", "explosion", "explosive", "lava"),
        "score": 14,
    },
    {
        "choice_markers": ("sunset", "sunrise", "sun"),
        "text_markers": ("sun", "daylight", "day", "sunrise", "sunset", "burn"),
        "score": 8,
    },
    {
        "choice_markers": ("ocean", "sea", "water"),
        "text_markers": ("water", "ocean", "sea", "river", "boat", "fish", "swim", "wet"),
        "score": 12,
    },
    {
        "choice_markers": ("winter", "snow", "ice", "frost"),
        "text_markers": ("winter", "snow", "ice", "frost", "cold", "freeze", "frozen", "blizzard"),
        "score": 12,
    },
    {
        "choice_markers": ("spring", "forest", "grass", "flowers", "flower", "mountain", "breeze"),
        "text_markers": ("spring", "forest", "grass", "flower", "flowers", "nature", "mountain", "field", "trees"),
        "score": 8,
    },
    {
        "choice_markers": ("anime", "girl"),
        "text_markers": ("anime", "girl", "character", "npc", "waifu", "companion"),
        "score": 10,
        "mismatch_penalty": -10,
    },
)


def visual_text_looks_admin(value: Any) -> bool:
    lowered = normalize_single_line(value).lower()
    return bool(lowered) and any(marker in lowered for marker in VISUAL_ADMIN_MARKERS)


def score_background_choice_relevance(
    choice: BackgroundImageChoice,
    *,
    title: str,
    summary: str,
    long_description: str,
) -> int:
    choice_blob = f"{choice.relative_name} {choice.label}".lower()
    text_blob = " ".join(
        part for part in (
            normalize_single_line(title),
            normalize_single_line(summary),
            normalize_single_line(long_description),
        )
        if part
    ).lower()
    if not choice_blob:
        return 0

    choice_tokens = set(tokenize_name_words(choice_blob))
    text_tokens = set(tokenize_name_words(text_blob))
    score = 0

    for token in choice_tokens & text_tokens:
        if len(token) >= 3:
            score += 4

    for rule in BACKGROUND_THEME_RULES:
        choice_hit = any(marker in choice_blob for marker in rule["choice_markers"])
        if not choice_hit:
            continue
        if any(marker in text_blob for marker in rule["text_markers"]):
            score += int(rule["score"])
        else:
            score += int(rule.get("mismatch_penalty", 0))

    generic_penalty_markers = ("anime", "girl")
    if any(marker in choice_blob for marker in generic_penalty_markers) and not any(marker in text_blob for marker in generic_penalty_markers):
        score -= 6

    return score


def recommend_background_images(
    background_images: tuple[BackgroundImageChoice, ...],
    *,
    title: str,
    summary: str,
    long_description: str,
    limit: int | None = None,
) -> tuple[BackgroundImageChoice, ...]:
    if not background_images:
        return ()

    scored: list[tuple[int, int, BackgroundImageChoice]] = []
    for index, choice in enumerate(background_images):
        score = score_background_choice_relevance(
            choice,
            title=title,
            summary=summary,
            long_description=long_description,
        )
        scored.append((score, -index, choice))

    ranked = [choice for score, _neg_index, choice in sorted(scored, reverse=True) if score > 0]
    if limit is not None:
        ranked = ranked[:limit]
    return tuple(ranked)


def collect_visual_feature_sentences(summary: str, long_description: str) -> list[str]:
    candidates: list[str] = []
    for raw_line in str(long_description or "").splitlines():
        stripped = strip_markdown_prefix(raw_line)
        if not stripped:
            continue
        if stripped.startswith("---"):
            continue
        candidates.extend(split_sentences(stripped) or [stripped])
    if summary:
        candidates.insert(0, normalize_single_line(summary))
    return dedupe_preserve_order(
        normalize_single_line(candidate)
        for candidate in candidates
        if normalize_single_line(candidate)
    )


def visual_feature_text_blob(summary: str, long_description: str) -> str:
    return "\n".join(collect_visual_feature_sentences(summary, long_description)).lower()


def build_visual_feature_defaults(*, title: str, summary: str, long_description: str) -> dict[str, Any]:
    clean_title = normalize_single_line(title) or "Example Mod"
    clean_summary = normalize_single_line(summary) or "A polished mod page presentation."
    feature_blob = visual_feature_text_blob(clean_summary, long_description)

    eyebrow = "Gameplay Mod"
    if "hostile mob" in feature_blob or "mobs" in feature_blob:
        eyebrow = "Mob Tweaks"
    elif "command" in feature_blob or "/tpa" in feature_blob or "/rtp" in feature_blob:
        eyebrow = "Server Tools"
    elif "redstone" in feature_blob or "hopper" in feature_blob or "automation" in feature_blob:
        eyebrow = "Automation Mod"

    chips: list[str] = []
    if ("daylight" in feature_blob or "sun" in feature_blob) and ("burn" in feature_blob or "fire" in feature_blob):
        chips.append("Daylight Burn")
    if "hostile mob" in feature_blob or "hostile mobs" in feature_blob:
        chips.append("Hostile Mobs")
    elif "all mobs" in feature_blob or "every mob" in feature_blob:
        chips.append("All Mobs")
    if "water" in feature_blob:
        chips.append("Water Stops It")
    if "fire immune" in feature_blob or "immune to fire" in feature_blob:
        chips.append("Fire Immune")
    if "20 ticks" in feature_blob or "one second" in feature_blob or "every second" in feature_blob:
        chips.append("20 Tick Damage")
    if len(chips) < 2:
        fallback_words = [
            "Sun Trigger" if "sun" in feature_blob or "daylight" in feature_blob else "",
            "Auto Effect" if "automatic" in feature_blob or "automatically" in feature_blob else "",
            "Core Feature",
        ]
        chips = dedupe_preserve_order(chips + [item for item in fallback_words if item])
    chips = chips[:4]

    stats: list[dict[str, str]] = []
    if "hostile mob" in feature_blob or "hostile mobs" in feature_blob:
        stats.append(
            {
                "value": "All",
                "label": "Affected Mobs",
                "note": "Every hostile mob gets hit by the effect while the sun is up.",
            }
        )
    elif "all mobs" in feature_blob or "every mob" in feature_blob:
        stats.append(
            {
                "value": "All",
                "label": "Affected Mobs",
                "note": "The effect applies to every mob covered by the mod's rules.",
            }
        )
    if "20 ticks" in feature_blob or "one second" in feature_blob or "every second" in feature_blob:
        stats.append(
            {
                "value": "20 Ticks",
                "label": "Damage Tick",
                "note": "The burn damage hits on a regular one-second rhythm.",
            }
        )
    elif "daylight" in feature_blob or "sun" in feature_blob:
        stats.append(
            {
                "value": "Sunlight",
                "label": "Trigger",
                "note": "The effect starts when mobs are out in daylight.",
            }
        )
    if ("water" in feature_blob) or ("fire immune" in feature_blob or "immune to fire" in feature_blob):
        stats.append(
            {
                "value": "Water / Fire",
                "label": "Exceptions",
                "note": "Water or fire immunity can stop the effect from applying.",
            }
        )
    if len(stats) == 0:
        stats.append(
            {
                "value": "Auto",
                "label": "Core Effect",
                "note": clean_summary,
            }
        )
    if len(stats) == 1:
        stats.append(
            {
                "value": "Visible",
                "label": "Player Hook",
                "note": clean_summary,
            }
        )
    stats = stats[:2]

    rail_left = chips[0] if chips else "Core Feature"
    rail_right = chips[1] if len(chips) > 1 else "Player Visible"
    return {
        "eyebrow": eyebrow,
        "subtitle": clean_summary,
        "tagline": clean_summary,
        "rail_left": rail_left,
        "rail_right": rail_right,
        "chips": chips[:4],
        "stats": stats[:2],
    }


def normalize_visual_design_response(
    data: dict[str, Any],
    *,
    title: str,
    summary: str,
    long_description: str = "",
    background_images: tuple[BackgroundImageChoice, ...] = (),
) -> dict[str, Any]:
    logo_data = data.get("logo", {}) if isinstance(data.get("logo"), dict) else {}
    description_data = data.get("description", {}) if isinstance(data.get("description"), dict) else {}
    feature_defaults = build_visual_feature_defaults(
        title=title,
        summary=summary,
        long_description=long_description,
    )

    chips_raw = description_data.get("chips")
    chips = [
        chip
        for chip in normalize_string_list(chips_raw)[:4]
        if chip and not visual_text_looks_admin(chip)
    ]
    if len(chips) < 2:
        chips = dedupe_preserve_order(chips + list(feature_defaults["chips"]))

    stats_raw = description_data.get("stats")
    stats: list[dict[str, str]] = []
    if isinstance(stats_raw, list):
        for item in stats_raw:
            if not isinstance(item, dict):
                continue
            value = normalize_single_line(item.get("value"))
            label = normalize_single_line(item.get("label"))
            note = normalize_single_line(item.get("note"))
            if (
                value
                and label
                and note
                and not visual_text_looks_admin(value)
                and not visual_text_looks_admin(label)
                and not visual_text_looks_admin(note)
            ):
                stats.append({"value": value, "label": label, "note": note})
            if len(stats) >= 2:
                break
    while len(stats) < 2:
        stats.append(feature_defaults["stats"][len(stats)])

    clean_title = normalize_single_line(title) or "Example Mod"
    clean_summary = normalize_single_line(summary) or "A polished mod page presentation."
    eyebrow = normalize_single_line(logo_data.get("eyebrow"))
    if not eyebrow or visual_text_looks_admin(eyebrow) or eyebrow.lower() in VISUAL_GENERIC_EYEBROWS:
        eyebrow = feature_defaults["eyebrow"]
    rail_left = normalize_single_line(logo_data.get("rail_left"))
    if not rail_left or visual_text_looks_admin(rail_left):
        rail_left = feature_defaults["rail_left"]
    rail_right = normalize_single_line(logo_data.get("rail_right"))
    if not rail_right or visual_text_looks_admin(rail_right):
        rail_right = feature_defaults["rail_right"]
    subtitle = normalize_single_line(logo_data.get("subtitle"))
    if not subtitle or visual_text_looks_admin(subtitle):
        subtitle = feature_defaults["subtitle"]
    tagline = normalize_single_line(description_data.get("tagline"))
    if not tagline or visual_text_looks_admin(tagline):
        tagline = feature_defaults["tagline"]
    return {
        "accent_color": normalize_hex_color(data.get("accent_color")),
        "background_image": normalize_background_choice(
            data.get("background_image"),
            background_images=background_images,
            title=clean_title,
            summary=clean_summary,
            long_description=long_description,
        ),
        "logo": {
            "eyebrow": eyebrow,
            "mark": normalize_single_line(logo_data.get("mark")) or default_visual_mark(clean_title),
            "title": normalize_single_line(logo_data.get("title")) or clean_title,
            "subtitle": subtitle or clean_summary,
            "rail_left": rail_left,
            "rail_right": rail_right,
        },
        "description": {
            "kicker": normalize_single_line(description_data.get("kicker")) or eyebrow,
            "title": normalize_single_line(description_data.get("title")) or clean_title,
            "tagline": tagline or clean_summary,
            "chips": chips[:4],
            "stats": stats[:2],
        },
    }


def parse_visual_prompt_response(raw_text: str) -> dict[str, str]:
    stripped = str(raw_text or "").strip()
    if stripped:
        data = extract_json_object(stripped, source_hint="art/visual-html.response.txt")
        if isinstance(data, dict):
            return data
    return {}


def finalize_generated_listing(
    *,
    listing: dict[str, str],
    summary_text: str,
    metadata: dict[str, Any],
    jar_path: Path,
    verified_facts_text: str = "",
) -> dict[str, str]:
    details = metadata.get("metadata", {})
    detected_name = normalize_single_line(details.get("name") or "")
    jar_display_name = humanize_name(jar_path.stem)
    mod_id_display_name = humanize_name(str(details.get("mod_id") or ""))
    fallback_name = mod_id_display_name or jar_display_name or humanize_name(str(jar_path.stem))
    summary = normalize_summary(
        summary_text or get_listing_summary(listing),
        fallback=details.get("description") or "",
    )
    summary = normalize_summary(
        repair_summary_candidate(
            summary,
            metadata=metadata,
            long_description=listing.get("long_description", ""),
        ),
        fallback=summary,
    )
    long_description = normalize_long_description(
        listing.get("long_description", ""),
        summary=summary,
        verified_facts_text=verified_facts_text,
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
        "summary": summary,
        "long_description": long_description,
    }


def ordered_json_candidates_from_text(text: str) -> list[str]:
    raw_candidates = json_candidates_from_text(str(text or "").strip())
    if not raw_candidates:
        return []
    primary = raw_candidates[:1]
    repaired = repaired_json_candidates_from_text(text)
    secondary = sorted(raw_candidates[1:], key=len, reverse=True)
    return dedupe_preserve_order(primary + repaired + secondary)


def extract_json_object(raw_text: str, *, source_hint: str = "ai_response.txt") -> dict[str, Any]:
    stripped = str(raw_text or "").strip()
    if not stripped:
        raise ModCompilerError("AI response was empty.")

    for candidate in ordered_json_candidates_from_text(stripped):
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            return parsed

    if "{" in stripped and stripped.count("{") > stripped.count("}"):
        raise ModCompilerError(
            "Could not parse a JSON object from the AI response because it appears truncated before the closing brace. "
            "The model likely ran out of room; rerun with a higher token budget such as `--max-tokens 3200` or higher. "
            f"Check {source_hint} in the bundle for the raw partial output."
        )

    raise ModCompilerError(
        "Could not parse a JSON object from the AI response. "
        f"Check {source_hint} in the bundle for the raw model output."
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


def get_listing_summary(listing: dict[str, Any]) -> str:
    return normalize_single_line(
        listing.get("summary")
        or listing.get("short_description")
        or listing.get("shortDescription")
        or ""
    )


def normalize_summary(raw_text: str, *, fallback: str) -> str:
    text = normalize_single_line(raw_text)
    if not text:
        text = normalize_single_line(fallback) or "A Minecraft mod."
    if len(text) <= SHORT_DESCRIPTION_MAX_CHARS:
        return text

    sentences = split_sentences(text)
    for sentence in sentences:
        normalized = normalize_single_line(sentence)
        if normalized and len(normalized) <= SHORT_DESCRIPTION_MAX_CHARS:
            return normalized

    for divider in (". ", "; ", " - ", ", "):
        head = text.split(divider, 1)[0].strip()
        if head and len(head) <= SHORT_DESCRIPTION_MAX_CHARS:
            return head.rstrip(".,;:") + "."
    return text[: SHORT_DESCRIPTION_MAX_CHARS - 3].rstrip() + "..."


def repair_summary_candidate(text: str, *, metadata: dict[str, Any], long_description: str) -> str:
    normalized = normalize_single_line(text)
    if not normalized:
        return normalized

    details = metadata.get("metadata", {})
    combined = "\n".join(
        str(item or "")
        for item in (
            normalized,
            details.get("description"),
            details.get("name"),
            long_description,
            metadata.get("primary_mod_id"),
        )
    ).lower()

    if (
        ("daylight" in combined or "sun" in combined)
        and "burn" in combined
        and any(
            phrase in combined
            for phrase in ("wander around", "ignore the sun", "keep walking at noon", "unscathed", "keep lurking")
        )
    ):
        subject = "hostile mobs" if "hostile" in combined else "mobs"
        return f"Hate when {subject} don't burn in daylight? Well this mod makes it so that ALL {subject} burn in the sun."

    return normalized


def normalize_short_description(raw_text: str, *, fallback: str) -> str:
    return normalize_summary(raw_text, fallback=fallback)


def normalize_long_description(raw_text: str, *, summary: str, verified_facts_text: str = "") -> str:
    cleaned = str(raw_text or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not cleaned:
        return ensure_terminal_punctuation(summary)

    short_key = comparable_text(summary)
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
                if (
                    is_overly_technical_sentence(stripped_markdown)
                    or is_generic_compatibility_sentence(stripped_markdown)
                    or is_generic_filler_sentence(stripped_markdown)
                    or is_unverified_claim_sentence(stripped_markdown, verified_facts_text)
                ):
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
                and not is_generic_filler_sentence(sentence)
                and not is_unverified_claim_sentence(sentence, verified_facts_text)
            ]
            if filtered:
                kept_lines.append(" ".join(filtered))

        if kept_lines:
            kept_blocks.append("\n".join(kept_lines))

    if not kept_blocks:
        return ensure_terminal_punctuation(summary)

    result = "\n\n".join(kept_blocks[:8]).strip()
    result = strip_empty_markdown_heading_blocks(result)
    if len(result) > 2_500:
        result = result[:2_497].rstrip() + "..."
    return result


def strip_empty_markdown_heading_blocks(text: str) -> str:
    blocks = [block.strip() for block in re.split(r"\n\s*\n", str(text or "").strip()) if block.strip()]
    if not blocks:
        return ""

    kept: list[str] = []
    for index, block in enumerate(blocks):
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        is_heading_only = len(lines) == 1 and lines[0].startswith("#")
        if is_heading_only:
            if lines[0].startswith("# ") and index == 0:
                kept.append(block)
                continue
            next_block = blocks[index + 1].strip() if index + 1 < len(blocks) else ""
            next_lines = [line.strip() for line in next_block.splitlines() if line.strip()]
            if not next_lines or (len(next_lines) == 1 and next_lines[0].startswith("#")):
                continue
        kept.append(block)
    return "\n\n".join(kept).strip()


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


def is_generic_filler_sentence(sentence: str) -> bool:
    lowered = sentence.lower()
    return any(marker in lowered for marker in GENERIC_FILLER_DESCRIPTION_MARKERS)


def is_unverified_claim_sentence(sentence: str, verified_facts_text: str) -> bool:
    lowered = normalize_single_line(sentence).lower()
    verified = normalize_single_line(verified_facts_text).lower()
    if not lowered or not verified:
        return False
    for group in UNVERIFIED_DESCRIPTION_MARKER_GROUPS:
        if any(marker in lowered for marker in group) and not any(marker in verified for marker in group):
            return True
    return False


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
    title: str = "",
    summary: str = "",
    long_description: str = "",
) -> str:
    if not background_images:
        return ""

    raw = normalize_single_line(value).replace("\\", "/")
    matched_choice: BackgroundImageChoice | None = None
    if raw:
        lowered = raw.lower()
        for choice in background_images:
            if lowered == choice.relative_name.lower() or lowered == choice.path.name.lower():
                matched_choice = choice
                break
        if matched_choice is None:
            raw_comp = comparable_text(raw)
            for choice in background_images:
                if raw_comp in {
                    comparable_text(choice.relative_name),
                    comparable_text(choice.path.name),
                    comparable_text(choice.label),
                }:
                    matched_choice = choice
                    break

    recommended = recommend_background_images(
        background_images,
        title=title,
        summary=summary,
        long_description=long_description,
        limit=1,
    )
    best_choice = recommended[0] if recommended else None
    if matched_choice is not None:
        if best_choice is None:
            return matched_choice.relative_name
        matched_score = score_background_choice_relevance(
            matched_choice,
            title=title,
            summary=summary,
            long_description=long_description,
        )
        best_score = score_background_choice_relevance(
            best_choice,
            title=title,
            summary=summary,
            long_description=long_description,
        )
        if matched_score <= 0 and best_score >= 4:
            return best_choice.relative_name
        return matched_choice.relative_name

    if best_choice is not None:
        return best_choice.relative_name

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


def repaired_json_candidates_from_text(text: str) -> list[str]:
    stripped = str(text or "").strip()
    if not stripped:
        return []

    candidates: list[str] = []
    looks_like_object_fields = bool(re.match(r'^\s*"[^"]+"\s*:', stripped))
    looks_like_missing_name_field = bool(
        re.match(r'^[^"{\n][^,\n]{1,240}",\s*"long_description"\s*:', stripped, flags=re.DOTALL)
    )
    missing_name_value_match = re.match(
        r'^\s*(?P<name_value>"(?:[^"\\]|\\.){1,400}")(?P<remainder>\s*,\s*"long_description"\s*:.*)$',
        stripped,
        flags=re.DOTALL,
    )

    if looks_like_object_fields and not stripped.startswith("{"):
        candidates.append("{" + stripped)
    if stripped.startswith("{") and not stripped.endswith("}"):
        candidates.append(stripped + "}")
    if looks_like_object_fields and not stripped.startswith("{") and not stripped.endswith("}"):
        candidates.append("{" + stripped + "}")
    if looks_like_missing_name_field:
        candidates.append('{"name": "' + stripped)
        if not stripped.endswith("}"):
            candidates.append('{"name": "' + stripped + "}")
    if missing_name_value_match:
        repaired = (
            '{"name": '
            + missing_name_value_match.group("name_value")
            + missing_name_value_match.group("remainder")
        )
        candidates.append(repaired)
        if not repaired.endswith("}"):
            candidates.append(repaired + "}")

    return dedupe_preserve_order(candidates)


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


def extract_text_fragments_from_chat_parts(parts: Any) -> list[str]:
    if isinstance(parts, dict):
        parts = [parts]
    if not isinstance(parts, list):
        return []

    fragments: list[str] = []
    for part in parts:
        if isinstance(part, (str, int, float, bool)):
            text = str(part)
            if text:
                fragments.append(text)
            continue
        if not isinstance(part, dict):
            continue

        text_value = part.get("text")
        if isinstance(text_value, (str, int, float, bool)):
            text = str(text_value)
            if text:
                fragments.append(text)
                continue

        type_name = str(part.get("type") or "").strip().lower()
        if type_name in {"text", "output_text", "input_text"}:
            for key in ("content", "value"):
                value = part.get(key)
                if isinstance(value, (str, int, float, bool)):
                    text = str(value)
                    if text:
                        fragments.append(text)
                    break
    return fragments


def merge_stream_text_candidates(primary_text: str, secondary_text: str) -> str:
    primary = str(primary_text or "")
    secondary = str(secondary_text or "")
    if not primary.strip():
        return secondary.strip()
    if not secondary.strip():
        return primary.strip()

    if primary in secondary:
        return secondary.strip()
    if secondary in primary:
        return primary.strip()

    primary_start = primary.lstrip()[:1]
    secondary_start = secondary.lstrip()[:1]
    primary_end = primary.rstrip()[-1:] if primary.rstrip() else ""
    secondary_end = secondary.rstrip()[-1:] if secondary.rstrip() else ""
    sentence_endings = {".", "!", "?", ":", ";", '"', "'"}

    if secondary_end not in sentence_endings and primary_start and primary_start.islower():
        glue = "" if secondary.endswith((" ", "\n", "\t")) else " "
        return (secondary + glue + primary).strip()
    if primary_end not in sentence_endings and secondary_start and secondary_start.islower():
        glue = "" if primary.endswith((" ", "\n", "\t")) else " "
        return (primary + glue + secondary).strip()

    max_overlap = min(len(primary), len(secondary))
    for overlap in range(max_overlap, 7, -1):
        if primary.endswith(secondary[:overlap]):
            return (primary + secondary[overlap:]).strip()
        if secondary.endswith(primary[:overlap]):
            return (secondary + primary[overlap:]).strip()

    return primary.strip() if len(primary) >= len(secondary) else secondary.strip()


def text_looks_clipped_or_partial(text: str) -> bool:
    normalized = normalize_single_line(text)
    if not normalized:
        return True
    if normalized.startswith(("{", "[")):
        return False
    if normalized[:1].islower():
        return True
    lowered = normalized.lower()
    clipped_starts = (
        "don't ",
        "doesn't ",
        "isn't ",
        "aren't ",
        "can't ",
        "won't ",
        "shouldn't ",
        "wouldn't ",
        "couldn't ",
        "mobs ",
        "ile mobs",
        "lurking ",
    )
    return any(lowered.startswith(prefix) for prefix in clipped_starts)


def choose_preferred_chat_text(stream_text: str, reasoning_candidate: str) -> str:
    stream = str(stream_text or "").strip()
    reasoning = str(reasoning_candidate or "").strip()
    if not stream:
        return reasoning
    if not reasoning:
        return stream

    if stream == reasoning:
        return stream
    if stream in reasoning and len(reasoning) > len(stream):
        return reasoning
    if reasoning in stream and len(stream) >= len(reasoning):
        return stream
    if text_looks_clipped_or_partial(stream) and not text_looks_clipped_or_partial(reasoning):
        return reasoning
    return stream


def combine_reasoning_text(reasoning_chunks: list[str], end_reasoning_text: str) -> str:
    streaming_text = "".join(chunk for chunk in reasoning_chunks if isinstance(chunk, str)).strip()
    final_text = str(end_reasoning_text or "").strip()
    if not streaming_text:
        return final_text
    if not final_text:
        return streaming_text
    if final_text == streaming_text:
        return final_text
    if final_text.startswith(streaming_text):
        return final_text
    if streaming_text.startswith(final_text):
        return streaming_text
    return f"{streaming_text}\n{final_text}".strip()


def summary_text_looks_glitched(text: str) -> bool:
    normalized = normalize_single_line(text)
    if not normalized:
        return True
    lowered = normalized.lower()
    if lowered.startswith("count:"):
        return True
    markers = (
        "count characters",
        "character count",
        "let's count",
        "check character count",
        "i'll count",
        "space)",
        "return only that line",
    )
    if any(marker in lowered for marker in markers):
        return True
    if re.search(r"\b[A-Za-z]\(\d+\)", normalized):
        return True
    if len(re.findall(r"\b[A-Za-z]\d+\b", normalized)) >= 3:
        return True
    return False


def score_summary_candidate(text: str) -> int:
    normalized = normalize_single_line(text)
    if not normalized or summary_text_looks_glitched(normalized):
        return -10_000

    lowered = normalized.lower()
    score = 0

    if normalized[:1].isupper():
        score += 2
    else:
        score -= 4

    if normalized.endswith((".", "!", "?")):
        score += 1

    if "?" in normalized:
        score += 4

    hook_starts = (
        "hate ",
        "hate that",
        "tired of",
        "wanting",
        "want to",
        "wanting to",
        "annoyed",
        "sick of",
        "ever ",
    )
    if any(lowered.startswith(prefix) for prefix in hook_starts):
        score += 6

    if any(phrase in lowered for phrase in ("this mod", "well this mod", "now every", "makes it so", "you can")):
        score += 3

    if lowered.count("?") >= 2:
        score += 3

    if any(
        phrase in lowered
        for phrase in (
            "hate that",
            "hate the",
            "well this mod",
            "makes it so that",
            "but in single player",
            "cancels that pain",
            "hate when",
            "which means",
            "basically",
        )
    ):
        score += 4

    if any(phrase in lowered for phrase in (" well ", " now ", " so ", " which means", " basically")):
        score += 1

    generic_starts = (
        "hostile mobs",
        "mobs ",
        "all hostile mobs",
        "during daylight",
        "this mod",
        "take extra damage",
    )
    if any(lowered.startswith(prefix) for prefix in generic_starts):
        score -= 5

    bad_phrases = (
        "ignite",
        "deadly zones",
        "roaming safely",
        "free-range mob-killer",
        "literally burn",
        "roaming at night",
        "night raids",
        "wander in daylight",
        "wander around",
        "killing them faster",
        "ignore the sun",
        "unscathed",
        "keep walking at noon",
    )
    if any(phrase in lowered for phrase in bad_phrases):
        score -= 5

    if len(normalized.split()) < 5:
        score -= 2

    return score


def choose_best_summary_candidate(candidates: list[str]) -> str:
    best_text = ""
    best_score = -10_000
    for candidate in candidates:
        normalized = normalize_single_line(candidate)
        if not normalized:
            continue
        score = score_summary_candidate(normalized)
        if score > best_score:
            best_text = normalized
            best_score = score
    return best_text


def extract_best_answer_candidate_text(text: str) -> str:
    raw = str(text or "").strip()
    if not raw:
        return ""

    for candidate in ordered_json_candidates_from_text(raw):
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            return candidate.strip()

    quoted_candidates = re.findall(r'["“]([^"\n]{8,500})["”]', raw)
    for candidate in reversed(quoted_candidates):
        normalized = normalize_single_line(candidate.strip())
        if normalized and not summary_text_looks_glitched(normalized):
            return normalized

    line_candidates = [normalize_single_line(line) for line in raw.splitlines() if normalize_single_line(line)]
    for candidate in reversed(line_candidates):
        lowered = candidate.lower()
        if lowered.startswith(("thus final answer:", "final answer:", "answer:", "response:", "output:")):
            candidate = normalize_single_line(candidate.split(":", 1)[1])
        if candidate and not summary_text_looks_glitched(candidate):
            return candidate

    sentence_candidates = split_sentences(normalize_single_line(raw))
    for candidate in reversed(sentence_candidates):
        normalized = normalize_single_line(candidate)
        if normalized and not summary_text_looks_glitched(normalized):
            return ensure_terminal_punctuation(normalized)

    return ""


def extract_answer_from_reasoning_text(reasoning_text: str) -> str:
    text = str(reasoning_text or "").strip()
    if not text:
        return ""

    think_match = re.search(r"</think>\s*(.+)$", text, flags=re.DOTALL | re.IGNORECASE)
    if think_match:
        candidate = extract_best_answer_candidate_text(think_match.group(1))
        if candidate:
            return candidate

    for marker in ("final answer", "answer", "response", "output"):
        match = re.search(rf"{marker}\s*:\s*(.+)$", text, flags=re.DOTALL | re.IGNORECASE)
        if match:
            candidate = extract_best_answer_candidate_text(match.group(1))
            if candidate:
                return candidate

    for separator in ("❖", "◆"):
        if separator in text:
            candidate = extract_best_answer_candidate_text(text.split(separator)[-1])
            if candidate:
                return candidate

    candidate = extract_best_answer_candidate_text(text)
    if candidate:
        return candidate

    paragraphs = [item.strip() for item in re.split(r"\n\s*\n", text) if item.strip()]
    if len(paragraphs) >= 2:
        last_paragraph = extract_best_answer_candidate_text(paragraphs[-1])
        if last_paragraph:
            return last_paragraph

    return ""


def request_local_c05_chat(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    system_prompt: str = "",
    reasoning_effort: str = "",
    extra_body: dict[str, Any] | None = None,
    timeout_seconds: int,
    session_id: str,
    app_id: str,
    include_history: bool = False,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "user_prompt": user_prompt,
        "model": model,
        "hoster": hoster,
        "app_id": app_id,
        "session_id": session_id,
        "include_history": bool(include_history),
    }
    if system_prompt:
        payload["system_prompt"] = system_prompt
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
    structured_text_chunks: list[str] = []
    reasoning_chunks: list[str] = []
    end_reasoning_text = ""
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
                if event.get("event") == "reasoning_text":
                    reasoning_piece = str(event.get("content", ""))
                    if reasoning_piece:
                        reasoning_chunks.append(reasoning_piece)
                    continue
                if event.get("event") == "content_part":
                    structured_text_chunks.extend(extract_text_fragments_from_chat_parts(event.get("content")))
                    image_urls.extend(extract_image_urls_from_chat_parts(event.get("content")))
                    continue
                if event.get("event") == "content_parts":
                    structured_text_chunks.extend(extract_text_fragments_from_chat_parts(event.get("content")))
                    image_urls.extend(extract_image_urls_from_chat_parts(event.get("content")))
                    continue
                if event.get("error"):
                    last_error = str(event.get("last_error", "")).strip()
                    detail = str(event["error"]).strip()
                    if last_error:
                        detail = f"{detail} Last error: {last_error}"
                    raise ModCompilerError(f"C05 Local AI stream reported an error: {detail}")
                if event.get("status") == "end":
                    if event.get("reasoning_text"):
                        end_reasoning_text = str(event.get("reasoning_text", ""))
                    break
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace").strip()
        raise ModCompilerError(f"C05 Local AI request failed with HTTP {error.code}. {detail}") from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Could not reach C05 Local AI at {base_url}: {error.reason}") from None

    text = merge_stream_text_candidates("".join(chunks), "".join(structured_text_chunks))
    reasoning_text = combine_reasoning_text(reasoning_chunks, end_reasoning_text)
    used_reasoning_fallback = False
    extracted_reasoning = extract_answer_from_reasoning_text(reasoning_text) if reasoning_text else ""
    if not text and extracted_reasoning:
        text = extracted_reasoning
        used_reasoning_fallback = True
    elif text and extracted_reasoning:
        preferred = choose_preferred_chat_text(text, extracted_reasoning)
        if preferred != text:
            text = preferred
            used_reasoning_fallback = True
    elif not text and reasoning_text:
        extracted = extract_answer_from_reasoning_text(reasoning_text)
        if extracted:
            text = extracted
            used_reasoning_fallback = True

    return {
        "text": text,
        "image_urls": dedupe_preserve_order(image_urls),
        "reasoning_text": reasoning_text,
        "used_reasoning_fallback": used_reasoning_fallback,
    }


def stream_local_c05_chat(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    system_prompt: str = "",
    reasoning_effort: str,
    temperature: float,
    max_tokens: int | None,
    timeout_seconds: int,
    session_id: str,
    app_id: str,
    include_history: bool = False,
    request_label: str = "listing generation",
) -> str:
    response = request_local_c05_chat(
        base_url=base_url,
        hoster=hoster,
        model=model,
        user_prompt=user_prompt,
        system_prompt=system_prompt,
        reasoning_effort=reasoning_effort,
        extra_body={
            key: value
            for key, value in {
                "temperature": temperature,
                "max_tokens": max_tokens,
            }.items()
            if value is not None
        },
        timeout_seconds=timeout_seconds,
        session_id=session_id,
        app_id=app_id,
        include_history=include_history,
    )
    text = str(response.get("text", "")).strip()
    if not text:
        reasoning_text = str(response.get("reasoning_text", "")).strip()
        if reasoning_text:
            raise ModCompilerError(
                f"C05 Local AI returned reasoning traces but no final answer text for the {request_label} request."
            )
        raise ModCompilerError(f"C05 Local AI returned no content for the {request_label} request.")
    return text


def request_visual_design_response(
    *,
    base_url: str,
    hoster: str,
    model: str,
    user_prompt: str,
    reasoning_effort: str,
    temperature: float,
    max_tokens: int | None,
    timeout_seconds: int,
    session_id_prefix: str,
    title: str,
    summary: str,
    background_images: tuple[BackgroundImageChoice, ...] = (),
) -> tuple[dict[str, Any], str, str]:
    response = request_local_c05_chat(
        base_url=base_url,
        hoster=hoster,
        model=model,
        user_prompt=user_prompt,
        system_prompt="",
        reasoning_effort=reasoning_effort,
        extra_body={
            key: value
            for key, value in {
                "temperature": temperature,
                "max_tokens": max_tokens,
            }.items()
            if value is not None
        },
        timeout_seconds=timeout_seconds,
        session_id=f"{session_id_prefix}-{uuid.uuid4().hex[:8]}",
        app_id="auto-create-modrinth-visual-prompts",
    )
    if not str(response.get("text", "")).strip() and not str(response.get("reasoning_text", "")).strip():
        raise ModCompilerError("C05 Local AI returned no content for the visual design generation request.")
    return response, hoster, model


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
    source_dir: Path | None,
    input_entry_dir: Path | None,
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
        "input_entry_dir": str(input_entry_dir.resolve()) if input_entry_dir and input_entry_dir.exists() else "",
        "provided_source_dir": str(source_dir.resolve()) if source_dir and source_dir.exists() else "",
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
            "visual_hoster": options.visual_c05_hoster,
            "visual_model": options.visual_c05_model,
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
        prepared = ImageOps.contain(
            opened.convert("RGB"),
            (VISUAL_BACKGROUND_MAX_SIDE, VISUAL_BACKGROUND_MAX_SIDE),
            Image.Resampling.LANCZOS,
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
    canvas_size: tuple[int, int] | None = None,
) -> str:
    safe_title = html_lib.escape(normalize_single_line(title) or "Mod")
    body_class = "modcompiler-description" if variant == "description" else "modcompiler-logo"
    body_opacity = "0.80" if variant == "description" else "1"
    default_canvas_size = DESCRIPTION_IMAGE_SIZE if variant == "description" else LOGO_IMAGE_SIZE
    canvas_width, canvas_height = canvas_size or default_canvas_size
    if variant == "description":
        stage_width, stage_height = DESCRIPTION_LAYOUT_BASE_SIZE
        stage_scale = min(canvas_width / stage_width, canvas_height / stage_height)
        root_style = textwrap.dedent(
            f"""
            #modcompiler-root {{
              position: absolute;
              z-index: 1;
              width: {stage_width}px;
              height: {stage_height}px;
              left: 50%;
              top: 50%;
              transform: translate(-50%, -50%) scale({stage_scale:.6f});
              transform-origin: center center;
              opacity: {body_opacity};
            }}
            """
        ).strip()
        background_scale = "1.06" if (canvas_width, canvas_height) == DESCRIPTION_LAYOUT_BASE_SIZE else "1.18"
    else:
        root_style = textwrap.dedent(
            f"""
            #modcompiler-root {{
              position: relative;
              z-index: 1;
              width: 100%;
              height: 100%;
              opacity: {body_opacity};
            }}
            """
        ).strip()
        background_scale = "1.06"
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
              width: {canvas_width}px;
              height: {canvas_height}px;
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
              transform: scale({background_scale});
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

            {root_style}

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


def save_icon_with_modrinth_limit(*, image: Image.Image, output_path_stem: Path) -> Path:
    working = image.convert("RGB")
    best_within_limit: tuple[bytes, int] | None = None
    sizes = (512, 480, 448, 416, 384, 352, 320, 288, 256)
    qualities = (92, 88, 84, 80, 76, 72, 68, 64, 60, 56, 52, 48, 44, 40)

    for size in sizes:
        resized = working.resize((size, size), Image.Resampling.LANCZOS) if working.size != (size, size) else working
        for quality in qualities:
            buffer = io.BytesIO()
            resized.save(buffer, format="WEBP", quality=quality, method=6)
            data = buffer.getvalue()
            if len(data) <= MODRINTH_ICON_TARGET_BYTES:
                output_path = output_path_stem.with_suffix(".webp")
                output_path.write_bytes(data)
                return output_path
            if len(data) <= MODRINTH_ICON_MAX_BYTES:
                if best_within_limit is None or len(data) > best_within_limit[1]:
                    best_within_limit = (data, len(data))

    if best_within_limit is not None:
        output_path = output_path_stem.with_suffix(".webp")
        output_path.write_bytes(best_within_limit[0])
        return output_path

    raise ModCompilerError(
        f"Generated icon could not be compressed under Modrinth's {MODRINTH_ICON_MAX_BYTES} byte limit."
    )


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
    resize_mode: str = "contain",
) -> Image.Image:
    preview_bytes = render_html_preview_via_qlmanage(
        html_path=html_path,
        render_size=VISUAL_RENDER_SIZE,
        timeout_seconds=timeout_seconds,
    )
    with Image.open(io.BytesIO(preview_bytes)) as preview:
        image = preview.convert("RGB")
        if target_size:
            if resize_mode == "fit":
                image = ImageOps.fit(image, target_size, Image.Resampling.LANCZOS, centering=(0.5, 0.5))
            else:
                contained = ImageOps.contain(image, target_size, Image.Resampling.LANCZOS)
                canvas = Image.new("RGB", target_size, (0, 0, 0))
                offset = (
                    max(0, (target_size[0] - contained.width) // 2),
                    max(0, (target_size[1] - contained.height) // 2),
                )
                canvas.paste(contained, offset)
                image = canvas
        return image.copy()


def build_description_background_canvas(
    *,
    background_path: Path,
    target_size: tuple[int, int],
    zoom: float = 1.16,
) -> Image.Image:
    target_width, target_height = target_size
    expanded_size = (
        max(target_width, int(round(target_width * zoom))),
        max(target_height, int(round(target_height * zoom))),
    )
    with Image.open(background_path) as opened:
        fitted = ImageOps.fit(
            opened.convert("RGB"),
            expanded_size,
            Image.Resampling.LANCZOS,
            centering=(0.5, 0.5),
        )
    left = max(0, (fitted.width - target_width) // 2)
    top = max(0, (fitted.height - target_height) // 2)
    background = fitted.crop((left, top, left + target_width, top + target_height))
    background = ImageEnhance.Color(background).enhance(1.05)
    background = ImageEnhance.Brightness(background).enhance(0.72)
    return background


def build_horizontal_feather_mask(*, size: tuple[int, int], feather_px: int = 96) -> Image.Image:
    width, height = size
    mask = Image.new("L", size, 255)
    feather = max(0, min(int(feather_px), width // 2))
    if feather == 0:
        return mask

    draw = ImageDraw.Draw(mask)
    divisor = max(1, feather - 1)
    for offset in range(feather):
        alpha = int(255 * (offset / divisor))
        draw.line((offset, 0, offset, height), fill=alpha)
        draw.line((width - 1 - offset, 0, width - 1 - offset, height), fill=alpha)
    return mask


def compose_description_poster_image(
    *,
    background_path: Path,
    stage_image: Image.Image,
    target_size: tuple[int, int],
) -> Image.Image:
    background = build_description_background_canvas(background_path=background_path, target_size=target_size)
    content_size = (target_size[1], target_size[1])
    content = ImageOps.fit(stage_image.convert("RGB"), content_size, Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    offset = ((target_size[0] - content_size[0]) // 2, (target_size[1] - content_size[1]) // 2)
    mask = build_horizontal_feather_mask(size=content_size, feather_px=min(112, max(48, content_size[0] // 10)))
    background.paste(content, offset, mask)
    return background


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
    try:
        with Image.open(logo_path) as opened:
            icon_image = ImageOps.fit(opened.convert("RGB"), (512, 512), Image.Resampling.LANCZOS)
            icon_path = save_icon_with_modrinth_limit(
                image=icon_image,
                output_path_stem=bundle_dir / "icon",
            )
    except ModCompilerError as error:
        return "", str(error)
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


def load_bundle_external_links(bundle_dir: Path) -> dict[str, Any]:
    path = bundle_dir / EXTERNAL_LINKS_FILENAME
    if not path.exists():
        return {}
    return load_json(path)


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
        response_payload, visual_hoster, visual_model = request_visual_design_response(
            base_url=options.c05_url,
            hoster=options.visual_c05_hoster,
            model=options.visual_c05_model,
            user_prompt=request_text,
            reasoning_effort=options.reasoning_effort,
            temperature=options.temperature,
            max_tokens=None,
            timeout_seconds=options.timeout_seconds,
            session_id_prefix=f"auto-create-art-prompts-{bundle_dir.name}",
            title=listing.get("name", ""),
            summary=get_listing_summary(listing),
            background_images=options.background_images,
        )
    response_text = str(response_payload.get("text", "") or "")
    reasoning_text = str(response_payload.get("reasoning_text", "") or "")
    (art_dir / "visual-html.response.txt").write_text(response_text, encoding="utf-8")
    if reasoning_text.strip():
        (art_dir / "visual-html.reasoning.txt").write_text(reasoning_text, encoding="utf-8")
    warnings: list[str] = []
    try:
        parsed_visual = parse_visual_prompt_response(response_text)
    except Exception:
        parsed_visual = {}
    if not parsed_visual and reasoning_text.strip():
        try:
            parsed_visual = parse_visual_prompt_response(extract_answer_from_reasoning_text(reasoning_text))
        except Exception:
            parsed_visual = {}
    if not parsed_visual and reasoning_text.strip():
        try:
            parsed_visual = parse_visual_prompt_response(reasoning_text)
        except Exception:
            parsed_visual = {}
    if not parsed_visual:
        warnings.append(
            "Visual design AI response could not be parsed, so local feature-based fallback copy was used. "
            "Check art/visual-html.response.txt and art/visual-html.reasoning.txt if you want to inspect the raw model output."
        )
    prompts = normalize_visual_design_response(
        parsed_visual or {},
        title=listing.get("name", ""),
        summary=get_listing_summary(listing),
        long_description=listing.get("long_description", ""),
        background_images=options.background_images,
    )
    write_json(art_dir / "visual-html.json", prompts)

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
        canvas_size=DESCRIPTION_LAYOUT_BASE_SIZE,
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

        description_stage_image = render_html_document_to_image(
            html_path=art_dir / "description-image.render.html",
            timeout_seconds=options.image_timeout_seconds,
            target_size=DESCRIPTION_LAYOUT_BASE_SIZE,
            resize_mode="fit",
        )
        description_stage_path = save_image_with_size_constraints(
            image=description_stage_image,
            output_path_stem=art_dir / "description-image-stage",
        )
        description_poster_image = compose_description_poster_image(
            background_path=background_path,
            stage_image=description_stage_image,
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
            "stage_file": description_stage_path.name,
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
        "description_stage_file": f"{ART_DIRNAME}/{description_asset['stage_file']}",
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
    generated_links: dict[str, Any] | None = None,
    include_links: bool = True,
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
    generated_links = generated_links or {}
    if include_links:
        issues_url = (
            options.issues_url
            or normalize_single_line(generated_links.get("issues_url"))
            or empty_to_none(details.get("issues"))
        )
        source_url = (
            options.source_url
            or normalize_single_line(generated_links.get("source_url"))
            or empty_to_none(details.get("sources"))
        )
        wiki_url = (
            options.wiki_url
            or normalize_single_line(generated_links.get("wiki_url"))
            or empty_to_none(details.get("homepage"))
        )
        discord_url = empty_to_none(options.discord_url)
    else:
        issues_url = None
        source_url = None
        wiki_url = None
        discord_url = None

    project = {
        "slug": slugify_text(listing["name"]) or slugify_text(details.get("mod_id", "")),
        "title": listing["name"],
        "description": get_listing_summary(listing),
        "categories": categories,
        "client_side": client_side,
        "server_side": server_side,
        "body": listing["long_description"],
        "status": project_status,
        "additional_categories": additional_categories,
        "issues_url": issues_url,
        "source_url": source_url,
        "wiki_url": wiki_url,
        "discord_url": discord_url,
        "donation_urls": [] if include_links else None,
        "license_id": license_id,
        "license_url": license_url,
        "project_type": "mod",
    }
    keep_empty_arrays = {"additional_categories"}
    if include_links:
        keep_empty_arrays.add("donation_urls")
    return clean_modrinth_payload(project, keep_empty_arrays=keep_empty_arrays)


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


def strip_generated_description_images(*, body: str, title: str) -> str:
    clean_body = str(body or "").strip()
    if not clean_body:
        return ""
    escaped_title = re.escape(normalize_single_line(title))
    pattern = re.compile(
        rf"^\!\[(?:{escaped_title} cover image|{escaped_title} poster section \d+ of \d+)\]\([^)]+\)\s*$",
        re.MULTILINE,
    )
    updated = re.sub(pattern, "", clean_body)
    updated = re.sub(r"\n{3,}", "\n\n", updated).strip()
    return updated


def inject_description_image_into_body(*, body: str, title: str, image_url: str) -> str:
    return inject_description_images_into_body(body=body, title=title, image_urls=[image_url] if image_url else [])


def inject_description_images_into_body(*, body: str, title: str, image_urls: list[str]) -> str:
    clean_body = strip_generated_description_images(body=body, title=title)
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


def sync_project_icon(
    *,
    client: ModrinthClient,
    project_id: str,
    bundle_dir: Path,
) -> list[str]:
    icon_path = find_bundle_icon(bundle_dir)
    if icon_path is None or not icon_path.exists():
        return []
    try:
        client.change_project_icon(project_ref=project_id, icon_path=icon_path)
    except ModCompilerError as error:
        return [
            "Generated project icon could not be uploaded automatically. "
            f"You can still use `{icon_path.name}` manually. {error}"
        ]
    return []


def sync_project_description_image(
    *,
    client: ModrinthClient,
    project_id: str,
    bundle_dir: Path,
    project_payload: dict[str, Any],
    project_path: Path,
    publish_state: dict[str, Any],
    allow_external_urls: bool = True,
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
    external_links = load_bundle_external_links(bundle_dir) if allow_external_urls else {}
    preferred_urls = normalize_string_list(external_links.get("description_image_urls"))
    if not preferred_urls:
        preferred_urls = normalize_string_list(external_links.get("description_image_url"))
    gallery_titles = [
        f"{title} Cover Image" if len(image_paths) == 1 else f"{title} Poster {index + 1}"
        for index in range(len(image_paths))
    ]

    try:
        if len(preferred_urls) == len(image_paths) and all(preferred_urls):
            stored_urls = preferred_urls
            publish_state["description_image_urls"] = stored_urls
            publish_state["description_image_url"] = stored_urls[0] if stored_urls else ""
            write_bundle_state(bundle_dir, publish_state)
        else:
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


def resolve_source_roots(source_dir: Path | None) -> list[Path]:
    if source_dir is None or not source_dir.exists():
        return []

    candidates = [
        source_dir / "java",
        source_dir / "kotlin",
        source_dir / "src" / "main" / "java",
        source_dir / "src" / "main" / "kotlin",
        source_dir / "src" / "client" / "java",
        source_dir / "src" / "client" / "kotlin",
        source_dir / "src" / "java",
        source_dir / "src" / "kotlin",
        source_dir / "src",
    ]

    roots: list[Path] = []
    seen: set[Path] = set()
    for path in candidates:
        if not path.exists() or not path.is_dir():
            continue
        if not directory_contains_source_files(path):
            continue
        resolved = path.resolve()
        if resolved in seen or any(
            existing == resolved or existing in resolved.parents or resolved in existing.parents
            for existing in seen
        ):
            continue
        seen.add(resolved)
        roots.append(path)
    if roots:
        return roots

    if directory_contains_source_files(source_dir):
        return [source_dir]
    return roots


def resolve_decompiled_source_roots(decompiled_dir: Path | None) -> list[Path]:
    return resolve_source_roots(decompiled_dir)


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
        - title and summary are accurate
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
            f"- Summary: `{get_listing_summary(listing) or '-'}`",
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
        "summary": read_or_fallback("summary.txt", "A short accurate Modrinth summary."),
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
