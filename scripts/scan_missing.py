#!/usr/bin/env python3
"""Scan Modrinth projects for missing items and auto-generate what's possible.

Checks a bundle directory and/or live Modrinth project for:
  - Icon (missing or default)
  - Gallery images
  - Source URL / Issues URL / Wiki URL
  - Source code on GitHub
  - ai_metadata/ completeness
  - JAR file

Auto-generates missing items when possible:
  - Icon + gallery via HTML+qlmanage rendering
  - GitHub repo + source push
  - ai_metadata/ scaffolding

Usage:
    python3 scripts/scan_missing.py --bundle-dir AutoCreateModrinthBundles/longer-day-1.0.0
    python3 scripts/scan_missing.py --bundle-dir AutoCreateModrinthBundles/stop-1.0.0 --fix
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from modcompiler.modrinth import ModrinthClient, build_modrinth_user_agent


def discover_token(explicit: str = "") -> str:
    if explicit:
        return explicit.strip()
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if token:
        return token
    for secrets_path in [Path.cwd().parent / "secrets.txt", REPO_ROOT.parent / "secrets.txt"]:
        if secrets_path.exists():
            try:
                lines = secrets_path.read_text(encoding="utf-8").splitlines()
                if lines and lines[0].strip():
                    return lines[0].strip()
            except OSError:
                pass
    return ""


def discover_github_token() -> str:
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    try:
        result = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return ""


def discover_github_owner() -> str:
    try:
        result = subprocess.run(["gh", "api", "user", "--jq", ".login"], capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return ""


class MissingItem:
    def __init__(self, name: str, status: str, fixable: bool, fix_description: str = ""):
        self.name = name
        self.status = status
        self.fixable = fixable
        self.fix_description = fix_description or ("Auto-generate" if fixable else "Manual action required")

    def __str__(self) -> str:
        icon = "✅" if self.status == "ok" else "❌" if self.status == "missing" else "⚠️"
        fix_tag = f" [FIXABLE: {self.fix_description}]" if self.fixable and self.status != "ok" else ""
        return f"  {icon} {self.name}: {self.status}{fix_tag}"


def scan_bundle(bundle_dir: Path) -> list[MissingItem]:
    items: list[MissingItem] = []

    ai_meta_dir = bundle_dir / "ai_metadata"
    if ai_meta_dir.exists():
        items.append(MissingItem("ai_metadata/", "ok", False))

        project_json = ai_meta_dir / "project_info.json"
        if project_json.exists():
            items.append(MissingItem("project_info.json", "ok", False))
        else:
            items.append(MissingItem("project_info.json", "missing", True, "Create from bundle_metadata.json or modrinth.project.json"))

        version_json = ai_meta_dir / "version_info.json"
        if version_json.exists():
            items.append(MissingItem("version_info.json", "ok", False))
        else:
            items.append(MissingItem("version_info.json", "missing", True, "Create from modrinth.version.json"))

        desc_md = ai_meta_dir / "description.md"
        if desc_md.exists():
            items.append(MissingItem("description.md", "ok", False))
        else:
            items.append(MissingItem("description.md", "missing", True, "Create from modrinth.project.json body"))

        summary_txt = ai_meta_dir / "summary.txt"
        if summary_txt.exists():
            items.append(MissingItem("summary.txt", "ok", False))
        else:
            items.append(MissingItem("summary.txt", "missing", True, "Create from project description"))

        visual_json = ai_meta_dir / "visual_info.json"
        if visual_json.exists():
            items.append(MissingItem("visual_info.json", "ok", False))
        else:
            items.append(MissingItem("visual_info.json", "missing", True, "Auto-generate defaults from title/categories"))
    else:
        items.append(MissingItem("ai_metadata/", "missing", True, "Create full ai_metadata/ from existing bundle files"))

    jar_found = False
    input_dir = bundle_dir / "input"
    if input_dir.exists():
        jars = list(input_dir.glob("*.jar"))
        if jars:
            items.append(MissingItem("JAR file", "ok", False, f"Found {jars[0].name}"))
            jar_found = True
    if not jar_found:
        jars = list(bundle_dir.glob("*.jar"))
        if jars:
            items.append(MissingItem("JAR file", "ok", False, f"Found {jars[0].name}"))
            jar_found = True
    if not jar_found:
        items.append(MissingItem("JAR file", "missing", False, "Build the mod first"))

    source_dir = None
    for name in ("source", "src"):
        candidate = bundle_dir / name
        if candidate.exists() and candidate.is_dir():
            source_dir = candidate
            break
    if source_dir:
        items.append(MissingItem("Source code", "ok", False))
    else:
        items.append(MissingItem("Source code", "missing", False, "Copy source into bundle/source/"))

    icon_path = None
    for ext in (".webp", ".png"):
        candidate = bundle_dir / f"icon{ext}"
        if candidate.exists():
            icon_path = candidate
            break
    if icon_path:
        items.append(MissingItem("Icon", "ok", False, f"{icon_path.name} ({icon_path.stat().st_size} bytes)"))
    else:
        items.append(MissingItem("Icon", "missing", True, "Generate via HTML+qlmanage"))

    gallery_path = None
    for ext in (".webp", ".png"):
        for prefix in ("gallery-cover", "gallery", "description-image"):
            candidate = bundle_dir / f"{prefix}{ext}"
            if candidate.exists():
                gallery_path = candidate
                break
        if gallery_path:
            break
    if gallery_path:
        items.append(MissingItem("Gallery image", "ok", False, f"{gallery_path.name} ({gallery_path.stat().st_size} bytes)"))
    else:
        items.append(MissingItem("Gallery image", "missing", True, "Generate via HTML+qlmanage"))

    return items


def scan_modrinth(slug: str, token: str) -> list[MissingItem]:
    items: list[MissingItem] = []
    client = ModrinthClient(token=token, user_agent=build_modrinth_user_agent())

    try:
        project = client.resolve_project(slug)
    except Exception:
        items.append(MissingItem("Modrinth project", "missing", False, "Publish the project first"))
        return items

    items.append(MissingItem("Modrinth project", "ok", False, f"id={project.get('id', '')}"))

    if project.get("icon_url"):
        items.append(MissingItem("Modrinth icon", "ok", False))
    else:
        items.append(MissingItem("Modrinth icon", "missing", True, "Upload icon via fast_modrinth_publish.py update"))

    gallery = project.get("gallery", [])
    if gallery:
        items.append(MissingItem("Modrinth gallery", "ok", False, f"{len(gallery)} image(s)"))
    else:
        items.append(MissingItem("Modrinth gallery", "missing", True, "Upload gallery via fast_modrinth_publish.py update"))

    if project.get("source_url"):
        items.append(MissingItem("Source URL", "ok", False, project["source_url"]))
    else:
        items.append(MissingItem("Source URL", "missing", True, "Create GitHub repo + set source_url"))

    if project.get("issues_url"):
        items.append(MissingItem("Issues URL", "ok", False, project["issues_url"]))
    else:
        items.append(MissingItem("Issues URL", "missing", True, "Set from GitHub repo"))

    if project.get("wiki_url"):
        items.append(MissingItem("Wiki URL", "ok", False, project["wiki_url"]))
    else:
        items.append(MissingItem("Wiki URL", "missing", True, "Set from GitHub repo"))

    return items


def scan_github(owner: str, repo_name: str, github_token: str) -> list[MissingItem]:
    items: list[MissingItem] = []

    try:
        result = subprocess.run(
            ["gh", "api", f"repos/{owner}/{repo_name}", "--jq", ".html_url"],
            capture_output=True, text=True, timeout=15,
            env={**os.environ, "GH_TOKEN": github_token},
        )
        if result.returncode == 0 and result.stdout.strip():
            items.append(MissingItem("GitHub repo", "ok", False, result.stdout.strip()))

            try:
                contents = subprocess.run(
                    ["gh", "api", f"repos/{owner}/{repo_name}/contents", "--jq", ".[].name"],
                    capture_output=True, text=True, timeout=15,
                    env={**os.environ, "GH_TOKEN": github_token},
                )
                if contents.returncode == 0 and contents.stdout.strip():
                    files = contents.stdout.strip().split("\n")
                    items.append(MissingItem("GitHub source files", "ok", False, f"{len(files)} file(s)"))
                else:
                    items.append(MissingItem("GitHub source files", "missing", True, "Push source via fast_modrinth_publish.py"))
            except Exception:
                items.append(MissingItem("GitHub source files", "warning", True, "Could not check"))
        else:
            items.append(MissingItem("GitHub repo", "missing", True, "Create via fast_modrinth_publish.py publish"))
    except Exception:
        items.append(MissingItem("GitHub repo", "missing", True, "Create via fast_modrinth_publish.py publish"))

    return items


def fix_missing(bundle_dir: Path) -> None:
    ai_meta_dir = bundle_dir / "ai_metadata"
    ai_meta_dir.mkdir(parents=True, exist_ok=True)

    project_json = ai_meta_dir / "project_info.json"
    if not project_json.exists():
        project_data: dict[str, Any] = {}

        old_project = bundle_dir / "modrinth.project.json"
        if old_project.exists():
            old = json.loads(old_project.read_text(encoding="utf-8"))
            project_data = {
                "name": old.get("title", ""),
                "slug": old.get("slug", ""),
                "summary": old.get("description", ""),
                "categories": old.get("categories", []),
                "client_side": old.get("client_side", "optional"),
                "server_side": old.get("server_side", "optional"),
                "license": old.get("license_id", "MIT"),
            }
        else:
            bundle_meta = bundle_dir / "bundle_metadata.json"
            if bundle_meta.exists():
                meta = json.loads(bundle_meta.read_text(encoding="utf-8"))
                project_data = {
                    "name": meta.get("slug", ""),
                    "slug": meta.get("slug", ""),
                    "summary": "",
                    "categories": [],
                    "client_side": "optional",
                    "server_side": "optional",
                    "license": "MIT",
                }

        if project_data:
            project_json.write_text(json.dumps(project_data, indent=2) + "\n", encoding="utf-8")
            print(f"  Created project_info.json")
        else:
            print(f"  WARNING: Could not create project_info.json — no source data found")

    version_json = ai_meta_dir / "version_info.json"
    if not version_json.exists():
        version_data: dict[str, Any] = {}

        old_version = bundle_dir / "modrinth.version.json"
        if old_version.exists():
            old = json.loads(old_version.read_text(encoding="utf-8"))
            version_data = {
                "version_number": old.get("version_number", "1.0.0"),
                "changelog": old.get("changelog", ""),
                "game_versions": old.get("game_versions", []),
                "loaders": old.get("loaders", []),
                "featured": old.get("featured", False),
            }
        else:
            bundle_meta = bundle_dir / "bundle_metadata.json"
            if bundle_meta.exists():
                meta = json.loads(bundle_meta.read_text(encoding="utf-8"))
                version_data = {"version_number": meta.get("version", "1.0.0"), "changelog": "", "game_versions": [], "loaders": [], "featured": True}

        if version_data:
            version_json.write_text(json.dumps(version_data, indent=2) + "\n", encoding="utf-8")
            print(f"  Created version_info.json")

    desc_md = ai_meta_dir / "description.md"
    if not desc_md.exists():
        old_project = bundle_dir / "modrinth.project.json"
        if old_project.exists():
            old = json.loads(old_project.read_text(encoding="utf-8"))
            body = old.get("body", "")
            if body:
                desc_md.write_text(body, encoding="utf-8")
                print(f"  Created description.md from modrinth.project.json body")

    summary_txt = ai_meta_dir / "summary.txt"
    if not summary_txt.exists():
        project_info = {}
        if project_json.exists():
            project_info = json.loads(project_json.read_text(encoding="utf-8"))
        summary = project_info.get("summary", "")
        if summary:
            summary_txt.write_text(summary + "\n", encoding="utf-8")
            print(f"  Created summary.txt")

    visual_json = ai_meta_dir / "visual_info.json"
    if not visual_json.exists():
        print(f"  visual_info.json will be auto-generated by fast_modrinth_publish.py from title/categories")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Scan Modrinth project bundles for missing items")
    parser.add_argument("--bundle-dir", required=True, help="Path to the bundle directory")
    parser.add_argument("--modrinth-token", default="", help="Modrinth API token")
    parser.add_argument("--fix", action="store_true", help="Auto-fix missing items where possible")
    parser.add_argument("--no-remote", action="store_true", help="Skip Modrinth and GitHub checks")

    args = parser.parse_args(argv)
    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.exists():
        print(f"ERROR: Bundle directory not found: {bundle_dir}", file=sys.stderr)
        return 1

    print(f"Scanning: {bundle_dir.name}")
    print()

    print("=== Local Bundle ===")
    local_items = scan_bundle(bundle_dir)
    for item in local_items:
        print(item)

    missing_count = sum(1 for i in local_items if i.status == "missing")
    fixable_count = sum(1 for i in local_items if i.fixable and i.status == "missing")

    if not args.no_remote:
        token = discover_token(args.modrinth_token)

        project_json = bundle_dir / "ai_metadata" / "project_info.json"
        slug = ""
        if project_json.exists():
            slug = json.loads(project_json.read_text(encoding="utf-8")).get("slug", "")
        if not slug:
            old_project = bundle_dir / "modrinth.project.json"
            if old_project.exists():
                slug = json.loads(old_project.read_text(encoding="utf-8")).get("slug", "")

        if slug and token:
            print()
            print("=== Modrinth Project ===")
            modrinth_items = scan_modrinth(slug, token)
            for item in modrinth_items:
                print(item)
            missing_count += sum(1 for i in modrinth_items if i.status == "missing")
            fixable_count += sum(1 for i in modrinth_items if i.fixable and i.status == "missing")

        github_token = discover_github_token()
        github_owner = discover_github_owner()
        if slug and github_token and github_owner:
            print()
            print("=== GitHub Repo ===")
            github_items = scan_github(github_owner, slug, github_token)
            for item in github_items:
                print(item)
            missing_count += sum(1 for i in github_items if i.status == "missing")
            fixable_count += sum(1 for i in github_items if i.fixable and i.status == "missing")

    print()
    print(f"Total: {missing_count} missing, {fixable_count} fixable")

    if args.fix and fixable_count > 0:
        print()
        print("Fixing local bundle items...")
        fix_missing(bundle_dir)
        print()
        print("Local fixes applied. Run fast_modrinth_publish.py update to push to Modrinth.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
