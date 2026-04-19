#!/usr/bin/env python3
"""
Generates the Common Server Core bundle.

Strategy:
- Forge 1.16.5+: Use the 1.21 Forge source directly (reflection-based, works all versions)
- Fabric 1.16.5+: Use shared logic from 1.21 Forge source + clean Fabric entrypoint
- Forge 1.12.2: Write from scratch with correct MCP names

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

def mod_txt(group: str, entrypoint: str) -> str:
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={group}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )

# ============================================================
# SOURCE: 1.21 Forge (reflection-based, works all versions)
# ============================================================
REF_FORGE = "jUgSQFCi"  # 1.21 Forge

FORGE_PKG  = "com/itamio/servercore/forge"
FABRIC_PKG = "com/itamio/servercore/fabric"

FORGE_SHARED_FILES = [
    "HomeRecord.java",
    "HomeService.java",
    "MessageUtil.java",
    "RandomTeleportService.java",
    "ServerCoreAccess.java",
    "ServerCoreCommands.java",
    "ServerCoreData.java",
    "TeleportRequestService.java",
    "TeleportUtil.java",
]

def read_forge_file(filename: str) -> str:
    p = BUNDLE_SRC / "versions" / REF_FORGE / "decompiled" / "src" / "src" / "main" / "java" / FORGE_PKG / filename
    return p.read_text(encoding="utf-8")

def write_forge_src(base: Path):
    """Write all Forge source files using the 1.21 reflection-based source."""
    for fname in FORGE_SHARED_FILES:
        write(base / "src" / "main" / "java" / FORGE_PKG / fname, read_forge_file(fname))
    # Main mod class
    write(base / "src" / "main" / "java" / FORGE_PKG / "ServerCoreForgeMod.java",
          read_forge_file("ServerCoreForgeMod.java"))

def write_fabric_src(base: Path):
    """Write Fabric source: shared logic from Forge (renamed to fabric package) + clean entrypoint."""
    # Copy shared logic files, renaming package from forge to fabric
    for fname in FORGE_SHARED_FILES:
        content = read_forge_file(fname)
        # Rename package declaration
        content = content.replace(
            "package com.itamio.servercore.forge;",
            "package com.itamio.servercore.fabric;"
        )
        # Rename imports of other forge classes to fabric
        content = content.replace(
            "import com.itamio.servercore.forge.",
            "import com.itamio.servercore.fabric."
        )
        write(base / "src" / "main" / "java" / FABRIC_PKG / fname, content)

    # Write clean Fabric entrypoint using official Mojang names
    fabric_mod = """\
package com.itamio.servercore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ServerCoreFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                ServerCoreCommands.register(dispatcher)
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerCoreData data = ServerCoreData.get(server);
            if (!data.hasSeen(player.getUUID())) {
                data.markSeen(player.getUUID());
                RandomTeleportService.RtpResult result =
                    RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
                if (!result.isSuccess()) {
                    MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
                }
            }
        });
    }
}
"""
    write(base / "src" / "main" / "java" / FABRIC_PKG / "ServerCoreFabricMod.java", fabric_mod)

# ============================================================
# 1.12.2 Forge — written from scratch with correct MCP names
# The decompiled source uses SRG names; we need MCP names.
# ============================================================
SRC_1122_MAIN = """\
package asd.itamio.servercore;

import asd.itamio.servercore.command.*;
import asd.itamio.servercore.config.ServerCoreConfig;
import asd.itamio.servercore.event.ServerCoreEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "servercore", name = "ServerCore", version = "1.0.0",
     acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12,1.12.2]")
public class ServerCoreMod {
    public static final String MOD_ID = "servercore";
    public static final Logger LOGGER = LogManager.getLogger("ServerCore");
    @Instance("servercore")
    public static ServerCoreMod INSTANCE;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ServerCoreConfig.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new ServerCoreEvents());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTpa());
        event.registerServerCommand(new CommandTpahere());
        event.registerServerCommand(new CommandTpacancel());
        event.registerServerCommand(new CommandTpaccept());
        event.registerServerCommand(new CommandTpacceptAll());
        event.registerServerCommand(new CommandTpadeny());
        event.registerServerCommand(new CommandTpadenyAll());
        event.registerServerCommand(new CommandSetHome());
        event.registerServerCommand(new CommandHome());
        event.registerServerCommand(new CommandDelHome());
        event.registerServerCommand(new CommandRtp());
    }
}
"""

def write_1122_src(base: Path):
    """Write 1.12.2 Forge source from the decompiled bundle.
    The decompiled source uses SRG names for methods — we copy it as-is
    but the 1.12.2 Forge template uses SRG mappings (not MCP), so SRG names
    should actually work. Let's try copying directly first."""
    src_root = BUNDLE_SRC / "versions" / "SBNCQth7" / "decompiled" / "src" / "src" / "main" / "java"
    dest_java = base / "src" / "main" / "java"
    if dest_java.exists():
        shutil.rmtree(dest_java)
    shutil.copytree(src_root, dest_java)


# ============================================================
# TARGETS
# ============================================================
targets = [
    # (folder, mc_version, loader, write_fn, mod_txt_str)
    ("SC1122Forge",   "1.12.2", "forge",  write_1122_src,
     mod_txt("asd.itamio.servercore", "asd.itamio.servercore.ServerCoreMod")),

    ("SC1165Forge",   "1.16.5", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1165Fabric",  "1.16.5", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),

    ("SC1171Forge",   "1.17.1", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1171Fabric",  "1.17.1", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),

    ("SC118Forge",    "1.18",   "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC118Fabric",   "1.18",   "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1181Forge",   "1.18.1", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1181Fabric",  "1.18.1", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1182Forge",   "1.18.2", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1182Fabric",  "1.18.2", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),

    ("SC119Forge",    "1.19",   "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC119Fabric",   "1.19",   "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1191Forge",   "1.19.1", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1191Fabric",  "1.19.1", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1192Forge",   "1.19.2", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1192Fabric",  "1.19.2", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1193Forge",   "1.19.3", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1193Fabric",  "1.19.3", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1194Forge",   "1.19.4", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1194Fabric",  "1.19.4", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),

    ("SC1201Forge",   "1.20.1", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1201Fabric",  "1.20.1", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1202Forge",   "1.20.2", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1202Fabric",  "1.20.2", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1203Forge",   "1.20.3", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1203Fabric",  "1.20.3", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1204Forge",   "1.20.4", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1204Fabric",  "1.20.4", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    # 1.20.5 Forge not in manifest
    ("SC1205Fabric",  "1.20.5", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1206Forge",   "1.20.6", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1206Fabric",  "1.20.6", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),

    ("SC121Forge",    "1.21",   "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC121Fabric",   "1.21",   "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1211Forge",   "1.21.1", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1211Fabric",  "1.21.1", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    # 1.21.2 Forge not in manifest
    ("SC1212Fabric",  "1.21.2", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1213Forge",   "1.21.3", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1213Fabric",  "1.21.3", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1214Forge",   "1.21.4", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1214Fabric",  "1.21.4", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1215Forge",   "1.21.5", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1215Fabric",  "1.21.5", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1216Forge",   "1.21.6", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1216Fabric",  "1.21.6", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1217Forge",   "1.21.7", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1217Fabric",  "1.21.7", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1218Forge",   "1.21.8", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1218Fabric",  "1.21.8", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC1219Forge",   "1.21.9", "forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC1219Fabric",  "1.21.9", "fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC12110Forge",  "1.21.10","forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC12110Fabric", "1.21.10","fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
    ("SC12111Forge",  "1.21.11","forge",  write_forge_src,
     mod_txt("com.itamio.servercore.forge", "com.itamio.servercore.forge.ServerCoreForgeMod")),
    ("SC12111Fabric", "1.21.11","fabric", write_fabric_src,
     mod_txt("com.itamio.servercore.fabric", "com.itamio.servercore.fabric.ServerCoreFabricMod")),
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
for (folder, mc_ver, loader, write_fn, mod_txt_str) in active_targets:
    base = BUNDLE / folder
    try:
        write(base / "mod.txt", mod_txt_str)
        write(base / "version.txt", version_txt(mc_ver, loader))
        write_fn(base)
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
