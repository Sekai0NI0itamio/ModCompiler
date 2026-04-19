#!/usr/bin/env python3
"""
fetch_modrinth_project.py
--------------------------
Fetches all public metadata for a Modrinth project and writes a structured
bundle to an output directory so an IDE agent can load it locally without
needing live search tools.

Bundle layout
-------------
<output_dir>/
  project.json          - full project object (title, description, authors, etc.)
  summary.txt           - human-readable one-page overview
  versions/
    <version_id>/
      version.json      - full version object (game versions, loaders, deps, etc.)
      changelog.txt     - changelog text for this version (empty if none)
      files.json        - list of file objects (name, url, hashes, size)
  README.md             - bundle index the IDE agent should read first

Usage
-----
  python3 scripts/fetch_modrinth_project.py \
      --project https://modrinth.com/mod/sort-chest \
      --output-dir modrinth-bundle

Environment variables
---------------------
  MODRINTH_TOKEN   optional; allows fetching draft/private projects
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

MODRINTH_API_BASE = "https://api.modrinth.com/v2"
USER_AGENT = "ModCompilerIDEAgent/1.0 (github.com/modcompiler)"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _headers(token: str | None) -> dict[str, str]:
    h = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token:
        h["Authorization"] = token
    return h


def _get(path: str, token: str | None, params: dict | None = None) -> object:
    url = MODRINTH_API_BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=_headers(token))
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8", errors="replace"))
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                raise SystemExit(f"Project not found (HTTP 404): {url}") from None
            if exc.code in {429, 500, 502, 503, 504} and attempt < 5:
                time.sleep(2 ** attempt)
                continue
            raise SystemExit(f"HTTP {exc.code} from Modrinth: {url}") from None
        except urllib.error.URLError as exc:
            if attempt < 5:
                time.sleep(2 ** attempt)
                continue
            raise SystemExit(f"Network error: {exc.reason}") from None
    raise SystemExit("Modrinth API request failed after retries.")


def _normalize_ref(value: str) -> str:
    """Accept a full URL or a bare slug/ID."""
    value = value.strip().rstrip("/")
    # https://modrinth.com/mod/<slug>  or  https://modrinth.com/mod/<slug>/versions
    m = re.search(r"modrinth\.com/(?:mod|plugin|resourcepack|shader|datapack|modpack)/([^/?#]+)", value)
    if m:
        return m.group(1)
    return value  # assume it's already a slug or ID


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _write_json(path: Path, obj: object) -> None:
    _write(path, json.dumps(obj, indent=2, ensure_ascii=False))


# ---------------------------------------------------------------------------
# Summary builder
# ---------------------------------------------------------------------------

def _build_summary(project: dict, versions: list[dict]) -> str:
    lines: list[str] = []

    lines.append(f"Project: {project.get('title', '(unknown)')}")
    lines.append(f"Slug:    {project.get('slug', '(unknown)')}")
    lines.append(f"ID:      {project.get('id', '(unknown)')}")

    team = project.get("team", "")
    if team:
        lines.append(f"Team ID: {team}")

    lines.append(f"Type:    {project.get('project_type', 'mod')}")
    lines.append(f"License: {project.get('license', {}).get('id', 'unknown')}")
    lines.append(f"Status:  {project.get('status', 'unknown')}")
    lines.append(f"Downloads: {project.get('downloads', 0):,}")
    lines.append(f"Followers: {project.get('followers', 0):,}")
    lines.append("")

    categories = project.get("categories", []) + project.get("additional_categories", [])
    if categories:
        lines.append(f"Categories: {', '.join(categories)}")

    client_side = project.get("client_side", "")
    server_side = project.get("server_side", "")
    if client_side or server_side:
        lines.append(f"Sides: client={client_side}  server={server_side}")

    game_versions = project.get("game_versions", [])
    if game_versions:
        lines.append(f"Game versions: {', '.join(game_versions)}")

    loaders = project.get("loaders", [])
    if loaders:
        lines.append(f"Loaders: {', '.join(loaders)}")

    lines.append("")
    lines.append("--- Description ---")
    lines.append(project.get("description", "(no description)"))
    lines.append("")

    body = project.get("body", "")
    if body:
        lines.append("--- Body (full mod page text) ---")
        lines.append(body)
        lines.append("")

    lines.append(f"--- Versions ({len(versions)} total) ---")
    for v in versions:
        vid = v.get("id", "?")
        vnum = v.get("version_number", "?")
        vname = v.get("name", "")
        vtype = v.get("version_type", "release")
        gvs = ", ".join(v.get("game_versions", []))
        vloaders = ", ".join(v.get("loaders", []))
        lines.append(f"  [{vid}] {vnum}  ({vname})  type={vtype}  mc={gvs}  loaders={vloaders}")

    links = project.get("source_url") or project.get("issues_url") or project.get("wiki_url") or project.get("discord_url")
    if links:
        lines.append("")
        lines.append("--- External links ---")
        for key in ("source_url", "issues_url", "wiki_url", "discord_url", "donation_urls"):
            val = project.get(key)
            if val:
                if isinstance(val, list):
                    for item in val:
                        lines.append(f"  {item.get('platform', key)}: {item.get('url', '')}")
                else:
                    lines.append(f"  {key}: {val}")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# README builder
# ---------------------------------------------------------------------------

def _build_readme(project: dict, versions: list[dict], out_dir: Path) -> str:
    slug = project.get("slug", "unknown")
    title = project.get("title", slug)
    version_count = len(versions)

    lines = [
        f"# Modrinth Bundle: {title}",
        "",
        "This bundle was generated by the **Modrinth Project Fetch** GitHub Actions workflow.",
        "It contains all public metadata for the project so an IDE agent can understand",
        "the mod without needing live internet access.",
        "",
        "## Files in this bundle",
        "",
        "| File | Contents |",
        "|------|----------|",
        "| `project.json` | Full Modrinth project object (title, description, authors, categories, game versions, loaders, links) |",
        "| `summary.txt` | Human-readable one-page overview of the project |",
        f"| `versions/<id>/version.json` | Full version object for each of the {version_count} versions |",
        "| `versions/<id>/changelog.txt` | Changelog text for that version |",
        "| `versions/<id>/files.json` | File list with names, download URLs, hashes, and sizes |",
        "",
        "## Quick facts",
        "",
        f"- **Slug**: `{slug}`",
        f"- **Project ID**: `{project.get('id', '?')}`",
        f"- **Type**: {project.get('project_type', 'mod')}",
        f"- **License**: {project.get('license', {}).get('id', 'unknown')}",
        f"- **Downloads**: {project.get('downloads', 0):,}",
        f"- **Game versions covered**: {', '.join(project.get('game_versions', []))}",
        f"- **Loaders**: {', '.join(project.get('loaders', []))}",
        "",
        "## How to use this bundle",
        "",
        "1. Read `summary.txt` for a full plain-text overview.",
        "2. Read `project.json` for the raw API data.",
        "3. Browse `versions/` to see every published version, its changelog, and its files.",
        "4. Use the file download URLs in `versions/<id>/files.json` to fetch specific jars if needed.",
        "",
        "## Modrinth links",
        "",
        f"- Project page: https://modrinth.com/mod/{slug}",
        f"- API: https://api.modrinth.com/v2/project/{slug}",
    ]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch a Modrinth project bundle for IDE agent use.")
    parser.add_argument("--project", required=True, help="Modrinth project URL, slug, or ID")
    parser.add_argument("--output-dir", required=True, help="Directory to write the bundle into")
    args = parser.parse_args()

    token = None  # MODRINTH_TOKEN injected via env in workflow; read here if present
    import os
    token = os.environ.get("MODRINTH_TOKEN") or None

    ref = _normalize_ref(args.project)
    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)

    print(f"Fetching project: {ref}")
    project: dict = _get(f"/project/{urllib.parse.quote(ref, safe='')}", token)  # type: ignore[assignment]
    project_id = project.get("id") or ref
    print(f"  Title: {project.get('title')}  ID: {project_id}")

    # Fetch all versions (no filter = all loaders, all game versions)
    print("Fetching version list...")
    versions: list[dict] = _get(  # type: ignore[assignment]
        f"/project/{urllib.parse.quote(project_id, safe='')}/version",
        token,
    )
    if not isinstance(versions, list):
        versions = []
    print(f"  Found {len(versions)} versions")

    # Write project.json
    _write_json(out / "project.json", project)

    # Write per-version data
    for v in versions:
        vid = v.get("id", "unknown")
        vdir = out / "versions" / vid
        _write_json(vdir / "version.json", v)

        changelog = v.get("changelog") or ""
        _write(vdir / "changelog.txt", changelog)

        files = v.get("files", [])
        _write_json(vdir / "files.json", files)

    # Write summary.txt
    summary = _build_summary(project, versions)
    _write(out / "summary.txt", summary)

    # Write README.md
    readme = _build_readme(project, versions, out)
    _write(out / "README.md", readme)

    # Write a machine-readable index for the agent
    index = {
        "project_id": project_id,
        "slug": project.get("slug"),
        "title": project.get("title"),
        "version_count": len(versions),
        "version_ids": [v.get("id") for v in versions],
        "game_versions": project.get("game_versions", []),
        "loaders": project.get("loaders", []),
        "bundle_files": [
            "README.md",
            "project.json",
            "summary.txt",
        ] + [f"versions/{v.get('id')}/version.json" for v in versions],
    }
    _write_json(out / "index.json", index)

    print(f"\nBundle written to: {out.resolve()}")
    print(f"  project.json, summary.txt, README.md, index.json")
    print(f"  versions/ ({len(versions)} subdirs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
