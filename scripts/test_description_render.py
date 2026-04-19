#!/usr/bin/env python3

from __future__ import annotations

import argparse
from pathlib import Path
import sys

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from modcompiler.auto_create_modrinth import (
    ART_DIRNAME,
    ART_METADATA_FILENAME,
    DEFAULT_OUTPUT_DIR,
    DESCRIPTION_LAYOUT_BASE_SIZE,
    DESCRIPTION_POSTER_IMAGE_SIZE,
    build_visual_html_document,
    compose_description_poster_image,
    render_html_document_to_image,
    save_image_with_size_constraints,
)
from modcompiler.common import ModCompilerError, load_json


def resolve_bundle_dir(bundle_value: str, output_dir: Path) -> Path:
    raw = Path(bundle_value)
    if raw.is_absolute():
        return raw
    if raw.exists():
        return raw.resolve()
    return (output_dir / bundle_value).resolve()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Render a bundle's description poster into debug images for visual review."
    )
    parser.add_argument("bundle", help="Bundle slug under AutoCreateModrinthBundles or an explicit bundle path.")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--timeout-seconds", type=int, default=300)
    args = parser.parse_args(argv)

    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = Path.cwd() / output_dir
    bundle_dir = resolve_bundle_dir(str(args.bundle), output_dir)
    if not bundle_dir.exists():
        raise ModCompilerError(f"Bundle directory does not exist: {bundle_dir}")

    art_dir = bundle_dir / ART_DIRNAME
    listing = load_json(bundle_dir / "listing.json")
    fragments = load_json(art_dir / "visual-fragments.json")
    art_metadata = load_json(bundle_dir / ART_METADATA_FILENAME)

    background_rel = str(art_metadata.get("background_file", "") or "").strip()
    if not background_rel:
        raise ModCompilerError(f"{bundle_dir / ART_METADATA_FILENAME} does not contain background_file")
    background_path = bundle_dir / background_rel
    if not background_path.exists():
        raise ModCompilerError(f"Background file does not exist: {background_path}")

    description_html = build_visual_html_document(
        title=listing.get("name", ""),
        body_html=fragments.get("description_body_html", ""),
        css=fragments.get("description_css", ""),
        variant="description",
        background_filename=background_path.name,
        canvas_size=DESCRIPTION_LAYOUT_BASE_SIZE,
    )
    debug_html_path = art_dir / "description-debug-stage.render.html"
    debug_html_path.write_text(description_html, encoding="utf-8")

    stage_image = render_html_document_to_image(
        html_path=debug_html_path,
        timeout_seconds=max(60, int(args.timeout_seconds)),
        target_size=DESCRIPTION_LAYOUT_BASE_SIZE,
        resize_mode="fit",
    )
    stage_path = save_image_with_size_constraints(
        image=stage_image,
        output_path_stem=art_dir / "description-debug-stage",
    )

    final_image = compose_description_poster_image(
        background_path=background_path,
        stage_image=stage_image,
        target_size=DESCRIPTION_POSTER_IMAGE_SIZE,
    )
    final_path = save_image_with_size_constraints(
        image=final_image,
        output_path_stem=art_dir / "description-debug-final",
    )

    print(f"Bundle: {bundle_dir}")
    print(f"Stage HTML: {debug_html_path}")
    print(f"Stage image: {stage_path}")
    print(f"Final image: {final_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
