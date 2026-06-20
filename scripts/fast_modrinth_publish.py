#!/usr/bin/env python3
"""Fast Modrinth publishing for AI coding IDEs.

No AI API calls. Uses the same HTML+qlmanage rendering pipeline as the old
auto_create_modrinth system for professional icons and gallery images with
background images, glassmorphism, accent colors, and proper layout.

The AI IDE provides all metadata; this script handles:
  - GitHub repo creation + source push (with API fallback)
  - HTML+qlmanage icon/gallery rendering (same quality as old system)
  - Modrinth project creation/update with icon, gallery, source links

Usage:
    # Full pipeline (generate + publish):
    python3 scripts/fast_modrinth_publish.py publish --bundle-dir AutoCreateModrinthBundles/longer-day-1.0.0

    # Update an existing project (icon, source links, gallery):
    python3 scripts/fast_modrinth_publish.py update --bundle-dir AutoCreateModrinthBundles/longer-day-1.0.0

    # Dry run:
    python3 scripts/fast_modrinth_publish.py publish --bundle-dir AutoCreateModrinthBundles/longer-day-1.0.0 --dry-run

Bundle structure (ai_metadata/ provided by AI IDE):
    bundle_dir/
    ├── ai_metadata/
    │   ├── project_info.json    # name, slug, summary, categories, client_side, server_side, license
    │   ├── version_info.json    # version_number, changelog, game_versions, loaders
    │   ├── description.md       # Full Modrinth description
    │   ├── summary.txt          # One-line summary
    │   └── visual_info.json     # accent_color, eyebrow, mark, subtitle, rail_left, rail_right, chips, stats
    ├── input/
    │   └── <mod>.jar
    └── source/                  # Source code to push to GitHub
"""

from __future__ import annotations

import argparse
import html as html_lib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

try:
    from PIL import Image, ImageDraw, ImageEnhance, ImageFont, ImageOps
except ModuleNotFoundError:
    Image = None
    ImageDraw = None
    ImageFont = None
    ImageOps = None

from modcompiler.modrinth import (
    ModrinthClient,
    build_modrinth_user_agent,
    encode_multipart_form_data,
    guess_content_type,
)

MODRINTH_API_BASE = "https://api.modrinth.com/v2"
MODRINTH_ICON_MAX_BYTES = 256 * 1024
MODRINTH_ICON_TARGET_BYTES = MODRINTH_ICON_MAX_BYTES - 4 * 1024
LOGO_IMAGE_SIZE = (1024, 1024)
DESCRIPTION_LAYOUT_BASE_SIZE = (1920, 1080)
DESCRIPTION_POSTER_IMAGE_SIZE = (1920, 1080)
ICON_SIZE = 512
BACKGROUND_IMAGES_DIR = REPO_ROOT / "assets" / "backgrounds"
VISUAL_BACKGROUND_MAX_SIDE = 2560


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
        result = subprocess.run(
            ["gh", "api", "user", "--jq", ".login"],
            capture_output=True, text=True, timeout=10,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return ""


def slugify(text: str) -> str:
    text = text.lower().strip()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def normalize_single_line(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def load_ai_metadata(bundle_dir: Path) -> dict[str, Any]:
    meta_dir = bundle_dir / "ai_metadata"
    result: dict[str, Any] = {}

    project_path = meta_dir / "project_info.json"
    if project_path.exists():
        result["project"] = json.loads(project_path.read_text(encoding="utf-8"))

    version_path = meta_dir / "version_info.json"
    if version_path.exists():
        result["version"] = json.loads(version_path.read_text(encoding="utf-8"))

    desc_path = meta_dir / "description.md"
    if desc_path.exists():
        result["description"] = desc_path.read_text(encoding="utf-8")

    summary_path = meta_dir / "summary.txt"
    if summary_path.exists():
        result["summary"] = summary_path.read_text(encoding="utf-8").strip()

    visual_path = meta_dir / "visual_info.json"
    if visual_path.exists():
        result["visual"] = json.loads(visual_path.read_text(encoding="utf-8"))

    return result


def find_jar(bundle_dir: Path) -> Path | None:
    input_dir = bundle_dir / "input"
    if input_dir.exists():
        for f in sorted(input_dir.glob("*.jar")):
            if f.is_file() and "-sources" not in f.name and "-javadoc" not in f.name:
                return f
    for f in sorted(bundle_dir.glob("*.jar")):
        if f.is_file() and "-sources" not in f.name and "-javadoc" not in f.name:
            return f
    return None


def find_source_dir(bundle_dir: Path) -> Path | None:
    for name in ("source", "src"):
        candidate = bundle_dir / name
        if candidate.exists() and candidate.is_dir():
            return candidate
    return None


def discover_background_images() -> list[Path]:
    if not BACKGROUND_IMAGES_DIR.exists():
        return []
    images = []
    for f in sorted(BACKGROUND_IMAGES_DIR.iterdir()):
        if f.is_file() and f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp", ".avif"):
            images.append(f)
    return images


def pick_background(categories: list[str], title: str) -> Path | None:
    images = discover_background_images()
    if not images:
        return None

    name_lower = title.lower()
    cat_lower = [c.lower() for c in categories]

    keywords_map: dict[str, list[str]] = {
        "ocean.jpg": ["ocean", "water", "sea", "transportation", "boat"],
        "sunset in grassfield.jpg": ["sunset", "grass", "field", "day", "time", "nature"],
        "winter forest with sunset.jpg": ["winter", "cold", "snow", "forest"],
        "fire burning.jpg": ["fire", "furnace", "heat", "combat", "danger"],
        "fresh spring.png": ["spring", "fresh", "green", "nature", "growth"],
        "mountain breeze with spring forest.png": ["mountain", "breeze", "forest", "nature"],
        "purple flowers and sun set or sun rise.png": ["purple", "flower", "magic", "mystical"],
    }

    best_match = None
    best_score = 0
    for img in images:
        score = 0
        keywords = keywords_map.get(img.name, [])
        for kw in keywords:
            if kw in name_lower or kw in " ".join(cat_lower):
                score += 2
        if score > best_score:
            best_score = score
            best_match = img

    return best_match or images[0]


def prepare_background(background_path: Path, art_dir: Path) -> Path:
    if Image is None:
        shutil.copy2(background_path, art_dir / "background.webp")
        return art_dir / "background.webp"

    with Image.open(background_path) as opened:
        prepared = ImageOps.contain(
            opened.convert("RGB"),
            (VISUAL_BACKGROUND_MAX_SIDE, VISUAL_BACKGROUND_MAX_SIDE),
            Image.Resampling.LANCZOS,
        )
    buffer = io.BytesIO()
    prepared.save(buffer, format="WEBP", quality=90, method=6)
    data = buffer.getvalue()
    out_path = art_dir / "background.webp"
    out_path.write_bytes(data)
    return out_path


def build_default_visual_info(title: str, summary: str, categories: list[str]) -> dict[str, Any]:
    category_colors: dict[str, str] = {
        "utility": "#3498db",
        "game-mechanics": "#2ecc71",
        "social": "#9b59b6",
        "optimization": "#1abc9c",
        "adventure": "#e67e22",
        "technology": "#34495e",
        "storage": "#f1c40f",
        "transportation": "#e74c3c",
        "decoration": "#f39c12",
        "mobs": "#c0392b",
        "food": "#27ae60",
        "magic": "#8e44ad",
        "equipment": "#2c3e50",
        "worldgen": "#16a085",
        "management": "#7f8c8d",
        "economy": "#f1c40f",
        "library": "#95a5a6",
        "cursed": "#2c3e50",
        "minigame": "#d35400",
    }

    primary_cat = categories[0] if categories else "utility"
    accent_color = category_colors.get(primary_cat, "#3498db")

    words = re.findall(r"[A-Za-z0-9]+", title)
    if len(words) <= 2:
        mark = "".join(w[0] for w in words if w).upper()
    else:
        mark = (words[0][0] + words[-1][0]).upper()

    return {
        "accent_color": accent_color,
        "logo": {
            "eyebrow": "MINECRAFT MOD",
            "mark": mark,
            "title": title.upper(),
            "subtitle": summary[:80],
            "rail_left": primary_cat.upper(),
            "rail_right": "MODRINTH",
        },
        "description": {
            "kicker": "MINECRAFT MOD",
            "title": title,
            "tagline": summary[:120],
            "chips": [c.replace("-", " ").title() for c in categories[:4]],
            "stats": [
                {"value": "1.0", "label": "VERSION", "note": "Initial release"},
                {"value": str(len(categories)), "label": "CATEGORIES", "note": ", ".join(categories[:2])},
            ],
        },
    }


def css_variable_style(variables: dict[str, str]) -> str:
    return "; ".join(f"{key}: {value}" for key, value in variables.items() if str(value or "").strip())


def compute_logo_layout_variables(title: str) -> dict[str, str]:
    clean_title = normalize_single_line(title)
    words = re.findall(r"[A-Za-z0-9]+", clean_title)
    char_count = len(clean_title)
    max_word_length = max((len(word) for word in words), default=0)

    if char_count >= 20 or max_word_length >= 10:
        return {
            "--frame-inset": "40px", "--bracket-inset": "78px",
            "--content-pad-top": "92px", "--content-pad-x": "70px", "--content-pad-bottom": "82px",
            "--eyebrow-size": "15px", "--eyebrow-spacing": "0.24em",
            "--mark-size": "270px", "--title-size": "56px", "--title-line-height": "0.95",
            "--title-max-width": "100%", "--sub-size": "21px", "--sub-max-width": "100%",
            "--rail-inset": "72px", "--rail-size": "15px", "--rail-spacing": "0.22em",
        }
    return {
        "--frame-inset": "62px", "--bracket-inset": "112px",
        "--content-pad-top": "110px", "--content-pad-x": "110px", "--content-pad-bottom": "94px",
        "--eyebrow-size": "18px", "--eyebrow-spacing": "0.32em",
        "--mark-size": "320px", "--title-size": "74px", "--title-line-height": "0.94",
        "--title-max-width": "88%", "--sub-size": "24px", "--sub-max-width": "660px",
        "--rail-inset": "110px", "--rail-size": "18px", "--rail-spacing": "0.26em",
    }


def compute_description_layout_variables(title: str) -> dict[str, str]:
    clean_title = normalize_single_line(title)
    char_count = len(clean_title)
    max_word_length = max((len(w) for w in re.findall(r"[A-Za-z0-9]+", clean_title)), default=0)

    if char_count >= 22 or max_word_length >= 10:
        return {
            "--root-pad-top": "126px", "--root-pad-bottom": "126px", "--root-pad-x": "68px",
            "--layout-gap": "28px", "--side-width": "214px",
            "--hero-pad-y": "40px", "--hero-pad-x": "46px",
            "--kicker-size": "14px", "--kicker-spacing": "0.20em",
            "--description-title-size": "74px", "--description-title-line-height": "0.95",
            "--tagline-size": "22px", "--tagline-max-width": "100%",
            "--chip-size": "14px", "--stat-value-size": "48px",
            "--stat-label-size": "14px", "--stat-note-size": "15px",
            "--hero-stack-gap": "14px", "--meta-padding-top": "18px",
        }
    return {
        "--root-pad-top": "154px", "--root-pad-bottom": "154px", "--root-pad-x": "94px",
        "--layout-gap": "42px", "--side-width": "260px",
        "--hero-pad-y": "54px", "--hero-pad-x": "68px",
        "--kicker-size": "16px", "--kicker-spacing": "0.24em",
        "--description-title-size": "118px", "--description-title-line-height": "0.9",
        "--tagline-size": "30px", "--tagline-max-width": "680px",
        "--chip-size": "16px", "--stat-value-size": "58px",
        "--stat-label-size": "15px", "--stat-note-size": "16px",
        "--hero-stack-gap": "18px", "--meta-padding-top": "22px",
    }


def build_logo_html(visual_data: dict[str, Any], accent_color: str, background_filename: str) -> str:
    logo = visual_data["logo"]
    eyebrow = html_lib.escape(logo.get("eyebrow", "MINECRAFT MOD"))
    mark = html_lib.escape(logo.get("mark", "?"))
    title = html_lib.escape(logo.get("title", "MOD"))
    subtitle = html_lib.escape(logo.get("subtitle", ""))
    rail_left = html_lib.escape(logo.get("rail_left", ""))
    rail_right = html_lib.escape(logo.get("rail_right", "MODRINTH"))
    frame_style = html_lib.escape(css_variable_style(compute_logo_layout_variables(logo.get("title", ""))))

    canvas_width, canvas_height = LOGO_IMAGE_SIZE
    safe_bg = html_lib.escape(background_filename)

    body_html = (
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

    css = textwrap.dedent(f"""
        .frame {{
          position: absolute; inset: var(--frame-inset, 62px); border-radius: 42px;
          background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.015)), rgba(14,14,16,0.64);
          border: 1px solid rgba(255,255,255,0.12);
          box-shadow: 0 35px 100px rgba(0,0,0,0.55), inset 0 1px 0 rgba(255,255,255,0.08), inset 0 -24px 40px rgba(0,0,0,0.35);
          backdrop-filter: blur(14px); overflow: hidden;
        }}
        .frame::before, .frame::after {{
          content: ""; position: absolute; width: 320px; height: 320px; border-radius: 999px;
          filter: blur(18px); opacity: 0.72;
        }}
        .frame::before {{
          top: -96px; left: -82px;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 75%, white 25%), transparent 72%);
        }}
        .frame::after {{
          right: -100px; bottom: -120px;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 68%, black 12%), transparent 72%);
        }}
        .rings {{ position: absolute; inset: 0; display: grid; place-items: center; pointer-events: none; }}
        .ring {{ position: absolute; border-radius: 50%; border: 1px solid rgba(255,255,255,0.12);
          box-shadow: 0 0 0 1px color-mix(in srgb, {accent_color} 22%, transparent) inset; }}
        .ring.one {{ width: 610px; height: 610px; }}
        .ring.two {{ width: 470px; height: 470px; }}
        .ring.three {{ width: 310px; height: 310px; }}
        .brackets {{ position: absolute; inset: var(--bracket-inset, 112px); pointer-events: none; }}
        .brackets span {{ position: absolute; width: 92px; height: 92px; border-color: rgba(255,255,255,0.24); border-style: solid; opacity: 0.88; }}
        .brackets .tl {{ top: 0; left: 0; border-width: 2px 0 0 2px; }}
        .brackets .tr {{ top: 0; right: 0; border-width: 2px 2px 0 0; }}
        .brackets .bl {{ bottom: 0; left: 0; border-width: 0 0 2px 2px; }}
        .brackets .br {{ bottom: 0; right: 0; border-width: 0 2px 2px 0; }}
        .content {{ position: relative; z-index: 2; height: 100%; display: flex; flex-direction: column;
          align-items: center; justify-content: center; min-width: 0;
          padding: var(--content-pad-top, 110px) var(--content-pad-x, 110px) var(--content-pad-bottom, 94px);
          text-align: center; }}
        .eyebrow {{ margin-bottom: 26px; padding: 10px 18px 11px; border-radius: 999px;
          border: 1px solid rgba(255,255,255,0.14); background: rgba(255,255,255,0.05);
          font-size: var(--eyebrow-size, 18px); font-weight: 700;
          letter-spacing: var(--eyebrow-spacing, 0.32em); text-transform: uppercase;
          color: rgba(255,255,255,0.82); box-shadow: inset 0 1px 0 rgba(255,255,255,0.1); }}
        .mark {{ position: relative; line-height: 0.82; font-size: var(--mark-size, 320px);
          font-weight: 900; letter-spacing: -0.08em; text-transform: uppercase; color: #f4f7fb;
          text-shadow: 0 0 18px rgba(255,255,255,0.28), 0 0 46px rgba(255,255,255,0.18), 0 26px 65px rgba(0,0,0,0.48); }}
        .mark::after {{ content: ""; position: absolute; left: 6%; right: 6%; top: 54%; height: 18px;
          border-radius: 999px; background: linear-gradient(90deg, transparent, {accent_color}, transparent);
          box-shadow: 0 0 18px color-mix(in srgb, {accent_color} 82%, transparent); transform: skewX(-18deg); }}
        .title {{ margin-top: 18px; max-width: var(--title-max-width, 88%);
          font-size: var(--title-size, 74px); line-height: var(--title-line-height, 0.94);
          font-weight: 900; letter-spacing: -0.04em; text-transform: uppercase; color: white; }}
        .sub {{ margin-top: 22px; max-width: var(--sub-max-width, 660px);
          font-size: var(--sub-size, 24px); line-height: 1.45; color: rgba(255,255,255,0.74); letter-spacing: 0.02em; }}
        .rail {{ position: absolute; left: var(--rail-inset, 110px); right: var(--rail-inset, 110px); bottom: 72px;
          display: flex; justify-content: space-between; align-items: center; gap: 16px;
          font-size: var(--rail-size, 18px); font-weight: 700;
          letter-spacing: var(--rail-spacing, 0.26em); text-transform: uppercase; color: rgba(255,255,255,0.56); }}
        .rail::before {{ content: ""; position: absolute; left: 0; right: 0; top: -20px; height: 1px;
          background: linear-gradient(90deg, transparent, rgba(255,255,255,0.10), transparent); }}
        .rail .warn {{ color: color-mix(in srgb, {accent_color} 76%, white 24%); }}
    """).strip()

    root_style = textwrap.dedent(f"""
        #modcompiler-root {{
          position: relative; z-index: 1; width: 100%; height: 100%; opacity: 1;
        }}
    """).strip()

    return textwrap.dedent(f"""
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <title>{html_lib.escape(title)}</title>
          <style>
            :root {{ color-scheme: dark; }}
            * {{ box-sizing: border-box; }}
            html, body {{ margin: 0; width: {canvas_width}px; height: {canvas_height}px;
              overflow: hidden; background: #000; }}
            body {{ position: relative; font-family: "Helvetica Neue", "Avenir Next", "SF Pro Display", Arial, sans-serif;
              color: #fff; isolation: isolate; }}
            body::before {{
              content: ""; position: absolute; inset: 0;
              background: linear-gradient(180deg, rgba(0,0,0,0.18), rgba(0,0,0,0.72)),
                url("{safe_bg}") center center / cover no-repeat;
              filter: saturate(1.05) brightness(0.68); transform: scale(1.06); transform-origin: center;
            }}
            body::after {{
              content: ""; position: absolute; inset: 0;
              background: radial-gradient(circle at top left, rgba(255,255,255,0.12), transparent 38%),
                radial-gradient(circle at bottom right, rgba(255,255,255,0.10), transparent 34%);
              mix-blend-mode: screen; pointer-events: none;
            }}
            {root_style}
            {css}
          </style>
        </head>
        <body class="modcompiler-logo">
          <div id="modcompiler-root">{body_html}</div>
        </body>
        </html>
    """).strip() + "\n"


def build_description_html(visual_data: dict[str, Any], accent_color: str, background_filename: str) -> str:
    desc = visual_data["description"]
    kicker = html_lib.escape(desc.get("kicker", "MINECRAFT MOD"))
    title = html_lib.escape(desc.get("title", ""))
    tagline = html_lib.escape(desc.get("tagline", ""))
    chips = desc.get("chips", [])
    stats = desc.get("stats", [])

    chips_html = "".join(f'<div class="chip">{html_lib.escape(c)}</div>' for c in chips)
    stats_html = "".join(
        f'<section class="stat">'
        f'<div class="value">{html_lib.escape(s["value"])}</div>'
        f'<div class="label">{html_lib.escape(s["label"])}</div>'
        f'<div class="note">{html_lib.escape(s["note"])}</div>'
        f'</section>'
        for s in stats
    )

    root_style = html_lib.escape(css_variable_style(compute_description_layout_variables(desc.get("title", ""))))

    body_html = (
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

    canvas_width, canvas_height = DESCRIPTION_LAYOUT_BASE_SIZE
    stage_width, stage_height = DESCRIPTION_LAYOUT_BASE_SIZE
    stage_scale = min(canvas_width / stage_width, canvas_height / stage_height)
    safe_bg = html_lib.escape(background_filename)

    root_css = textwrap.dedent(f"""
        #modcompiler-root {{
          position: absolute; z-index: 1; width: {stage_width}px; height: {stage_height}px;
          left: 50%; top: 50%; transform: translate(-50%, -50%) scale({stage_scale:.6f});
          transform-origin: center center; opacity: 0.80;
        }}
    """).strip()

    css = textwrap.dedent(f"""
        #root {{ position: relative; z-index: 1; width: 100%; height: 100%; opacity: 0.8;
          padding: var(--root-pad-top, 154px) var(--root-pad-x, 94px) var(--root-pad-bottom, 154px); }}
        .layout {{ height: 100%; display: grid; grid-template-columns: minmax(0, 1fr) var(--side-width, 260px);
          gap: var(--layout-gap, 42px); align-items: stretch; min-height: 0; }}
        .hero {{ position: relative; display: flex; flex-direction: column; justify-content: flex-start;
          gap: var(--hero-stack-gap, 18px); min-width: 0;
          padding: var(--hero-pad-y, 54px) var(--hero-pad-x, 68px);
          border-radius: 34px;
          background: linear-gradient(180deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02)), rgba(10,10,12,0.46);
          border: 1px solid rgba(255,255,255,0.14);
          box-shadow: 0 30px 90px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.1);
          backdrop-filter: blur(14px); overflow: hidden; }}
        .hero::before {{ content: ""; position: absolute; top: -80px; left: -80px; width: 260px; height: 260px;
          border-radius: 999px; filter: blur(18px); opacity: 0.72;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 46%, transparent), transparent 72%); }}
        .hero::after {{ content: ""; position: absolute; right: -80px; bottom: -80px; width: 220px; height: 220px;
          border-radius: 999px; filter: blur(18px); opacity: 0.72;
          background: radial-gradient(circle, color-mix(in srgb, {accent_color} 38%, transparent), transparent 72%); }}
        .kicker {{ padding: 10px 18px 11px; border-radius: 999px;
          border: 1px solid rgba(255,255,255,0.14); background: rgba(255,255,255,0.06);
          font-size: var(--kicker-size, 16px); font-weight: 700;
          letter-spacing: var(--kicker-spacing, 0.24em); text-transform: uppercase;
          color: rgba(255,255,255,0.82); display: inline-block; width: fit-content; }}
        h1 {{ margin: 0; font-size: var(--description-title-size, 118px);
          line-height: var(--description-title-line-height, 0.9);
          font-weight: 900; letter-spacing: -0.04em; text-transform: uppercase; color: white; }}
        .tagline {{ margin: 0; font-size: var(--tagline-size, 30px); line-height: 1.45;
          color: rgba(255,255,255,0.74); letter-spacing: 0.02em; max-width: var(--tagline-max-width, 680px); }}
        .meta {{ display: flex; flex-wrap: wrap; gap: 12px; padding-top: var(--meta-padding-top, 22px); }}
        .chip {{ padding: 8px 16px; border-radius: 999px; border: 1px solid rgba(255,255,255,0.12);
          background: rgba(255,255,255,0.06); font-size: var(--chip-size, 16px); font-weight: 600;
          letter-spacing: 0.04em; text-transform: uppercase; color: rgba(255,255,255,0.78); }}
        .side {{ display: flex; flex-direction: column; gap: 24px;
          padding: 40px 36px; border-radius: 34px;
          background: linear-gradient(180deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02)), rgba(10,10,12,0.46);
          border: 1px solid rgba(255,255,255,0.14);
          box-shadow: 0 30px 90px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.1);
          backdrop-filter: blur(14px); overflow: hidden; }}
        .stat {{ text-align: center; }}
        .stat .value {{ font-size: var(--stat-value-size, 58px); font-weight: 900;
          letter-spacing: -0.04em; color: white; }}
        .stat .label {{ font-size: var(--stat-label-size, 15px); font-weight: 700;
          letter-spacing: 0.14em; text-transform: uppercase; color: rgba(255,255,255,0.56); }}
        .stat .note {{ font-size: var(--stat-note-size, 16px); color: rgba(255,255,255,0.48); margin-top: 4px; }}
    """).strip()

    return textwrap.dedent(f"""
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <title>{html_lib.escape(title)}</title>
          <style>
            :root {{ color-scheme: dark; }}
            * {{ box-sizing: border-box; }}
            html, body {{ margin: 0; width: {canvas_width}px; height: {canvas_height}px;
              overflow: hidden; background: #000; }}
            body {{ position: relative; font-family: "Helvetica Neue", "Avenir Next", "SF Pro Display", Arial, sans-serif;
              color: #fff; isolation: isolate; }}
            body::before {{
              content: ""; position: absolute; inset: 0;
              background: linear-gradient(180deg, rgba(0,0,0,0.18), rgba(0,0,0,0.72)),
                url("{safe_bg}") center center / cover no-repeat;
              filter: saturate(1.05) brightness(0.68); transform: scale(1.18); transform-origin: center;
            }}
            body::after {{
              content: ""; position: absolute; inset: 0;
              background: radial-gradient(circle at top left, rgba(255,255,255,0.12), transparent 38%),
                radial-gradient(circle at bottom right, rgba(255,255,255,0.10), transparent 34%);
              mix-blend-mode: screen; pointer-events: none;
            }}
            {root_css}
            {css}
          </style>
        </head>
        <body class="modcompiler-description">
          <div id="modcompiler-root">{body_html}</div>
        </body>
        </html>
    """).strip() + "\n"


CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def render_html_via_chrome(html_path: Path, width: int, height: int, timeout: int = 30) -> bytes:
    output_path = html_path.parent / (html_path.stem + ".png")
    command = [
        CHROME_PATH,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--screenshot=%s" % str(output_path),
        "--window-size=%d,%d" % (width, height),
        str(html_path),
    ]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        print(f"  WARNING: Chrome timed out for {html_path.name}")
        return b""
    if not output_path.exists():
        print(f"  WARNING: Chrome did not produce output for {html_path.name}")
        return b""
    return output_path.read_bytes()


def crop_qlmanage_output(img: Image.Image, html_width: int, html_height: int) -> Image.Image:
    return img


def save_icon_with_modrinth_limit(image: Image.Image, output_path_stem: Path) -> Path:
    working = image.convert("RGB")
    sizes = (512, 480, 448, 416, 384, 352, 320, 288, 256)
    qualities = (92, 88, 84, 80, 76, 72, 68, 64, 60, 56, 52, 48, 44, 40)

    for size in sizes:
        resized = working.resize((size, size), Image.Resampling.LANCZOS) if working.size != (size, size) else working
        for quality in qualities:
            buffer = io.BytesIO()
            resized.save(buffer, format="WEBP", quality=quality, method=6)
            data = buffer.getvalue()
            if len(data) <= MODRINTH_ICON_TARGET_BYTES:
                icon_path = output_path_stem.with_suffix(".webp")
                icon_path.write_bytes(data)
                return icon_path

    buffer = io.BytesIO()
    working.resize((256, 256), Image.Resampling.LANCZOS).save(buffer, format="WEBP", quality=40, method=6)
    icon_path = output_path_stem.with_suffix(".webp")
    icon_path.write_bytes(buffer.getvalue())
    return icon_path


def save_image_webp(image: Image.Image, output_path_stem: Path, max_bytes: int | None = None) -> Path:
    working = image.convert("RGB")
    if max_bytes is None:
        buffer = io.BytesIO()
        working.save(buffer, format="WEBP", quality=85, method=6)
        out = output_path_stem.with_suffix(".webp")
        out.write_bytes(buffer.getvalue())
        return out

    for quality in range(90, 30, -5):
        buffer = io.BytesIO()
        working.save(buffer, format="WEBP", quality=quality, method=6)
        data = buffer.getvalue()
        if len(data) <= max_bytes:
            out = output_path_stem.with_suffix(".webp")
            out.write_bytes(data)
            return out

    buffer = io.BytesIO()
    working.save(buffer, format="WEBP", quality=30, method=6)
    out = output_path_stem.with_suffix(".webp")
    out.write_bytes(buffer.getvalue())
    return out


def generate_visual_assets(bundle_dir: Path, visual_data: dict[str, Any]) -> tuple[Path | None, Path | None]:
    if Image is None:
        print("  WARNING: Pillow not installed, cannot generate visuals")
        return None, None

    art_dir = bundle_dir / "art"
    art_dir.mkdir(parents=True, exist_ok=True)

    categories = []
    project_meta = load_ai_metadata(bundle_dir).get("project", {})
    categories = project_meta.get("categories", [])

    bg_path = pick_background(categories, visual_data.get("logo", {}).get("title", ""))
    if bg_path is None:
        print("  WARNING: No background images found, using fallback")
        return None, None

    print(f"  Background: {bg_path.name}")
    bg_prepared = prepare_background(bg_path, art_dir)

    accent_color = visual_data.get("accent_color", "#3498db")

    logo_html = build_logo_html(visual_data, accent_color, bg_prepared.name)
    logo_html_path = art_dir / "logo.render.html"
    logo_html_path.write_text(logo_html, encoding="utf-8")

    desc_html = build_description_html(visual_data, accent_color, bg_prepared.name)
    desc_html_path = art_dir / "description-image.render.html"
    desc_html_path.write_text(desc_html, encoding="utf-8")

    print(f"  Rendering logo via Chrome...")
    logo_png_data = render_html_via_chrome(logo_html_path, LOGO_IMAGE_SIZE[0], LOGO_IMAGE_SIZE[1], timeout=30)

    print(f"  Rendering description via Chrome...")
    desc_png_data = render_html_via_chrome(desc_html_path, DESCRIPTION_LAYOUT_BASE_SIZE[0], DESCRIPTION_LAYOUT_BASE_SIZE[1], timeout=30)

    icon_path = None
    gallery_path = None

    if logo_png_data:
        try:
            logo_img = Image.open(io.BytesIO(logo_png_data))
            print(f"  Logo qlmanage output size: {logo_img.size}")
            logo_img = crop_qlmanage_output(logo_img, LOGO_IMAGE_SIZE[0], LOGO_IMAGE_SIZE[1])
            icon_img = ImageOps.fit(logo_img.convert("RGB"), (ICON_SIZE, ICON_SIZE), Image.Resampling.LANCZOS)
            icon_path = save_icon_with_modrinth_limit(icon_img, bundle_dir / "icon")
            print(f"  Icon: {icon_path.name} ({icon_path.stat().st_size} bytes)")
        except Exception as e:
            print(f"  WARNING: Icon generation failed: {e}")

    if desc_png_data:
        try:
            desc_img = Image.open(io.BytesIO(desc_png_data))
            print(f"  Description qlmanage output size: {desc_img.size}")
            desc_img = crop_qlmanage_output(desc_img, DESCRIPTION_LAYOUT_BASE_SIZE[0], DESCRIPTION_LAYOUT_BASE_SIZE[1])
            with Image.open(bg_prepared) as bg:
                bg_fitted = ImageOps.fit(bg.convert("RGB"), DESCRIPTION_POSTER_IMAGE_SIZE, Image.Resampling.LANCZOS)
                bg_fitted = ImageEnhance.Color(bg_fitted).enhance(1.05)
                bg_fitted = ImageEnhance.Brightness(bg_fitted).enhance(0.72)

            desc_resized = desc_img.convert("RGBA").resize(DESCRIPTION_POSTER_IMAGE_SIZE, Image.Resampling.LANCZOS)
            bg_fitted.paste(desc_resized, (0, 0), desc_resized)
            gallery_path = save_image_webp(bg_fitted, bundle_dir / "gallery-cover")
            print(f"  Gallery: {gallery_path.name} ({gallery_path.stat().st_size} bytes)")
        except Exception as e:
            print(f"  WARNING: Gallery generation failed: {e}")

    return icon_path, gallery_path


def create_github_repo(owner: str, repo_name: str, description: str, github_token: str) -> dict[str, str]:
    result: dict[str, str] = {
        "repo_url": "", "source_url": "", "issues_url": "", "wiki_url": "", "created": "false",
    }
    try:
        check = subprocess.run(
            ["gh", "api", f"repos/{owner}/{repo_name}", "--jq", ".html_url"],
            capture_output=True, text=True, timeout=15,
            env={**os.environ, "GH_TOKEN": github_token},
        )
        if check.returncode == 0 and check.stdout.strip():
            repo_url = check.stdout.strip()
            result.update({"repo_url": repo_url, "source_url": repo_url,
                           "issues_url": f"{repo_url}/issues", "wiki_url": f"{repo_url}/wiki", "created": "exists"})
            return result
    except Exception:
        pass

    try:
        create_result = subprocess.run(
            ["gh", "repo", "create", f"{owner}/{repo_name}", "--public", "--description", description],
            capture_output=True, text=True, timeout=30,
            env={**os.environ, "GH_TOKEN": github_token},
        )
        if create_result.returncode != 0:
            print(f"  WARNING: Could not create GitHub repo: {create_result.stderr.strip()}")
            return result
        repo_url = f"https://github.com/{owner}/{repo_name}"
        result.update({"repo_url": repo_url, "source_url": repo_url,
                       "issues_url": f"{repo_url}/issues", "wiki_url": f"{repo_url}/wiki", "created": "true"})
    except Exception as e:
        print(f"  WARNING: GitHub repo creation failed: {e}")
    return result


def push_source_via_api(owner: str, repo_name: str, source_dir: Path, github_token: str) -> bool:
    import base64
    try:
        files_pushed = 0
        for f in sorted(source_dir.rglob("*")):
            if not f.is_file():
                continue
            rel = f.relative_to(source_dir)
            remote = str(rel)
            content = base64.b64encode(f.read_bytes()).decode()
            payload = {"message": f"Add {remote}", "content": content, "branch": "main"}
            result = subprocess.run(
                ["gh", "api", "-X", "PUT", f"repos/{owner}/{repo_name}/contents/{remote}", "--input", "-"],
                input=json.dumps(payload), capture_output=True, text=True, timeout=30,
                env={**os.environ, "GH_TOKEN": github_token},
            )
            if result.returncode != 0:
                if "422" not in result.stderr:
                    print(f"  WARNING: Failed to push {remote}: {result.stderr[:100]}")
            else:
                files_pushed += 1
        print(f"  Pushed {files_pushed} files via GitHub API")
        return files_pushed > 0
    except Exception as e:
        print(f"  WARNING: GitHub API push failed: {e}")
        return False


def push_source_to_github(owner: str, repo_name: str, source_dir: Path, github_token: str) -> bool:
    try:
        with tempfile.TemporaryDirectory(prefix="modcompiler-push-") as temp_dir:
            checkout = Path(temp_dir) / "repo"
            repo_url = f"https://x-access-token:{github_token}@github.com/{owner}/{repo_name}.git"
            try:
                clone_result = subprocess.run(
                    ["git", "clone", "--depth", "1", repo_url, str(checkout)],
                    capture_output=True, text=True, timeout=30,
                )
                if clone_result.returncode != 0:
                    raise subprocess.TimeoutExpired(cmd="git clone", timeout=30)
            except (subprocess.TimeoutExpired, Exception):
                print(f"  Git clone failed/timed out, using GitHub Contents API...")
                return push_source_via_api(owner, repo_name, source_dir, github_token)

            subprocess.run(["git", "config", "user.name", "ModCompiler"], cwd=str(checkout), capture_output=True, timeout=5)
            subprocess.run(["git", "config", "user.email", "modcompiler@users.noreply.github.com"], cwd=str(checkout), capture_output=True, timeout=5)
            src_dest = checkout / "src"
            if src_dest.exists():
                shutil.rmtree(src_dest)
            if source_dir.exists():
                shutil.copytree(source_dir, src_dest)
            subprocess.run(["git", "add", "-A"], cwd=str(checkout), capture_output=True, timeout=30)
            subprocess.run(["git", "commit", "-m", "Auto-sync source from ModCompiler"], cwd=str(checkout), capture_output=True, timeout=30)
            subprocess.run(["git", "branch", "-M", "main"], cwd=str(checkout), capture_output=True, timeout=5)
            push_result = subprocess.run(
                ["git", "push", "-u", "origin", "main", "--force"],
                cwd=str(checkout), capture_output=True, text=True, timeout=60,
            )
            if push_result.returncode != 0:
                print(f"  Git push failed, falling back to GitHub Contents API...")
                return push_source_via_api(owner, repo_name, source_dir, github_token)
        return True
    except Exception as e:
        print(f"  Git push failed: {e}, falling back to GitHub Contents API...")
        return push_source_via_api(owner, repo_name, source_dir, github_token)


def build_modrinth_project_payload(ai_meta: dict[str, Any], github_links: dict[str, str], description: str, project_status: str) -> dict[str, Any]:
    project_info = ai_meta.get("project", {})
    slug = project_info.get("slug", "") or slugify(project_info.get("name", ""))
    payload = {
        "slug": slug, "title": project_info.get("name", ""),
        "description": ai_meta.get("summary", "") or project_info.get("summary", ""),
        "body": description, "categories": project_info.get("categories", []),
        "client_side": project_info.get("client_side", "optional"),
        "server_side": project_info.get("server_side", "optional"),
        "license_id": project_info.get("license", "MIT"), "project_type": "mod", "status": project_status,
    }
    if github_links.get("source_url"):
        payload["source_url"] = github_links["source_url"]
    if github_links.get("issues_url"):
        payload["issues_url"] = github_links["issues_url"]
    if github_links.get("wiki_url"):
        payload["wiki_url"] = github_links["wiki_url"]
    return {k: v for k, v in payload.items() if v not in ("", None, [])}


def build_modrinth_version_payload(ai_meta: dict[str, Any], project_id: str, version_status: str) -> dict[str, Any]:
    version_info = ai_meta.get("version", {})
    return {
        "project_id": project_id,
        "name": f"{ai_meta.get('project', {}).get('name', '')} {version_info.get('version_number', '1.0.0')}",
        "version_number": version_info.get("version_number", "1.0.0"),
        "changelog": version_info.get("changelog", ""),
        "dependencies": [], "game_versions": version_info.get("game_versions", []),
        "loaders": version_info.get("loaders", []), "version_type": "release",
        "featured": version_info.get("featured", False), "status": version_status,
        "client_side": ai_meta.get("project", {}).get("client_side", "optional"),
        "server_side": ai_meta.get("project", {}).get("server_side", "optional"),
        "file_parts": ["file"],
    }


def create_modrinth_project(client: ModrinthClient, project_payload: dict[str, Any], version_payload: dict[str, Any], jar_path: Path, icon_path: Path | None) -> dict[str, Any]:
    body_dict = {k: v for k, v in project_payload.items() if v not in ("", None, [])}
    initial_version = dict(version_payload)
    initial_version["file_parts"] = ["file"]
    body_dict["initial_versions"] = [initial_version]
    files = [("file", jar_path.name, jar_path.read_bytes(), guess_content_type(jar_path.name))]
    if icon_path:
        files.append(("icon", icon_path.name, icon_path.read_bytes(), guess_content_type(icon_path.name)))
    body, content_type = encode_multipart_form_data(fields={"data": json.dumps(body_dict)}, files=files)
    return client.request_json("POST", "/project", body=body, extra_headers={"Content-Type": content_type})


def create_modrinth_version(client: ModrinthClient, version_payload: dict[str, Any], jar_path: Path) -> dict[str, Any]:
    body, content_type = encode_multipart_form_data(
        fields={"data": json.dumps(version_payload)},
        files=[("file", jar_path.name, jar_path.read_bytes(), guess_content_type(jar_path.name))],
    )
    return client.request_json("POST", "/version", body=body, extra_headers={"Content-Type": content_type})


def strip_description_images(body: str, title: str) -> str:
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


def inject_gallery_image_into_body(body: str, title: str, image_url: str) -> str:
    clean_body = strip_description_images(body, title)
    if not image_url:
        return clean_body
    if image_url in clean_body:
        return clean_body

    image_markdown = f"![{title} cover image]({image_url})"

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


def resolve_gallery_image_url(client: ModrinthClient, slug: str, expected_title: str, before_urls: set[str]) -> str:
    try:
        project = client.resolve_project(slug)
        gallery = project.get("gallery", []) or []
        for item in gallery:
            if not isinstance(item, dict):
                continue
            url = normalize_single_line(item.get("url", ""))
            title = normalize_single_line(item.get("title", ""))
            if url and title == expected_title and url not in before_urls:
                return url
        for item in gallery:
            if not isinstance(item, dict):
                continue
            url = normalize_single_line(item.get("url", ""))
            if url and url not in before_urls:
                return url
    except Exception:
        pass
    return ""


def update_modrinth_project(client: ModrinthClient, slug: str, project_payload: dict[str, Any], icon_path: Path | None, gallery_path: Path | None) -> None:
    patch_fields = {}
    for key in ("title", "description", "body", "categories", "additional_categories",
                "client_side", "server_side", "license_id", "source_url", "issues_url",
                "wiki_url", "donation_urls"):
        value = project_payload.get(key)
        if value is not None and value != "" and value != []:
            patch_fields[key] = value
    if patch_fields:
        client.modify_project(project_ref=slug, payload=patch_fields)
        print(f"  Updated project metadata: {', '.join(patch_fields.keys())}")
    if icon_path and icon_path.exists():
        client.change_project_icon(project_ref=slug, icon_path=icon_path)
        print(f"  Updated icon: {icon_path.name}")
    if gallery_path and gallery_path.exists():
        title = project_payload.get("title", slug)
        gallery_title = f"{title} Cover Image"

        before_urls: set[str] = set()
        existing_gallery_url = ""
        try:
            before_project = client.resolve_project(slug)
            for item in before_project.get("gallery", []) or []:
                if isinstance(item, dict):
                    url = normalize_single_line(item.get("url", ""))
                    item_title = normalize_single_line(item.get("title", ""))
                    if url:
                        before_urls.add(url)
                    if url and item_title == gallery_title:
                        existing_gallery_url = url
        except Exception:
            pass

        if existing_gallery_url:
            try:
                client.delete_gallery_image(project_ref=slug, image_url=existing_gallery_url)
                print(f"  Deleted old gallery image")
            except Exception as e:
                print(f"  WARNING: Could not delete old gallery image: {e}")

        gallery_url = ""
        try:
            client.add_gallery_image(project_ref=slug, image_path=gallery_path, featured=True,
                                     title=gallery_title, description=f"Cover image for {title}", ordering=0)
            print(f"  Uploaded gallery image: {gallery_path.name}")
            gallery_url = resolve_gallery_image_url(client, slug, gallery_title, before_urls)
        except Exception as e:
            err_msg = str(e)
            if "duplicate" in err_msg.lower():
                print(f"  Gallery image already exists (duplicate), resolving URL...")
                gallery_url = resolve_gallery_image_url(client, slug, gallery_title, before_urls)
            else:
                print(f"  WARNING: Gallery upload failed: {e}")

        if gallery_url:
            current_body = project_payload.get("body", "")
            updated_body = inject_gallery_image_into_body(current_body, title, gallery_url)
            if updated_body != current_body:
                client.modify_project(project_ref=slug, payload={"body": updated_body})
                print(f"  Injected gallery image into description body")


def cmd_publish(args: argparse.Namespace) -> int:
    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.exists():
        print(f"ERROR: Bundle directory not found: {bundle_dir}", file=sys.stderr)
        return 1

    ai_meta = load_ai_metadata(bundle_dir)
    if not ai_meta.get("project"):
        print("ERROR: ai_metadata/project_info.json not found or empty.", file=sys.stderr)
        return 1

    jar_path = find_jar(bundle_dir)
    source_dir = find_source_dir(bundle_dir)
    project_info = ai_meta["project"]
    slug = project_info.get("slug", "") or slugify(project_info.get("name", ""))
    title = project_info.get("name", "")
    description = ai_meta.get("description", "")

    visual_data = ai_meta.get("visual")
    if not visual_data:
        visual_data = build_default_visual_info(title, ai_meta.get("summary", ""), project_info.get("categories", []))

    print(f"Mod: {title} ({slug})")
    if jar_path:
        print(f"JAR: {jar_path.name} ({jar_path.stat().st_size} bytes)")
    if source_dir:
        print(f"Source: {source_dir}")
    print()

    token = discover_token(args.modrinth_token)
    if not token and not args.dry_run:
        print("ERROR: No Modrinth token. Use --modrinth-token or set MODRINTH_TOKEN.", file=sys.stderr)
        return 1

    github_links: dict[str, str] = {}
    github_token = ""
    if not args.no_github:
        github_token = discover_github_token()
        github_owner = discover_github_owner()
        if github_token and github_owner:
            repo_name = slug
            print(f"[1/4] Setting up GitHub repo {github_owner}/{repo_name}...")
            if args.dry_run:
                print(f"  Would create/use GitHub repo: {github_owner}/{repo_name}")
                github_links = {"repo_url": f"https://github.com/{github_owner}/{repo_name}",
                                "source_url": f"https://github.com/{github_owner}/{repo_name}",
                                "issues_url": f"https://github.com/{github_owner}/{repo_name}/issues",
                                "wiki_url": f"https://github.com/{github_owner}/{repo_name}/wiki"}
            else:
                github_links = create_github_repo(github_owner, repo_name, ai_meta.get("summary", title), github_token)
                if github_links.get("source_url"):
                    print(f"  GitHub repo: {github_links['source_url']} ({github_links['created']})")
                if source_dir and github_links.get("repo_url"):
                    print(f"  Pushing source code...")
                    push_source_to_github(github_owner, repo_name, source_dir, github_token)
        else:
            print("[1/4] GitHub: skipped (no gh auth or GITHUB_TOKEN)")
    else:
        print("[1/4] GitHub: skipped (--no-github)")
    print()

    icon_path = None
    gallery_path = None
    if not args.no_images:
        print("[2/4] Generating icon and gallery image (HTML+qlmanage)...")
        if args.dry_run:
            print(f"  Would render HTML logo + description via qlmanage")
        else:
            icon_path, gallery_path = generate_visual_assets(bundle_dir, visual_data)
    else:
        print("[2/4] Images: skipped (--no-images)")
    print()

    project_payload = build_modrinth_project_payload(ai_meta, github_links, description, args.project_status)

    if args.dry_run:
        print("[3/4] DRY RUN - Would create/update Modrinth project:")
        print(f"  Title: {project_payload.get('title')}")
        print(f"  Slug: {project_payload.get('slug')}")
        if github_links.get("source_url"):
            print(f"  Source: {github_links['source_url']}")
        if icon_path:
            print(f"  Icon: {icon_path.name}")
        if gallery_path:
            print(f"  Gallery: {gallery_path.name}")
        print()
        print("[4/4] DRY RUN - Would upload version:")
        version_info = ai_meta.get("version", {})
        print(f"  Version: {version_info.get('version_number', '?')}")
        print(f"  Game: {version_info.get('game_versions', [])}")
        print(f"  Loaders: {version_info.get('loaders', [])}")
        return 0

    client = ModrinthClient(token=token, user_agent=build_modrinth_user_agent())

    project_id = ""
    existing_project = False
    try:
        existing = client.resolve_project(slug)
        project_id = existing.get("id", "")
        existing_project = True
        print(f"[3/4] Found existing project: {existing.get('title', '')} (id={project_id})")
    except Exception:
        print(f"[3/4] No existing project for '{slug}'. Will create new.")

    if existing_project:
        try:
            update_modrinth_project(client, slug, project_payload, icon_path, gallery_path)
        except Exception as e:
            print(f"  WARNING: Update failed: {e}")

        if jar_path and not args.update_only:
            version_payload = build_modrinth_version_payload(ai_meta, project_id, args.version_status)
            print()
            print(f"[4/4] Uploading version {version_payload.get('version_number', '?')}...")
            try:
                version_result = create_modrinth_version(client, version_payload, jar_path)
                print(f"  Version uploaded: id={version_result.get('id', '')}")
            except Exception as e:
                print(f"  ERROR: Version upload failed: {e}", file=sys.stderr)
                return 1
        else:
            print(f"[4/4] Version upload: {'skipped (--update-only)' if args.update_only else 'skipped (no JAR)'}")
    else:
        if not jar_path:
            print("ERROR: JAR file required for new project creation.", file=sys.stderr)
            return 1
        version_payload = build_modrinth_version_payload(ai_meta, "", args.version_status)
        print(f"[4/4] Creating new project with version {version_payload.get('version_number', '?')}...")
        try:
            created = create_modrinth_project(client, project_payload, version_payload, jar_path, icon_path)
            project_id = created.get("id", "")
            project_slug = created.get("slug", slug)
            print(f"  Project created: id={project_id} slug={project_slug}")
            version_ids = created.get("versions", [])
            if version_ids:
                print(f"  Version created: id={version_ids[0]}")
            if gallery_path and gallery_path.exists():
                try:
                    gallery_title = f"{title} Cover Image"
                    client.add_gallery_image(project_ref=project_slug, image_path=gallery_path, featured=True,
                                             title=gallery_title, description=f"Cover image for {title}", ordering=0)
                    print(f"  Gallery image uploaded.")

                    gallery_url = resolve_gallery_image_url(client, project_slug, gallery_title, set())
                    if gallery_url:
                        current_body = project_payload.get("body", "")
                        updated_body = inject_gallery_image_into_body(current_body, title, gallery_url)
                        if updated_body != current_body:
                            client.modify_project(project_ref=project_slug, payload={"body": updated_body})
                            print(f"  Injected gallery image into description body")
                except Exception as e:
                    print(f"  WARNING: Gallery upload failed: {e}")
        except Exception as e:
            print(f"ERROR: Project creation failed: {e}", file=sys.stderr)
            return 1

    print()
    print(f"SUCCESS! https://modrinth.com/mod/{slug}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fast Modrinth publishing for AI coding IDEs")
    subparsers = parser.add_subparsers(dest="command")

    pub_parser = subparsers.add_parser("publish", help="Generate assets and publish to Modrinth")
    pub_parser.add_argument("--bundle-dir", required=True)
    pub_parser.add_argument("--modrinth-token", default="")
    pub_parser.add_argument("--project-status", default="listed", choices=["draft", "listed", "unlisted", "private"])
    pub_parser.add_argument("--version-status", default="listed", choices=["listed", "unlisted", "draft"])
    pub_parser.add_argument("--dry-run", action="store_true")
    pub_parser.add_argument("--no-github", action="store_true")
    pub_parser.add_argument("--no-images", action="store_true")
    pub_parser.add_argument("--update-only", action="store_true")

    upd_parser = subparsers.add_parser("update", help="Update existing Modrinth project")
    upd_parser.add_argument("--bundle-dir", required=True)
    upd_parser.add_argument("--modrinth-token", default="")
    upd_parser.add_argument("--project-status", default="listed", choices=["draft", "listed", "unlisted", "private"])
    upd_parser.add_argument("--dry-run", action="store_true")
    upd_parser.add_argument("--no-github", action="store_true")
    upd_parser.add_argument("--no-images", action="store_true")

    args = parser.parse_args(argv)
    if not args.command:
        parser.print_help()
        return 1
    if args.command == "update":
        args.update_only = True
        args.version_status = "listed"
    return cmd_publish(args)


if __name__ == "__main__":
    raise SystemExit(main())
