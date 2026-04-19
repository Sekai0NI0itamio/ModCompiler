#!/usr/bin/env python3
"""Inspect sort-chest (remaining) + common-server-core + craftable-slime-balls."""
import io, json, os, time, urllib.error, urllib.request, zipfile

API = "https://api.modrinth.com/v2"
TOKEN = os.environ.get("MODRINTH_TOKEN", "")

PROJECTS = ["sort-chest", "common-server-core", "craftable-slime-balls"]

# For sort-chest, skip the 17 already confirmed real above
SORT_CHEST_DONE = {
    "7Hpf8Vbe","kdhy064z","kQhXjjxF","RFN1BwHG","S1Iebg4Y","EHWSkrb1",
    "gL5h1kE8","hQpogRkl","atKBkyOL","aN7iAV0I","RhcJYc5V","akBQ32Nl",
    "JqfRHmbr","CJ998ZkB","s1f78wHi","896TSqr3","7lpx1s8s",
}

def headers():
    h = {"User-Agent": "ModCompiler/1.0", "Accept": "application/json"}
    if TOKEN: h["Authorization"] = TOKEN
    return h

def get(path):
    req = urllib.request.Request(API + path, headers=headers())
    for attempt in range(1, 5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code in {429,500,502,503} and attempt < 4:
                time.sleep(2**attempt); continue
            raise
        except urllib.error.URLError:
            if attempt < 4: time.sleep(2**attempt); continue
            raise

def download_bytes(url):
    req = urllib.request.Request(url, headers=headers())
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception:
            if attempt < 3: time.sleep(2**attempt)
    return None

def inspect_jar(data):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            entries = zf.namelist()
    except Exception as e:
        return {"error": str(e), "classes": 0, "recipes": 0}
    classes = [e for e in entries if e.endswith(".class")]
    recipes = [e for e in entries if "/recipes/" in e and e.endswith(".json")]
    return {"classes": len(classes), "recipes": len(recipes), "error": None}

all_results = {}

for slug in PROJECTS:
    print(f"\n{'='*55}")
    print(f"Project: {slug}")
    print(f"{'='*55}")
    versions = get(f"/project/{slug}/version")
    print(f"Total versions: {len(versions)}")

    true_shells, recipe_mods, real_mods = [], [], []

    for v in versions:
        vid = v["id"]
        if slug == "sort-chest" and vid in SORT_CHEST_DONE:
            real_mods.append(vid)  # already confirmed real
            continue

        vnum = v.get("version_number","?")
        mc = v.get("game_versions",[])
        loaders = v.get("loaders",[])
        files = v.get("files",[])
        primary = next((f for f in files if f.get("primary")), files[0] if files else None)
        if not primary:
            print(f"  [NO FILE] [{vid}]")
            continue

        size = primary.get("size", 0)
        data = download_bytes(primary["url"])
        if data is None:
            print(f"  [DL FAIL] [{vid}]")
            continue

        info = inspect_jar(data)
        classes = info["classes"]
        recipes = info["recipes"]

        if classes == 0:
            verdict = "❌ TRUE_SHELL"
            true_shells.append({"id": vid, "vnum": vnum, "mc": mc,
                                 "loaders": loaders, "size": size})
        elif classes <= 2 and recipes > 0:
            verdict = "📦 RECIPE_MOD"
            recipe_mods.append(vid)
        else:
            verdict = "✅ REAL"
            real_mods.append(vid)

        print(f"  {verdict} [{vid}] {vnum} mc={mc} loader={loaders} "
              f"size={size:,}B classes={classes} recipes={recipes}")

    print(f"\n  Summary for {slug}:")
    print(f"    ✅ Real:         {len(real_mods)}")
    print(f"    📦 Recipe mods: {len(recipe_mods)}")
    print(f"    ❌ True shells: {len(true_shells)}")
    if true_shells:
        print(f"  TRUE SHELLS:")
        for s in true_shells:
            print(f"    [{s['id']}] {s['vnum']} mc={s['mc']} loader={s['loaders']} size={s['size']:,}B")
    all_results[slug] = {"real": len(real_mods), "recipe": len(recipe_mods), "shells": len(true_shells), "shell_list": true_shells}

print(f"\n{'='*55}")
print("FINAL SUMMARY")
print(f"{'='*55}")
for slug, r in all_results.items():
    print(f"{slug}: ✅{r['real']} real  📦{r['recipe']} recipe  ❌{r['shells']} shells")
    if r["shell_list"]:
        for s in r["shell_list"]:
            print(f"  SHELL: [{s['id']}] {s['vnum']} {s['loaders']} {s['mc']}")
