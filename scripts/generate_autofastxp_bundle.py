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
import shutil
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

def mod_txt(group=None, entrypoint=None):
    g = group or GROUP
    ep = entrypoint or f"{g}.AutoFastXpMod"
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={g}\nentrypoint_class={ep}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=client\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ===========================================================================
# FORGE 1.8.9 — Java 6, net.minecraft.item (old), playerController.sendUseItem
# ===========================================================================
SRC_189_MOD = """\
package asd.itamio.autofastxp;

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
# FORGE 1.16.5 — net.minecraft.world.item, net.minecraft.util.Hand
# TickEvent in net.minecraftforge.event.TickEvent
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
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
        boolean useMain = isXpBottle(main);
        boolean useOff = isXpBottle(off);
        if (!useMain && !useOff) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        InteractionHand hand = useMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, mc.level, hand);
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# 1.17.1 and 1.18.x Forge — same API as 1.16.5 for this mod
SRC_171_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_171_FORGE_HANDLER = SRC_1165_FORGE_HANDLER
SRC_118_FORGE_MOD = SRC_1165_FORGE_MOD
SRC_118_FORGE_HANDLER = SRC_1165_FORGE_HANDLER


# ===========================================================================
# FORGE 1.21.6-1.21.8 — EventBus 7
# TickEvent.ClientTickEvent.Post.BUS.addListener(Consumer<Post>)
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
            TickEvent.ClientTickEvent.Post.BUS.addListener(AutoFastXpHandler::onClientTick);
        }
    }
}
"""

SRC_1216_FORGE_HANDLER = """\
package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
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
        boolean useMain = isXpBottle(main);
        boolean useOff = isXpBottle(off);
        if (!useMain && !useOff) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        InteractionHand hand = useMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private static boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FORGE 1.21.9-1.21.11 — EventBus 7, record-based TickEvent
# TickEvent.ClientTickEvent.Post.BUS.addListener(Consumer<Post>)
# Same as 1.21.6-1.21.8 pattern
# ===========================================================================
SRC_1219_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_1219_FORGE_HANDLER = SRC_1216_FORGE_HANDLER

# ===========================================================================
# FORGE 26.1.2 — same EventBus 7 pattern
# ===========================================================================
SRC_261_FORGE_MOD = SRC_1216_FORGE_MOD
SRC_261_FORGE_HANDLER = SRC_1216_FORGE_HANDLER


# ===========================================================================
# FABRIC 1.16.5 — presplit (src/main/java), options.keyUse, interactItem(player, hand)
# ===========================================================================
SRC_1165_FABRIC = """\
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
            boolean useMain = isXpBottle(main);
            boolean useOff = isXpBottle(off);
            if (!useMain && !useOff) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            Hand hand = useMain ? Hand.MAIN_HAND : Hand.OFF_HAND;
            client.interactionManager.interactItem(client.player, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FABRIC 1.17-1.17.1 — presplit, same as 1.16.5
# ===========================================================================
SRC_117_FABRIC = SRC_1165_FABRIC

# ===========================================================================
# FABRIC 1.18-1.18.2 — presplit, options.useKey (renamed), interactItem(player, world, hand)
# ===========================================================================
SRC_118_FABRIC = """\
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
            if (!client.options.useKey.isPressed()) return;
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            boolean useMain = isXpBottle(main);
            boolean useOff = isXpBottle(off);
            if (!useMain && !useOff) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            Hand hand = useMain ? Hand.MAIN_HAND : Hand.OFF_HAND;
            client.interactionManager.interactItem(client.player, client.world, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FABRIC 1.19-1.19.4 — presplit, options.useKey, interactItem(player, hand) — 2 args again
# ===========================================================================
SRC_119_FABRIC = """\
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
            if (!client.options.useKey.isPressed()) return;
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            boolean useMain = isXpBottle(main);
            boolean useOff = isXpBottle(off);
            if (!useMain && !useOff) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            Hand hand = useMain ? Hand.MAIN_HAND : Hand.OFF_HAND;
            client.interactionManager.interactItem(client.player, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# ===========================================================================
# FABRIC 1.20+ — split adapter: client code goes in src/client/java/
# options.useKey, interactItem(player, hand)
# ===========================================================================
SRC_120_FABRIC = SRC_119_FABRIC
SRC_121_FABRIC = SRC_119_FABRIC
SRC_1212_FABRIC = SRC_119_FABRIC
SRC_1219_FABRIC = SRC_119_FABRIC
SRC_261_FABRIC = SRC_119_FABRIC


# ===========================================================================
# NEOFORGE 1.20.2-1.21.8 — net.neoforged.neoforge.client.event.ClientTickEvent
# (NOT net.neoforged.neoforge.event.TickEvent — that doesn't have ClientTickEvent)
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
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean useMain = isXpBottle(main);
        boolean useOff = isXpBottle(off);
        if (!useMain && !useOff) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        InteractionHand hand = useMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
"""

# 1.21-1.21.8 NeoForge same as 1.20.x
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
# FMLEnvironment import is net.minecraftforge.fml.loading.FMLEnvironment
# ===========================================================================
SRC_261_NEO_MOD = """\
package net.itamio.autofastxp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

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
# TARGETS
# (folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint, client_srcset)
# client_srcset=True  → client code goes in src/client/java/ (fabric_split 1.20+)
# client_srcset=False → client code goes in src/main/java/  (fabric_presplit)
# handler_src=None    → single-file mod (Fabric)
# ===========================================================================
TARGETS = [
    # ---- FORGE ----
    ("AutoFastXP-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_MOD,        SRC_189_HANDLER,        GROUP_FORGE_LEGACY, f"{GROUP_FORGE_LEGACY}.AutoFastXpMod", False),
    ("AutoFastXP-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE_MOD, SRC_1165_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.17.1-forge",   "1.17.1",  "forge",    SRC_171_FORGE_MOD,  SRC_171_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE_MOD,  SRC_118_FORGE_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),

    # ---- FABRIC (presplit = src/main/java) ----
    ("AutoFastXP-1.18-1.18.2-fabric",   "1.18-1.18.2",   "fabric", SRC_118_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.19-1.19.4-fabric",   "1.19-1.19.4",   "fabric", SRC_119_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", False),

    # ---- FABRIC (split = src/client/java) ----
    ("AutoFastXP-1.20-1.20.6-fabric",   "1.20.1-1.20.6", "fabric", SRC_120_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-1.21-1.21.1-fabric",   "1.21-1.21.1",   "fabric", SRC_121_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-1.21.2-1.21.8-fabric", "1.21.2-1.21.8", "fabric", SRC_1212_FABRIC, None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-1.21.9-1.21.11-fabric","1.21.9-1.21.11","fabric", SRC_1219_FABRIC, None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-26.1-fabric",          "26.1",          "fabric", SRC_261_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-26.1.1-fabric",        "26.1.1",        "fabric", SRC_261_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", True),
    ("AutoFastXP-26.1.2-fabric",        "26.1.2",        "fabric", SRC_261_FABRIC,  None, GROUP, f"{GROUP}.AutoFastXpMod", True),

    # ---- NEOFORGE ----
    ("AutoFastXP-1.20.2-neoforge", "1.20.2", "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.20.4-neoforge", "1.20.4", "neoforge", SRC_120_NEO_MOD,  SRC_120_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.2-neoforge", "1.21.2", "neoforge", SRC_121_NEO_MOD,  SRC_121_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-26.1-neoforge",   "26.1",   "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-26.1.1-neoforge", "26.1.1", "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
    ("AutoFastXP-26.1.2-neoforge", "26.1.2", "neoforge", SRC_261_NEO_MOD,  SRC_261_NEO_HANDLER,  GROUP, f"{GROUP}.AutoFastXpMod", False),
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


def write_target(folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint, client_srcset):
    pkg_path = group.replace(".", "/")
    base = BUNDLE_DIR / folder_name

    write(base / "mod.txt", mod_txt(group=group, entrypoint=entrypoint))
    write(base / "version.txt", version_txt(mc_version, loader))

    # For fabric_split (1.20+), client entrypoint goes in src/client/java/
    if client_srcset:
        java_dir = base / "src" / "client" / "java"
    else:
        java_dir = base / "src" / "main" / "java"

    write(java_dir / pkg_path / "AutoFastXpMod.java", mod_src)

    if handler_src is not None:
        write(java_dir / pkg_path / "AutoFastXpHandler.java", handler_src)


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
                print(f"Warning: failed set did not match any TARGETS — generating all")
                targets = TARGETS
    else:
        targets = TARGETS

    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Generating {len(targets)} target(s)...")
    for t in targets:
        folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint, client_srcset = t
        print(f"  {folder_name}")
        write_target(folder_name, mc_version, loader, mod_src, handler_src, group, entrypoint, client_srcset)

    build_zip(targets)
    print("Done.")


if __name__ == "__main__":
    main()
