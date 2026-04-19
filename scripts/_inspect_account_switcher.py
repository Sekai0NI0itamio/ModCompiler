#!/usr/bin/env python3
"""
Deep-inspect all account-switcher versions.
Downloads every jar, checks class count, and for real jars lists all class names
so we can understand the mod structure for porting.
"""
import io, json, os, time, urllib.error, urllib.request, zipfile

API = "https://api.modrinth.com/v2"
TOKEN = os.environ.get("MODRINTH_TOKEN", "")

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
        return {"error": str(e), "classes": [], "all_entries": []}
    classes = [e for e in entries if e.endswith(".class")]
    return {"error": None, "classes": classes, "all_entries": entries}

versions = get("/project/account-switcher/version")
print(f"Total versions: {len(versions)}\n")

true_shells = []
real_versions = []

for v in sorted(versions, key=lambda x: x.get("date_published","")):
    vid = v["id"]
    vnum = v.get("version_number","?")
    mc = v.get("game_versions",[])
    loaders = v.get("loaders",[])
    files = v.get("files",[])
    primary = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not primary:
        print(f"  [NO FILE] [{vid}] {vnum}")
        continue

    size = primary.get("size", 0)
    data = download_bytes(primary["url"])
    if data is None:
        print(f"  [DL FAIL] [{vid}] {vnum}")
        continue

    info = inspect_jar(data)
    classes = info["classes"]
    n_classes = len(classes)

    if n_classes == 0:
        status = "❌ TRUE_SHELL"
        true_shells.append({"id": vid, "vnum": vnum, "mc": mc, "loaders": loaders, "size": size})
    else:
        status = "✅ REAL"
        real_versions.append({"id": vid, "vnum": vnum, "mc": mc, "loaders": loaders,
                               "size": size, "classes": classes})

    print(f"{status} [{vid}] {vnum} mc={mc} loader={loaders} size={size:,}B classes={n_classes}")
    if n_classes > 0:
        for c in classes:
            print(f"    {c}")
    print()

print(f"\n{'='*60}")
print(f"SUMMARY")
print(f"{'='*60}")
print(f"✅ Real versions: {len(real_versions)}")
print(f"❌ True shells:   {len(true_shells)}")

if true_shells:
    print(f"\nShells to fix ({len(true_shells)}):")
    for s in true_shells:
        print(f"  [{s['id']}] {s['vnum']} mc={s['mc']} loader={s['loaders']} size={s['size']:,}B")

print(f"\nReal versions (source of truth):")
for r in real_versions:
    print(f"  [{r['id']}] {r['vnum']} mc={r['mc']} loader={r['loaders']} size={r['size']:,}B")
    print(f"    Classes: {r['classes']}")
