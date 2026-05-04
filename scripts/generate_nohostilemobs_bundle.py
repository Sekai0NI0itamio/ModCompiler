#!/usr/bin/env python3
"""
Generates the No Hostile Mobs bundle for all missing MC versions and loaders.
Mod: https://modrinth.com/mod/no-hostile-mobs
Server-side mod: prevents hostile mobs from spawning.

Already published (skip these):
  1.12.2  forge  (published as 1.12, 1.12.1, 1.12.2)

Run:
    python3 scripts/generate_nohostilemobs_bundle.py
    python3 scripts/generate_nohostilemobs_bundle.py --failed-only
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT       = Path(__file__).resolve().parents[1]
BUNDLE_DIR = ROOT / "incoming" / "nohostilemobs-all-versions"
ZIP_PATH   = ROOT / "incoming" / "nohostilemobs-all-versions.zip"

MOD_ID      = "nohostilemobs"
MOD_NAME    = "No Hostile Mobs"
MOD_VERSION = "1.0.0"
GROUP       = "asd.itamio.nohostilemobs"
ENTRYPOINT  = f"{GROUP}.NoHostileMobsMod"
DESCRIPTION = "Prevents hostile mobs from spawning in your world, regardless of difficulty. Fully configurable."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/no-hostile-mobs"


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
# Java 6: no underscores, no diamond <>, no lambdas
# LivingSpawnEvent.CheckSpawn with HasResult
# IMob interface identifies hostile mobs
# event.entityLiving is EntityLivingBase (not EntityLiving) in 1.8.9
# SubscribeEvent in net.minecraftforge.fml.common.eventhandler
# ===========================================================================
SRC_189_FORGE = """\
package asd.itamio.nohostilemobs;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = NoHostileMobsMod.MODID, name = "No Hostile Mobs", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.9]")
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity instanceof IMob) {
            event.setResult(Result.DENY);
        }
    }
}
"""


# ===========================================================================
# 1.16.5 FORGE
# LivingSpawnEvent.CheckSpawn still exists
# SubscribeEvent in net.minecraftforge.eventbus.api
# MobEntity (not EntityLiving)
# net.minecraft.entity.monster.IMob still exists in 1.16.5
# ===========================================================================
SRC_1165_FORGE = """\
package asd.itamio.nohostilemobs;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getEntity() instanceof IMob) {
            event.setResult(Result.DENY);
        }
    }
}
"""

# ===========================================================================
# 1.17.1 FORGE
# LivingSpawnEvent.CheckSpawn still exists
# net.minecraft.world.entity.monster.Monster (renamed from IMob)
# ===========================================================================
SRC_1171_FORGE = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getEntity() instanceof Monster) {
            event.setResult(Result.DENY);
        }
    }
}
"""

# 1.18.x same as 1.17.1
SRC_118_FORGE = SRC_1171_FORGE


# ===========================================================================
# 1.19–1.19.3 FORGE (Forge 41.x–44.x)
# MobSpawnEvent.FinalizeSpawn NOT accessible in Forge 41.x–44.x API jar
# Use EntityJoinLevelEvent (cancelable) to block hostile mobs joining the level
# MobCategory.MONSTER identifies hostile mobs
# ===========================================================================
SRC_119_FORGE_EARLY = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# 1.19.4–1.21.5 FORGE (EventBus 6)
# MobSpawnEvent.FinalizeSpawn accessible from Forge 45.x (1.19.4+)
# Check MobCategory.MONSTER via entity.getType().getCategory()
# ===========================================================================
SRC_119_FORGE = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setSpawnCancelled(true);
        }
    }
}
"""

# 1.21–1.21.5 same as 1.19.4
SRC_121_FORGE = SRC_119_FORGE

# ===========================================================================
# 1.21.6–1.21.8 FORGE (EventBus 7)
# MobSpawnEvent.FinalizeSpawn.BUS.addListener() pattern
# FMLJavaModLoadingContext constructor injection
# ===========================================================================
SRC_1216_FORGE = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod(FMLJavaModLoadingContext context) {
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(this::onFinalizeSpawn);
    }

    private void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setSpawnCancelled(true);
        }
    }
}
"""

# ===========================================================================
# 1.21.9–26.1.2 FORGE (EventBus 7, same pattern as 1.21.6)
# ===========================================================================
SRC_1219_FORGE = SRC_1216_FORGE


# ===========================================================================
# FABRIC 1.16.5 (presplit, yarn mappings)
# Use Mixin on MobEntity.canSpawn to block MONSTER spawn group
# net.minecraft.entity.SpawnGroup.MONSTER
# In 1.16.5 yarn: net.minecraft.entity.mob.MobEntity (NOT net.minecraft.entity.MobEntity)
# ===========================================================================
SRC_1165_FABRIC_MIXIN = """\
package asd.itamio.nohostilemobs.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.WorldAccess;
import net.minecraft.entity.SpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z",
            at = @At("HEAD"), cancellable = true)
    private void blockHostileSpawn(WorldAccess world, SpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity)(Object)this;
        if (self.getType().getSpawnGroup() == SpawnGroup.MONSTER) {
            cir.setReturnValue(false);
        }
    }
}
"""

SRC_1165_FABRIC_MOD = """\
package asd.itamio.nohostilemobs;

import net.fabricmc.api.ModInitializer;

public class NoHostileMobsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Spawn blocking handled via MobSpawnMixin
    }
}
"""

# ===========================================================================
# FABRIC 1.17–1.20.6 (presplit/split, yarn mappings)
# net.minecraft.entity.mob.MobEntity (1.17 uses same yarn package as 1.16.5)
# Actually 1.17+ uses net.minecraft.entity.mob.MobEntity (yarn)
# SpawnGroup.MONSTER still works
# ===========================================================================
SRC_117_FABRIC_MIXIN = """\
package asd.itamio.nohostilemobs.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.WorldAccess;
import net.minecraft.entity.SpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z",
            at = @At("HEAD"), cancellable = true)
    private void blockHostileSpawn(WorldAccess world, SpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity)(Object)this;
        if (self.getType().getSpawnGroup() == SpawnGroup.MONSTER) {
            cir.setReturnValue(false);
        }
    }
}
"""

SRC_117_FABRIC_MOD = SRC_1165_FABRIC_MOD


# ===========================================================================
# FABRIC 1.21–1.21.1 (split, Mojang mappings)
# net.minecraft.world.entity.Mob (Mojang class name)
# net.minecraft.world.entity.MobCategory.MONSTER
# checkSpawnRules(LevelAccessor, MobSpawnType) — MobSpawnType still exists here
# ===========================================================================
SRC_121_FABRIC_MIXIN = """\
package asd.itamio.nohostilemobs.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobSpawnMixin {
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void blockHostileSpawn(LevelAccessor level, MobSpawnType spawnType, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob)(Object)this;
        if (self.getType().getCategory() == MobCategory.MONSTER) {
            cir.setReturnValue(false);
        }
    }
}
"""

SRC_121_FABRIC_MOD = """\
package asd.itamio.nohostilemobs;

import net.fabricmc.api.ModInitializer;

public class NoHostileMobsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Spawn blocking handled via MobSpawnMixin
    }
}
"""

# ===========================================================================
# FABRIC 1.21.2–1.21.8 (split, Mojang mappings)
# MobSpawnType renamed to EntitySpawnReason in 1.21.2
# checkSpawnRules(LevelAccessor, EntitySpawnReason)
# ===========================================================================
SRC_1212_FABRIC_MIXIN = """\
package asd.itamio.nohostilemobs.mixin;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobSpawnMixin {
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void blockHostileSpawn(LevelAccessor level, EntitySpawnReason spawnReason, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob)(Object)this;
        if (self.getType().getCategory() == MobCategory.MONSTER) {
            cir.setReturnValue(false);
        }
    }
}
"""

SRC_1212_FABRIC_MOD = SRC_121_FABRIC_MOD

# ===========================================================================
# FABRIC 1.21.9–26.1.2 (split, Mojang mappings)
# Same as 1.21.2+ — EntitySpawnReason
# ===========================================================================
SRC_1219_FABRIC_MIXIN = SRC_1212_FABRIC_MIXIN
SRC_1219_FABRIC_MOD = SRC_121_FABRIC_MOD


# ===========================================================================
# NEOFORGE 1.20.2–1.20.6
# FinalizeSpawnEvent NOT accessible in NeoForge 20.2.x/20.4.x/20.6.x API jar
# Use EntityJoinLevelEvent (cancelable) to block hostile mobs
# net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
# ===========================================================================
SRC_120_NEO = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21–1.21.8
# Same as 1.20.x NeoForge
# ===========================================================================
SRC_121_NEO = SRC_120_NEO

# ===========================================================================
# NEOFORGE 1.21.9–1.21.11
# ModContainer required in constructor
# ===========================================================================
SRC_1219_NEO = """\
package asd.itamio.nohostilemobs;

import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@Mod(NoHostileMobsMod.MODID)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";

    public NoHostileMobsMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setSpawnCancelled(true);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 26.1–26.1.2
# FMLJavaModLoadingContext removed — constructor injection (IEventBus, ModContainer)
# Same FinalizeSpawnEvent API
# ===========================================================================
SRC_261_NEO = SRC_1219_NEO


# ===========================================================================
# TARGETS — all 67 missing versions
# Format: (folder_name, mc_version, loader, src_or_tuple, group, entrypoint)
# For Fabric targets with mixins, src is a tuple: (mod_src, mixin_src)
# ===========================================================================
TARGETS = [
    # ---- FORGE 1.8.9 ----
    ("NoHostileMobs-1.8.9-forge",      "1.8.9",   "forge",    SRC_189_FORGE,    GROUP, ENTRYPOINT),
    # ---- FORGE 1.16.5 ----
    ("NoHostileMobs-1.16.5-forge",     "1.16.5",  "forge",    SRC_1165_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.17.1 ----
    ("NoHostileMobs-1.17.1-forge",     "1.17.1",  "forge",    SRC_1171_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.18–1.18.2 ----
    ("NoHostileMobs-1.18-forge",       "1.18",    "forge",    SRC_118_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.18.1-forge",     "1.18.1",  "forge",    SRC_118_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.18.2-forge",     "1.18.2",  "forge",    SRC_118_FORGE,    GROUP, ENTRYPOINT),
    # ---- FORGE 1.19–1.19.3 (EntityJoinLevelEvent — MobSpawnEvent not in Forge 41.x–44.x) ----
    ("NoHostileMobs-1.19-forge",       "1.19",    "forge",    SRC_119_FORGE_EARLY, GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.1-forge",     "1.19.1",  "forge",    SRC_119_FORGE_EARLY, GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.2-forge",     "1.19.2",  "forge",    SRC_119_FORGE_EARLY, GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.3-forge",     "1.19.3",  "forge",    SRC_119_FORGE_EARLY, GROUP, ENTRYPOINT),
    # ---- FORGE 1.19.4–1.20.6 (MobSpawnEvent.FinalizeSpawn accessible from Forge 45.x) ----
    ("NoHostileMobs-1.19.4-forge",     "1.19.4",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.1-forge",     "1.20.1",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.2-forge",     "1.20.2",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.3-forge",     "1.20.3",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.4-forge",     "1.20.4",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.6-forge",     "1.20.6",  "forge",    SRC_119_FORGE,    GROUP, ENTRYPOINT),
    # ---- FORGE 1.21–1.21.5 ----
    ("NoHostileMobs-1.21-forge",       "1.21",    "forge",    SRC_121_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.1-forge",     "1.21.1",  "forge",    SRC_121_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.3-forge",     "1.21.3",  "forge",    SRC_121_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.4-forge",     "1.21.4",  "forge",    SRC_121_FORGE,    GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.5-forge",     "1.21.5",  "forge",    SRC_121_FORGE,    GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.6–1.21.8 (EventBus 7) ----
    ("NoHostileMobs-1.21.6-forge",     "1.21.6",  "forge",    SRC_1216_FORGE,   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.7-forge",     "1.21.7",  "forge",    SRC_1216_FORGE,   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.8-forge",     "1.21.8",  "forge",    SRC_1216_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.9–26.1.2 (EventBus 7) ----
    ("NoHostileMobs-1.21.9-forge",     "1.21.9",  "forge",    SRC_1219_FORGE,   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.10-forge",    "1.21.10", "forge",    SRC_1219_FORGE,   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.11-forge",    "1.21.11", "forge",    SRC_1219_FORGE,   GROUP, ENTRYPOINT),
    ("NoHostileMobs-26.1.2-forge",     "26.1.2",  "forge",    SRC_1219_FORGE,   GROUP, ENTRYPOINT),
    # ---- FABRIC 1.16.5 ----
    ("NoHostileMobs-1.16.5-fabric",    "1.16.5",  "fabric",   (SRC_1165_FABRIC_MOD, SRC_1165_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    # ---- FABRIC 1.17 ----
    ("NoHostileMobs-1.17-fabric",      "1.17",    "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    # ---- FABRIC 1.18–1.20.6 ----
    ("NoHostileMobs-1.18-fabric",      "1.18",    "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19-fabric",      "1.19",    "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.1-fabric",    "1.19.1",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.2-fabric",    "1.19.2",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.3-fabric",    "1.19.3",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.19.4-fabric",    "1.19.4",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.1-fabric",    "1.20.1",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.2-fabric",    "1.20.2",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.3-fabric",    "1.20.3",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.4-fabric",    "1.20.4",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.5-fabric",    "1.20.5",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.6-fabric",    "1.20.6",  "fabric",   (SRC_117_FABRIC_MOD, SRC_117_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    # ---- FABRIC 1.21–1.21.1 (Mojang mappings, MobSpawnType) ----
    ("NoHostileMobs-1.21-fabric",      "1.21",    "fabric",   (SRC_121_FABRIC_MOD, SRC_121_FABRIC_MIXIN),   GROUP, ENTRYPOINT),
    # ---- FABRIC 1.21.2–1.21.8 (Mojang mappings, EntitySpawnReason) ----
    ("NoHostileMobs-1.21.2-fabric",    "1.21.2",  "fabric",   (SRC_1212_FABRIC_MOD, SRC_1212_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    # ---- FABRIC 1.21.9–1.21.11 (EntitySpawnReason) ----
    ("NoHostileMobs-1.21.9-fabric",    "1.21.9",  "fabric",   (SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    # ---- FABRIC 26.1–26.1.2 (EntitySpawnReason) ----
    ("NoHostileMobs-26.1-fabric",      "26.1",    "fabric",   (SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    ("NoHostileMobs-26.1.1-fabric",    "26.1.1",  "fabric",   (SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    ("NoHostileMobs-26.1.2-fabric",    "26.1.2",  "fabric",   (SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_MIXIN), GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.20.2–1.20.6 ----
    ("NoHostileMobs-1.20.2-neoforge",  "1.20.2",  "neoforge", SRC_120_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.4-neoforge",  "1.20.4",  "neoforge", SRC_120_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.5-neoforge",  "1.20.5",  "neoforge", SRC_120_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.20.6-neoforge",  "1.20.6",  "neoforge", SRC_120_NEO,      GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.21–1.21.8 ----
    ("NoHostileMobs-1.21-neoforge",    "1.21",    "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.1-neoforge",  "1.21.1",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.2-neoforge",  "1.21.2",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.3-neoforge",  "1.21.3",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.4-neoforge",  "1.21.4",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.5-neoforge",  "1.21.5",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.6-neoforge",  "1.21.6",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.7-neoforge",  "1.21.7",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.8-neoforge",  "1.21.8",  "neoforge", SRC_121_NEO,      GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.21.9–1.21.11 (ModContainer required) ----
    ("NoHostileMobs-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO,     GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO,     GROUP, ENTRYPOINT),
    ("NoHostileMobs-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO,     GROUP, ENTRYPOINT),
    # ---- NEOFORGE 26.1–26.1.2 ----
    ("NoHostileMobs-26.1-neoforge",    "26.1",    "neoforge", SRC_261_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-26.1.1-neoforge",  "26.1.1",  "neoforge", SRC_261_NEO,      GROUP, ENTRYPOINT),
    ("NoHostileMobs-26.1.2-neoforge",  "26.1.2",  "neoforge", SRC_261_NEO,      GROUP, ENTRYPOINT),
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
        if mods_dir.exists() and any("nohostilemobs" in m.name.lower() for m in mods_dir.iterdir()):
            run_dir = r
            break
    if run_dir is None:
        print("No run with nohostilemobs mods found.", file=sys.stderr)
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


def generate_target(folder_name: str, mc: str, loader: str, src,
                    group: str, entrypoint: str) -> None:
    pkg_path = group.replace(".", "/")
    class_name = entrypoint.split(".")[-1]
    base = BUNDLE_DIR / folder_name
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc, loader))

    if isinstance(src, tuple):
        # Fabric with mixin: (mod_src, mixin_src)
        mod_src, mixin_src = src
        write(base / "src" / "main" / "java" / pkg_path / f"{class_name}.java", mod_src)
        mixin_pkg_path = (group + ".mixin").replace(".", "/")
        write(base / "src" / "main" / "java" / mixin_pkg_path / "MobSpawnMixin.java", mixin_src)
    else:
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
