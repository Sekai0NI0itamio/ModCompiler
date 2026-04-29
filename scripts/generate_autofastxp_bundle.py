#!/usr/bin/env python3
"""
Generator for Auto Fast XP — all missing versions bundle.
Mod: https://modrinth.com/mod/auto-fast-xp
Client-side only mod: throws XP bottles rapidly when right-click is held.

Run:
    python3 scripts/generate_autofastxp_bundle.py
    python3 scripts/generate_autofastxp_bundle.py --failed-only

Already published (skip these):
  1.12.2        forge
  1.16.5        fabric
  1.17, 1.17.1  fabric
  1.19-1.19.4   forge
  1.20.1-1.20.4, 1.20.6  forge
  1.21, 1.21.1  forge
  1.21.3-1.21.5 forge
  26.1.2        forge
  1.20.5, 1.20.6  neoforge
  1.21-1.21.8   neoforge
"""

import argparse
import json
import os
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "autofastxp-all-versions"
ZIP_PATH = ROOT / "incoming" / "autofastxp-all-versions.zip"

MOD_ID = "autofastxp"
MOD_NAME = "Auto Fast XP"
MOD_VERSION = "1.0.0"
GROUP_FORGE_LEGACY = "asd.itamio.autofastxp"
GROUP = "net.itamio.autofastxp"
DESCRIPTION = "Automatically throws XP bottles rapidly when you hold right-click. Perfect for quickly gaining XP!"
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/auto-fast-xp"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(group=None, entrypoint=None, runtime_side="client"):
    g = group or GROUP
    ep = entrypoint or f"{g}.AutoFastXpMod"
    lines = [
        f"mod_id={MOD_ID}",
        f"name={MOD_NAME}",
        f"mod_version={MOD_VERSION}",
        f"group={g}",
        f"entrypoint_class={ep}",
        f"description={DESCRIPTION}",
        f"authors={AUTHORS}",
        f"license={LICENSE}",
        f"homepage={HOMEPAGE}",
        f"runtime_side={runtime_side}",
    ]
    return "\n".join(lines) + "\n"

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ===========================================================================
# FORGE 1.8.9 — Java 6 compat: no underscores in literals, no <>, no lambdas
# Uses ClientTickEvent from net.minecraftforge.fml.common.gameevent.TickEvent
# Uses Minecraft.getMinecraft(), player.getHeldItem(), gameSettings.keyBindUseItem
# ===========================================================================
SRC_189_MOD = """\
package asd.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = AutoFastXpMod.MODID, name = "Auto Fast XP", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AutoFastXpMod {
    public static final String MODID = "autofastxp";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
"""

SRC_189_HANDLER = """\
package asd.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) return;
        ItemStack main = mc.thePlayer.getHeldItem();
        if (!isXpBottle(main)) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, main);
    }

    private boolean isXpBottle(ItemStack stack) {
        if (stack == null) return false;
        return Item.getIdFromItem(stack.getItem()) == 384;
    }
}
"""


# ===========================================================================
# FORGE 1.16.5 — mods.toml era, new event bus, isClientSide
# Uses TickEvent.ClientTickEvent, gameSettings.keyBindUseItem.isDown()
# Items.EXPERIENCE_BOTTLE
# ===========================================================================
SRC_1165_FORGE_MOD = """\
package net.itamio.autofastxp;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
"""

SRC_1165_FORGE_HANDLER = """\
package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
        if (active == null) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        net.minecraft.util.Hand hand = isXpBottle(main) ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
        mc.gameMode.useItem(mc.player, mc.level, hand);
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""


# ===========================================================================
# FORGE 1.17.1 — same as 1.16.5 but uses ClientRegistry for keys (no RegisterKeyMappingsEvent)
# TickEvent still in net.minecraftforge.event.TickEvent
# ===========================================================================
SRC_171_FORGE_MOD = SRC_1165_FORGE_MOD

SRC_171_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.18-1.18.2 — same API as 1.16.5/1.17.1 for this simple mod
# ===========================================================================
SRC_118_FORGE_MOD = SRC_1165_FORGE_MOD

SRC_118_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.19-1.19.4 — same API, already published but keeping for reference
# ===========================================================================
SRC_119_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_119_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.20-1.20.6 — same API as 1.16.5+ for this simple mod
# ===========================================================================
SRC_120_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_120_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.21-1.21.5 — same API as 1.16.5+ for this simple mod
# ===========================================================================
SRC_121_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_121_FORGE_HANDLER = SRC_1165_FORGE_HANDLER

# ===========================================================================
# FORGE 1.21.6-1.21.8 — EventBus 7: @SubscribeEvent removed
# TickEvent.ClientTickEvent.BUS.addListener() pattern
# Constructor takes FMLJavaModLoadingContext
# ===========================================================================
SRC_1216_FORGE_MOD = """\
package net.itamio.autofastxp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.BUS.addListener(AutoFastXpHandler::onClientTick);
        }
    }
}
"""

SRC_1216_FORGE_HANDLER = """\
package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private static int tickCounter = 0;

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
        if (active == null) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        net.minecraft.world.InteractionHand hand = isXpBottle(main)
            ? net.minecraft.world.InteractionHand.MAIN_HAND
            : net.minecraft.world.InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private static boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""


# ===========================================================================
# FORGE 1.21.9-1.21.11 — EventBus 7 with static BUS pattern
# TickEvent is now record-based: TickEvent.ClientTickEvent.Pre/Post
# ===========================================================================
SRC_1219_FORGE_MOD = """\
package net.itamio.autofastxp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.BUS.addListener(AutoFastXpHandler::onClientTick);
        }
    }
}
"""

SRC_1219_FORGE_HANDLER = """\
package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private static int tickCounter = 0;

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
        if (active == null) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        net.minecraft.world.InteractionHand hand = isXpBottle(main)
            ? net.minecraft.world.InteractionHand.MAIN_HAND
            : net.minecraft.world.InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private static boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FORGE 26.1.2 — EventBus 7, same pattern as 1.21.9+
# ResourceLocation -> Identifier rename but not needed for this simple mod
# ===========================================================================
SRC_261_FORGE_MOD = SRC_1219_FORGE_MOD
SRC_261_FORGE_HANDLER = SRC_1219_FORGE_HANDLER


# ===========================================================================
# FABRIC 1.16.5 — presplit source dir, command.v1, getMinecraftServer()
# Uses ClientTickCallback (fabric-api), Items.EXPERIENCE_BOTTLE
# package: net.itamio.autofastxp
# entrypoint: client
# ===========================================================================
SRC_1165_FABRIC_MOD = """\
package net.itamio.autofastxp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class AutoFastXpMod implements ClientModInitializer {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (client.currentScreen != null) return;
            if (!client.options.keyUse.isPressed()) return;
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
            if (active == null) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            Hand hand = isXpBottle(main) ? Hand.MAIN_HAND : Hand.OFF_HAND;
            client.interactionManager.interactItem(client.player, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FABRIC 1.17-1.17.1 — presplit, same as 1.16.5 but getServer() not getMinecraftServer()
# ClientTickEvents.END_CLIENT_TICK is the same
# ===========================================================================
SRC_117_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.18-1.18.2 — presplit, same API for this simple mod
# ===========================================================================
SRC_118_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.19-1.19.4 — presplit, same API
# Note: 1.19-1.19.2 use old registry path but we don't use registry here
# ===========================================================================
SRC_119_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.20-1.20.6 — split source dir, same API for this simple mod
# ===========================================================================
SRC_120_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.21-1.21.1 — split source dir, same API
# ===========================================================================
SRC_121_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.21.2-1.21.8 — split source dir, same API
# ===========================================================================
SRC_1212_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 1.21.9-1.21.11 — split source dir, same API
# ===========================================================================
SRC_1219_FABRIC_MOD = SRC_1165_FABRIC_MOD

# ===========================================================================
# FABRIC 26.1-26.1.2 — split source dir, same API
# Note: 26.x uses loom 1.16-SNAPSHOT, fabric_api_version (no yarn_mappings key)
# ===========================================================================
SRC_261_FABRIC_MOD = SRC_1165_FABRIC_MOD


# ===========================================================================
# NEOFORGE 1.20.2-1.20.6 — neoforge.mods.toml, IEventBus constructor
# Uses NeoForge.EVENT_BUS, @Mod.EventBusSubscriber(Bus.FORGE)
# TickEvent.ClientTickEvent, same as Forge 1.16.5+ for this simple mod
# ===========================================================================
SRC_120_NEO_MOD = """\
package net.itamio.autofastxp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
"""

SRC_120_NEO_HANDLER = """\
package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
        if (active == null) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        net.minecraft.world.InteractionHand hand = isXpBottle(main)
            ? net.minecraft.world.InteractionHand.MAIN_HAND
            : net.minecraft.world.InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21-1.21.8 — same as 1.20.x NeoForge
# ===========================================================================
SRC_121_NEO_MOD = SRC_120_NEO_MOD
SRC_121_NEO_HANDLER = SRC_120_NEO_HANDLER

# ===========================================================================
# NEOFORGE 1.21.9-1.21.11 — ModContainer required in constructor
# ===========================================================================
SRC_1219_NEO_MOD = """\
package net.itamio.autofastxp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(IEventBus modBus, ModContainer modContainer) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
"""

SRC_1219_NEO_HANDLER = SRC_120_NEO_HANDLER

# ===========================================================================
# NEOFORGE 26.1-26.1.2 — standalone @EventBusSubscriber, ModContainer required
# ===========================================================================
SRC_261_NEO_MOD = """\
package net.itamio.autofastxp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(IEventBus modBus, ModContainer modContainer) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
"""

SRC_261_NEO_HANDLER = SRC_120_NEO_HANDLER


# ===========================================================================
# TARGET DEFINITIONS
# Each entry: (folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint)
# handler_src=None means single-file mod (no separate handler)
# ===========================================================================

# Already published — DO NOT include:
# 1.12.2 forge, 1.16.5 fabric, 1.17/1.17.1 fabric
# 1.19-1.19.4 forge, 1.20.1-1.20.4/1.20.6 forge
# 1.21/1.21.1 forge, 1.21.3-1.21.5 forge, 26.1.2 forge
# 1.20.5/1.20.6 neoforge, 1.21-1.21.8 neoforge

TARGETS = [
    # ---- FORGE ----
    # 1.8.9
    ("AutoFastXP-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_MOD,       SRC_189_HANDLER,       GROUP_FORGE_LEGACY, f"{GROUP_FORGE_LEGACY}.AutoFastXpMod"),
    # 1.16.5
    ("AutoFastXP-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE_MOD, SRC_1165_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.17.1
    ("AutoFastXP-1.17.1-forge",   "1.17.1",  "forge",    SRC_171_FORGE_MOD, SRC_171_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.18-1.18.2
    ("AutoFastXP-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.6-1.21.8 (EventBus 7)
    ("AutoFastXP-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.9-1.21.11 (EventBus 7, record-based TickEvent)
    ("AutoFastXP-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 26.1.2 already published — skip

    # ---- FABRIC ----
    # 1.18-1.18.2 (anchor_only range — use range version.txt)
    ("AutoFastXP-1.18-1.18.2-fabric", "1.18-1.18.2", "fabric", SRC_118_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.19-1.19.4 (anchor_only range)
    ("AutoFastXP-1.19-1.19.4-fabric", "1.19-1.19.4", "fabric", SRC_119_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.20.1-1.20.6
    ("AutoFastXP-1.20-1.20.6-fabric", "1.20.1-1.20.6", "fabric", SRC_120_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21-1.21.1 (anchor_only range)
    ("AutoFastXP-1.21-1.21.1-fabric", "1.21-1.21.1", "fabric", SRC_121_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.2-1.21.8 (anchor_only range)
    ("AutoFastXP-1.21.2-1.21.8-fabric", "1.21.2-1.21.8", "fabric", SRC_1212_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.9-1.21.11 (anchor_only range)
    ("AutoFastXP-1.21.9-1.21.11-fabric", "1.21.9-1.21.11", "fabric", SRC_1219_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 26.1-26.1.2
    ("AutoFastXP-26.1-fabric",    "26.1",    "fabric",   SRC_261_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-26.1.1-fabric",  "26.1.1",  "fabric",   SRC_261_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-26.1.2-fabric",  "26.1.2",  "fabric",   SRC_261_FABRIC_MOD, None, GROUP, f"{GROUP}.AutoFastXpMod"),

    # ---- NEOFORGE ----
    # 1.20.2, 1.20.4 (1.20.5/1.20.6 already published)
    ("AutoFastXP-1.20.2-neoforge", "1.20.2", "neoforge", SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.20.4-neoforge", "1.20.4", "neoforge", SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.2 (1.21/1.21.1/1.21.3-1.21.8 already published)
    ("AutoFastXP-1.21.2-neoforge", "1.21.2", "neoforge", SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 1.21.9-1.21.11 (ModContainer required)
    ("AutoFastXP-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    # 26.1-26.1.2 (standalone @EventBusSubscriber, ModContainer required)
    ("AutoFastXP-26.1-neoforge",   "26.1",   "neoforge", SRC_261_NEO_MOD, SRC_261_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-26.1.1-neoforge", "26.1.1", "neoforge", SRC_261_NEO_MOD, SRC_261_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
    ("AutoFastXP-26.1.2-neoforge", "26.1.2", "neoforge", SRC_261_NEO_MOD, SRC_261_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod"),
]


# ===========================================================================
# BUILD HELPERS
# ===========================================================================

def get_failed_targets():
    """Read the most recent ModCompileRuns/ folder and return set of failed folder names."""
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
                        # name is like "AutoFastXP-1.8.9-forge"
                        failed.add(name)
                print(f"Reading failures from: {run.name}")
                print(f"Failed targets: {sorted(failed)}")
                return failed
            except Exception as e:
                print(f"Warning: could not parse {result_file}: {e}")
    # Also check SUMMARY.md for failed targets
    for run in runs:
        summary = run / "SUMMARY.md"
        if summary.exists():
            text = summary.read_text()
            failed = set()
            for line in text.splitlines():
                if "FAILED" in line or "failed" in line or "❌" in line:
                    # Try to extract folder name from line
                    for t in TARGETS:
                        if t[0] in line:
                            failed.add(t[0])
            if failed:
                print(f"Reading failures from SUMMARY: {run.name}")
                return failed
    return None


def write_target(folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint):
    """Write one mod target folder."""
    pkg_path = group.replace(".", "/")
    base = BUNDLE_DIR / folder_name

    # mod.txt
    write(base / "mod.txt", mod_txt(group=group, entrypoint=entrypoint))

    # version.txt
    write(base / "version.txt", version_txt(mc_version, loader))

    # Main mod source
    write(base / "src" / "main" / "java" / pkg_path / "AutoFastXpMod.java", mod_src)

    # Handler source (if separate)
    if handler_src is not None:
        write(base / "src" / "main" / "java" / pkg_path / "AutoFastXpHandler.java", handler_src)


def build_zip(targets_to_include):
    """Package the bundle dir into a zip."""
    ZIP_PATH.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder_name, *_ in targets_to_include:
            folder = BUNDLE_DIR / folder_name
            for file in sorted(folder.rglob("*")):
                if file.is_file():
                    zf.write(file, file.relative_to(BUNDLE_DIR))
    print(f"Wrote {ZIP_PATH} ({ZIP_PATH.stat().st_size // 1024} KB, {len(targets_to_include)} targets)")


def main():
    parser = argparse.ArgumentParser(description="Generate Auto Fast XP all-versions bundle")
    parser.add_argument("--failed-only", action="store_true",
                        help="Only include targets that failed in the last run")
    args = parser.parse_args()

    if args.failed_only:
        failed = get_failed_targets()
        if failed is None:
            print("No previous run found — generating all targets")
            targets = TARGETS
        elif not failed:
            print("No failed targets found in last run — nothing to do")
            sys.exit(0)
        else:
            targets = [t for t in TARGETS if t[0] in failed]
            if not targets:
                print(f"Warning: failed set {failed} did not match any TARGETS folder names")
                print("Generating all targets as fallback")
                targets = TARGETS
    else:
        targets = TARGETS

    # Clean and regenerate
    if BUNDLE_DIR.exists():
        import shutil
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Generating {len(targets)} target(s)...")
    for t in targets:
        folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint = t
        print(f"  {folder_name}")
        write_target(folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint)

    build_zip(targets)
    print("Done.")


if __name__ == "__main__":
    main()
