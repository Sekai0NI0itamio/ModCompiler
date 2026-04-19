#!/usr/bin/env python3
"""Download and decompile the real account-switcher jars to read the source."""
import io, json, os, time, urllib.request, zipfile
from pathlib import Path

API = "https://api.modrinth.com/v2"
TOKEN = os.environ.get("MODRINTH_TOKEN", "")

def headers():
    h = {"User-Agent": "ModCompiler/1.0", "Accept": "application/json"}
    if TOKEN: h["Authorization"] = TOKEN
    return h

def download(url, dest):
    req = urllib.request.Request(url, headers=headers())
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    Path(dest).parent.mkdir(parents=True, exist_ok=True)
    Path(dest).write_bytes(data)
    return data

# Download the two key real jars
targets = [
    ("UeiCtbGY", "account-switcher-2.0.0-fabric-1.21.jar"),   # Fabric 2.0.0 — best source
    ("w16LaP5n", "account-switcher-2.0.0-forge-1.8.9.jar"),   # Forge 2.0.0 1.8.9
]

out = Path("/tmp/account-switcher-jars")
out.mkdir(exist_ok=True)

for vid, fname in targets:
    v = json.loads(urllib.request.urlopen(
        urllib.request.Request(f"{API}/version/{vid}", headers=headers()), timeout=30
    ).read())
    files = v.get("files", [])
    primary = next((f for f in files if f.get("primary")), files[0])
    data = download(primary["url"], out / fname)
    print(f"Downloaded {fname} ({len(data):,}B)")

    # Extract and print all class files
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        print(f"  Contents of {fname}:")
        for entry in zf.namelist():
            print(f"    {entry}")
        print()
