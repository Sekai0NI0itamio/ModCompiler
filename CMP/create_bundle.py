#!/usr/bin/env python3
"""
CMP Bundle Creator — Generate a CMP bundle directory with manifest.json and assets.

IMPORTANT: This script does NOT generate any images. All icons, screenshots,
gallery images, and description images must be created by the human user
themselves. AI agents must NOT use image generation tools — only the user
creates and provides images.

Usage:
    # From a JSON config file:
    python3 CMP/create_bundle.py --config bundle_config.json

    # With individual arguments:
    python3 CMP/create_bundle.py \
        --name "My Cool Mod" \
        --slug "my-cool-mod" \
        --summary "A cool mod that does things" \
        --project-type mod \
        --categories "adventure,utility" \
        --loaders "fabric,forge" \
        --mc-versions "1.20.1,1.20.4,1.21.1" \
        --mod-version "1.0.0" \
        --version-type release \
        --client-side required \
        --server-side optional \
        --license MIT \
        --jar-path /path/to/mod.jar \
        --source-path /path/to/source \
        --icon-path /path/to/icon.png \
        --github-owner myuser \
        --github-repo my-cool-mod
"""

import argparse
import json
import os
import shutil
import sys
import uuid
from pathlib import Path

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

PROJECT_ROOT = Path(__file__).resolve().parent.parent  # ModCompiler/
BUNDLE_DRAFTS = PROJECT_ROOT / "CMP" / "BundleDrafts"

VALID_PROJECT_TYPES = [
    "mod", "modpack", "resourcepack", "shader", "plugin", "datapack",
    "minecraft_java_server",
]

VALID_SIDE_TYPES = ["required", "optional", "unsupported", "unknown"]
VALID_VERSION_TYPES = ["release", "beta", "alpha"]
VALID_DEPENDENCY_TYPES = ["required", "optional", "incompatible", "embedded"]
VALID_DONATION_PLATFORMS = ["patreon", "bmac", "paypal", "github", "ko-fi", "other"]
VALID_REQUESTED_STATUSES = ["approved", "archived", "unlisted", "private", "draft", ""]

COMMON_LICENSES = [
    "MIT", "Apache-2.0", "GPL-3.0-or-later", "LGPL-3.0-or-later",
    "CC0-1.0", "Unlicense", "All-Rights-Reserved",
]

# Categories by project type (sourced from Modrinth API)
CATEGORIES_BY_PROJECT_TYPE: dict[str, list[str]] = {
    "mod": [
        "adventure", "cursed", "decoration", "economy", "equipment", "food",
        "game-mechanics", "library", "magic", "management", "minigame", "mobs",
        "optimization", "social", "storage", "technology", "transportation",
        "utility", "worldgen",
    ],
    "modpack": [
        "adventure", "challenging", "combat", "kitchen-sink", "lightweight",
        "multiplayer", "optimization", "quests", "technology",
    ],
    "resourcepack": [
        "16x", "32x", "48x", "64x", "128x", "256x", "512x+", "8x-", "audio",
        "blocks", "combat", "core-shaders", "cursed", "decoration", "entities",
        "environment", "equipment", "fonts", "gui", "items", "locale", "modded",
        "models", "realistic", "simplistic", "themed", "tweaks", "utility",
        "vanilla-like",
    ],
    "shader": [
        "atmosphere", "bloom", "cartoon", "colored-lighting", "cursed", "fantasy",
        "foliage", "high", "low", "medium", "path-tracing", "pbr", "potato",
        "realistic", "reflections", "screenshot", "semi-realistic", "shadows",
        "vanilla-like",
    ],
    "plugin": [],
    "datapack": [],
    "minecraft_java_server": [
        "adventure-mode", "anarchy", "battle-royale", "bedwars", "classes",
        "competitive", "creative-mode", "creator-community", "crossplay",
        "custom-content", "dungeons", "economy", "factions", "gens",
        "hardcore-mode", "keep-inventory", "kitpvp", "lifesteal", "media",
        "microgames", "minigames", "mmo", "network", "offline-mode", "oneblock",
        "op", "parkour", "personal-worlds", "plots", "pokemon", "prison", "pve",
        "pvp", "questing", "racing", "recording-smp", "roleplay", "rpg",
        "skyblock", "smp", "social", "survival-mode", "teams", "technical",
        "towns", "vanilla-like", "whitelisted", "world-resets",
    ],
}

# Loaders by project type (sourced from Modrinth API)
LOADERS_BY_PROJECT_TYPE: dict[str, list[str]] = {
    "mod": [
        "fabric", "forge", "neoforge", "quilt", "rift", "liteloader", "babric",
        "legacy-fabric", "ornithe", "nilloader", "modloader", "bta-babric",
        "java-agent", "datapack", "bukkit", "spigot", "paper", "purpur",
        "folia", "sponge", "velocity", "bungeecord", "waterfall", "geyser",
    ],
    "modpack": ["fabric", "forge", "neoforge", "quilt"],
    "resourcepack": ["minecraft"],
    "shader": ["canvas", "iris", "optifine", "vanilla"],
    "plugin": [
        "bukkit", "spigot", "paper", "purpur", "folia", "sponge", "velocity",
        "bungeecord", "waterfall", "geyser",
    ],
    "datapack": ["datapack"],
    "minecraft_java_server": [],
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def generate_slug(name: str) -> str:
    """Generate a URL-safe slug from a project name."""
    import re
    slug = name.lower()
    slug = re.sub(r"[^a-z0-9]+", "-", slug)
    slug = slug.strip("-")
    return slug


def validate_choices(value: str, valid: list[str], field_name: str) -> list[str]:
    """Parse a comma-separated string and validate each item against valid choices."""
    items = [item.strip() for item in value.split(",") if item.strip()]
    invalid = [item for item in items if item not in valid]
    if invalid:
        print(f"WARNING: Invalid {field_name}: {invalid}")
        print(f"  Valid values for project_type: {valid}")
    return items


def copy_path(src: str | Path, dest: str | Path, label: str) -> None:
    """Copy a file or directory tree to the destination."""
    src = Path(src)
    dest = Path(dest)

    if not src.exists():
        print(f"WARNING: {label} path does not exist: {src}")
        return

    if src.is_dir():
        if dest.exists():
            shutil.rmtree(dest)
        shutil.copytree(src, dest)
        print(f"  Copied directory: {src} -> {dest}")
    else:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        print(f"  Copied file: {src} -> {dest}")


# ---------------------------------------------------------------------------
# Bundle creation
# ---------------------------------------------------------------------------

def create_bundle(config: dict) -> Path:
    """Create a CMP bundle from a configuration dictionary.

    Returns the path to the created bundle directory.
    """
    # --- Extract config with defaults ---
    name = config.get("name", "")
    if not name:
        raise ValueError("'name' is required")

    slug = config.get("slug") or generate_slug(name)
    summary = config.get("summary", "")
    project_type = config.get("project_type", "mod")
    categories = config.get("categories", [])
    additional_categories = config.get("additional_categories", [])
    license_id = config.get("license_id", "MIT")
    license_url = config.get("license_url", "")
    donation_urls = config.get("donation_urls", [])

    mod_version = config.get("mod_version", "1.0.0")
    loaders = config.get("loaders", ["fabric"])
    client_side = config.get("client_side", "required")
    server_side = config.get("server_side", "optional")
    minecraft_versions = config.get("minecraft_versions", [])
    version_type = config.get("version_type", "release")
    changelog = config.get("changelog", "")
    dependencies = config.get("dependencies", [])
    featured = config.get("featured", False)

    description_body = config.get("description_body", "")
    description_images = config.get("description_images", [])

    icon_path = config.get("icon_path", "")
    gallery_images = config.get("gallery_images", [])

    jar_path = config.get("jar_path", "")
    source_path = config.get("source_path", "")

    issues_url = config.get("issues_url", "")
    source_url = config.get("source_url", "")
    wiki_url = config.get("wiki_url", "")
    discord_url = config.get("discord_url", "")

    modrinth_project_id = config.get("modrinth_project_id", "")
    github_owner = config.get("github_owner", "")
    github_repo_name = config.get("github_repo_name", "")
    requested_status = config.get("requested_status", "")

    # --- Validate ---
    if project_type not in VALID_PROJECT_TYPES:
        raise ValueError(f"Invalid project_type '{project_type}'. Must be one of: {VALID_PROJECT_TYPES}")

    if client_side not in VALID_SIDE_TYPES:
        raise ValueError(f"Invalid client_side '{client_side}'. Must be one of: {VALID_SIDE_TYPES}")

    if server_side not in VALID_SIDE_TYPES:
        raise ValueError(f"Invalid server_side '{server_side}'. Must be one of: {VALID_SIDE_TYPES}")

    if version_type not in VALID_VERSION_TYPES:
        raise ValueError(f"Invalid version_type '{version_type}'. Must be one of: {VALID_VERSION_TYPES}")

    if requested_status and requested_status not in VALID_REQUESTED_STATUSES:
        raise ValueError(f"Invalid requested_status '{requested_status}'. Must be one of: {VALID_REQUESTED_STATUSES}")

    # Validate categories
    valid_cats = CATEGORIES_BY_PROJECT_TYPE.get(project_type, [])
    for cat in categories:
        if valid_cats and cat not in valid_cats:
            print(f"WARNING: Category '{cat}' may not be valid for project_type '{project_type}'")

    # Validate loaders
    valid_loaders = LOADERS_BY_PROJECT_TYPE.get(project_type, [])
    for loader in loaders:
        if valid_loaders and loader not in valid_loaders:
            print(f"WARNING: Loader '{loader}' may not be valid for project_type '{project_type}'")

    # --- Create bundle directory ---
    bundle_dir = BUNDLE_DRAFTS / slug
    if bundle_dir.exists():
        print(f"WARNING: Bundle directory already exists: {bundle_dir}")
        print("  Existing files may be overwritten.")

    bundle_dir.mkdir(parents=True, exist_ok=True)
    (bundle_dir / "jar").mkdir(exist_ok=True)
    (bundle_dir / "source").mkdir(exist_ok=True)
    (bundle_dir / "gallery").mkdir(exist_ok=True)
    (bundle_dir / "description_images").mkdir(exist_ok=True)

    print(f"Created bundle directory: {bundle_dir}")

    # --- Copy jar ---
    jar_relative = ""
    if jar_path:
        jar_src = Path(jar_path)
        if jar_src.exists() and jar_src.is_file():
            jar_dest = bundle_dir / "jar" / jar_src.name
            shutil.copy2(jar_src, jar_dest)
            jar_relative = f"jar/{jar_src.name}"
            print(f"  Copied jar: {jar_src} -> {jar_dest}")
        else:
            print(f"  WARNING: Jar path does not exist or is not a file: {jar_path}")

    # --- Copy source ---
    source_relative = "source/"
    if source_path:
        source_src = Path(source_path)
        if source_src.exists():
            source_dest = bundle_dir / "source"
            # Copy contents into source/
            if source_src.is_dir():
                for item in source_src.iterdir():
                    dest_item = source_dest / item.name
                    if item.is_dir():
                        if dest_item.exists():
                            shutil.rmtree(dest_item)
                        shutil.copytree(item, dest_item)
                    else:
                        shutil.copy2(item, dest_item)
                print(f"  Copied source directory contents: {source_src} -> {source_dest}")
            else:
                shutil.copy2(source_src, source_dest / source_src.name)
                print(f"  Copied source file: {source_src} -> {source_dest / source_src.name}")
        else:
            print(f"  WARNING: Source path does not exist: {source_path}")

    # --- Copy icon ---
    # NOTE: AI agents must NOT generate icons. Only the user provides icon images.
    icon_relative = ""
    if icon_path:
        icon_src = Path(icon_path)
        if icon_src.exists() and icon_src.is_file():
            icon_dest = bundle_dir / icon_src.name
            shutil.copy2(icon_src, icon_dest)
            icon_relative = icon_src.name
            print(f"  Copied icon: {icon_src} -> {icon_dest}")
        else:
            print(f"  WARNING: Icon path does not exist or is not a file: {icon_path}")

    # --- Copy gallery images ---
    # NOTE: AI agents must NOT generate gallery images. Only the user provides screenshots.
    gallery_entries = []
    for idx, img in enumerate(gallery_images):
        if isinstance(img, str):
            img_src = Path(img)
            img_entry = {
                "index": idx,
                "file": f"gallery/{idx}{img_src.suffix}",
                "featured": idx == 0,
                "title": "",
                "description": "",
            }
        elif isinstance(img, dict):
            img_src = Path(img["path"])
            img_entry = {
                "index": idx,
                "file": f"gallery/{idx}{img_src.suffix}",
                "featured": img.get("featured", idx == 0),
                "title": img.get("title", ""),
                "description": img.get("description", ""),
            }
        else:
            continue

        if img_src.exists() and img_src.is_file():
            shutil.copy2(img_src, bundle_dir / "gallery" / f"{idx}{img_src.suffix}")
            gallery_entries.append(img_entry)
            print(f"  Copied gallery image {idx}: {img_src}")
        else:
            print(f"  WARNING: Gallery image not found: {img_src}")

    # --- Copy description images ---
    # NOTE: AI agents must NOT generate description images. Only the user provides these.
    desc_image_entries = []
    for idx, img in enumerate(description_images):
        if isinstance(img, str):
            img_src = Path(img)
            img_entry = {
                "index": idx,
                "file": f"description_images/{idx}{img_src.suffix}",
                "caption": "",
            }
        elif isinstance(img, dict):
            img_src = Path(img["path"])
            img_entry = {
                "index": idx,
                "file": f"description_images/{idx}{img_src.suffix}",
                "caption": img.get("caption", ""),
            }
        else:
            continue

        if img_src.exists() and img_src.is_file():
            shutil.copy2(img_src, bundle_dir / "description_images" / f"{idx}{img_src.suffix}")
            desc_image_entries.append(img_entry)
            print(f"  Copied description image {idx}: {img_src}")
        else:
            print(f"  WARNING: Description image not found: {img_src}")

    # --- Ensure donation_urls have IDs ---
    processed_donation_urls = []
    for d in donation_urls:
        entry = {
            "id": d.get("id", str(uuid.uuid4())),
            "platform": d.get("platform", "other"),
            "url": d.get("url", ""),
        }
        if entry["platform"] not in VALID_DONATION_PLATFORMS:
            print(f"  WARNING: Invalid donation platform '{entry['platform']}'. Must be one of: {VALID_DONATION_PLATFORMS}")
        processed_donation_urls.append(entry)

    # --- Ensure dependencies have all fields ---
    processed_dependencies = []
    for dep in dependencies:
        processed_dependencies.append({
            "project_id": dep.get("project_id", ""),
            "version_id": dep.get("version_id", ""),
            "file_name": dep.get("file_name", ""),
            "dependency_type": dep.get("dependency_type", "optional"),
        })

    # --- Build manifest ---
    manifest = {
        "cmp_version": 1,
        "mod_info": {
            "name": name,
            "slug": slug,
            "summary": summary,
            "project_type": project_type,
            "categories": categories,
            "additional_categories": additional_categories,
            "license_id": license_id,
            "license_url": license_url,
            "donation_urls": processed_donation_urls,
        },
        "version_info": {
            "mod_version": mod_version,
            "loaders": loaders,
            "client_side": client_side,
            "server_side": server_side,
            "minecraft_versions": minecraft_versions,
            "version_type": version_type,
            "changelog": changelog,
            "dependencies": processed_dependencies,
            "featured": featured,
        },
        "description": {
            "body": description_body,
            "images": desc_image_entries,
        },
        "icon": icon_relative,
        "gallery": gallery_entries,
        "files": {
            "jar": jar_relative,
            "source": source_relative,
        },
        "links": {
            "issues_url": issues_url,
            "source_url": source_url,
            "wiki_url": wiki_url,
            "discord_url": discord_url,
        },
        "publishing": {
            "modrinth_project_id": modrinth_project_id,
            "github_owner": github_owner,
            "github_repo_name": github_repo_name,
            "requested_status": requested_status,
        },
    }

    # --- Write manifest.json ---
    manifest_path = bundle_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print(f"  Wrote manifest.json: {manifest_path}")

    print(f"\nBundle created successfully: {bundle_dir}")
    return bundle_dir


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Create a CMP bundle directory with manifest.json and assets.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    # Config file mode
    parser.add_argument(
        "--config",
        type=str,
        default=None,
        help="Path to a JSON config file with all bundle fields",
    )

    # Mod info
    parser.add_argument("--name", type=str, default="", help="Project name")
    parser.add_argument("--slug", type=str, default="", help="URL-safe slug (auto-generated from name if omitted)")
    parser.add_argument("--summary", type=str, default="", help="One-line project summary")
    parser.add_argument("--project-type", type=str, default="mod", help="Project type (mod, modpack, resourcepack, shader, plugin, datapack, minecraft_java_server)")
    parser.add_argument("--categories", type=str, default="", help="Comma-separated primary categories")
    parser.add_argument("--additional-categories", type=str, default="", help="Comma-separated additional categories")
    parser.add_argument("--license", type=str, default="MIT", help="SPDX license identifier")
    parser.add_argument("--license-url", type=str, default="", help="URL to license text")

    # Version info
    parser.add_argument("--mod-version", type=str, default="1.0.0", help="Mod version string")
    parser.add_argument("--loaders", type=str, default="fabric", help="Comma-separated loader names")
    parser.add_argument("--mc-versions", type=str, default="", help="Comma-separated Minecraft versions")
    parser.add_argument("--version-type", type=str, default="release", help="Version type: release, beta, alpha")
    parser.add_argument("--client-side", type=str, default="required", help="Client side: required, optional, unsupported, unknown")
    parser.add_argument("--server-side", type=str, default="optional", help="Server side: required, optional, unsupported, unknown")
    parser.add_argument("--changelog", type=str, default="", help="Version changelog text")

    # Files
    parser.add_argument("--jar-path", type=str, default="", help="Path to the mod jar file")
    parser.add_argument("--source-path", type=str, default="", help="Path to the source code directory")
    parser.add_argument("--icon-path", type=str, default="", help="Path to the project icon image")

    # Description
    parser.add_argument("--description-body", type=str, default="", help="Markdown description body (use {{image:N}} for images)")

    # Links
    parser.add_argument("--discord-url", type=str, default="", help="Discord invite URL")

    # Publishing
    parser.add_argument("--github-owner", type=str, default="", help="GitHub username or org")
    parser.add_argument("--github-repo", type=str, default="", help="GitHub repository name")
    parser.add_argument("--modrinth-project-id", type=str, default="", help="Existing Modrinth project ID (leave empty for new)")
    parser.add_argument("--requested-status", type=str, default="", help="Modrinth status: approved, archived, unlisted, private, draft")

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    # --- Config file mode ---
    if args.config:
        config_path = Path(args.config)
        if not config_path.exists():
            print(f"ERROR: Config file not found: {config_path}", file=sys.stderr)
            sys.exit(1)

        with open(config_path, "r", encoding="utf-8") as f:
            config = json.load(f)

        print(f"Loaded config from: {config_path}")
        create_bundle(config)
        return

    # --- CLI argument mode ---
    if not args.name:
        print("ERROR: --name is required when not using --config", file=sys.stderr)
        parser.print_help()
        sys.exit(1)

    # Parse comma-separated lists
    categories = [c.strip() for c in args.categories.split(",") if c.strip()] if args.categories else []
    additional_categories = [c.strip() for c in args.additional_categories.split(",") if c.strip()] if args.additional_categories else []
    loaders = [l.strip() for l in args.loaders.split(",") if l.strip()] if args.loaders else ["fabric"]
    mc_versions = [v.strip() for v in args.mc_versions.split(",") if v.strip()] if args.mc_versions else []

    # Auto-generate slug from name if not provided
    slug = args.slug or generate_slug(args.name)

    # Auto-generate github_repo_name from slug if not provided
    github_repo = args.github_repo or slug

    config = {
        "name": args.name,
        "slug": slug,
        "summary": args.summary,
        "project_type": args.project_type,
        "categories": categories,
        "additional_categories": additional_categories,
        "license_id": args.license,
        "license_url": args.license_url,
        "donation_urls": [],
        "mod_version": args.mod_version,
        "loaders": loaders,
        "client_side": args.client_side,
        "server_side": args.server_side,
        "minecraft_versions": mc_versions,
        "version_type": args.version_type,
        "changelog": args.changelog,
        "dependencies": [],
        "featured": False,
        "description_body": args.description_body,
        "description_images": [],
        "icon_path": args.icon_path,
        "gallery_images": [],
        "jar_path": args.jar_path,
        "source_path": args.source_path,
        "issues_url": "",
        "source_url": "",
        "wiki_url": "",
        "discord_url": args.discord_url,
        "modrinth_project_id": args.modrinth_project_id,
        "github_owner": args.github_owner,
        "github_repo_name": github_repo,
        "requested_status": args.requested_status,
    }

    create_bundle(config)


if __name__ == "__main__":
    main()
