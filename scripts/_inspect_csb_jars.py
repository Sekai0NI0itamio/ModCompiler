#!/usr/bin/env python3
"""Download and inspect a few craftable-slime-balls Forge/NeoForge jars to check if they're real."""
import json
import os
import urllib.request
import zipfile
import io

headers = {"User-Agent": "ModCompiler/1.0", "Accept": "application/json"}
token = os.environ.get("MODRINTH_TOKEN", "")
if token:
    headers["Authorization"] = token

# Pick a few representative version IDs to inspect
# Forge 1.12.2 (3724B), Forge 1.16.5 (2781B), Forge 1.21 (2371B), NeoForge 1.21 (2395B)
sample_ids = ["y9o6k90Q", "xmEEP4cS", "N70TyjU8", "kXuj21qw"]

for vid in sample_ids:
    req = urllib.request.Request(
        f"https://api.modrinth.com/v2/version/{vid}",
        headers=headers
    )
    with urllib.request.urlopen(req) as r:
        v = json.loads(r.read())

    files = v.get("files", [])
    primary = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not primary:
        print(f"[{vid}] no file")
        continue

    url = primary["url"]
    fname = primary["filename"]
    size = primary.get("size", 0)
    mc = v.get("game_versions", [])
    loader = v.get("loaders", [])
    vnum = v.get("version_number", "")

    print(f"\n[{vid}] {vnum} mc={mc} loader={loader} size={size:,}B  file={fname}")

    # Download and inspect zip contents
    req2 = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req2) as r:
        data = r.read()

    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            entries = zf.namelist()
            classes = [e for e in entries if e.endswith(".class")]
            jsons = [e for e in entries if e.endswith(".json")]
            print(f"  Total entries: {len(entries)}")
            print(f"  .class files ({len(classes)}): {classes[:10]}")
            print(f"  .json files ({len(jsons)}): {jsons[:10]}")
            print(f"  All entries: {entries}")
    except Exception as e:
        print(f"  Failed to open as zip: {e}")
