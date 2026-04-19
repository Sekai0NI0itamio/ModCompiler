#!/usr/bin/env python3
"""
Deep-inspect all versions of the 4 documented projects by downloading each jar
and checking its actual contents (class count, recipe files, metadata files).
Reports true shells (0 classes) vs real jars regardless of file size.
"""
import io
import json
import os
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.modrinth.com/v2"
TOKEN = os.environ.get("MODRINTH_TOKEN", "")

PROJECTS = [
    "set-home-anywhere",
    "sort-chest",
    "common-server-core",
    "craftable-slime-balls",
]

def headers():
    h = {"User-Agent": "ModCompiler/1.0", "Accept": "application/json"}
    if TOKEN:
        h["Authorization"] = TOKEN
    return h

def get(path):
    req = urllib.request.Request(API + path, headers=headers())
    for attempt in range(1, 5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code in {429, 500, 502, 503} and attempt < 4:
                time.sleep(2 ** attempt)
                continue
            raise
        except urllib.error.URLError:
            if attempt < 4:
                time.sleep(2 ** attempt)
                continue
            raise

def download_bytes(url):
    req = urllib.request.Request(url, headers=headers())
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception:
            if attempt < 3:
                time.sleep(2 ** attempt)
    return None

def inspect_jar(data: bytes) -> dict:
    """Inspect a jar's contents and return a summary."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            entries = zf.namelist()
    except Exception as e:
        return {"error": str(e), "classes": 0, "entries": 0}

    classes = [e for e in entries if e.endswith(".class")]
    recipes = [e for e in entries if "/recipes/" in e and e.endswith(".json")]
    has_mods_toml = any("mods.toml" in e for e in entries)
    has_fabric_json = any("fabric.mod.json" in e for e in entries)
    has_neoforge_toml = any("neoforge.mods.toml" in e for e in entries)
    has_mcmod_info = any("mcmod.info" in e for e in entries)
    has_pack_mcmeta = any("pack.mcmeta" in e for e in entries)

    return {
        "classes": len(classes),
        "class_names": classes[:5],  # first 5 for display
        "recipes": len(recipes),
        "recipe_names": recipes[:3],
        "has_mods_toml": has_mods_toml,
        "has_fabric_json": has_fabric_json,
        "has_neoforge_toml": has_neoforge_toml,
        "has_mcmod_info": has_mcmod_info,
        "has_pack_mcmeta": has_pack_mcmeta,
        "total_entries": len(entries),
        "error": None,
    }

def verdict(size: int, info: dict) -> str:
    if info.get("error"):
        return f"ERROR({info['error']})"
    classes = info["classes"]
    if classes == 0:
        return "TRUE_SHELL (0 classes)"
    if classes <= 2 and info["recipes"] > 0:
        return f"RECIPE_MOD ({classes} class, {info['recipes']} recipe)"
    return f"REAL ({classes} classes)"

all_results = {}

for slug in PROJECTS:
    print(f"\n{'='*60}")
    print(f"Project: {slug}")
    print(f"{'='*60}")

    versions = get(f"/project/{slug}/version")
    print(f"Total versions: {len(versions)}")

    true_shells = []
    recipe_mods = []
    real_mods = []
    errors = []

    for v in versions:
        vid = v["id"]
        vnum = v.get("version_number", "?")
        mc = v.get("game_versions", [])
        loaders = v.get("loaders", [])
        files = v.get("files", [])

        primary = next((f for f in files if f.get("primary")), files[0] if files else None)
        if not primary:
            print(f"  [{vid}] {vnum} mc={mc} loader={loaders} — NO FILE")
            errors.append(vid)
            continue

        url = primary["url"]
        fname = primary["filename"]
        size = primary.get("size", 0)

        data = download_bytes(url)
        if data is None:
            print(f"  [{vid}] {vnum} — DOWNLOAD FAILED")
            errors.append(vid)
            continue

        info = inspect_jar(data)
        v_verdict = verdict(size, info)

        # Classify
        if "TRUE_SHELL" in v_verdict:
            true_shells.append({"id": vid, "vnum": vnum, "mc": mc, "loaders": loaders,
                                 "size": size, "verdict": v_verdict, "info": info})
        elif "RECIPE_MOD" in v_verdict:
            recipe_mods.append({"id": vid, "vnum": vnum, "mc": mc, "loaders": loaders,
                                 "size": size, "verdict": v_verdict, "info": info})
        else:
            real_mods.append({"id": vid, "vnum": vnum, "mc": mc, "loaders": loaders,
                               "size": size, "verdict": v_verdict})

        status_icon = "❌" if "TRUE_SHELL" in v_verdict else ("📦" if "RECIPE_MOD" in v_verdict else "✅")
        print(f"  {status_icon} [{vid}] {vnum} mc={mc} loader={loaders} "
              f"size={size:,}B classes={info['classes']} recipes={info['recipes']} → {v_verdict}")

    print(f"\n  Summary for {slug}:")
    print(f"    ✅ Real mods:    {len(real_mods)}")
    print(f"    📦 Recipe mods: {len(recipe_mods)}  (tiny but functional)")
    print(f"    ❌ True shells: {len(true_shells)}  (0 classes — actually broken)")
    if errors:
        print(f"    ⚠️  Errors:      {len(errors)}")

    if true_shells:
        print(f"\n  TRUE SHELLS (need fixing):")
        for s in true_shells:
            print(f"    [{s['id']}] {s['vnum']} mc={s['mc']} loader={s['loaders']} size={s['size']:,}B")

    all_results[slug] = {
        "real": len(real_mods),
        "recipe_mods": len(recipe_mods),
        "true_shells": len(true_shells),
        "true_shell_ids": [s["id"] for s in true_shells],
        "true_shell_details": true_shells,
    }

print(f"\n{'='*60}")
print("FINAL SUMMARY ACROSS ALL PROJECTS")
print(f"{'='*60}")
for slug, r in all_results.items():
    total = r["real"] + r["recipe_mods"] + r["true_shells"]
    print(f"\n{slug} ({total} versions):")
    print(f"  ✅ Real:         {r['real']}")
    print(f"  📦 Recipe mods: {r['recipe_mods']}")
    print(f"  ❌ True shells: {r['true_shells']}")
    if r["true_shells"]:
        print(f"  Shell IDs: {r['true_shell_ids']}")
