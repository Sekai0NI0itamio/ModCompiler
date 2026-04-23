#!/usr/bin/env python3
"""
Generator for Optimized Vein Miner — all versions bundle.
Mod: https://modrinth.com/mod/optimized-vein-miner
Original: 1.12.2 Forge only. Server-side required, client-side has keybind.
runtime_side=both (server handles block break, client handles toggle key)

Run:
    python3 scripts/generate_veinminer_bundle.py
    python3 scripts/generate_veinminer_bundle.py --failed-only
"""

import argparse
import json
import os
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "veinminer-all-versions"
ZIP_PATH = ROOT / "incoming" / "veinminer-all-versions.zip"

# ---------------------------------------------------------------------------
# mod.txt shared fields
# ---------------------------------------------------------------------------
MOD_TXT_BASE = """\
mod_id=veinminer
name=Vein Miner
mod_version=1.0.0
group=asd.itamio.veinminer
description=Mine entire ore veins and connected blocks at once! Highly optimized with single sound, items dropped at one location, and configurable block limits.
authors=Itamio
license=MIT
homepage=https://modrinth.com/mod/optimized-vein-miner
"""

# ---------------------------------------------------------------------------
# VeinMinerConfig — identical across all versions (pure config, no MC API)
# ---------------------------------------------------------------------------
CONFIG_SRC = """\
package asd.itamio.veinminer;

public class VeinMinerConfig {
    public boolean enableVeinMiner = true;
    public boolean requireSneak = true;
    public int maxBlocks = 64;
    public boolean consumeDurability = true;
    public boolean consumeHunger = true;
    public float hungerMultiplier = 1.0f;
    public boolean limitToCorrectTool = true;
    public boolean dropAtOneLocation = true;
    public boolean disableParticles = true;
    public boolean disableSound = false;
    public int cooldownTicks = 0;
    public boolean mineOres = true;
    public boolean mineLogs = true;
    public boolean mineStone = false;
    public boolean mineDirt = false;
    public boolean mineGravel = false;
    public boolean mineSand = false;
    public boolean mineClay = false;
    public boolean mineNetherrack = false;
    public boolean mineEndStone = false;
    public boolean mineGlowstone = true;
}
"""

# ===========================================================================
# FORGE SOURCES
# ===========================================================================

# ---------------------------------------------------------------------------
# Forge 1.8.9
# ---------------------------------------------------------------------------
FORGE_189_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = VeinMinerMod.MODID, name = VeinMinerMod.NAME, version = VeinMinerMod.VERSION)
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static final String NAME = "Vein Miner";
    public static final String VERSION = "1.0.0";
    public static Logger logger;
    public static VeinMinerConfig config = new VeinMinerConfig();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_189_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        EntityPlayer player = event.getPlayer();
        World world = event.world;
        BlockPos pos = event.pos;
        Block block = world.getBlockState(pos).getBlock();
        if (world.isRemote) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTotalWorldTime();
            Long last = cooldowns.get(player.getUniqueID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUniqueID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            ItemStack held = player.getHeldItem();
            if (!isCorrectTool(block, held)) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, pos);
    }

    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore)) return true;
        if (VeinMinerMod.config.mineLogs && (b==Blocks.log||b==Blocks.log2)) return true;
        if (VeinMinerMod.config.mineStone && (b==Blocks.stone||b==Blocks.cobblestone)) return true;
        if (VeinMinerMod.config.mineDirt && (b==Blocks.dirt||b==Blocks.grass)) return true;
        if (VeinMinerMod.config.mineGravel && b==Blocks.gravel) return true;
        if (VeinMinerMod.config.mineSand && b==Blocks.sand) return true;
        if (VeinMinerMod.config.mineClay && b==Blocks.clay) return true;
        if (VeinMinerMod.config.mineNetherrack && b==Blocks.netherrack) return true;
        if (VeinMinerMod.config.mineEndStone && b==Blocks.end_stone) return true;
        if (VeinMinerMod.config.mineGlowstone && b==Blocks.glowstone) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool == null || tool.getItem() == null) return false;
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : tool.getItem().getToolClasses(tool).iterator().next();
        if (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore||b==Blocks.stone||b==Blocks.cobblestone||b==Blocks.netherrack||b==Blocks.end_stone||b==Blocks.glowstone) return tc.equals("pickaxe");
        if (b==Blocks.log||b==Blocks.log2) return tc.equals("axe");
        if (b==Blocks.dirt||b==Blocks.grass||b==Blocks.gravel||b==Blocks.sand||b==Blocks.clay) return tc.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        int startMeta = world.getBlockState(start).getBlock().getMetaFromState(world.getBlockState(start));
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb) || vein.size()>=max) continue;
                Block nb_block = world.getBlockState(nb).getBlock();
                if (nb_block != target) continue;
                if (target==Blocks.log||target==Blocks.log2) {
                    int nbMeta = target.getMetaFromState(world.getBlockState(nb));
                    if ((nbMeta&3) != (startMeta&3)) continue;
                }
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, EntityPlayer player, Set<BlockPos> vein, BlockPos origin) {
        ItemStack tool = player.getHeldItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            List<ItemStack> drops = state.getBlock().getDrops(world, pos, state, player.experienceLevel);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos);
            mined++;
            if (VeinMinerMod.config.consumeDurability && tool != null && !tool.isEmpty()) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) { tool.stackSize = 0; break; }
            }
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName() + ":" + d.getItemDamage();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.stackSize + d.stackSize;
                    ex.stackSize = Math.min(nc, ex.getMaxStackSize());
                    if (nc > ex.getMaxStackSize()) {
                        ItemStack ov = d.copy(); ov.stackSize = nc - ex.getMaxStackSize();
                        combined.put(key+"_"+combined.size(), ov);
                    }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (s != null && s.stackSize > 0) {
                    EntityItem ei = new EntityItem(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickupDelay();
                    world.spawnEntityInWorld(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.addExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_189_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", Keyboard.KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.12.2 (original — clean rewrite with proper MCP names)
# ---------------------------------------------------------------------------
FORGE_1122_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = VeinMinerMod.MODID, name = VeinMinerMod.NAME, version = VeinMinerMod.VERSION,
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static final String NAME = "Vein Miner";
    public static final String VERSION = "1.0.0";
    public static Logger logger;
    public static VeinMinerConfig config = new VeinMinerConfig();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        File configFile = new File(event.getModConfigurationDirectory(), "veinminer.cfg");
        Configuration cfg = new Configuration(configFile);
        cfg.load();
        config.enableVeinMiner = cfg.getBoolean("Enable Vein Miner", "general", true, "");
        config.requireSneak = cfg.getBoolean("Require Sneak", "general", true, "");
        config.maxBlocks = cfg.getInt("Max Blocks", "general", 64, 1, 1000, "");
        config.consumeDurability = cfg.getBoolean("Consume Durability", "balance", true, "");
        config.consumeHunger = cfg.getBoolean("Consume Hunger", "balance", true, "");
        config.hungerMultiplier = cfg.getFloat("Hunger Multiplier", "balance", 1.0f, 0f, 10f, "");
        config.limitToCorrectTool = cfg.getBoolean("Limit To Correct Tool", "balance", true, "");
        config.dropAtOneLocation = cfg.getBoolean("Drop At One Location", "performance", true, "");
        config.disableSound = cfg.getBoolean("Disable Sound", "performance", false, "");
        config.cooldownTicks = cfg.getInt("Cooldown Ticks", "balance", 0, 0, 200, "");
        config.mineOres = cfg.getBoolean("Mine Ores", "blocks", true, "");
        config.mineLogs = cfg.getBoolean("Mine Logs", "blocks", true, "");
        config.mineStone = cfg.getBoolean("Mine Stone", "blocks", false, "");
        config.mineDirt = cfg.getBoolean("Mine Dirt", "blocks", false, "");
        config.mineGravel = cfg.getBoolean("Mine Gravel", "blocks", false, "");
        config.mineSand = cfg.getBoolean("Mine Sand", "blocks", false, "");
        config.mineClay = cfg.getBoolean("Mine Clay", "blocks", false, "");
        config.mineNetherrack = cfg.getBoolean("Mine Netherrack", "blocks", false, "");
        config.mineEndStone = cfg.getBoolean("Mine End Stone", "blocks", false, "");
        config.mineGlowstone = cfg.getBoolean("Mine Glowstone", "blocks", true, "");
        if (cfg.hasChanged()) cfg.save();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_1122_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        EntityPlayer player = event.getPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isRemote) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTotalWorldTime();
            Long last = cooldowns.get(player.getUniqueID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUniqueID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            ItemStack held = player.getHeldItemMainhand();
            if (!isCorrectTool(block, held)) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b==Blocks.COAL_ORE||b==Blocks.IRON_ORE||b==Blocks.GOLD_ORE||b==Blocks.DIAMOND_ORE||b==Blocks.EMERALD_ORE||b==Blocks.LAPIS_ORE||b==Blocks.REDSTONE_ORE||b==Blocks.LIT_REDSTONE_ORE||b==Blocks.QUARTZ_ORE)) return true;
        if (VeinMinerMod.config.mineLogs && (b==Blocks.LOG||b==Blocks.LOG2)) return true;
        if (VeinMinerMod.config.mineStone && (b==Blocks.STONE||b==Blocks.COBBLESTONE)) return true;
        if (VeinMinerMod.config.mineDirt && (b==Blocks.DIRT||b==Blocks.GRASS)) return true;
        if (VeinMinerMod.config.mineGravel && b==Blocks.GRAVEL) return true;
        if (VeinMinerMod.config.mineSand && b==Blocks.SAND) return true;
        if (VeinMinerMod.config.mineClay && b==Blocks.CLAY) return true;
        if (VeinMinerMod.config.mineNetherrack && b==Blocks.NETHERRACK) return true;
        if (VeinMinerMod.config.mineEndStone && b==Blocks.END_STONE) return true;
        if (VeinMinerMod.config.mineGlowstone && b==Blocks.GLOWSTONE) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : tool.getItem().getToolClasses(tool).iterator().next();
        if (b==Blocks.COAL_ORE||b==Blocks.IRON_ORE||b==Blocks.GOLD_ORE||b==Blocks.DIAMOND_ORE||b==Blocks.EMERALD_ORE||b==Blocks.LAPIS_ORE||b==Blocks.REDSTONE_ORE||b==Blocks.LIT_REDSTONE_ORE||b==Blocks.QUARTZ_ORE||b==Blocks.STONE||b==Blocks.COBBLESTONE||b==Blocks.NETHERRACK||b==Blocks.END_STONE||b==Blocks.GLOWSTONE) return tc.equals("pickaxe");
        if (b==Blocks.LOG||b==Blocks.LOG2) return tc.equals("axe");
        if (b==Blocks.DIRT||b==Blocks.GRASS||b==Blocks.GRAVEL||b==Blocks.SAND||b==Blocks.CLAY) return tc.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, IBlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        int startMeta = target.getMetaFromState(startState);
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                IBlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (target==Blocks.LOG||target==Blocks.LOG2) {
                    if ((target.getMetaFromState(nbs)&3)!=(startMeta&3)) continue;
                }
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, EntityPlayer player, Set<BlockPos> vein, IBlockState origState, BlockPos origin) {
        ItemStack tool = player.getHeldItemMainhand();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            IBlockState state = world.getBlockState(pos);
            NonNullList<ItemStack> drops = NonNullList.create();
            state.getBlock().getDrops(drops, world, pos, state, player.experienceLevel);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) { tool.shrink(1); break; }
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getBlock().getSoundType(origState, world, origin, player).getBreakSound(),
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getMetadata();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    EntityItem ei = new EntityItem(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickupDelay();
                    world.spawnEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.addExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_1122_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", Keyboard.KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(msg));
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.16.5 — mods.toml era, new event/item APIs
# ---------------------------------------------------------------------------
FORGE_1165_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_1165_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        PlayerEntity player = event.getPlayer();
        World world = (World) event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = b.getRegistryName() == null ? "" : b.getRegistryName().toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = b.getRegistryName() == null ? "" : b.getRegistryName().toString();
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : tool.getItem().getToolClasses(tool).iterator().next();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tc.equals("pickaxe");
        if (n.contains("_log")||n.contains("_wood")) return tc.equals("axe");
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tc.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = target.getRegistryName() == null ? "" : target.getRegistryName().toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = net.minecraft.block.Block.getDrops(state, (net.minecraft.world.server.ServerWorld) world, pos, world.getBlockEntity(pos));
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_1165_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.glfw.GLFW;

@SideOnly(Side.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new StringTextComponent(msg), false);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.17.1 — Java 16, event.world package, TextComponent
# ---------------------------------------------------------------------------
FORGE_117_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_117_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        PlayerEntity player = event.getPlayer();
        World world = (World) event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = b.getRegistryName() == null ? "" : b.getRegistryName().toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = b.getRegistryName() == null ? "" : b.getRegistryName().toString();
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : tool.getItem().getToolClasses(tool).iterator().next();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tc.equals("pickaxe");
        if (n.contains("_log")||n.contains("_wood")) return tc.equals("axe");
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tc.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = target.getRegistryName() == null ? "" : target.getRegistryName().toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = net.minecraft.block.Block.getDrops(state, (net.minecraft.world.server.ServerWorld) world, pos, world.getBlockEntity(pos));
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_117_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(msg), false);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.18-1.19.4 — ScreenEvent era, ServerLevel, same key API
# ---------------------------------------------------------------------------
FORGE_118_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_118_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = net.minecraft.core.Registry.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = net.minecraft.core.Registry.BLOCK.getKey(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (isAxe) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (isShovel) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = net.minecraft.core.Registry.BLOCK.getKey(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = net.minecraft.core.Registry.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_118_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(msg), false);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.20.1-1.20.4 — Component.literal, BuiltInRegistries, GuiGraphics era
# ---------------------------------------------------------------------------
FORGE_120_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

# Handler for 1.20.1-1.20.4: BuiltInRegistries, isSameItemSameTags
FORGE_120_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (isAxe) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (isShovel) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_120_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.20.6 — same as 1.20.1-1.20.4 but uses isSameItemSameComponents
# (getDamageValue still works for key, no tag changes needed for this mod)
# ---------------------------------------------------------------------------
FORGE_1206_MOD = FORGE_120_MOD
FORGE_1206_HANDLER = FORGE_120_HANDLER  # same API for this mod
FORGE_1206_KEY = FORGE_120_KEY

# ---------------------------------------------------------------------------
# Forge 1.21-1.21.5 — ResourceLocation constructor change, same handler
# ---------------------------------------------------------------------------
FORGE_121_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

FORGE_121_HANDLER = FORGE_120_HANDLER  # BuiltInRegistries still valid

FORGE_121_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

FORGE_121_MOD_WITH_KEY = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.RegisterKeyMappingsEvent;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        bus.addListener(VeinMinerKeyHandler::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.21.6-1.21.11 — EventBus 7: no @SubscribeEvent, no eventbus.api
# ---------------------------------------------------------------------------
FORGE_1216_MOD = """\
package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.RegisterKeyMappingsEvent;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        bus.addListener(VeinMinerKeyHandler::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

# EventBus 7: no @SubscribeEvent import, register(this) dispatches by method sig
FORGE_1216_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    // No @SubscribeEvent — register(this) dispatches by method signature
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (isAxe) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (isShovel) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FORGE_1216_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }

    // No @SubscribeEvent — register(this) dispatches by method signature
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE SOURCES
# ===========================================================================

# ---------------------------------------------------------------------------
# NeoForge 1.20.2-1.21.4 — NeoForge.EVENT_BUS, Bus.FORGE, same handler shape
# ---------------------------------------------------------------------------
NEOFORGE_120_MOD = """\
package asd.itamio.veinminer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        modBus.addListener(VeinMinerKeyHandler::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        NeoForge.EVENT_BUS.register(new VeinMinerHandler());
        NeoForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""

NEOFORGE_120_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (isAxe) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (isShovel) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin,
                origState.getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

NEOFORGE_120_KEY = """\
package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

# NeoForge 1.21.5+ — same structure, handler/key identical
NEOFORGE_1215_MOD = NEOFORGE_120_MOD
NEOFORGE_1215_HANDLER = NEOFORGE_120_HANDLER
NEOFORGE_1215_KEY = NEOFORGE_120_KEY

# ===========================================================================
# FABRIC SOURCES
# ===========================================================================

# ---------------------------------------------------------------------------
# Fabric 1.16.5 — presplit, yarn mappings, Log4j, no split client dir
# ---------------------------------------------------------------------------
FABRIC_1165_MOD_JSON = """\
{
  "schemaVersion": 1,
  "id": "veinminer",
  "version": "1.0.0",
  "name": "Vein Miner",
  "description": "Mine entire ore veins at once.",
  "authors": ["Itamio"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["asd.itamio.veinminer.VeinMinerMod"]
  },
  "depends": {
    "fabricloader": ">=0.11.0",
    "minecraft": "1.16.5"
  }
}
"""

FABRIC_1165_MAIN = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VeinMinerMod implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("veinminer");
    public static VeinMinerConfig config = new VeinMinerConfig();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(new VeinMinerHandler());
    }
}
"""

FABRIC_1165_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return true;
        if (world.isClient) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTime();
            Long last = cooldowns.get(player.getUuid());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUuid(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandStack())) return true;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }

    private boolean isVeinMineable(Block b) {
        String n = net.minecraft.util.registry.Registry.BLOCK.getId(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = net.minecraft.util.registry.Registry.BLOCK.getId(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof PickaxeItem;
        if (isAxe) return tool.getItem() instanceof AxeItem;
        if (isShovel) return tool.getItem() instanceof ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = net.minecraft.util.registry.Registry.BLOCK.getId(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandStack();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        ServerWorld sw = (ServerWorld) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damage(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin, origState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = net.minecraft.util.registry.Registry.ITEM.getId(d.getItem())+":"+d.getDamage();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxCount()));
                    if (nc>ex.getMaxCount()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxCount()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setToDefaultPickupDelay();
                    world.spawnEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.addExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

# Fabric 1.16.5 key handler — presplit, no client entrypoint split
FABRIC_1165_KEY = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Toggle Vein Miner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.sendMessage(new LiteralText(msg), false);
            }
        });
    }
}
"""

FABRIC_1165_MOD_JSON_WITH_CLIENT = """\
{
  "schemaVersion": 1,
  "id": "veinminer",
  "version": "1.0.0",
  "name": "Vein Miner",
  "description": "Mine entire ore veins at once.",
  "authors": ["Itamio"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["asd.itamio.veinminer.VeinMinerMod"],
    "client": ["asd.itamio.veinminer.VeinMinerKeyHandler"]
  },
  "depends": {
    "fabricloader": ">=0.11.0",
    "minecraft": "1.16.5",
    "fabric": "*"
  }
}
"""

# Fabric sources for vein miner — 1.17+ (presplit) and 1.20+ (split/Mojang)
# Imported by generate_veinminer_bundle.py

# ---------------------------------------------------------------------------
# Fabric 1.17-1.19.4 — presplit, yarn mappings, SLF4J, Text.translatable 1.19+
# ---------------------------------------------------------------------------
FABRIC_117_MOD_JSON = """\
{
  "schemaVersion": 1,
  "id": "veinminer",
  "version": "1.0.0",
  "name": "Vein Miner",
  "description": "Mine entire ore veins at once.",
  "authors": ["Itamio"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["asd.itamio.veinminer.VeinMinerMod"],
    "client": ["asd.itamio.veinminer.VeinMinerKeyHandler"]
  },
  "depends": {
    "fabricloader": ">=0.12.0",
    "minecraft": ">=1.17",
    "fabric": "*"
  }
}
"""

FABRIC_117_MAIN = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VeinMinerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("veinminer");
    public static VeinMinerConfig config = new VeinMinerConfig();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(new VeinMinerHandler());
    }
}
"""

# Fabric 1.17-1.18.x handler — yarn: Registry.BLOCK, getCursorStack added 1.17
FABRIC_117_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return true;
        if (world.isClient) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTime();
            Long last = cooldowns.get(player.getUuid());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUuid(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandStack())) return true;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }

    private boolean isVeinMineable(Block b) {
        String n = Registry.BLOCK.getId(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = Registry.BLOCK.getId(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof PickaxeItem;
        if (isAxe) return tool.getItem() instanceof AxeItem;
        if (isShovel) return tool.getItem() instanceof ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = Registry.BLOCK.getId(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandStack();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        ServerWorld sw = (ServerWorld) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damage(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin, origState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = Registry.ITEM.getId(d.getItem())+":"+d.getDamage();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxCount()));
                    if (nc>ex.getMaxCount()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxCount()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setToDefaultPickupDelay();
                    world.spawnEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.addExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FABRIC_117_KEY = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Toggle Vein Miner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.sendMessage(Text.of(msg), false);
            }
        });
    }
}
"""

# Fabric 1.19.4 — Text.translatable replaced LiteralText, same handler
FABRIC_119_KEY = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Toggle Vein Miner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.sendMessage(Text.literal(msg), false);
            }
        });
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric 1.20.x — split adapter: main in src/main/java, client in src/client/java
# Yarn mappings through 1.20.x
# ---------------------------------------------------------------------------
FABRIC_120_MOD_JSON = """\
{
  "schemaVersion": 1,
  "id": "veinminer",
  "version": "1.0.0",
  "name": "Vein Miner",
  "description": "Mine entire ore veins at once.",
  "authors": ["Itamio"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["asd.itamio.veinminer.VeinMinerMod"],
    "client": ["asd.itamio.veinminer.VeinMinerKeyHandler"]
  },
  "depends": {
    "fabricloader": ">=0.14.0",
    "minecraft": ">=1.20",
    "fabric": "*"
  }
}
"""

# 1.20.x main — same as 1.17 (yarn, Registry.BLOCK)
FABRIC_120_MAIN = FABRIC_117_MAIN
FABRIC_120_HANDLER = FABRIC_117_HANDLER  # yarn Registry still valid in 1.20.x

# 1.20.x client key — goes in src/client/java
FABRIC_120_KEY = FABRIC_119_KEY  # Text.literal, same API

# ---------------------------------------------------------------------------
# Fabric 1.21+ — Mojang mappings (same as Forge), split adapter
# BuiltInRegistries, Block.getDrops, SoundSource
# ---------------------------------------------------------------------------
FABRIC_121_MOD_JSON = """\
{
  "schemaVersion": 1,
  "id": "veinminer",
  "version": "1.0.0",
  "name": "Vein Miner",
  "description": "Mine entire ore veins at once.",
  "authors": ["Itamio"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["asd.itamio.veinminer.VeinMinerMod"],
    "client": ["asd.itamio.veinminer.VeinMinerKeyHandler"]
  },
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": ">=1.21",
    "fabric-api": "*"
  }
}
"""

FABRIC_121_MAIN = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VeinMinerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("veinminer");
    public static VeinMinerConfig config = new VeinMinerConfig();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(new VeinMinerHandler());
    }
}
"""

# 1.21+ Fabric handler — Mojang mappings (same package names as Forge)
FABRIC_121_HANDLER = """\
package asd.itamio.veinminer;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return true;
        if (world.isClientSide) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool) {
            if (!isCorrectTool(block, player.getMainHandItem())) return true;
        }
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }

    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        boolean isPick = n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone");
        boolean isAxe = n.contains("_log")||n.contains("_wood");
        boolean isShovel = n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay");
        if (isPick) return tool.getItem() instanceof PickaxeItem;
        if (isAxe) return tool.getItem() instanceof AxeItem;
        if (isShovel) return tool.getItem() instanceof ShovelItem;
        return true;
    }

    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String startName = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = startName.contains("_log") || startName.contains("_wood");
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog && nbs != startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }

    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        ServerLevel sl = (ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.getCount()+d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.setCount(nc-ex.getMaxStackSize()); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (!s.isEmpty()) {
                    ItemEntity ei = new ItemEntity(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickUpDelay();
                    world.addFreshEntity(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) {
            player.causeFoodExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
        }
    }
}
"""

FABRIC_121_KEY = """\
package asd.itamio.veinminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.displayClientMessage(Component.literal(msg), false);
            }
        });
    }
}
"""

# ===========================================================================
# BUNDLE DEFINITIONS
# Each entry: (folder_name, version_txt, mod_txt_extra, files_dict)
# files_dict keys are relative paths inside src/main/java/... or src/client/java/...
# ===========================================================================

PKG = "asd/itamio/veinminer"

def _forge_files(mod, handler, key, config=CONFIG_SRC):
    return {
        f"src/main/java/{PKG}/VeinMinerMod.java": mod,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }

def _fabric_presplit_files(main, handler, key, config=CONFIG_SRC, mod_json=None):
    """Fabric presplit: all source in src/main/java"""
    d = {
        f"src/main/java/{PKG}/VeinMinerMod.java": main,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }
    if mod_json:
        d["src/main/resources/fabric.mod.json"] = mod_json
    return d

def _fabric_split_files(main, handler, key, config=CONFIG_SRC, mod_json=None):
    """Fabric split: main in src/main/java, client in src/client/java"""
    d = {
        f"src/main/java/{PKG}/VeinMinerMod.java": main,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/client/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }
    if mod_json:
        d["src/main/resources/fabric.mod.json"] = mod_json
    return d

def _neoforge_files(mod, handler, key, config=CONFIG_SRC):
    return {
        f"src/main/java/{PKG}/VeinMinerMod.java": mod,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }

# (folder_name, mc_version, loader, files_dict)
# ===========================================================================
# FIXED SOURCE STRINGS
# ===========================================================================

# --- Shared state class for Fabric split adapter ---
VEINMINER_STATE_SRC = """\
package asd.itamio.veinminer;
public class VeinMinerState {
    public static boolean veinMinerEnabled = true;
}
"""

# --- Forge 1.8.9: Java 6 — no diamond operator ---
FORGE_189_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        EntityPlayer player = event.getPlayer();
        World world = event.world;
        BlockPos pos = event.pos;
        Block block = world.getBlockState(pos).getBlock();
        if (world.isRemote) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTotalWorldTime();
            Long last = cooldowns.get(player.getUniqueID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUniqueID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            ItemStack held = player.getHeldItem();
            if (!isCorrectTool(block, held)) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, pos);
    }
    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore)) return true;
        if (VeinMinerMod.config.mineLogs && (b==Blocks.log||b==Blocks.log2)) return true;
        if (VeinMinerMod.config.mineStone && (b==Blocks.stone||b==Blocks.cobblestone)) return true;
        if (VeinMinerMod.config.mineDirt && (b==Blocks.dirt||b==Blocks.grass)) return true;
        if (VeinMinerMod.config.mineGravel && b==Blocks.gravel) return true;
        if (VeinMinerMod.config.mineSand && b==Blocks.sand) return true;
        if (VeinMinerMod.config.mineClay && b==Blocks.clay) return true;
        if (VeinMinerMod.config.mineNetherrack && b==Blocks.netherrack) return true;
        if (VeinMinerMod.config.mineEndStone && b==Blocks.end_stone) return true;
        if (VeinMinerMod.config.mineGlowstone && b==Blocks.glowstone) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool == null || tool.getItem() == null) return false;
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : (String)tool.getItem().getToolClasses(tool).iterator().next();
        if (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore||b==Blocks.stone||b==Blocks.cobblestone||b==Blocks.netherrack||b==Blocks.end_stone||b==Blocks.glowstone) return tc.equals("pickaxe");
        if (b==Blocks.log||b==Blocks.log2) return tc.equals("axe");
        if (b==Blocks.dirt||b==Blocks.grass||b==Blocks.gravel||b==Blocks.sand||b==Blocks.clay) return tc.equals("shovel");
        return true;
    }
    private Set<BlockPos> findVein(World world, BlockPos start, Block target, int max) {
        Set<BlockPos> vein = new HashSet<BlockPos>();
        Queue<BlockPos> queue = new LinkedList<BlockPos>();
        queue.add(start); vein.add(start);
        int startMeta = world.getBlockState(start).getBlock().getMetaFromState(world.getBlockState(start));
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                Block nb_block = world.getBlockState(nb).getBlock();
                if (nb_block != target) continue;
                if (target==Blocks.log||target==Blocks.log2) {
                    int nbMeta = target.getMetaFromState(world.getBlockState(nb));
                    if ((nbMeta&3)!=(startMeta&3)) continue;
                }
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(World world, EntityPlayer player, Set<BlockPos> vein, BlockPos origin) {
        ItemStack tool = player.getHeldItem();
        List<ItemStack> allDrops = new ArrayList<ItemStack>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            List<ItemStack> drops = state.getBlock().getDrops(world, pos, state, player.experienceLevel);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos);
            mined++;
            if (VeinMinerMod.config.consumeDurability && tool != null && tool.stackSize > 0) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) { tool.stackSize = 0; break; }
            }
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<String,ItemStack>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getItemDamage();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.stackSize+d.stackSize;
                    ex.stackSize = Math.min(nc, ex.getMaxStackSize());
                    if (nc>ex.getMaxStackSize()) { ItemStack ov=d.copy(); ov.stackSize=nc-ex.getMaxStackSize(); combined.put(key+"_"+combined.size(),ov); }
                } else { combined.put(key, d.copy()); }
            }
            for (ItemStack s : combined.values()) {
                if (s!=null&&s.stackSize>0) {
                    EntityItem ei = new EntityItem(world, origin.getX()+0.5, origin.getY()+0.5, origin.getZ()+0.5, s);
                    ei.setDefaultPickupDelay(); world.spawnEntityInWorld(ei);
                }
            }
        }
        if (VeinMinerMod.config.consumeHunger) player.addExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# --- Forge 1.16.5 key: remove fml.relauncher, use @OnlyIn(Dist.CLIENT) ---
FORGE_1165_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;
    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new StringTextComponent(msg), false);
        }
    }
}
"""

# --- Forge 1.17.1 handler: event.world (not event.level), hurtAndBreak with EquipmentSlot ---
FORGE_117_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = b.getRegistryName()==null?"":b.getRegistryName().toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.contains("deepslate")&&n.contains("_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = b.getRegistryName()==null?"":b.getRegistryName().toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = target.getRegistryName()==null?"":target.getRegistryName().toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# Forge 1.17.1 key: TextComponent (not Component.literal), ClientRegistry still exists
FORGE_117_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;
    public VeinMinerKeyHandler() {
        toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(msg), false);
        }
    }
}
"""


# --- Forge 1.18.x handler: event.world (not event.level), EquipmentSlot ---
# event.level was introduced in 1.19; 1.18 still uses event.world
FORGE_118_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = net.minecraft.core.Registry.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = net.minecraft.core.Registry.BLOCK.getKey(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = net.minecraft.core.Registry.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = net.minecraft.core.Registry.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# Forge 1.18.x key: TextComponent, ClientRegistry still exists
FORGE_118_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.ClientRegistry;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;
    public VeinMinerKeyHandler() {
        toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(msg), false);
        }
    }
}
"""


# --- Forge 1.19-1.20.4 handler: event.level, EquipmentSlot, BuiltInRegistries ---
# event.level introduced in 1.19; ClientRegistry removed in 1.19 -> RegisterKeyMappingsEvent
FORGE_119_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# Forge 1.19-1.20.4 key: RegisterKeyMappingsEvent (ClientRegistry removed), Component.literal
FORGE_119_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class VeinMinerKeyHandler {
    public static final KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;
    public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

# Forge 1.19-1.20.4 mod: register key mappings event
FORGE_119_MOD_FIXED = """\
package asd.itamio.veinminer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();
    public VeinMinerMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        bus.addListener(VeinMinerKeyHandler::register);
    }
    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""


# --- Forge 1.21-1.21.5: RegisterKeyMappingsEvent, EquipmentSlot ---
FORGE_121_KEY_FIXED = FORGE_119_KEY_FIXED
FORGE_121_MOD_FIXED = FORGE_119_MOD_FIXED
FORGE_121_HANDLER_FIXED = FORGE_119_HANDLER_FIXED

# --- Forge 1.21.6+: EventBus 7, no @SubscribeEvent, EquipmentSlot ---
FORGE_1216_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

FORGE_1216_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
public class VeinMinerKeyHandler {
    public static final KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;
    public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""

FORGE_1216_MOD_FIXED = """\
package asd.itamio.veinminer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();
    public VeinMinerMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        bus.addListener(VeinMinerKeyHandler::register);
    }
    private void setup(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
"""


# ===========================================================================
# FABRIC FIXES
# ===========================================================================

# --- Fabric 1.16.5 key: client.option (no 's') ---
FABRIC_1165_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Toggle Vein Miner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.sendMessage(new LiteralText(msg), false);
            }
        });
    }
}
"""

# --- Fabric 1.19-1.20.x handler: net.minecraft.registry.Registries (moved in 1.19) ---
FABRIC_119_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @Override
    public boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return true;
        if (world.isClient) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTime();
            Long last = cooldowns.get(player.getUuid());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUuid(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandStack())) return true;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }
    private boolean isVeinMineable(Block b) {
        String n = Registries.BLOCK.getId(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = Registries.BLOCK.getId(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = Registries.BLOCK.getId(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.add(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandStack();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        ServerWorld sw = (ServerWorld) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damage(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = Registries.ITEM.getId(d.getItem())+":"+d.getDamage();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxCount())); if(nc>ex.getMaxCount()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxCount());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setToDefaultPickupDelay(); world.spawnEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.addExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# Fabric 1.19 key: Text.literal (LiteralText removed in 1.19)
FABRIC_119_KEY_FIXED = FABRIC_119_KEY  # already uses Text.literal


# --- Fabric 1.21-1.21.4 (Mojang mappings, split):
# VeinMinerKeyHandler is in src/client/java — handler can't reference it directly.
# Solution: put veinMinerEnabled in VeinMinerMod (main source tree), key reads from there.
FABRIC_121_MAIN_FIXED = """\
package asd.itamio.veinminer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class VeinMinerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("veinminer");
    public static VeinMinerConfig config = new VeinMinerConfig();
    public static boolean veinMinerEnabled = true;
    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(new VeinMinerHandler());
    }
}
"""

# Fabric 1.21-1.21.4 handler: references VeinMinerMod.veinMinerEnabled, EquipmentSlot
FABRIC_121_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @Override
    public boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerMod.veinMinerEnabled) return true;
        if (world.isClientSide) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return true;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        ServerLevel sl = (ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# Fabric 1.21 key: reads VeinMinerMod.veinMinerEnabled (in main source tree)
FABRIC_121_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyMapping toggleKey;
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                VeinMinerMod.veinMinerEnabled = !VeinMinerMod.veinMinerEnabled;
                String msg = VeinMinerMod.veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.displayClientMessage(Component.literal(msg), false);
            }
        });
    }
}
"""

# Fabric 1.21.5+ handler: PickaxeItem/AxeItem/ShovelItem removed -> use tag check
# Also: isClientSide is accessible via world instanceof ServerLevel check
FABRIC_1215_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
public class VeinMinerHandler implements PlayerBlockBreakEvents.Before {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @Override
    public boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!VeinMinerMod.config.enableVeinMiner) return true;
        if (!VeinMinerMod.veinMinerEnabled) return true;
        if (!(world instanceof ServerLevel)) return true;
        if (player.isCreative()) return true;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return true;
        Block block = state.getBlock();
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return true;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return true;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(state, player.getMainHandItem())) return true;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
        return true;
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(BlockState state, ItemStack tool) {
        if (tool.isEmpty()) return false;
        return tool.isCorrectToolForDrops(state);
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        ServerLevel sl = (ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""


# ===========================================================================
# NEOFORGE FIXES
# ===========================================================================

# NeoForge 1.20.5-1.21.4: EquipmentSlot fix, PickaxeItem still exists
NEOFORGE_120_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.bus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.world.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.world.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.world.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# NeoForge 1.21.5+: isClientSide private -> use instanceof ServerLevel, PickaxeItem removed -> isCorrectToolForDrops
NEOFORGE_1215_HANDLER_FIXED = """\
package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.bus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        Player player = event.getPlayer();
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (!(world instanceof ServerLevel)) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(state, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore")||n.equals("minecraft:deepslate_coal_ore")||n.equals("minecraft:deepslate_iron_ore")||n.equals("minecraft:deepslate_gold_ore")||n.equals("minecraft:deepslate_diamond_ore")||n.equals("minecraft:deepslate_emerald_ore")||n.equals("minecraft:deepslate_lapis_ore")||n.equals("minecraft:deepslate_redstone_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:deepslate"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(BlockState state, ItemStack tool) {
        if (tool.isEmpty()) return false;
        return tool.isCorrectToolForDrops(state);
    }
    private Set<BlockPos> findVein(Level world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = BuiltInRegistries.BLOCK.getKey(target).toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(Level world, Player player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        ServerLevel sl = (ServerLevel) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, sl, pos, world.getBlockEntity(pos), player, tool);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = BuiltInRegistries.ITEM.getKey(d.getItem())+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
"""

# NeoForge 1.21.9+: KeyMapping category changed to Category object
NEOFORGE_1219_KEY_FIXED = """\
package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;
    public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\\u00a7aVein Miner: ENABLED" : "\\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
"""


# ===========================================================================
# BUNDLE DEFINITIONS (FIXED)
# ===========================================================================

PKG = "asd/itamio/veinminer"

def _forge_files(mod, handler, key, config=CONFIG_SRC):
    return {
        f"src/main/java/{PKG}/VeinMinerMod.java": mod,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }

def _fabric_presplit_files(main, handler, key, config=CONFIG_SRC, mod_json=None):
    d = {
        f"src/main/java/{PKG}/VeinMinerMod.java": main,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }
    if mod_json: d["src/main/resources/fabric.mod.json"] = mod_json
    return d

def _fabric_split_files(main, handler, key, config=CONFIG_SRC, mod_json=None):
    d = {
        f"src/main/java/{PKG}/VeinMinerMod.java": main,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/client/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }
    if mod_json: d["src/main/resources/fabric.mod.json"] = mod_json
    return d

def _neoforge_files(mod, handler, key, config=CONFIG_SRC):
    return {
        f"src/main/java/{PKG}/VeinMinerMod.java": mod,
        f"src/main/java/{PKG}/VeinMinerHandler.java": handler,
        f"src/main/java/{PKG}/VeinMinerKeyHandler.java": key,
        f"src/main/java/{PKG}/VeinMinerConfig.java": config,
    }

ALL_TARGETS = [
    # ---- Forge ----
    ("VeinMiner-1.8.9-forge",       "1.8.9",        "forge",
     _forge_files(FORGE_189_MOD, FORGE_189_HANDLER_FIXED, FORGE_189_KEY)),

    ("VeinMiner-1.12.2-forge",      "1.12.2",       "forge",
     _forge_files(FORGE_1122_MOD, FORGE_1122_HANDLER, FORGE_1122_KEY)),

    ("VeinMiner-1.16.5-forge",      "1.16.5",       "forge",
     _forge_files(FORGE_1165_MOD, FORGE_1165_HANDLER, FORGE_1165_KEY_FIXED)),

    ("VeinMiner-1.17.1-forge",      "1.17.1",       "forge",
     _forge_files(FORGE_117_MOD, FORGE_117_HANDLER_FIXED, FORGE_117_KEY_FIXED)),

    ("VeinMiner-1.18-1.18.2-forge", "1.18-1.18.2",  "forge",
     _forge_files(FORGE_118_MOD, FORGE_118_HANDLER_FIXED, FORGE_118_KEY_FIXED)),

    ("VeinMiner-1.19-1.19.4-forge", "1.19-1.19.4",  "forge",
     _forge_files(FORGE_119_MOD_FIXED, FORGE_119_HANDLER_FIXED, FORGE_119_KEY_FIXED)),

    ("VeinMiner-1.20.1-forge",      "1.20.1",       "forge",
     _forge_files(FORGE_119_MOD_FIXED, FORGE_119_HANDLER_FIXED, FORGE_119_KEY_FIXED)),

    ("VeinMiner-1.20.4-forge",      "1.20.4",       "forge",
     _forge_files(FORGE_119_MOD_FIXED, FORGE_119_HANDLER_FIXED, FORGE_119_KEY_FIXED)),

    ("VeinMiner-1.20.6-forge",      "1.20.6",       "forge",
     _forge_files(FORGE_119_MOD_FIXED, FORGE_119_HANDLER_FIXED, FORGE_119_KEY_FIXED)),

    ("VeinMiner-1.21-1.21.1-forge", "1.21-1.21.1",  "forge",
     _forge_files(FORGE_121_MOD_FIXED, FORGE_121_HANDLER_FIXED, FORGE_121_KEY_FIXED)),

    ("VeinMiner-1.21.3-1.21.5-forge","1.21.3-1.21.5","forge",
     _forge_files(FORGE_121_MOD_FIXED, FORGE_121_HANDLER_FIXED, FORGE_121_KEY_FIXED)),

    ("VeinMiner-1.21.6-1.21.8-forge","1.21.6-1.21.8","forge",
     _forge_files(FORGE_1216_MOD_FIXED, FORGE_1216_HANDLER_FIXED, FORGE_1216_KEY_FIXED)),

    ("VeinMiner-1.21.9-1.21.11-forge","1.21.9-1.21.11","forge",
     _forge_files(FORGE_1216_MOD_FIXED, FORGE_1216_HANDLER_FIXED, FORGE_1216_KEY_FIXED)),

    # ---- Fabric ----
    ("VeinMiner-1.16.5-fabric",     "1.16.5",       "fabric",
     _fabric_presplit_files(FABRIC_1165_MAIN, FABRIC_1165_HANDLER, FABRIC_1165_KEY_FIXED,
                            mod_json=FABRIC_1165_MOD_JSON_WITH_CLIENT)),

    ("VeinMiner-1.17.1-fabric",     "1.17.1",       "fabric",
     _fabric_presplit_files(FABRIC_117_MAIN, FABRIC_117_HANDLER, FABRIC_117_KEY,
                            mod_json=FABRIC_117_MOD_JSON)),

    ("VeinMiner-1.18-1.18.2-fabric","1.18-1.18.2",  "fabric",
     _fabric_presplit_files(FABRIC_117_MAIN, FABRIC_117_HANDLER, FABRIC_117_KEY,
                            mod_json=FABRIC_117_MOD_JSON)),

    ("VeinMiner-1.19-1.19.4-fabric","1.19-1.19.4",  "fabric",
     _fabric_presplit_files(FABRIC_117_MAIN, FABRIC_119_HANDLER_FIXED, FABRIC_119_KEY,
                            mod_json=FABRIC_117_MOD_JSON)),

    ("VeinMiner-1.20.1-1.20.6-fabric","1.20.1-1.20.6","fabric",
     _fabric_split_files(FABRIC_120_MAIN, FABRIC_119_HANDLER_FIXED, FABRIC_120_KEY,
                         mod_json=FABRIC_120_MOD_JSON)),

    ("VeinMiner-1.21-1.21.1-fabric","1.21-1.21.1",  "fabric",
     _fabric_split_files(FABRIC_121_MAIN_FIXED, FABRIC_121_HANDLER_FIXED, FABRIC_121_KEY_FIXED,
                         mod_json=FABRIC_121_MOD_JSON)),

    ("VeinMiner-1.21.2-1.21.4-fabric","1.21.2-1.21.4","fabric",
     _fabric_split_files(FABRIC_121_MAIN_FIXED, FABRIC_121_HANDLER_FIXED, FABRIC_121_KEY_FIXED,
                         mod_json=FABRIC_121_MOD_JSON)),

    ("VeinMiner-1.21.5-1.21.8-fabric","1.21.5-1.21.8","fabric",
     _fabric_split_files(FABRIC_121_MAIN_FIXED, FABRIC_1215_HANDLER_FIXED, FABRIC_121_KEY_FIXED,
                         mod_json=FABRIC_121_MOD_JSON)),

    ("VeinMiner-1.21.9-1.21.11-fabric","1.21.9-1.21.11","fabric",
     _fabric_split_files(FABRIC_121_MAIN_FIXED, FABRIC_1215_HANDLER_FIXED, FABRIC_121_KEY_FIXED,
                         mod_json=FABRIC_121_MOD_JSON)),

    # ---- NeoForge ----
    # Use exact versions to avoid 1.20.3 which is not in supported_versions
    ("VeinMiner-1.20.2-neoforge",   "1.20.2",       "neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.20.4-neoforge",   "1.20.4",       "neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.20.5-neoforge",   "1.20.5",       "neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.20.6-neoforge",   "1.20.6",       "neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.21-1.21.1-neoforge","1.21-1.21.1","neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.21.2-1.21.4-neoforge","1.21.2-1.21.4","neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_120_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.21.5-1.21.8-neoforge","1.21.5-1.21.8","neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_1215_HANDLER_FIXED, NEOFORGE_120_KEY)),

    ("VeinMiner-1.21.9-1.21.11-neoforge","1.21.9-1.21.11","neoforge",
     _neoforge_files(NEOFORGE_120_MOD, NEOFORGE_1215_HANDLER_FIXED, NEOFORGE_1219_KEY_FIXED)),
]

# ===========================================================================
# FAILED-ONLY SUPPORT
# ===========================================================================

def get_failed_targets():
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists(): return None
    runs = sorted([d for d in runs_dir.iterdir() if (d/"artifacts"/"all-mod-builds"/"mods").exists()], reverse=True)
    if not runs: return None
    mods_dir = runs[0] / "artifacts" / "all-mod-builds" / "mods"
    import json
    failed = set()
    for mod_dir in mods_dir.iterdir():
        r = mod_dir / "result.json"
        if r.exists():
            data = json.loads(r.read_text())
            if data.get("status") != "success":
                failed.add(mod_dir.name)
    return failed if failed else None

# ===========================================================================
# MAIN
# ===========================================================================

def write_target(base: Path, folder: str, mc_version: str, loader: str, files: dict):
    target_dir = base / folder
    target_dir.mkdir(parents=True, exist_ok=True)
    (target_dir / "mod.txt").write_text(
        MOD_TXT_BASE + "entrypoint_class=asd.itamio.veinminer.VeinMinerMod\n",
        encoding="utf-8")
    (target_dir / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader={loader}\n", encoding="utf-8")
    for rel, content in files.items():
        p = target_dir / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")

def build_zip(bundle_dir: Path, zip_path: Path):
    import zipfile
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(bundle_dir.rglob("*")):
            if f.is_file():
                zf.write(f, f.relative_to(bundle_dir))
    print(f"Created {zip_path} ({zip_path.stat().st_size // 1024} KB)")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true")
    args = parser.parse_args()

    targets = ALL_TARGETS
    if args.failed_only:
        failed = get_failed_targets()
        if failed:
            targets = [t for t in ALL_TARGETS if t[0] in failed]
            print(f"--failed-only: {len(targets)} targets: {[t[0] for t in targets]}")
        else:
            print("--failed-only: no failed targets found, rebuilding all")

    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)
    for folder, mc_version, loader, files in targets:
        print(f"  Writing {folder}")
        write_target(BUNDLE_DIR, folder, mc_version, loader, files)

    build_zip(BUNDLE_DIR, ZIP_PATH)
    print(f"\nDone. {len(targets)} targets written.")

if __name__ == "__main__":
    main()
