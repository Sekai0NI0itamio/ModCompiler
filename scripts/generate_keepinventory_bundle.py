#!/usr/bin/env python3
"""
Generates the Keep Inventory bundle for all missing MC versions and loaders.
Mod: https://modrinth.com/mod/keep-inventory
Server-side mod: enforces keepInventory gamerule = true on all worlds.

Already published (skip these):
  1.12.2  forge

Run:
    python3 scripts/generate_keepinventory_bundle.py
    python3 scripts/generate_keepinventory_bundle.py --failed-only
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT       = Path(__file__).resolve().parents[1]
BUNDLE_DIR = ROOT / "incoming" / "keepinventory-all-versions"
ZIP_PATH   = ROOT / "incoming" / "keepinventory-all-versions.zip"

MOD_ID      = "keepinventory"
MOD_NAME    = "Keep Inventory"
MOD_VERSION = "1.0.0"
GROUP       = "asd.itamio.keepinventory"
ENTRYPOINT  = f"{GROUP}.KeepInventoryMod"
DESCRIPTION = "Enforces keepInventory gamerule to always be true. Never lose your items on death!"
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/keep-inventory"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")


def mod_txt() -> str:
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )


def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ===========================================================================
# 1.8.9 FORGE
# Java 6: no underscores in literals, no diamond <>, no lambdas
# TickEvent in net.minecraftforge.fml.common.gameevent (1.8.9 only)
# WorldEvent.Load from net.minecraftforge.event.world
# event.world is a public field in 1.8.9
# ===========================================================================
SRC_189_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = KeepInventoryMod.MODID, name = "Keep Inventory", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.9]")
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world != null && !event.world.isRemote) {
            GameRules rules = event.world.getGameRules();
            if (rules != null) {
                rules.setOrCreateGameRule("keepInventory", "true");
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean("keepInventory")) {
                rules.setOrCreateGameRule("keepInventory", "true");
            }
        }
    }
}
"""

# ===========================================================================
# 1.12.2 FORGE — already published, included for reference only
# TickEvent in net.minecraftforge.fml.common.gameevent
# WorldEvent.getWorld() returns IWorld — cast to World
# ===========================================================================
SRC_1122_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = KeepInventoryMod.MODID, name = "Keep Inventory", version = "1.0.0",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof World)) return;
        World world = (World) event.getWorld();
        if (!world.isRemote) {
            GameRules rules = world.getGameRules();
            if (rules != null) {
                rules.setOrCreateGameRule("keepInventory", "true");
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean("keepInventory")) {
                rules.setOrCreateGameRule("keepInventory", "true");
            }
        }
    }
}
"""


# ===========================================================================
# 1.16.5 FORGE
# TickEvent in net.minecraftforge.event.TickEvent
# WorldEvent.getWorld() returns IWorld — cast to World
# World.isClientSide() in 1.16.5
# GameRules.RULE_KEEPINVENTORY (Key<BooleanValue>)
# ===========================================================================
SRC_1165_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof World)) return;
        World world = (World) event.getWorld();
        if (!world.isClientSide()) {
            GameRules rules = world.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
"""

# ===========================================================================
# 1.17.1 FORGE
# TickEvent in net.minecraftforge.event.TickEvent
# WorldEvent still in net.minecraftforge.event.world (1.17 uses "world" pkg)
# Level.isClientSide() — same as 1.16.5
# net.minecraft.world.level.GameRules (1.17+ package)
# ===========================================================================
SRC_1171_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof Level)) return;
        Level level = (Level) event.getWorld();
        if (!level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
"""

# ===========================================================================
# 1.18–1.18.2 FORGE
# TickEvent in net.minecraftforge.event.TickEvent
# WorldEvent still in net.minecraftforge.event.world (NOT level — rename in 1.19)
# WorldTickEvent (NOT LevelTickEvent — rename in 1.19)
# event.world field (public) on WorldTickEvent
# ===========================================================================
SRC_118_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof Level)) return;
        Level level = (Level) event.getWorld();
        if (!level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
"""

# ===========================================================================
# 1.19–1.20.6 FORGE (EventBus 6)
# LevelEvent.Load from net.minecraftforge.event.level (renamed in 1.19)
# TickEvent.LevelTickEvent (renamed in 1.19)
# event.level field (public) on LevelTickEvent
# Level.getGameRules() still exists in 1.19-1.20.6
# ===========================================================================
SRC_119_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof Level)) return;
        Level level = (Level) event.getLevel();
        if (!level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.level.getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
"""

# ===========================================================================
# 1.21–1.21.2 FORGE (EventBus 6)
# Same as 1.19-1.20.6 — LevelEvent.Load + TickEvent.LevelTickEvent
# Level.getGameRules() still exists in 1.21-1.21.2
# ===========================================================================
SRC_121_FORGE = SRC_119_FORGE


# ===========================================================================
# 1.21.3–1.21.5 FORGE (EventBus 6)
# Level.getGameRules() removed — must cast to ServerLevel
# LevelEvent.Load + TickEvent.LevelTickEvent still work
# ===========================================================================
SRC_1213_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) la;
            sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel)) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            ServerLevel sl = (ServerLevel) event.level;
            if (!sl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
            }
        }
    }
}
"""

# ===========================================================================
# 1.21.6–1.21.8 FORGE (EventBus 7)
# LevelEvent.Load.BUS.addListener() + TickEvent.LevelTickEvent.Post.BUS.addListener()
# LevelTickEvent.Post has public field: Level level (not a record yet)
# Level.getGameRules() removed — cast to ServerLevel
# ===========================================================================
SRC_1216_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(FMLJavaModLoadingContext context) {
        LevelEvent.Load.BUS.addListener(this::onLevelLoad);
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::onLevelTick);
    }

    private void onLevelLoad(LevelEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) la;
            sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
        }
    }

    private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel)) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            ServerLevel sl = (ServerLevel) event.level;
            if (!sl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
            }
        }
    }
}
"""

# ===========================================================================
# 1.21.9–26.1.2 FORGE (EventBus 7, record-based TickEvent)
# TickEvent.LevelTickEvent is now a sealed interface with record Pre/Post
# record Post has accessor: level() — NOT a field
# Level.getGameRules() removed — cast to ServerLevel
# ===========================================================================
SRC_1219_FORGE = """\
package asd.itamio.keepinventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(FMLJavaModLoadingContext context) {
        LevelEvent.Load.BUS.addListener(this::onLevelLoad);
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::onLevelTick);
    }

    private void onLevelLoad(LevelEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) la;
            sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
        }
    }

    private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level() instanceof ServerLevel)) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            ServerLevel sl = (ServerLevel) event.level();
            if (!sl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
            }
        }
    }
}
"""


# ===========================================================================
# FABRIC 1.16.5 (presplit, yarn mappings)
# net.minecraft.world.GameRules (pre-1.17 package)
# GameRules.KEEP_INVENTORY (Key<BooleanRule>)
# ServerLifecycleEvents + ServerTickEvents
# server.getWorlds() returns Iterable<ServerWorld>
# ===========================================================================
SRC_1165_FABRIC = """\
package asd.itamio.keepinventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

public class KeepInventoryMod implements ModInitializer {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerStarting(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            for (ServerWorld world : server.getWorlds()) {
                if (!world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                    world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
                }
            }
        }
    }
}
"""

# ===========================================================================
# FABRIC 1.17–1.20.6 (presplit/split, yarn mappings)
# Same API as 1.16.5 — ServerLifecycleEvents + ServerTickEvents
# GameRules.KEEP_INVENTORY still works in yarn mappings
# ===========================================================================
SRC_117_FABRIC = SRC_1165_FABRIC
SRC_120_FABRIC = SRC_1165_FABRIC

# ===========================================================================
# FABRIC 1.21–1.21.8 (split, Mojang mappings)
# net.minecraft.world.level.GameRules (Mojang package)
# GameRules.RULE_KEEPINVENTORY (Key<BooleanValue>)
# ServerLevel (not ServerWorld)
# server.getAllLevels() (not getWorlds())
# ===========================================================================
SRC_121_FABRIC = """\
package asd.itamio.keepinventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public class KeepInventoryMod implements ModInitializer {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerStarting(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                    level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                }
            }
        }
    }
}
"""

# ===========================================================================
# FABRIC 1.21.9–1.21.11 (split, Mojang mappings)
# Same as 1.21+ — ServerLifecycleEvents + ServerTickEvents unchanged
# ===========================================================================
SRC_1219_FABRIC = SRC_121_FABRIC

# ===========================================================================
# FABRIC 26.1–26.1.2 (split, Mojang mappings)
# GameRules moved to net.minecraft.world.level.gamerules.GameRules
# API changed: GameRule<Boolean> with get()/set() instead of BooleanValue
# server.getAllLevels() still works
# ===========================================================================
SRC_261_FABRIC = """\
package asd.itamio.keepinventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

public class KeepInventoryMod implements ModInitializer {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerStarting(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                    level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                }
            }
        }
    }
}
"""


# ===========================================================================
# NEOFORGE 1.20.2–1.21.8 (IEventBus constructor)
# Use ServerStartingEvent for initial set + ServerTickEvent.Post for periodic
# Avoids LevelTickEvent which may not exist in early NeoForge 1.20.2 (20.2.93)
# ServerStartingEvent from net.neoforged.neoforge.event.server
# ServerTickEvent from net.neoforged.neoforge.event.tick (exists in all versions)
# ===========================================================================
SRC_120_NEO = """\
package asd.itamio.keepinventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        }
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            MinecraftServer server = event.getServer();
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                    level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                }
            }
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21.9–1.21.11 (ModContainer required in constructor)
# Same as 1.20.2-1.21.8 but constructor takes (IEventBus, ModContainer)
# ===========================================================================
SRC_1219_NEO = """\
package asd.itamio.keepinventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            MinecraftServer server = event.getServer();
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                    level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                }
            }
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 26.1–26.1.2
# FMLJavaModLoadingContext removed — constructor injection (IEventBus, ModContainer)
# GameRules moved to net.minecraft.world.level.gamerules.GameRules
# API changed: GameRule<Boolean> with get()/set() instead of BooleanValue
# ===========================================================================
SRC_261_NEO = """\
package asd.itamio.keepinventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            MinecraftServer server = event.getServer();
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                    level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                }
            }
        }
    }
}
"""


# ===========================================================================
# TARGETS — all 68 missing versions
# ===========================================================================
TARGETS = [
    # ---- FORGE 1.8.9 ----
    ("KeepInventory-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.12 ----
    ("KeepInventory-1.12-forge",     "1.12",    "forge",    SRC_1122_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.16.5 ----
    ("KeepInventory-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.17.1 ----
    ("KeepInventory-1.17.1-forge",   "1.17.1",  "forge",    SRC_1171_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.18–1.18.2 (WorldEvent/WorldTickEvent — NOT LevelEvent) ----
    ("KeepInventory-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.19–1.20.6 (LevelEvent/LevelTickEvent, Level.getGameRules()) ----
    ("KeepInventory-1.19-forge",     "1.19",    "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.1-forge",   "1.19.1",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.2-forge",   "1.19.2",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.3-forge",   "1.19.3",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.4-forge",   "1.19.4",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.1-forge",   "1.20.1",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.2-forge",   "1.20.2",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.3-forge",   "1.20.3",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.4-forge",   "1.20.4",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.6-forge",   "1.20.6",  "forge",    SRC_119_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.21–1.21.2 (LevelEvent/LevelTickEvent, Level.getGameRules()) ----
    ("KeepInventory-1.21-forge",     "1.21",    "forge",    SRC_121_FORGE,   GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.1-forge",   "1.21.1",  "forge",    SRC_121_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.3–1.21.5 (ServerLevel cast required) ----
    ("KeepInventory-1.21.3-forge",   "1.21.3",  "forge",    SRC_1213_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.4-forge",   "1.21.4",  "forge",    SRC_1213_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.5-forge",   "1.21.5",  "forge",    SRC_1213_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.6–1.21.8 (EventBus 7, field access) ----
    ("KeepInventory-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.9–26.1.2 (EventBus 7, record accessor) ----
    ("KeepInventory-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    ("KeepInventory-26.1.2-forge",   "26.1.2",  "forge",    SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    # ---- FABRIC 1.16.5 ----
    ("KeepInventory-1.16.5-fabric",  "1.16.5",  "fabric",   SRC_1165_FABRIC, GROUP, ENTRYPOINT),
    # ---- FABRIC 1.17 ----
    ("KeepInventory-1.17-fabric",    "1.17",    "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    # ---- FABRIC 1.18–1.20.6 ----
    ("KeepInventory-1.18-fabric",    "1.18",    "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.19-fabric",    "1.19",    "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.1-fabric",  "1.19.1",  "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.2-fabric",  "1.19.2",  "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.3-fabric",  "1.19.3",  "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.19.4-fabric",  "1.19.4",  "fabric",   SRC_117_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.1-fabric",  "1.20.1",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.2-fabric",  "1.20.2",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.3-fabric",  "1.20.3",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.4-fabric",  "1.20.4",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.5-fabric",  "1.20.5",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.6-fabric",  "1.20.6",  "fabric",   SRC_120_FABRIC,  GROUP, ENTRYPOINT),
    # ---- FABRIC 1.21–1.21.8 (Mojang mappings) ----
    ("KeepInventory-1.21-fabric",    "1.21",    "fabric",   SRC_121_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.2-fabric",  "1.21.2",  "fabric",   SRC_121_FABRIC,  GROUP, ENTRYPOINT),
    # ---- FABRIC 1.21.9–1.21.11 ----
    ("KeepInventory-1.21.9-fabric",  "1.21.9",  "fabric",   SRC_1219_FABRIC, GROUP, ENTRYPOINT),
    # ---- FABRIC 26.1–26.1.2 (new GameRules API) ----
    ("KeepInventory-26.1-fabric",    "26.1",    "fabric",   SRC_261_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-26.1.1-fabric",  "26.1.1",  "fabric",   SRC_261_FABRIC,  GROUP, ENTRYPOINT),
    ("KeepInventory-26.1.2-fabric",  "26.1.2",  "fabric",   SRC_261_FABRIC,  GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.20.2–1.21.8 (ServerStartingEvent + ServerTickEvent) ----
    ("KeepInventory-1.20.2-neoforge","1.20.2",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.4-neoforge","1.20.4",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.5-neoforge","1.20.5",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.20.6-neoforge","1.20.6",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21-neoforge",  "1.21",    "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.1-neoforge","1.21.1",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.2-neoforge","1.21.2",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.3-neoforge","1.21.3",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.4-neoforge","1.21.4",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.5-neoforge","1.21.5",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.6-neoforge","1.21.6",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.7-neoforge","1.21.7",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.8-neoforge","1.21.8",  "neoforge", SRC_120_NEO,     GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.21.9–1.21.11 (ModContainer required) ----
    ("KeepInventory-1.21.9-neoforge","1.21.9",  "neoforge", SRC_1219_NEO,    GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.10-neoforge","1.21.10","neoforge", SRC_1219_NEO,    GROUP, ENTRYPOINT),
    ("KeepInventory-1.21.11-neoforge","1.21.11","neoforge", SRC_1219_NEO,    GROUP, ENTRYPOINT),
    # ---- NEOFORGE 26.1–26.1.2 (new GameRules API) ----
    ("KeepInventory-26.1-neoforge",  "26.1",    "neoforge", SRC_261_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-26.1.1-neoforge","26.1.1",  "neoforge", SRC_261_NEO,     GROUP, ENTRYPOINT),
    ("KeepInventory-26.1.2-neoforge","26.1.2",  "neoforge", SRC_261_NEO,     GROUP, ENTRYPOINT),
]


# ===========================================================================
# BUILD HELPERS
# ===========================================================================

def get_failed_slugs() -> set:
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        print("No ModCompileRuns found.", file=sys.stderr)
        sys.exit(1)
    runs = sorted(runs_dir.iterdir())
    run_dir = None
    for r in reversed(runs):
        mods_dir = r / "artifacts" / "all-mod-builds" / "mods"
        if mods_dir.exists() and any("keepinventory" in m.name.lower() for m in mods_dir.iterdir()):
            run_dir = r
            break
    if run_dir is None:
        print("No run with keepinventory mods found.", file=sys.stderr)
        sys.exit(1)
    mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
    failed = set()
    for mod_dir in mods_dir.iterdir():
        rf = mod_dir / "result.json"
        if not rf.exists():
            failed.add(mod_dir.name)
            continue
        if json.loads(rf.read_text()).get("status") != "success":
            failed.add(mod_dir.name)
    print(f"Latest run: {run_dir.name}")
    print(f"Failed slugs ({len(failed)}): {', '.join(sorted(failed))}")
    return failed


def slug_for_target(folder_name: str, mc: str, loader: str) -> str:
    return f"{MOD_ID}-{loader}-{mc.replace('.', '-')}"


def generate_target(folder_name: str, mc: str, loader: str, src: str,
                    group: str, entrypoint: str) -> None:
    pkg_path = group.replace(".", "/")
    class_name = entrypoint.split(".")[-1]
    base = BUNDLE_DIR / folder_name
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc, loader))
    write(base / "src" / "main" / "java" / pkg_path / f"{class_name}.java", src)


def build_zip(targets: list, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        total = 0
        for folder_name, mc, loader, src, group, entrypoint in targets:
            for fpath in (BUNDLE_DIR / folder_name).rglob("*"):
                if fpath.is_file():
                    zf.write(fpath, str(fpath.relative_to(BUNDLE_DIR)))
                    total += 1
            print(f"  + {folder_name}")
    print(f"\nWrote {out_path}  ({total} files, {len(targets)} targets)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true")
    args = parser.parse_args()

    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True)

    if args.failed_only:
        failed_slugs = get_failed_slugs()
        selected = [t for t in TARGETS if slug_for_target(*t[:3]) in failed_slugs]
        for t in TARGETS:
            slug = slug_for_target(*t[:3])
            print(f"  {'INCLUDE' if slug in failed_slugs else 'skip   '}: {t[0]}")
    else:
        selected = TARGETS

    print(f"\nGenerating {len(selected)} targets...")
    for t in selected:
        generate_target(*t)
        print(f"  wrote {t[0]}")

    print(f"\nBuilding zip -> {ZIP_PATH}")
    build_zip(selected, ZIP_PATH)
    print("\nDone.")


if __name__ == "__main__":
    main()
