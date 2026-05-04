#!/usr/bin/env python3
"""
Generator for World No Particles — all missing versions bundle.
Mod: https://modrinth.com/mod/world-no-particles
Client-side only mod: disables all particle effects.

Already published (skip these):
  1.12.2  forge

Run:
    python3 scripts/generate_no_particles_bundle.py
    python3 scripts/generate_no_particles_bundle.py --failed-only

Key API notes:
  - 1.8.9 Forge: ASM transformer targeting World.spawnParticle + ParticleManager.addEffect
  - 1.12.x Forge: Already published (ASM transformer)
  - 1.16.5-1.21.5 Forge: TickEvent.ClientTickEvent + reflection to clear particles map
  - 1.21.6+ Forge: EventBus 7 pattern (TickEvent.ClientTickEvent.Post.BUS.addListener)
  - Fabric 1.16.5-1.20.x: ClientTickEvents + reflection to clear particles map (Yarn: particleManager)
  - Fabric 1.21+: ClientTickEvents + reflection to clear particles map (Mojang: particleEngine)
  - NeoForge 1.20.x: LevelTickEvent.Post (client-side) + reflection to clear particles
  - NeoForge 1.21+: ClientTickEvent.Post + reflection to clear particles
  - NeoForge 1.21.9+: ModContainer required in constructor
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "no-particles-all-versions"
ZIP_PATH = ROOT / "incoming" / "no-particles-all-versions.zip"

MOD_ID = "noparticles"
MOD_NAME = "World No Particles"
MOD_VERSION = "1.0.0"
GROUP = "asd.itamio.noparticles"
DESCRIPTION = "Completely disables all particle effects for maximum FPS."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/world-no-particles"


def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")


def mod_txt(entrypoint: str) -> str:
    return (
        f"mod_id={MOD_ID}\n"
        f"name={MOD_NAME}\n"
        f"mod_version={MOD_VERSION}\n"
        f"group={GROUP}\n"
        f"entrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\n"
        f"authors={AUTHORS}\n"
        f"license={LICENSE}\n"
        f"homepage={HOMEPAGE}\n"
        f"runtime_side=client\n"
    )


def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


PKG = "asd/itamio/noparticles"
PKG_DOT = "asd.itamio.noparticles"


# ===========================================================================
# FORGE 1.8.9 — ASM transformer (Java 6 compat, no lambdas, no diamonds)
# Targets: World.spawnParticle (func_72869_a) + EffectRenderer.addEffect (func_78873_a)
# ===========================================================================
SRC_189_MOD = """\
package asd.itamio.noparticles;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

@Mod(modid = NoParticlesMod.MODID, name = "World No Particles", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class NoParticlesMod {
    public static final String MODID = "noparticles";
    public static Logger logger;

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("World No Particles initialized - all particles disabled!");
    }
}
"""

SRC_189_TRANSFORMER = """\
package asd.itamio.noparticles.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ParticleTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.world.World")) {
            return transformWorld(basicClass);
        }
        if (transformedName.equals("net.minecraft.client.particle.EffectRenderer")) {
            return transformEffectRenderer(basicClass);
        }
        return basicClass;
    }

    private byte[] transformWorld(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("spawnParticle") || method.name.equals("func_72869_a")) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private byte[] transformEffectRenderer(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("addEffect") || method.name.equals("func_78873_a")) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
"""

SRC_189_PLUGIN = """\
package asd.itamio.noparticles.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.Name("NoParticlesCore")
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.SortingIndex(1001)
public class NoParticlesLoadingPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "asd.itamio.noparticles.asm.ParticleTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
"""


# ===========================================================================
# FORGE 1.16.5-1.21.5 — TickEvent.ClientTickEvent + reflection to clear particles
# mc.particleEngine is a public field; clearParticles() may be private so use reflection
# ===========================================================================
SRC_1165_FORGE_MOD = """\
package asd.itamio.noparticles;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("noparticles")
public class NoParticlesMod {
    public NoParticlesMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new NoParticlesHandler());
    }
}
"""

SRC_1165_FORGE_HANDLER = """\
package asd.itamio.noparticles;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class NoParticlesHandler {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft mc) {
        try {
            if (mc.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = mc.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(mc.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        clearParticles(mc);
    }
}
"""

# 1.17.1, 1.18.x, 1.19.x, 1.20.x, 1.21-1.21.5 Forge — same pattern
SRC_171_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_171_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_118_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_118_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_119_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_119_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_120_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_120_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_121_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_121_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.21.6-1.21.8 — EventBus 7, constructor takes FMLJavaModLoadingContext
# TickEvent.ClientTickEvent.Post.BUS.addListener()
# ===========================================================================
SRC_1216_FORGE_MOD = """\
package asd.itamio.noparticles;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("noparticles")
public class NoParticlesMod {
    public NoParticlesMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(NoParticlesHandler::onClientTick);
        }
    }
}
"""

SRC_1216_FORGE_HANDLER = """\
package asd.itamio.noparticles;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class NoParticlesHandler {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft mc) {
        try {
            if (mc.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = mc.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(mc.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        clearParticles(mc);
    }
}
"""

# 1.21.9-1.21.11 and 26.1.2 Forge — same EventBus 7 pattern
SRC_1219_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_1219_FORGE_HANDLER = SRC_1216_FORGE_HANDLER
SRC_261_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_261_FORGE_HANDLER = SRC_1216_FORGE_HANDLER


# ===========================================================================
# FABRIC 1.16.5-1.20.x — Yarn mappings: MinecraftClient, particleManager
# clearParticles() is private — use reflection to clear the particles map
# ===========================================================================
SRC_1165_FABRIC = """\
package asd.itamio.noparticles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public class NoParticlesMod implements ClientModInitializer {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(MinecraftClient client) {
        try {
            if (client.particleManager == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = client.particleManager.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(client.particleManager);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            clearParticles(client);
        });
    }
}
"""

SRC_117_FABRIC = SRC_1165_FABRIC
SRC_118_FABRIC = SRC_1165_FABRIC
SRC_119_FABRIC = SRC_1165_FABRIC
SRC_120_FABRIC = SRC_1165_FABRIC

# ===========================================================================
# FABRIC 1.21+ — Mojang mappings: Minecraft, particleEngine
# clearParticles() is public in 1.21+ — call directly
# ===========================================================================
SRC_121_FABRIC = """\
package asd.itamio.noparticles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class NoParticlesMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (client.particleEngine != null) {
                client.particleEngine.clearParticles();
            }
        });
    }
}
"""

SRC_1212_FABRIC = SRC_121_FABRIC
SRC_1219_FABRIC = SRC_121_FABRIC
SRC_261_FABRIC = SRC_121_FABRIC

# ===========================================================================
# NEOFORGE 1.20.2-1.20.6 — ClientTickEvent does NOT exist in early build (20.2.93)
# Use LevelTickEvent.Post which fires on client side when client level ticks
# ===========================================================================
SRC_120_NEO_MOD = """\
package asd.itamio.noparticles;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("noparticles")
public class NoParticlesMod {
    public NoParticlesMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new NoParticlesHandler());
    }
}
"""

SRC_120_NEO_HANDLER = """\
package asd.itamio.noparticles;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class NoParticlesHandler {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft mc) {
        try {
            if (mc.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = mc.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(mc.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        clearParticles(mc);
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21-1.21.8 — ClientTickEvent exists in net.neoforged.neoforge.client.event
# ===========================================================================
SRC_121_NEO_MOD = """\
package asd.itamio.noparticles;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("noparticles")
public class NoParticlesMod {
    public NoParticlesMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new NoParticlesHandler());
    }
}
"""

SRC_121_NEO_HANDLER = """\
package asd.itamio.noparticles;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class NoParticlesHandler {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft mc) {
        try {
            if (mc.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = mc.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(mc.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        clearParticles(mc);
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21.9-1.21.11 — ModContainer required in constructor
# FMLEnvironment.getDist() (method call, not field)
# ===========================================================================
SRC_1219_NEO_MOD = """\
package asd.itamio.noparticles;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("noparticles")
public class NoParticlesMod {
    public NoParticlesMod(IEventBus modBus, ModContainer modContainer) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new NoParticlesHandler());
    }
}
"""

SRC_1219_NEO_HANDLER = SRC_121_NEO_HANDLER

# ===========================================================================
# NEOFORGE 26.1-26.1.2 — FMLJavaModLoadingContext removed, same as 1.21.9+
# ===========================================================================
SRC_261_NEO_MOD = SRC_1219_NEO_MOD
SRC_261_NEO_HANDLER = SRC_121_NEO_HANDLER


# ===========================================================================
# TARGET MATRIX
# ===========================================================================
EP = f"{PKG_DOT}.NoParticlesMod"

TARGETS = [
    # ---- FORGE ----
    # 1.8.9 — ASM transformer (special: 3 files, needs MANIFEST.MF)
    ("NP-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_MOD,        None,                   EP, False, True),
    # 1.12 — same ASM as 1.12.2 (already published: 1.12.2, but 1.12 is missing)
    # Note: 1.12 uses the same template as 1.12.2 (anchor_only)
    # We reuse the 1.12.2 ASM approach but target 1.12
    ("NP-1.12-forge",     "1.12",    "forge",    SRC_189_MOD,        None,                   EP, False, True),
    # 1.16.5+
    ("NP-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE_MOD, SRC_1165_FORGE_HANDLER, EP, False, False),
    ("NP-1.17.1-forge",   "1.17.1",  "forge",    SRC_171_FORGE_MOD,  SRC_171_FORGE_HANDLER,  EP, False, False),
    ("NP-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False, False),
    ("NP-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False, False),
    ("NP-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False, False),
    ("NP-1.19-forge",     "1.19",    "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False, False),
    ("NP-1.19.1-forge",   "1.19.1",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False, False),
    ("NP-1.19.2-forge",   "1.19.2",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False, False),
    ("NP-1.19.3-forge",   "1.19.3",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False, False),
    ("NP-1.19.4-forge",   "1.19.4",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False, False),
    ("NP-1.20.1-forge",   "1.20.1",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False, False),
    ("NP-1.20.2-forge",   "1.20.2",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False, False),
    ("NP-1.20.3-forge",   "1.20.3",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False, False),
    ("NP-1.20.4-forge",   "1.20.4",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False, False),
    ("NP-1.20.6-forge",   "1.20.6",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21-forge",     "1.21",    "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21.1-forge",   "1.21.1",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21.3-forge",   "1.21.3",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21.4-forge",   "1.21.4",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21.5-forge",   "1.21.5",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False, False),
    ("NP-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False, False),
    ("NP-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False, False),
    ("NP-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False, False),
    ("NP-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False, False),
    ("NP-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False, False),
    ("NP-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False, False),
    ("NP-26.1.2-forge",   "26.1.2",  "forge",    SRC_261_FORGE_MOD,  SRC_261_FORGE_HANDLER,  EP, False, False),

    # ---- FABRIC (presplit = src/main/java) ----
    ("NP-1.16.5-fabric",       "1.16.5",        "fabric", SRC_1165_FABRIC, None, EP, False, False),
    ("NP-1.17-fabric",         "1.17",          "fabric", SRC_117_FABRIC,  None, EP, False, False),
    ("NP-1.18-1.18.2-fabric",  "1.18-1.18.2",   "fabric", SRC_118_FABRIC,  None, EP, False, False),
    ("NP-1.19-1.19.4-fabric",  "1.19-1.19.4",   "fabric", SRC_119_FABRIC,  None, EP, False, False),

    # ---- FABRIC (split = src/client/java) ----
    ("NP-1.20-1.20.6-fabric",   "1.20.1-1.20.6", "fabric", SRC_120_FABRIC,  None, EP, True,  False),
    ("NP-1.21-1.21.1-fabric",   "1.21-1.21.1",   "fabric", SRC_121_FABRIC,  None, EP, True,  False),
    ("NP-1.21.2-1.21.8-fabric", "1.21.2-1.21.8", "fabric", SRC_1212_FABRIC, None, EP, True,  False),
    ("NP-1.21.9-1.21.11-fabric","1.21.9-1.21.11","fabric", SRC_1219_FABRIC, None, EP, True,  False),
    ("NP-26.1-fabric",          "26.1",          "fabric", SRC_261_FABRIC,  None, EP, True,  False),
    ("NP-26.1.1-fabric",        "26.1.1",        "fabric", SRC_261_FABRIC,  None, EP, True,  False),
    ("NP-26.1.2-fabric",        "26.1.2",        "fabric", SRC_261_FABRIC,  None, EP, True,  False),

    # ---- NEOFORGE ----
    ("NP-1.20.2-neoforge",  "1.20.2",  "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  EP, False, False),
    ("NP-1.20.4-neoforge",  "1.20.4",  "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  EP, False, False),
    ("NP-1.20.5-neoforge",  "1.20.5",  "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  EP, False, False),
    ("NP-1.20.6-neoforge",  "1.20.6",  "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  EP, False, False),
    ("NP-1.21-neoforge",    "1.21",    "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.1-neoforge",  "1.21.1",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.2-neoforge",  "1.21.2",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.3-neoforge",  "1.21.3",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.4-neoforge",  "1.21.4",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.5-neoforge",  "1.21.5",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.6-neoforge",  "1.21.6",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.7-neoforge",  "1.21.7",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.8-neoforge",  "1.21.8",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False, False),
    ("NP-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False, False),
    ("NP-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False, False),
    ("NP-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False, False),
    ("NP-26.1-neoforge",    "26.1",    "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False, False),
    ("NP-26.1.1-neoforge",  "26.1.1",  "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False, False),
    ("NP-26.1.2-neoforge",  "26.1.2",  "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False, False),
]


# ===========================================================================
# MANIFEST.MF content for ASM loading plugin (1.8.9 and 1.12)
# ===========================================================================
MANIFEST_189 = """\
Manifest-Version: 1.0
FMLCorePlugin: asd.itamio.noparticles.asm.NoParticlesLoadingPlugin
FMLCorePluginContainsFMLMod: true
"""

MANIFEST_112 = """\
Manifest-Version: 1.0
FMLCorePlugin: asd.itamio.noparticles.asm.NoParticlesLoadingPlugin
FMLCorePluginContainsFMLMod: true
"""

# 1.12 mod main class (same as 1.12.2 but acceptedMinecraftVersions covers 1.12)
SRC_112_MOD = """\
package asd.itamio.noparticles;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

@Mod(modid = NoParticlesMod.MODID, name = "World No Particles", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class NoParticlesMod {
    public static final String MODID = "noparticles";
    public static Logger logger;

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("World No Particles initialized - all particles disabled!");
    }
}
"""

SRC_112_TRANSFORMER = """\
package asd.itamio.noparticles.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ParticleTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.world.World")) {
            return transformWorld(basicClass);
        }
        if (transformedName.equals("net.minecraft.client.particle.ParticleManager")) {
            return transformParticleManager(basicClass);
        }
        return basicClass;
    }

    private byte[] transformWorld(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("spawnParticle") || method.name.equals("func_175688_a")) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private byte[] transformParticleManager(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("addEffect") || method.name.equals("func_78873_a")) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
"""

SRC_112_PLUGIN = """\
package asd.itamio.noparticles.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.Name("NoParticlesCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class NoParticlesLoadingPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "asd.itamio.noparticles.asm.ParticleTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
"""



def get_failed_targets():
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return None
    runs = sorted(runs_dir.iterdir(), reverse=True)
    for run in runs:
        result_file = run / "result.json"
        if result_file.exists():
            try:
                data = json.loads(result_file.read_text())
                failed = set()
                for job in data.get("jobs", []):
                    if job.get("status") != "success":
                        name = job.get("name", "")
                        # Extract folder name from job name
                        for t in TARGETS:
                            if t[0] in name:
                                failed.add(t[0])
                                break
                print(f"Reading failures from: {run.name}")
                print(f"Failed targets: {sorted(failed)}")
                return failed
            except Exception as e:
                print(f"Warning: could not parse {result_file}: {e}")
    return None


def write_target(folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset, is_asm):
    base = BUNDLE_DIR / folder_name
    write(base / "mod.txt", mod_txt(entrypoint))
    write(base / "version.txt", version_txt(mc_version, loader))

    if client_srcset:
        java_dir = base / "src" / "client" / "java"
    else:
        java_dir = base / "src" / "main" / "java"

    if is_asm:
        # ASM targets: write mod main + transformer + plugin + MANIFEST.MF
        if mc_version == "1.8.9":
            write(java_dir / PKG / "NoParticlesMod.java", SRC_189_MOD)
            write(java_dir / PKG / "asm" / "ParticleTransformer.java", SRC_189_TRANSFORMER)
            write(java_dir / PKG / "asm" / "NoParticlesLoadingPlugin.java", SRC_189_PLUGIN)
            write(base / "src" / "main" / "resources" / "META-INF" / "MANIFEST.MF", MANIFEST_189)
        else:
            # 1.12
            write(java_dir / PKG / "NoParticlesMod.java", SRC_112_MOD)
            write(java_dir / PKG / "asm" / "ParticleTransformer.java", SRC_112_TRANSFORMER)
            write(java_dir / PKG / "asm" / "NoParticlesLoadingPlugin.java", SRC_112_PLUGIN)
            write(base / "src" / "main" / "resources" / "META-INF" / "MANIFEST.MF", MANIFEST_112)
    else:
        write(java_dir / PKG / "NoParticlesMod.java", mod_src)
        if handler_src is not None:
            write(java_dir / PKG / "NoParticlesHandler.java", handler_src)


def build_zip(targets_to_include):
    ZIP_PATH.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for t in targets_to_include:
            folder_name = t[0]
            folder = BUNDLE_DIR / folder_name
            for file in sorted(folder.rglob("*")):
                if file.is_file():
                    zf.write(file, file.relative_to(BUNDLE_DIR))
    print(f"Wrote {ZIP_PATH} ({ZIP_PATH.stat().st_size // 1024} KB, {len(targets_to_include)} targets)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true")
    args = parser.parse_args()

    if args.failed_only:
        failed = get_failed_targets()
        if failed is None:
            print("No previous run found — generating all targets")
            targets = TARGETS
        elif not failed:
            print("No failed targets in last run — nothing to do")
            sys.exit(0)
        else:
            targets = [t for t in TARGETS if t[0] in failed]
            if not targets:
                print("Warning: failed set did not match any TARGETS — generating all")
                targets = TARGETS
    else:
        targets = TARGETS

    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Generating {len(targets)} target(s)...")
    for t in targets:
        folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset, is_asm = t
        print(f"  [{folder_name}] mc={mc_version} loader={loader}")
        write_target(folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset, is_asm)

    build_zip(targets)

    print("\nNext steps:")
    print("  git add incoming/no-particles-all-versions/ incoming/no-particles-all-versions.zip")
    print("  git commit -m 'Add World No Particles all-versions bundle (68 missing targets)'")
    print("  git push")
    print("  python3 scripts/run_build.py incoming/no-particles-all-versions.zip --modrinth https://modrinth.com/mod/world-no-particles --max-parallel all")


if __name__ == "__main__":
    main()
