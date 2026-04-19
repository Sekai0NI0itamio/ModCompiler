#!/usr/bin/env python3
"""
Generates the Common Server Core bundle.
Uses the exact decompiled source from each Modrinth version as the source of truth.
Run: python3 scripts/generate_servercore_bundle.py [--failed-only]
"""
import argparse, json, shutil, subprocess, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "common-server-core-all-versions"
BUNDLE_SRC = Path("/tmp/common-server-core-bundle")

MOD_ID      = "servercore"
MOD_NAME    = "ServerCore"
MOD_VERSION = "1.0.0"
DESCRIPTION = "Teleport requests, homes, random teleport, and first-join teleport without requiring cheats."
AUTHORS     = "Itamio"
LICENSE     = "All Rights Reserved"
HOMEPAGE    = "https://modrinth.com/mod/common-server-core"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"

def copy_src_from_bundle(version_id: str, dest_base: Path):
    """Copy all decompiled source files from the bundle version into dest_base/src/."""
    src_root = BUNDLE_SRC / "versions" / version_id / "decompiled" / "src" / "src" / "main" / "java"
    if not src_root.exists():
        raise FileNotFoundError(f"No source for version {version_id} at {src_root}")
    dest_java = dest_base / "src" / "main" / "java"
    if dest_java.exists():
        shutil.rmtree(dest_java)
    shutil.copytree(src_root, dest_java)

def get_mod_txt(version_id: str, loader: str) -> str:
    """Build mod.txt from the bundle's mod_info.txt."""
    info_file = BUNDLE_SRC / "versions" / version_id / "decompiled" / "mod_info.txt"
    info = {}
    if info_file.exists():
        for line in info_file.read_text().splitlines():
            if "=" in line:
                k, _, v = line.partition("=")
                info[k.strip()] = v.strip()

    # Determine group and entrypoint from the source package
    src_root = BUNDLE_SRC / "versions" / version_id / "decompiled" / "src" / "src" / "main" / "java"
    group = ""
    entrypoint = ""
    if src_root.exists():
        # Find the main mod class
        for java_file in src_root.rglob("*.java"):
            name = java_file.stem
            if "Mod" in name and ("Forge" in name or "Fabric" in name or name == "ServerCoreMod"):
                # Build the fully qualified class name
                rel = java_file.relative_to(src_root)
                fqn = str(rel).replace("/", ".").replace("\\", ".").removesuffix(".java")
                entrypoint = fqn
                group = ".".join(fqn.split(".")[:-1])
                break

    if not entrypoint:
        # Fallback
        if loader == "fabric":
            entrypoint = "com.itamio.servercore.fabric.ServerCoreFabricMod"
            group = "com.itamio.servercore.fabric"
        else:
            entrypoint = "com.itamio.servercore.forge.ServerCoreForgeMod"
            group = "com.itamio.servercore.forge"

    runtime_side = "server"
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={group}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side={runtime_side}\n"
    )

# ============================================================
# TARGETS: (folder, mc_version, loader, bundle_version_id)
# Each target uses the exact source from the matching bundle version.
# For versions not in the bundle, use the closest available version.
# ============================================================
targets = [
    # 1.12.2 Forge
    ("SC1122Forge",   "1.12.2", "forge",  "SBNCQth7"),

    # 1.16.5
    ("SC1165Forge",   "1.16.5", "forge",  "fwcQmXLS"),
    ("SC1165Fabric",  "1.16.5", "fabric", "NH7zywcl"),

    # 1.17.1
    ("SC1171Forge",   "1.17.1", "forge",  "D4zAnFX7"),
    ("SC1171Fabric",  "1.17.1", "fabric", "yiQSTnK0"),

    # 1.18.x
    ("SC118Forge",    "1.18",   "forge",  "dTzvws1Q"),
    ("SC118Fabric",   "1.18",   "fabric", "jDZYwa3I"),
    ("SC1181Forge",   "1.18.1", "forge",  "Ha1f82Dk"),
    ("SC1181Fabric",  "1.18.1", "fabric", "MwBQN4AQ"),
    ("SC1182Forge",   "1.18.2", "forge",  "VJ47WzKo"),
    ("SC1182Fabric",  "1.18.2", "fabric", "z2ST5IfW"),

    # 1.19.x
    ("SC119Forge",    "1.19",   "forge",  "SahI4RcB"),
    ("SC119Fabric",   "1.19",   "fabric", "nO9z3Ups"),
    ("SC1191Forge",   "1.19.1", "forge",  "w4y7fiBu"),
    ("SC1191Fabric",  "1.19.1", "fabric", "n10KaVaa"),
    ("SC1192Forge",   "1.19.2", "forge",  "Qmd91R58"),
    ("SC1192Fabric",  "1.19.2", "fabric", "s0jBqTcE"),
    ("SC1193Forge",   "1.19.3", "forge",  "dfvB3jPO"),
    ("SC1193Fabric",  "1.19.3", "fabric", "Z1gwfWzg"),
    ("SC1194Forge",   "1.19.4", "forge",  "VtlCqnae"),
    ("SC1194Fabric",  "1.19.4", "fabric", "jAT1kE4t"),

    # 1.20.x
    ("SC1201Forge",   "1.20.1", "forge",  "HVPtsILc"),
    ("SC1201Fabric",  "1.20.1", "fabric", "byRUAkdt"),
    ("SC1202Forge",   "1.20.2", "forge",  "JEfQZXRt"),
    ("SC1202Fabric",  "1.20.2", "fabric", "ggGELWan"),
    ("SC1203Forge",   "1.20.3", "forge",  "lK7OEg1u"),
    ("SC1203Fabric",  "1.20.3", "fabric", "8DTzGaJp"),
    ("SC1204Forge",   "1.20.4", "forge",  "XjmRUUuK"),
    ("SC1204Fabric",  "1.20.4", "fabric", "LWFeIlrv"),
    # 1.20.5 Forge not in manifest — skip
    ("SC1205Fabric",  "1.20.5", "fabric", "LUnZ4Y8r"),
    ("SC1206Forge",   "1.20.6", "forge",  "UQrHjVMA"),
    ("SC1206Fabric",  "1.20.6", "fabric", "WRuOzUG5"),

    # 1.21.x
    ("SC121Forge",    "1.21",   "forge",  "jUgSQFCi"),
    ("SC121Fabric",   "1.21",   "fabric", "b4HIDtey"),
    ("SC1211Forge",   "1.21.1", "forge",  "vV4r8jmb"),
    ("SC1211Fabric",  "1.21.1", "fabric", "s3yogR88"),
    # 1.21.2 Forge not in manifest — skip
    ("SC1212Fabric",  "1.21.2", "fabric", "RWUb5mgx"),
    ("SC1213Forge",   "1.21.3", "forge",  "zZwSbodr"),
    ("SC1213Fabric",  "1.21.3", "fabric", "YAcjTnXt"),
    ("SC1214Forge",   "1.21.4", "forge",  "CumKxfhj"),
    ("SC1214Fabric",  "1.21.4", "fabric", "frRQkMi4"),
    ("SC1215Forge",   "1.21.5", "forge",  "on4r1MJ0"),
    ("SC1215Fabric",  "1.21.5", "fabric", "NKQTKiFC"),
    ("SC1216Forge",   "1.21.6", "forge",  "y1YjcAyb"),
    ("SC1216Fabric",  "1.21.6", "fabric", "g5nCrPdK"),
    ("SC1217Forge",   "1.21.7", "forge",  "hq9wnJIZ"),
    ("SC1217Fabric",  "1.21.7", "fabric", "chypEGvk"),
    ("SC1218Forge",   "1.21.8", "forge",  "anM6enrG"),
    ("SC1218Fabric",  "1.21.8", "fabric", "sFwG5InH"),
    ("SC1219Forge",   "1.21.9", "forge",  "hsUBNvfV"),
    ("SC1219Fabric",  "1.21.9", "fabric", "aP3bh6bW"),
    ("SC12110Forge",  "1.21.10","forge",  "QPUD7gqL"),
    ("SC12110Fabric", "1.21.10","fabric", "qHNYyGLa"),
    ("SC12111Forge",  "1.21.11","forge",  "CqurFhjF"),
    ("SC12111Fabric", "1.21.11","fabric", "xyHAR2WW"),
]

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

errors = []
for (folder, mc_ver, loader, bundle_vid) in active_targets:
    base = BUNDLE / folder
    try:
        write(base / "mod.txt", get_mod_txt(bundle_vid, loader))
        write(base / "version.txt", version_txt(mc_ver, loader))
        copy_src_from_bundle(bundle_vid, base)
    except Exception as e:
        errors.append(f"{folder}: {e}")
        print(f"ERROR {folder}: {e}")

if errors:
    print(f"\n{len(errors)} errors during generation")
else:
    print(f"Generated {len(active_targets)} targets")

zip_path = ROOT / "incoming" / "common-server-core-all-versions.zip"
if active_targets and not errors:
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
    print("Nothing to build or errors occurred.")
