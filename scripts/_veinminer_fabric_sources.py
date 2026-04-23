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
