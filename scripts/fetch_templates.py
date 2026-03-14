#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shutil
import tempfile
import urllib.request
import zipfile
from pathlib import Path

from modcompiler.common import ModCompilerError, load_json


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch missing template directories from provenance sources.")
    parser.add_argument("--range-folder", default="all")
    parser.add_argument("--loader", default="all")
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def extract_backticked(text: str) -> list[str]:
    return re.findall(r"`([^`]+)`", text)


def parse_provenance(provenance_path: Path) -> tuple[str | None, str | None]:
    if not provenance_path.exists():
        return None, None
    source_url = None
    branch_hint = None
    lines = provenance_path.read_text(encoding="utf-8", errors="ignore").splitlines()
    for line in lines:
        if line.strip().startswith("- Source:"):
            tokens = extract_backticked(line)
            if tokens:
                source_url = tokens[0]
                break
    for line in lines:
        if "branch" in line and "`" in line:
            tokens = extract_backticked(line)
            if tokens:
                branch_hint = tokens[0]
                break
    return source_url, branch_hint


def resolve_download_url(source_url: str, branch_hint: str | None) -> str | None:
    if source_url.endswith(".zip"):
        return source_url
    if "github.com" in source_url:
        repo = source_url.rstrip("/").removesuffix(".git")
        branch = branch_hint or "main"
        return f"{repo}/archive/refs/heads/{branch}.zip"
    return None


def download_and_extract(url: str, dest_dir: Path, force: bool) -> None:
    if dest_dir.exists():
        if force:
            shutil.rmtree(dest_dir)
        else:
            return
    dest_dir.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="template-fetch-") as temp_dir:
        temp_dir_path = Path(temp_dir)
        archive_path = temp_dir_path / "template.zip"
        with urllib.request.urlopen(url) as response, archive_path.open("wb") as out_file:
            shutil.copyfileobj(response, out_file)

        with zipfile.ZipFile(archive_path, "r") as zf:
            zf.extractall(temp_dir_path / "unzipped")

        unzipped_root = temp_dir_path / "unzipped"
        entries = [p for p in unzipped_root.iterdir() if p.is_dir()]
        source_root = entries[0] if len(entries) == 1 else unzipped_root

        for item in source_root.iterdir():
            target = dest_dir / item.name
            if target.exists():
                if target.is_dir():
                    shutil.rmtree(target)
                else:
                    target.unlink()
            if item.is_dir():
                shutil.copytree(item, target)
            else:
                shutil.copy2(item, target)


def main() -> int:
    args = parse_args()
    manifest = load_json(Path("version-manifest.json"))
    range_filter = args.range_folder
    loader_filter = args.loader

    failures = 0
    for entry in manifest.get("ranges", []):
        if range_filter not in ("all", "") and entry.get("folder") != range_filter:
            continue
        for loader, loader_config in entry.get("loaders", {}).items():
            if loader_filter not in ("all", "") and loader != loader_filter:
                continue
            template_dir = Path(loader_config["template_dir"])
            provenance_path = Path(entry["folder"]) / loader / "PROVENANCE.md"
            source_url, branch_hint = parse_provenance(provenance_path)
            if not source_url:
                print(f"[WARN] No Source URL found in {provenance_path}")
                failures += 1
                continue

            download_url = resolve_download_url(source_url, branch_hint)
            if not download_url:
                print(f"[WARN] Unsupported Source URL: {source_url}")
                failures += 1
                continue

            try:
                download_and_extract(download_url, template_dir, args.force)
                print(f"[OK] Fetched template for {entry['folder']} {loader} -> {template_dir}")
            except Exception as exc:
                print(f"[ERROR] Failed to fetch {entry['folder']} {loader}: {exc}")
                failures += 1

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
