#!/usr/bin/env python3
"""
Generates the Common Server Core bundle — all 59 targets (56 shells + 3 missing).
Source of truth: decompiled from Modrinth versions.
Run: python3 scripts/generate_servercore_bundle.py [--failed-only]
"""
import argparse, json, shutil, subprocess, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "common-server-core-all-versions"

MOD_ID      = "servercore"
MOD_NAME    = "ServerCore"
MOD_VERSION = "1.0.0"
GROUP_FORGE  = "com.itamio.servercore.forge"
GROUP_FABRIC = "com.itamio.servercore.fabric"
DESCRIPTION = "Teleport requests, homes, random teleport, and first-join teleport without requiring cheats."
AUTHORS     = "Itamio"
LICENSE     = "All Rights Reserved"
HOMEPAGE    = "https://modrinth.com/mod/common-server-core"

ENTRYPOINT_FORGE_1122  = "asd.itamio.servercore.ServerCoreMod"
ENTRYPOINT_FORGE_MOD   = f"{GROUP_FORGE}.ServerCoreForgeMod"
ENTRYPOINT_FABRIC_MOD  = f"{GROUP_FABRIC}.ServerCoreFabricMod"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(entrypoint: str, group: str, runtime_side: str = "server") -> str:
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={group}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side={runtime_side}\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ============================================================
# SOURCE FILES — copied from decompiled bundle
# The modern source (1.16.5+) uses reflection for all API
# differences, so the same source compiles across all versions.
# ============================================================

BUNDLE_SRC = Path("/tmp/common-server-core-bundle")

def read_src(version_id: str, filename: str, subpkg: str) -> str:
    """Read a decompiled source file from the bundle."""
    base = BUNDLE_SRC / "versions" / version_id / "decompiled" / "src" / "src" / "main" / "java"
    # Try forge path first, then fabric
    for pkg in [f"com/itamio/servercore/{subpkg}", f"asd/itamio/servercore"]:
        p = base / pkg / filename
        if p.exists():
            return p.read_text(encoding="utf-8")
    raise FileNotFoundError(f"Cannot find {filename} in {version_id}")

# Reference version IDs from the bundle
REF_1122_FORGE  = "SBNCQth7"   # 1.12.2 Forge
REF_MODERN_FORGE = "jUgSQFCi"  # 1.21 Forge (reflection-based, works 1.16.5+)
REF_MODERN_FABRIC = "b4HIDtey" # 1.21 Fabric

# Modern Forge files (same for all 1.16.5+ Forge versions)
FORGE_FILES = [
    "HomeRecord.java",
    "HomeService.java",
    "MessageUtil.java",
    "RandomTeleportService.java",
    "ServerCoreAccess.java",
    "ServerCoreCommands.java",
    "ServerCoreData.java",
    "ServerCoreForgeMod.java",
    "TeleportRequestService.java",
    "TeleportUtil.java",
]

# Modern Fabric files (same for all 1.16.5+ Fabric versions)
FABRIC_FILES = [
    "HomeRecord.java",
    "HomeService.java",
    "MessageUtil.java",
    "RandomTeleportService.java",
    "ServerCoreAccess.java",
    "ServerCoreCommands.java",
    "ServerCoreData.java",
    "ServerCoreFabricMod.java",
    "TeleportRequestService.java",
    "TeleportUtil.java",
]

# 1.12.2 Forge files (old-style, different package)
FILES_1122 = [
    ("ServerCoreMod.java",                  ""),
    ("command/CommandDelHome.java",         "command"),
    ("command/CommandHome.java",            "command"),
    ("command/CommandRtp.java",             "command"),
    ("command/CommandSetHome.java",         "command"),
    ("command/CommandTpa.java",             "command"),
    ("command/CommandTpacancel.java",       "command"),
    ("command/CommandTpaccept.java",        "command"),
    ("command/CommandTpacceptAll.java",     "command"),
    ("command/CommandTpadeny.java",         "command"),
    ("command/CommandTpadenyAll.java",      "command"),
    ("command/CommandTpahere.java",         "command"),
    ("command/ServerCoreCommandBase.java",  "command"),
    ("command/TeleportRequestCommandHelper.java", "command"),
    ("config/ServerCoreConfig.java",        "config"),
    ("data/HomeRecord.java",                "data"),
    ("data/ServerCoreHomesData.java",       "data"),
    ("event/ServerCoreEvents.java",         "event"),
    ("service/HomeService.java",            "service"),
    ("service/RandomTeleportService.java",  "service"),
    ("service/TeleportRequestService.java", "service"),
    ("teleport/FixedPositionTeleporter.java", "teleport"),
    ("util/TeleportUtil.java",              "util"),
]

PKG_FORGE_MODERN  = "com/itamio/servercore/forge"
PKG_FABRIC_MODERN = "com/itamio/servercore/fabric"
PKG_1122          = "asd/itamio/servercore"

JAVA_MAIN_FORGE_MODERN  = f"src/main/java/{PKG_FORGE_MODERN}/ServerCoreForgeMod.java"
JAVA_MAIN_FABRIC_MODERN = f"src/main/java/{PKG_FABRIC_MODERN}/ServerCoreFabricMod.java"
JAVA_MAIN_1122          = f"src/main/java/{PKG_1122}/ServerCoreMod.java"

def write_modern_forge(base: Path):
    """Write all modern Forge source files."""
    for fname in FORGE_FILES:
        src = read_src(REF_MODERN_FORGE, fname, "forge")
        write(base / "src" / "main" / "java" / PKG_FORGE_MODERN / fname, src)

def write_modern_fabric(base: Path):
    """Write all modern Fabric source files."""
    for fname in FABRIC_FILES:
        src = read_src(REF_MODERN_FABRIC, fname, "fabric")
        write(base / "src" / "main" / "java" / PKG_FABRIC_MODERN / fname, src)

def write_1122_forge(base: Path):
    """Write all 1.12.2 Forge source files."""
    for rel_path, subpkg in FILES_1122:
        fname = Path(rel_path).name
        src_base = BUNDLE_SRC / "versions" / REF_1122_FORGE / "decompiled" / "src" / "src" / "main" / "java" / "asd" / "itamio" / "servercore"
        if subpkg:
            src_file = src_base / subpkg / fname
        else:
            src_file = src_base / fname
        if src_file.exists():
            write(base / "src" / "main" / "java" / PKG_1122 / rel_path, src_file.read_text(encoding="utf-8"))


# ============================================================
# TARGETS
# Format: (folder_name, mc_version, loader, write_fn, mod_txt_str)
# ============================================================

def make_targets():
    targets = []

    def add_forge(folder, mc, write_fn=write_modern_forge):
        targets.append((folder, mc, "forge", write_fn,
                         mod_txt(ENTRYPOINT_FORGE_MOD, GROUP_FORGE)))

    def add_fabric(folder, mc):
        targets.append((folder, mc, "fabric", write_modern_fabric,
                         mod_txt(ENTRYPOINT_FABRIC_MOD, GROUP_FABRIC)))

    # 1.12.2 Forge (old-style source)
    targets.append(("SC1122Forge", "1.12.2", "forge", write_1122_forge,
                     mod_txt(ENTRYPOINT_FORGE_1122, "asd.itamio.servercore")))

    # 1.16.5
    add_forge("SC1165Forge",   "1.16.5")
    add_fabric("SC1165Fabric", "1.16.5")

    # 1.17.1
    add_forge("SC1171Forge",   "1.17.1")
    add_fabric("SC1171Fabric", "1.17.1")

    # 1.18.x
    add_forge("SC118Forge",    "1.18")
    add_fabric("SC118Fabric",  "1.18")
    add_forge("SC1181Forge",   "1.18.1")
    add_fabric("SC1181Fabric", "1.18.1")
    add_forge("SC1182Forge",   "1.18.2")
    add_fabric("SC1182Fabric", "1.18.2")

    # 1.19.x
    add_forge("SC119Forge",    "1.19")
    add_fabric("SC119Fabric",  "1.19")
    add_forge("SC1191Forge",   "1.19.1")
    add_fabric("SC1191Fabric", "1.19.1")
    add_forge("SC1192Forge",   "1.19.2")
    add_fabric("SC1192Fabric", "1.19.2")
    add_forge("SC1193Forge",   "1.19.3")
    add_fabric("SC1193Fabric", "1.19.3")
    add_forge("SC1194Forge",   "1.19.4")
    add_fabric("SC1194Fabric", "1.19.4")

    # 1.20.x
    add_forge("SC1201Forge",   "1.20.1")
    add_fabric("SC1201Fabric", "1.20.1")
    add_forge("SC1202Forge",   "1.20.2")
    add_fabric("SC1202Fabric", "1.20.2")
    add_forge("SC1203Forge",   "1.20.3")
    add_fabric("SC1203Fabric", "1.20.3")
    add_forge("SC1204Forge",   "1.20.4")
    add_fabric("SC1204Fabric", "1.20.4")
    # SC1205Forge skipped — 1.20.5 Forge not in repo manifest
    add_fabric("SC1205Fabric", "1.20.5")
    add_forge("SC1206Forge",   "1.20.6")
    add_fabric("SC1206Fabric", "1.20.6")

    # 1.21.x
    add_forge("SC121Forge",    "1.21")
    add_fabric("SC121Fabric",  "1.21")
    add_forge("SC1211Forge",   "1.21.1")
    add_fabric("SC1211Fabric", "1.21.1")
    # SC1212Forge skipped — 1.21.2 Forge not in repo manifest
    add_fabric("SC1212Fabric", "1.21.2")
    add_forge("SC1213Forge",   "1.21.3")
    add_fabric("SC1213Fabric", "1.21.3")
    add_forge("SC1214Forge",   "1.21.4")
    add_fabric("SC1214Fabric", "1.21.4")
    add_forge("SC1215Forge",   "1.21.5")
    add_fabric("SC1215Fabric", "1.21.5")
    add_forge("SC1216Forge",   "1.21.6")
    add_fabric("SC1216Fabric", "1.21.6")
    add_forge("SC1217Forge",   "1.21.7")
    add_fabric("SC1217Fabric", "1.21.7")
    add_forge("SC1218Forge",   "1.21.8")
    add_fabric("SC1218Fabric", "1.21.8")
    add_forge("SC1219Forge",   "1.21.9")
    add_fabric("SC1219Fabric", "1.21.9")
    add_forge("SC12110Forge",  "1.21.10")
    add_fabric("SC12110Fabric","1.21.10")
    add_forge("SC12111Forge",  "1.21.11")
    add_fabric("SC12111Fabric","1.21.11")

    # 1.8.9 Forge — MISSING. The mod uses 1.12.2 old-style API.
    # 1.8.9 uses different command/event APIs so skip for now.
    # (CommandBase.getCommandName vs getName, different NBT API)
    # TODO: add 1.8.9 support in a future run

    return targets

targets = make_targets()


# ============================================================
# FAILED-ONLY MODE
# ============================================================
import re as _re
_ap = argparse.ArgumentParser()
_ap.add_argument("--failed-only", action="store_true")
_ap.add_argument("--run-dir", default="")
_parsed = _ap.parse_args()

active_targets = targets
if _parsed.failed_only:
    runs_root = ROOT / "ModCompileRuns"
    run_dir = Path(_parsed.run_dir) if _parsed.run_dir else (
        sorted(runs_root.iterdir())[-1] if runs_root.exists() and any(runs_root.iterdir()) else None
    )
    if run_dir is None:
        print("WARNING: no run dir found, using all targets.")
    else:
        art = run_dir / "artifacts" / "all-mod-builds" / "mods"
        if not art.exists():
            print(f"WARNING: no artifact at {art}, using all targets.")
        else:
            failed_slugs = set()
            for mod_dir in art.iterdir():
                if not mod_dir.is_dir(): continue
                rf = mod_dir / "result.json"
                if rf.exists():
                    try:
                        if json.loads(rf.read_text()).get("status") != "success":
                            failed_slugs.add(mod_dir.name)
                    except: failed_slugs.add(mod_dir.name)
                else:
                    failed_slugs.add(mod_dir.name)

            def slug_matches(folder, slug):
                return folder.lower() in slug.lower() or slug.lower() in folder.lower()

            failed_folders = {t[0] for t in targets if any(slug_matches(t[0], s) for s in failed_slugs)}
            if failed_folders:
                active_targets = [t for t in targets if t[0] in failed_folders]
                print(f"Failed-only: {len(active_targets)} targets (skipping {len(targets)-len(active_targets)} green)")
                for t in active_targets: print(f"  -> {t[0]}")
            else:
                print("No failed targets — all green!")
                active_targets = []

# ============================================================
# GENERATE
# ============================================================
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

for (folder, mc_ver, loader, write_fn, mod_txt_str) in active_targets:
    base = BUNDLE / folder
    write(base / "mod.txt", mod_txt_str)
    write(base / "version.txt", version_txt(mc_ver, loader))
    write_fn(base)

print(f"Generated {len(active_targets)} targets")

zip_path = ROOT / "incoming" / "common-server-core-all-versions.zip"
if active_targets:
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(BUNDLE.rglob("*")):
            if not path.is_file(): continue
            rel = path.relative_to(BUNDLE)
            if len(rel.parts) < 2: continue
            zf.write(path, rel)
    print(f"Zip: {zip_path}")

    r = subprocess.run(
        ["python3", "build_mods.py", "prepare",
         "--zip-path", str(zip_path),
         "--manifest", "version-manifest.json",
         "--output-dir", "/tmp/prepare-servercore"],
        capture_output=True, text=True, cwd=str(ROOT)
    )
    if r.returncode == 0:
        matrix = json.loads(r.stdout)
        print(f"Prepare OK — {len(matrix.get('include',[]))} build targets")
    else:
        print(f"Prepare FAILED:\n{r.stderr[:500]}")
else:
    print("Nothing to build.")
