#!/usr/bin/env python3
"""
Generate PingFix (FIX MY PINGGGGG) mod bundle for all missing versions.

Mod: https://modrinth.com/mod/pingfix  (slug: pingfix, id: 9GikcByI)
Side: client-only (runtime_side=client)

Missing versions (2026-04-26):
  Fabric:   1.21, 1.21.9, 1.21.10, 26.1, 26.1.1, 26.1.2
  Forge:    1.12, 26.1.2
  NeoForge: ALL (1.20.2, 1.20.4, 1.20.5, 1.20.6,
                 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
                 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
                 26.1, 26.1.1, 26.1.2)
"""
import json
import shutil
import sys
from pathlib import Path

failed_only = "--failed-only" in sys.argv

repo_root   = Path(__file__).parent.parent
incoming    = repo_root / "incoming"
bundle_name = "pingfix-missing-versions"
bundle_dir  = incoming / bundle_name
zip_path    = incoming / f"{bundle_name}.zip"

if failed_only:
    runs_dir = repo_root / "ModCompileRuns"
    if not runs_dir.exists():
        print("ERROR: No ModCompileRuns directory found."); sys.exit(1)
    run_dirs = sorted([d for d in runs_dir.iterdir() if d.is_dir()], reverse=True)
    if not run_dirs:
        print("ERROR: No run directories found."); sys.exit(1)
    latest_run = run_dirs[0]
    mods_dir = latest_run / "artifacts" / "all-mod-builds" / "mods"
    failed_targets = set()
    if mods_dir.exists():
        for mod_dir in mods_dir.iterdir():
            r_file = mod_dir / "result.json"
            if r_file.exists():
                try:
                    r = json.loads(r_file.read_text())
                    if r.get("status") != "success":
                        failed_targets.add(mod_dir.name)
                except Exception:
                    pass
    if not failed_targets:
        print("No failures found. Nothing to rebuild."); sys.exit(0)
    print(f"Found {len(failed_targets)} failed targets from {latest_run.name}")
    for t in sorted(failed_targets): print(f"  - {t}")
    # Convert build system names (pingfix-forge-26-1-2) to our folder names (pingfix-26.1.2-forge)
    # Build system format: {mod_id}-{loader}-{version_with_dashes}
    # Our format: {mod_id}-{version}-{loader}
    converted = set()
    for name in failed_targets:
        # name like "pingfix-forge-26-1-2" or "pingfix-neoforge-1-20-2"
        parts = name.split("-")
        # Find loader position
        for loader in ("neoforge", "forge", "fabric"):
            if loader in parts:
                idx = parts.index(loader)
                version = ".".join(parts[idx+1:])
                our_name = f"pingfix-{version}-{loader}"
                converted.add(our_name)
                break
        else:
            converted.add(name)  # fallback: keep as-is
    targets_to_build_names = converted
    print(f"Converted to our folder names: {sorted(targets_to_build_names)}")
else:
    targets_to_build_names = None

MOD_ID      = "pingfix"
MOD_NAME    = "FIX MY PINGGGGG"
MOD_VERSION = "1.0.0"
GROUP       = "com.itamio.pingfix"
DESCRIPTION = "Fixes multiplayer server list ping by periodically refreshing the server browser."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/pingfix"

if bundle_dir.exists():
    shutil.rmtree(bundle_dir)
bundle_dir.mkdir(parents=True, exist_ok=True)

def vt(v):
    parts = []
    for p in v.split("."):
        try: parts.append(int(p))
        except ValueError: parts.append(0)
    return tuple(parts)

def write_mod_files(mod_folder, loader, mc_version, entrypoint):
    (mod_folder / "mod.txt").write_text(
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=client\n"
    )
    (mod_folder / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader={loader}\n"
    )

# =============================================================================
# FABRIC
# =============================================================================

FABRIC_YARN_SRC = """\
package com.itamio.pingfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;

/**
 * PingFix Fabric (yarn mappings 1.16.5-1.20.x)
 * Periodically re-opens the server list to force a fresh ping of all servers.
 * Fixes the bug where Minecraft caches a stale network route after VPN changes.
 */
public final class PingFixClient implements ClientModInitializer {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof MultiplayerScreen) {
                tickCounter++;
                if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                    tickCounter = 0;
                    client.setScreen(new MultiplayerScreen(new TitleScreen()));
                }
            } else {
                tickCounter = 0;
            }
        });
    }
}
"""

FABRIC_MOJANG_SRC = """\
package com.itamio.pingfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * PingFix Fabric (Mojang mappings 1.21+)
 * Periodically re-opens the server list to force a fresh ping of all servers.
 * Fixes the bug where Minecraft caches a stale network route after VPN changes.
 */
public final class PingFixClient implements ClientModInitializer {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen instanceof JoinMultiplayerScreen) {
                tickCounter++;
                if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                    tickCounter = 0;
                    client.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
                }
            } else {
                tickCounter = 0;
            }
        });
    }
}
"""

def create_fabric(mc_version: str):
    folder_name = f"pingfix-{mc_version}-fabric"
    if targets_to_build_names is not None and folder_name not in targets_to_build_names:
        return

    mod_folder = bundle_dir / folder_name
    is_presplit = vt(mc_version) < (1, 20)
    is_26 = mc_version.startswith("26.")
    is_yarn = vt(mc_version) < (1, 21)

    if is_presplit:
        java_dir = mod_folder / "src" / "main" / "java" / "com" / "itamio" / "pingfix"
    else:
        java_dir = mod_folder / "src" / "client" / "java" / "com" / "itamio" / "pingfix"
    res_dir = mod_folder / "src" / "main" / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)

    src = FABRIC_YARN_SRC if is_yarn else FABRIC_MOJANG_SRC
    (java_dir / "PingFixClient.java").write_text(src)

    java_req = ">=25" if is_26 else ">=21" if not is_yarn else ">=16"
    fabric_mod = {
        "schemaVersion": 1,
        "id": MOD_ID,
        "version": MOD_VERSION,
        "name": MOD_NAME,
        "description": DESCRIPTION,
        "authors": [AUTHORS],
        "license": LICENSE,
        "contact": {"homepage": HOMEPAGE},
        "environment": "client",
        "entrypoints": {"client": ["com.itamio.pingfix.PingFixClient"]},
        "depends": {
            "fabricloader": ">=0.14.0",
            "minecraft": f"~{mc_version}",
            "java": java_req,
            "fabric-api": "*",
        }
    }
    (res_dir / "fabric.mod.json").write_text(json.dumps(fabric_mod, indent=2))
    write_mod_files(mod_folder, "fabric", mc_version, "com.itamio.pingfix.PingFixClient")
    print(f"  Created {folder_name}")


# =============================================================================
# FORGE
# =============================================================================

# Forge 1.12 — legacy mcmod.info, gameevent.TickEvent, GuiMultiplayer
FORGE_112_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * PingFix Forge 1.12 — periodically re-opens the server list to force
 * Minecraft to re-ping all servers, fixing stale network route issues.
 */
@Mod(modid = PingFixMod.MOD_ID, name = PingFixMod.MOD_NAME,
     version = PingFixMod.MOD_VERSION, clientSideOnly = true,
     acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12,1.12.2]")
public final class PingFixMod {
    public static final String MOD_ID      = "pingfix";
    public static final String MOD_NAME    = "FIX MY PINGGGGG";
    public static final String MOD_VERSION = "1.0.0";

    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (mc.currentScreen instanceof GuiMultiplayer) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

# Forge 1.16.5-1.21.5 — mods.toml, TickEvent.ClientTickEvent, JoinMultiplayerScreen
FORGE_MODERN_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * PingFix Forge 1.16.5-1.21.5 — periodically re-opens the server list to force
 * Minecraft to re-ping all servers, fixing stale network route issues.
 */
@Mod(PingFixMod.MOD_ID)
@Mod.EventBusSubscriber(modid = PingFixMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
                        value = Dist.CLIENT)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private static int tickCounter = 0;

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

# Forge 1.21.6+ / 26.1.2 — EventBus 7, TickEvent.ClientTickEvent.BUS
FORGE_EB7_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * PingFix Forge 1.21.6+ (EventBus 7) — periodically re-opens the server list
 * to force Minecraft to re-ping all servers, fixing stale network route issues.
 */
@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    public PingFixMod(FMLJavaModLoadingContext context) {
        // Only register the tick listener on the client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

# Forge 26.1.2 — TickEvent is now a sealed interface with record types (no Phase field)
# ClientTickEvent.Post.BUS.addListener() — handler takes Post record (no phase check needed)
FORGE_261_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * PingFix Forge 26.1.2 — TickEvent is now a sealed interface with record types.
 * ClientTickEvent.Post is a record (no phase field) — Post always means post-tick.
 * Uses EventBus 7 pattern: TickEvent.ClientTickEvent.Post.BUS.addListener(...)
 */
@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    public PingFixMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

def create_forge(mc_version: str):
    folder_name = f"pingfix-{mc_version}-forge"
    if targets_to_build_names is not None and folder_name not in targets_to_build_names:
        return

    mod_folder = bundle_dir / folder_name
    src_dir    = mod_folder / "src" / "main"
    java_dir   = src_dir / "java" / "com" / "itamio" / "pingfix"
    res_dir    = src_dir / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "META-INF").mkdir(exist_ok=True)

    v = vt(mc_version)
    is_legacy = v < (1, 13)          # 1.12.x
    is_261    = mc_version.startswith("26.")
    is_eb7    = v >= (1, 21, 6) and not is_261  # 1.21.6-1.21.11 only

    if is_legacy:
        src = FORGE_112_SRC
        (java_dir / "PingFixMod.java").write_text(src)
        mcmod = json.dumps([{
            "modid": MOD_ID,
            "name": MOD_NAME,
            "description": DESCRIPTION,
            "version": MOD_VERSION,
            "mcversion": mc_version,
            "authorList": [AUTHORS],
            "url": HOMEPAGE,
            "clientSideOnly": True,
        }], indent=2)
        (res_dir / "mcmod.info").write_text(mcmod)
        (res_dir / "pack.mcmeta").write_text(
            json.dumps({"pack": {"pack_format": 3, "description": MOD_NAME}}, indent=2))
    else:
        if is_261:
            src = FORGE_261_SRC
        elif is_eb7:
            src = FORGE_EB7_SRC
        else:
            src = FORGE_MODERN_SRC
        (java_dir / "PingFixMod.java").write_text(src)

        if is_eb7:
            loader_ver = "[64,)"
            mc_range   = f"[{mc_version},)"
        elif v >= (1, 21, 9):
            loader_ver = "[59,)"; mc_range = f"[{mc_version},1.22)"
        elif v >= (1, 21, 2):
            loader_ver = "[53,)"; mc_range = f"[{mc_version},1.22)"
        elif v >= (1, 21,):
            loader_ver = "[51,)"; mc_range = f"[{mc_version},1.22)"
        elif v >= (1, 20,):
            loader_ver = "[47,)"; mc_range = f"[{mc_version},1.21)"
        elif v >= (1, 19,):
            loader_ver = "[41,)"; mc_range = f"[{mc_version},1.20)"
        elif v >= (1, 18,):
            loader_ver = "[38,)"; mc_range = f"[{mc_version},1.19)"
        else:
            loader_ver = "[36,)"; mc_range = f"[{mc_version},1.18)"

        mods_toml = f"""modLoader="javafml"
loaderVersion="{loader_ver}"
license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"
authors="{AUTHORS}"
displayURL="{HOMEPAGE}"

[[dependencies.{MOD_ID}]]
modId="forge"
mandatory=true
versionRange="{loader_ver}"
ordering="NONE"
side="CLIENT"

[[dependencies.{MOD_ID}]]
modId="minecraft"
mandatory=true
versionRange="{mc_range}"
ordering="NONE"
side="CLIENT"
"""
        (res_dir / "META-INF" / "mods.toml").write_text(mods_toml)
        (res_dir / "pack.mcmeta").write_text(
            json.dumps({"pack": {"pack_format": 15, "description": MOD_NAME}}, indent=2))

    write_mod_files(mod_folder, "forge", mc_version, "com.itamio.pingfix.PingFixMod")
    print(f"  Created {folder_name}")


# =============================================================================
# NEOFORGE
# =============================================================================

# NeoForge 1.20.x-1.21.x — ClientTickEvent.Post, NeoForge.EVENT_BUS
NEO_MODERN_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * PingFix NeoForge 1.20.x-1.21.x — periodically re-opens the server list
 * to force Minecraft to re-ping all servers, fixing stale network route issues.
 * Client-only mod (runtime_side=client), no dist check needed.
 */
@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    public PingFixMod() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

# NeoForge 26.1+ — standalone @EventBusSubscriber, same ClientTickEvent.Post
NEO_261_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * PingFix NeoForge 26.1+ — periodically re-opens the server list
 * to force Minecraft to re-ping all servers, fixing stale network route issues.
 * NeoForge 26.1+: @EventBusSubscriber is standalone (not nested in @Mod).
 */
@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    public PingFixMod() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            tickCounter++;
            if (tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            tickCounter = 0;
        }
    }
}
"""

# NeoForge 1.20.2 — ClientTickEvent and LevelTickEvent not in public API for NeoForge 20.2.x
# Use RenderGuiEvent.Post (fires every frame, client-only, always accessible)
# We throttle with a frame counter — 1200 frames ≈ 10 seconds at 120fps, good enough
NEO_1202_SRC = """\
package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * PingFix NeoForge 1.20.2 — ClientTickEvent and LevelTickEvent are not in the
 * public API for NeoForge 20.2.x. Uses RenderGuiEvent.Post (fires every frame,
 * client-only) with a frame counter as a workaround.
 */
@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    // ~1200 frames at 120fps ≈ 10 seconds; at 60fps ≈ 20 seconds. Good enough.
    private static final int REFRESH_INTERVAL_FRAMES = 1200;
    private int frameCounter = 0;

    public PingFixMod() {
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);
    }

    private void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            frameCounter++;
            if (frameCounter >= REFRESH_INTERVAL_FRAMES) {
                frameCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            frameCounter = 0;
        }
    }
}
"""

def create_neoforge(mc_version: str):
    folder_name = f"pingfix-{mc_version}-neoforge"
    if targets_to_build_names is not None and folder_name not in targets_to_build_names:
        return

    mod_folder = bundle_dir / folder_name
    src_dir    = mod_folder / "src" / "main"
    java_dir   = src_dir / "java" / "com" / "itamio" / "pingfix"
    res_dir    = src_dir / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "META-INF").mkdir(exist_ok=True)

    is_26 = mc_version.startswith("26.")
    is_1202_era = mc_version in ("1.20.2", "1.20.4")  # ClientTickEvent not in public API
    if is_26:
        src = NEO_261_SRC
    elif is_1202_era:
        src = NEO_1202_SRC
    else:
        src = NEO_MODERN_SRC
    (java_dir / "PingFixMod.java").write_text(src)

    if is_26:
        neo_range = "[26.1,)"
        mc_range  = "[26.1,27)"
    else:
        neo_range = "[20.4,)"
        mc_range  = f"[{mc_version},1.22)"

    mods_toml = f"""modLoader="javafml"
loaderVersion="[1,)"
license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"
authors="{AUTHORS}"
displayURL="{HOMEPAGE}"

[[dependencies.{MOD_ID}]]
modId="neoforge"
mandatory=true
versionRange="{neo_range}"
ordering="NONE"
side="CLIENT"

[[dependencies.{MOD_ID}]]
modId="minecraft"
mandatory=true
versionRange="{mc_range}"
ordering="NONE"
side="CLIENT"
"""
    (res_dir / "META-INF" / "neoforge.mods.toml").write_text(mods_toml)
    (res_dir / "pack.mcmeta").write_text(
        json.dumps({"pack": {"pack_format": 15, "description": MOD_NAME}}, indent=2))

    write_mod_files(mod_folder, "neoforge", mc_version, "com.itamio.pingfix.PingFixMod")
    print(f"  Created {folder_name}")


# =============================================================================
# TARGETS
# =============================================================================

FABRIC_TARGETS = [
    "1.21",       # 1.21-1.21.1 range (fabric_split)
    "1.21.9",     # 1.21.9-1.21.11 range
    "1.21.10",    # 1.21.9-1.21.11 range
    "26.1",
    "26.1.1",
    "26.1.2",
]

FORGE_TARGETS = [
    "1.12",       # 1.12-1.12.2 range (legacy mcmod.info)
    "26.1.2",     # 26.1-26.x range (EventBus 7)
]

NEOFORGE_TARGETS = [
    "1.20.2",
    "1.20.4",
    "1.20.5",
    "1.20.6",
    "1.21",
    "1.21.1",
    "1.21.2",
    "1.21.3",
    "1.21.4",
    "1.21.5",
    "1.21.6",
    "1.21.7",
    "1.21.8",
    "1.21.9",
    "1.21.10",
    "1.21.11",
    "26.1",
    "26.1.1",
    "26.1.2",
]

total = len(FABRIC_TARGETS) + len(FORGE_TARGETS) + len(NEOFORGE_TARGETS)
print(f"Building {total} missing targets")

for v in FABRIC_TARGETS:
    create_fabric(v)
for v in FORGE_TARGETS:
    create_forge(v)
for v in NEOFORGE_TARGETS:
    create_neoforge(v)

# ── Create zip ────────────────────────────────────────────────────────────────
print(f"\nCreating zip: {zip_path}")
if zip_path.exists():
    zip_path.unlink()
shutil.make_archive(str(zip_path.with_suffix("")), "zip", bundle_dir)

count = len(list(bundle_dir.iterdir()))
print(f"\n✓ Bundle created: {zip_path}")
print(f"✓ Contains {count} mod folders")
print("\nNext steps:")
print("  1. git add scripts/ incoming/")
print(f'  2. git commit -m "Add PingFix missing versions"')
print("  3. git push")
print(f'  4. python3 scripts/run_build.py incoming/{bundle_name}.zip --modrinth https://modrinth.com/mod/pingfix')
