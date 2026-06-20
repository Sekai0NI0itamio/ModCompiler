#!/usr/bin/env python3
"""Manually publish a mod to Modrinth with full control over project info.

Usage:
    python publish_manual.py \\
        --jar "Longer Day-1.0.0.jar" \\
        --modrinth.project.json modrinth.project.json \\
        --modrinth.version.json modrinth.version.json \\
        --modrinth-token <token>

Or simply point to a bundle directory containing the JSON files:
    python publish_manual.py \\
        --bundle-dir ./longer-day-1.0.0 \\
        --jar "Longer Day-1.0.0.jar" \\
        --modrinth-token <token>

If the project already exists on Modrinth (by slug), it will create a new
version under the existing project. If it doesn't exist, it creates a draft
project first.

Use --update-only to update an existing project's metadata (source URLs,
icon, description, etc.) without uploading a new version.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from modcompiler.modrinth import (
    ModrinthClient,
    build_modrinth_user_agent,
    encode_multipart_form_data,
    guess_content_type,
)


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


def create_project(client: ModrinthClient, project_payload: dict[str, Any], version_payload: dict[str, Any], jar_path: Path, icon_path: Path | None) -> dict[str, Any]:
    body_dict = {k: v for k, v in project_payload.items() if k not in ("icon", "icon_path") and v not in ("", None, [])}

    initial_version = dict(version_payload)
    initial_version["file_parts"] = ["file"]
    body_dict["initial_versions"] = [initial_version]

    files = [("file", jar_path.name, jar_path.read_bytes(), guess_content_type(jar_path.name))]
    if icon_path:
        files.append(("icon", icon_path.name, icon_path.read_bytes(), guess_content_type(icon_path.name)))

    body, content_type = encode_multipart_form_data(
        fields={"data": json.dumps(body_dict)},
        files=files,
    )
    return client.request_json("POST", "/project", body=body, extra_headers={"Content-Type": content_type})


def create_version(client: ModrinthClient, version_payload: dict[str, Any], jar_path: Path) -> dict[str, Any]:
    body, content_type = encode_multipart_form_data(
        fields={"data": json.dumps(version_payload)},
        files=[("file", jar_path.name, jar_path.read_bytes(), guess_content_type(jar_path.name))],
    )
    return client.request_json("POST", "/version", body=body, extra_headers={"Content-Type": content_type})


def update_project_metadata(client: ModrinthClient, slug: str, project_payload: dict[str, Any], icon_path: Path | None) -> None:
    patch_fields = {}
    for key in ("title", "description", "body", "categories", "additional_categories",
                "client_side", "server_side", "license_id", "source_url", "issues_url",
                "wiki_url", "donation_urls"):
        value = project_payload.get(key)
        if value is not None and value != "" and value != []:
            patch_fields[key] = value

    if patch_fields:
        client.modify_project(project_ref=slug, payload=patch_fields)
        print(f"Updated project metadata: {', '.join(patch_fields.keys())}")

    if icon_path:
        client.change_project_icon(project_ref=slug, icon_path=icon_path)
        print(f"Updated project icon: {icon_path.name}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Manually publish a mod to Modrinth")
    parser.add_argument("--bundle-dir", default="", help="Directory containing modrinth.project.json and modrinth.version.json")
    parser.add_argument("--jar", default="", help="Path to the mod JAR file")
    parser.add_argument("--project-json", default="", help="Path to modrinth.project.json (overrides bundle-dir)")
    parser.add_argument("--version-json", default="", help="Path to modrinth.version.json (overrides bundle-dir)")
    parser.add_argument("--icon", default="", help="Path to icon image (png/webp)")
    parser.add_argument("--modrinth-token", default="", help="Modrinth API token (or set MODRINTH_TOKEN env)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be done without making API calls")
    parser.add_argument("--project-status", default="draft", choices=["draft", "listed", "unlisted", "private"], help="Project visibility status")
    parser.add_argument("--version-status", default="listed", choices=["listed", "unlisted", "draft"], help="Version visibility status")
    parser.add_argument("--update-only", action="store_true", help="Only update existing project metadata (source URLs, icon, etc.) without uploading a version")

    args = parser.parse_args(argv)

    bundle_dir = Path(args.bundle_dir) if args.bundle_dir else None
    project_json_path = Path(args.project_json) if args.project_json else None
    version_json_path = Path(args.version_json) if args.version_json else None

    if not project_json_path and bundle_dir:
        project_json_path = bundle_dir / "modrinth.project.json"
    if not version_json_path and bundle_dir:
        version_json_path = bundle_dir / "modrinth.version.json"

    if not project_json_path or not project_json_path.exists():
        print(f"ERROR: modrinth.project.json not found: {project_json_path}", file=sys.stderr)
        return 1

    project_payload = json.loads(project_json_path.read_text(encoding="utf-8"))

    if not args.update_only:
        if not args.jar:
            print("ERROR: --jar is required unless --update-only is specified.", file=sys.stderr)
            return 1
        jar_path = Path(args.jar)
        if not jar_path.exists():
            print(f"ERROR: JAR file not found: {jar_path}", file=sys.stderr)
            return 1
        if not version_json_path or not version_json_path.exists():
            print(f"ERROR: modrinth.version.json not found: {version_json_path}", file=sys.stderr)
            return 1
        version_payload = json.loads(version_json_path.read_text(encoding="utf-8"))
        version_payload["status"] = args.version_status
    else:
        jar_path = None
        version_payload = {}

    project_payload["status"] = args.project_status

    icon_path = None
    if args.icon:
        icon_path = Path(args.icon)
    elif bundle_dir:
        for ext in (".webp", ".png", ".jpg"):
            candidate = bundle_dir / f"icon{ext}"
            if candidate.exists():
                icon_path = candidate
                break

    token = discover_token(args.modrinth_token)
    if not token and not args.dry_run:
        print("ERROR: No Modrinth token provided. Use --modrinth-token or set MODRINTH_TOKEN.", file=sys.stderr)
        return 1

    slug = project_payload.get("slug", "")
    title = project_payload.get("title", "")

    print(f"Mod: {title} ({slug})")
    if jar_path:
        print(f"JAR: {jar_path.name} ({jar_path.stat().st_size} bytes)")
    print(f"Project status: {args.project_status}")
    if not args.update_only:
        print(f"Version status: {args.version_status}")
    if icon_path:
        print(f"Icon: {icon_path.name}")
    source_url = project_payload.get("source_url", "")
    issues_url = project_payload.get("issues_url", "")
    if source_url:
        print(f"Source URL: {source_url}")
    if issues_url:
        print(f"Issues URL: {issues_url}")
    print()

    if args.dry_run:
        print("=== DRY RUN ===")
        if args.update_only:
            print(f"Would update project '{slug}' metadata...")
            if icon_path:
                print(f"Would update icon to {icon_path.name}")
            if source_url:
                print(f"Would set source_url = {source_url}")
            if issues_url:
                print(f"Would set issues_url = {issues_url}")
        else:
            print(f"Would check if project '{slug}' exists on Modrinth...")
            print(f"Would create project with title '{title}' and slug '{slug}'" + (" (with icon)" if icon_path else ""))
            print(f"Would upload version {version_payload.get('version_number', '?')} with JAR {jar_path.name}")
        return 0

    client = ModrinthClient(token=token, user_agent=build_modrinth_user_agent())

    project_id = ""
    try:
        existing = client.resolve_project(slug)
        project_id = existing.get("id", "")
        print(f"Found existing project: {existing.get('title', '')} (id={project_id})")
    except Exception:
        print(f"No existing project found for slug '{slug}'.")

    if args.update_only:
        if not project_id:
            print("ERROR: --update-only requires the project to already exist on Modrinth.", file=sys.stderr)
            return 1
        try:
            update_project_metadata(client, slug, project_payload, icon_path)
            print()
            print(f"SUCCESS! Project updated: https://modrinth.com/mod/{slug}")
        except Exception as e:
            print(f"ERROR: Failed to update project: {e}", file=sys.stderr)
            return 1
        return 0

    if not project_id:
        print("Creating new project...")
        try:
            created = create_project(client, project_payload, version_payload, jar_path, icon_path)
            project_id = created.get("id", "")
            project_slug = created.get("slug", slug)
            print(f"Created project: id={project_id} slug={project_slug}")
            version_ids = created.get("versions", [])
            if version_ids:
                print(f"Version created with project: id={version_ids[0]}")

            if icon_path:
                try:
                    client.change_project_icon(project_ref=project_slug, icon_path=icon_path)
                    print(f"Icon uploaded: {icon_path.name}")
                except Exception as e:
                    print(f"WARNING: Failed to upload icon: {e}")

            update_fields = {}
            for key in ("source_url", "issues_url", "wiki_url"):
                value = project_payload.get(key)
                if value and value not in ("", None):
                    update_fields[key] = value
            if update_fields:
                try:
                    client.modify_project(project_ref=project_slug, payload=update_fields)
                    print(f"Updated project links: {', '.join(update_fields.keys())}")
                except Exception as e:
                    print(f"WARNING: Failed to update project links: {e}")

            return 0
        except Exception as e:
            print(f"ERROR: Failed to create project: {e}", file=sys.stderr)
            return 1

    version_payload["project_id"] = project_id

    print(f"Uploading version {version_payload.get('version_number', '?')}...")
    try:
        version_result = create_version(client, version_payload, jar_path)
        version_id = version_result.get("id", "")
        print(f"Version uploaded: id={version_id}")
    except Exception as e:
        print(f"ERROR: Failed to upload version: {e}", file=sys.stderr)
        return 1

    try:
        update_project_metadata(client, slug, project_payload, icon_path)
    except Exception as e:
        print(f"WARNING: Failed to update project metadata: {e}")

    print()
    print(f"SUCCESS! Project: https://modrinth.com/mod/{slug}")
    print(f"         Version: {version_payload.get('version_number', '?')} (id={version_id})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
