#!/usr/bin/env python3
"""
Generator for Working Full Bright — all missing versions bundle.
Mod: https://modrinth.com/mod/working-full-bright
Client-side only mod: sets gamma to 15.0 on every client tick.

Already published (skip these):
  1.12.2  forge

Run:
    python3 scripts/generate_fullbright_bundle.py
    python3 scripts/generate_fullbright_bundle.py --failed-only

Key API notes:
  - 1.8.9/1.12.2 Forge: mc.gameSettings.gammaSetting (public float field)
  - 1.16.5-1.18.2 Forge/Fabric: mc.options.gamma (public double field)
  - 1.19+ Forge/NeoForge/Fabric 1.21+: gamma is a PRIVATE SimpleOption<Double>
    on Options — must use double reflection:
      1. Field optGamma = Options.class.getDeclaredField("gamma"); setAccessible
      2. SimpleOption<?> opt = (SimpleOption<?>) optGamma.get(mc.options);
      3. Field valField = opt.getClass().getDeclaredField("value"); setAccessible
      4. valField.set(opt, 15.0);
  - NeoForge 1.20.x: ClientTickEvent doesn't exist in early build (20.2.93),
    use RenderFrameEvent.Pre instead.
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "fullbright-all-versions"
ZIP_PATH = ROOT / "incoming" / "fullbright-all-versions.zip"

MOD_ID = "fullbright"
MOD_NAME = "Working Full Bright"
MOD_VERSION = "1.0.0"
GROUP = "asd.itamio.fullbright"
DESCRIPTION = "A full bright mod that just works."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/working-full-bright"


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


PKG = "asd/itamio/fullbright"
PKG_DOT = "asd.itamio.fullbright"

# ===========================================================================
# FORGE 1.8.9 — Java 6, mc.gameSettings.gammaSetting (public float field)
# ===========================================================================
SRC_189_MOD = """\
package asd.itamio.fullbright;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = FullBrightMod.MODID, name = "Working Full Bright", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FullBrightMod {
    public static final String MODID = "fullbright";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
"""

SRC_189_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FullBrightHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        mc.gameSettings.gammaSetting = 15.0F;
    }
}
"""

# ===========================================================================
# FORGE 1.12.2 — same as 1.8.9 but mc.player (not mc.thePlayer)
# ===========================================================================
SRC_1122_MOD = """\
package asd.itamio.fullbright;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = FullBrightMod.MODID, name = "Working Full Bright", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class FullBrightMod {
    public static final String MODID = "fullbright";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
"""

SRC_1122_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FullBrightHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        mc.gameSettings.gammaSetting = 15.0F;
    }
}
"""


# ===========================================================================
# FORGE 1.16.5-1.18.2 — mc.options.gamma is a PUBLIC double field
# ===========================================================================
SRC_1165_FORGE_MOD = """\
package asd.itamio.fullbright;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
"""

SRC_1165_FORGE_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // gamma is a public double field in 1.16.5-1.18.2
        mc.options.gamma = 15.0;
    }
}
"""

# 1.17.1 and 1.18.x Forge — same (gamma still public double field)
SRC_171_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_171_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_118_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_118_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.19-1.21.5 — gamma is a PRIVATE SimpleOption<Double> on Options
# Must use double reflection:
#   1. Get "gamma" field from Options class (private)
#   2. Get "value" field from SimpleOption class (private)
#   3. Set value directly, bypassing DoubleSliderCallbacks clamping
# ===========================================================================
SRC_119_FORGE_MOD = SRC_1165_FORGE_MOD

SRC_119_FORGE_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (optionsGammaField == null) {
                // gamma is a private field on Options (net.minecraft.client.Options)
                optionsGammaField = mc.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(mc.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore — gamma stays at whatever it was
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
"""

# 1.20.x and 1.21-1.21.5 Forge — same reflection approach
SRC_120_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_120_FORGE_HANDLER = SRC_119_FORGE_HANDLER
SRC_121_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_121_FORGE_HANDLER = SRC_119_FORGE_HANDLER


# ===========================================================================
# FORGE 1.21.6-1.21.8 — EventBus 7, TickEvent.ClientTickEvent.Post.BUS
# Constructor takes FMLJavaModLoadingContext
# gamma still private SimpleOption<Double> — use reflection
# ===========================================================================
SRC_1216_FORGE_MOD = """\
package asd.itamio.fullbright;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(FullBrightHandler::onClientTick);
        }
    }
}
"""

SRC_1216_FORGE_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = mc.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(mc.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
"""

# 1.21.9-1.21.11 and 26.1.2 Forge — same EventBus 7 pattern
SRC_1219_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_1219_FORGE_HANDLER = SRC_1216_FORGE_HANDLER
SRC_261_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_261_FORGE_HANDLER = SRC_1216_FORGE_HANDLER


# ===========================================================================
# FABRIC 1.16.5-1.18.2 — presplit, options.gamma is a PUBLIC double field
# ===========================================================================
SRC_1165_FABRIC = """\
package asd.itamio.fullbright;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Environment(EnvType.CLIENT)
public class FullBrightMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            // gamma is a public double field in 1.16.5-1.18.2
            client.options.gamma = 15.0;
        });
    }
}
"""

SRC_117_FABRIC = SRC_1165_FABRIC
SRC_118_FABRIC = SRC_1165_FABRIC

# ===========================================================================
# FABRIC 1.19-1.20.x — presplit (1.19.x) / split (1.20.x)
# gamma is a PRIVATE SimpleOption<Double> — use double reflection
# Yarn mappings: MinecraftClient, options.getGamma() is a getter but
# the field itself is private — access via getDeclaredField("gamma")
# ===========================================================================
SRC_119_FABRIC = """\
package asd.itamio.fullbright;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public class FullBrightMod implements ClientModInitializer {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(MinecraftClient client, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = client.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(client.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            setGamma(client, 15.0);
        });
    }
}
"""

# 1.20.x Fabric — same reflection approach (still Yarn mappings, MinecraftClient)
SRC_120_FABRIC = SRC_119_FABRIC

# ===========================================================================
# FABRIC 1.21+ — split adapter, Mojang mappings: Minecraft (not MinecraftClient)
# gamma is still a PRIVATE SimpleOption<Double> — same double reflection
# client.player (not client.player), client.level (not client.world)
# ===========================================================================
SRC_121_FABRIC = """\
package asd.itamio.fullbright;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public class FullBrightMod implements ClientModInitializer {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft client, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = client.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(client.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            setGamma(client, 15.0);
        });
    }
}
"""

SRC_1212_FABRIC = SRC_121_FABRIC
SRC_1219_FABRIC = SRC_121_FABRIC
SRC_261_FABRIC = SRC_121_FABRIC


# ===========================================================================
# NEOFORGE 1.20.2-1.20.6 — NEITHER ClientTickEvent NOR RenderFrameEvent exist
# in the early build (NeoForge 20.2.93) that the template resolves to.
# The decompiled sources were generated with a newer build — do NOT trust them.
# Fix: set gamma once in FMLClientSetupEvent (runs on client thread after init).
# Gamma persists in options.txt so setting it once is sufficient.
# ===========================================================================
SRC_120_NEO_MOD = """\
package asd.itamio.fullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Set gamma once at startup — persists in options.txt
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            try {
                Field gammaField = mc.options.getClass().getDeclaredField("gamma");
                gammaField.setAccessible(true);
                Object gammaOption = gammaField.get(mc.options);
                if (gammaOption == null) return;
                Field valueField = gammaOption.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                valueField.set(gammaOption, 15.0);
            } catch (Exception e) {
                // ignore
            }
        });
    }
}
"""

# No handler needed — single-file mod
SRC_120_NEO_HANDLER = None

# ===========================================================================
# NEOFORGE 1.21-1.21.8 — ClientTickEvent exists in net.neoforged.neoforge.client.event
# gamma is private SimpleOption<Double> — use double reflection
# ===========================================================================
SRC_121_NEO_MOD = """\
package asd.itamio.fullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
"""

SRC_121_NEO_HANDLER = """\
package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = mc.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(mc.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21.9-1.21.11 — ModContainer required in constructor
# FMLEnvironment.getDist() (method call)
# ===========================================================================
SRC_1219_NEO_MOD = """\
package asd.itamio.fullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(IEventBus modBus, ModContainer modContainer) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
"""

SRC_1219_NEO_HANDLER = SRC_121_NEO_HANDLER

# ===========================================================================
# NEOFORGE 26.1-26.1.2 — FMLJavaModLoadingContext removed, constructor injection
# Same as 1.21.9+ pattern
# ===========================================================================
SRC_261_NEO_MOD = SRC_1219_NEO_MOD
SRC_261_NEO_HANDLER = SRC_121_NEO_HANDLER


# ===========================================================================
# TARGET MATRIX
# ===========================================================================
EP = f"{PKG_DOT}.FullBrightMod"

TARGETS = [
    # ---- FORGE ----
    ("FB-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_MOD,        SRC_189_HANDLER,        EP, False),
    ("FB-1.12-forge",     "1.12",    "forge",    SRC_1122_MOD,       SRC_1122_HANDLER,       EP, False),
    ("FB-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE_MOD, SRC_1165_FORGE_HANDLER, EP, False),
    ("FB-1.17.1-forge",   "1.17.1",  "forge",    SRC_171_FORGE_MOD,  SRC_171_FORGE_HANDLER,  EP, False),
    ("FB-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False),
    ("FB-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False),
    ("FB-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  EP, False),
    ("FB-1.19-forge",     "1.19",    "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False),
    ("FB-1.19.1-forge",   "1.19.1",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False),
    ("FB-1.19.2-forge",   "1.19.2",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False),
    ("FB-1.19.3-forge",   "1.19.3",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False),
    ("FB-1.19.4-forge",   "1.19.4",  "forge",    SRC_119_FORGE_MOD,  SRC_119_FORGE_HANDLER,  EP, False),
    ("FB-1.20.1-forge",   "1.20.1",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False),
    ("FB-1.20.2-forge",   "1.20.2",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False),
    ("FB-1.20.3-forge",   "1.20.3",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False),
    ("FB-1.20.4-forge",   "1.20.4",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False),
    ("FB-1.20.6-forge",   "1.20.6",  "forge",    SRC_120_FORGE_MOD,  SRC_120_FORGE_HANDLER,  EP, False),
    ("FB-1.21-forge",     "1.21",    "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False),
    ("FB-1.21.1-forge",   "1.21.1",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False),
    ("FB-1.21.3-forge",   "1.21.3",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False),
    ("FB-1.21.4-forge",   "1.21.4",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False),
    ("FB-1.21.5-forge",   "1.21.5",  "forge",    SRC_121_FORGE_MOD,  SRC_121_FORGE_HANDLER,  EP, False),
    ("FB-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False),
    ("FB-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False),
    ("FB-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, EP, False),
    ("FB-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False),
    ("FB-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False),
    ("FB-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, EP, False),
    ("FB-26.1.2-forge",   "26.1.2",  "forge",    SRC_261_FORGE_MOD,  SRC_261_FORGE_HANDLER,  EP, False),

    # ---- FABRIC (presplit = src/main/java) ----
    ("FB-1.16.5-fabric",       "1.16.5",        "fabric", SRC_1165_FABRIC, None, EP, False),
    ("FB-1.17-fabric",         "1.17",          "fabric", SRC_117_FABRIC,  None, EP, False),
    ("FB-1.18-1.18.2-fabric",  "1.18-1.18.2",   "fabric", SRC_118_FABRIC,  None, EP, False),
    ("FB-1.19-1.19.4-fabric",  "1.19-1.19.4",   "fabric", SRC_119_FABRIC,  None, EP, False),

    # ---- FABRIC (split = src/client/java) ----
    ("FB-1.20-1.20.6-fabric",   "1.20.1-1.20.6", "fabric", SRC_120_FABRIC,  None, EP, True),
    ("FB-1.21-1.21.1-fabric",   "1.21-1.21.1",   "fabric", SRC_121_FABRIC,  None, EP, True),
    ("FB-1.21.2-1.21.8-fabric", "1.21.2-1.21.8", "fabric", SRC_1212_FABRIC, None, EP, True),
    ("FB-1.21.9-1.21.11-fabric","1.21.9-1.21.11","fabric", SRC_1219_FABRIC, None, EP, True),
    ("FB-26.1-fabric",          "26.1",          "fabric", SRC_261_FABRIC,  None, EP, True),
    ("FB-26.1.1-fabric",        "26.1.1",        "fabric", SRC_261_FABRIC,  None, EP, True),
    ("FB-26.1.2-fabric",        "26.1.2",        "fabric", SRC_261_FABRIC,  None, EP, True),

    # ---- NEOFORGE ----
    ("FB-1.20.2-neoforge",  "1.20.2",  "neoforge", SRC_120_NEO_MOD,  None,                 EP, False),
    ("FB-1.20.4-neoforge",  "1.20.4",  "neoforge", SRC_120_NEO_MOD,  None,                 EP, False),
    ("FB-1.20.5-neoforge",  "1.20.5",  "neoforge", SRC_120_NEO_MOD,  None,                 EP, False),
    ("FB-1.20.6-neoforge",  "1.20.6",  "neoforge", SRC_120_NEO_MOD,  None,                 EP, False),
    ("FB-1.21-neoforge",    "1.21",    "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.1-neoforge",  "1.21.1",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.2-neoforge",  "1.21.2",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.3-neoforge",  "1.21.3",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.4-neoforge",  "1.21.4",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.5-neoforge",  "1.21.5",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.6-neoforge",  "1.21.6",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.7-neoforge",  "1.21.7",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.8-neoforge",  "1.21.8",  "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  EP, False),
    ("FB-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False),
    ("FB-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False),
    ("FB-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, EP, False),
    ("FB-26.1-neoforge",    "26.1",    "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False),
    ("FB-26.1.1-neoforge",  "26.1.1",  "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False),
    ("FB-26.1.2-neoforge",  "26.1.2",  "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  EP, False),
]


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
                        failed.add(name)
                print(f"Reading failures from: {run.name}")
                print(f"Failed targets: {sorted(failed)}")
                return failed
            except Exception as e:
                print(f"Warning: could not parse {result_file}: {e}")
    return None


def write_target(folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset):
    base = BUNDLE_DIR / folder_name
    write(base / "mod.txt", mod_txt(entrypoint))
    write(base / "version.txt", version_txt(mc_version, loader))

    if client_srcset:
        java_dir = base / "src" / "client" / "java"
    else:
        java_dir = base / "src" / "main" / "java"

    write(java_dir / PKG / "FullBrightMod.java", mod_src)
    if handler_src is not None:
        write(java_dir / PKG / "FullBrightHandler.java", handler_src)


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
        folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset = t
        print(f"  [{folder_name}] mc={mc_version} loader={loader}")
        write_target(folder_name, mc_version, loader, mod_src, handler_src, entrypoint, client_srcset)

    build_zip(targets)

    print("\nNext steps:")
    print("  git add incoming/fullbright-all-versions/ incoming/fullbright-all-versions.zip")
    print("  git commit -m 'Fix fullbright failing targets'")
    print("  git push")
    print("  python3 scripts/run_build.py incoming/fullbright-all-versions.zip --modrinth https://modrinth.com/mod/working-full-bright --max-parallel all")


if __name__ == "__main__":
    main()
