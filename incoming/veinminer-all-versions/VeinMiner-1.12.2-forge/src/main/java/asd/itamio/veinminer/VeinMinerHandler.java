package asd.itamio.veinminer;

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
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
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
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getHeldItemMainhand())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b == Blocks.COAL_ORE || b == Blocks.IRON_ORE ||
            b == Blocks.GOLD_ORE || b == Blocks.DIAMOND_ORE || b == Blocks.EMERALD_ORE ||
            b == Blocks.LAPIS_ORE || b == Blocks.REDSTONE_ORE || b == Blocks.LIT_REDSTONE_ORE ||
            b == Blocks.QUARTZ_ORE)) return true;
        if (VeinMinerMod.config.mineLogs && (b == Blocks.LOG || b == Blocks.LOG2)) return true;
        if (VeinMinerMod.config.mineStone && (b == Blocks.STONE || b == Blocks.COBBLESTONE)) return true;
        if (VeinMinerMod.config.mineDirt && (b == Blocks.DIRT || b == Blocks.GRASS)) return true;
        if (VeinMinerMod.config.mineGravel && b == Blocks.GRAVEL) return true;
        if (VeinMinerMod.config.mineSand && b == Blocks.SAND) return true;
        if (VeinMinerMod.config.mineClay && b == Blocks.CLAY) return true;
        if (VeinMinerMod.config.mineNetherrack && b == Blocks.NETHERRACK) return true;
        if (VeinMinerMod.config.mineEndStone && b == Blocks.END_STONE) return true;
        if (VeinMinerMod.config.mineGlowstone && b == Blocks.GLOWSTONE) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String toolClass = tool.getItem().getToolClasses(tool).isEmpty() ? "" :
                          tool.getItem().getToolClasses(tool).iterator().next();
        if (b == Blocks.COAL_ORE || b == Blocks.IRON_ORE || b == Blocks.GOLD_ORE ||
            b == Blocks.DIAMOND_ORE || b == Blocks.EMERALD_ORE || b == Blocks.LAPIS_ORE ||
            b == Blocks.REDSTONE_ORE || b == Blocks.LIT_REDSTONE_ORE || b == Blocks.QUARTZ_ORE ||
            b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.NETHERRACK ||
            b == Blocks.END_STONE || b == Blocks.GLOWSTONE) return toolClass.equals("pickaxe");
        if (b == Blocks.LOG || b == Blocks.LOG2) return toolClass.equals("axe");
        if (b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.GRAVEL ||
            b == Blocks.SAND || b == Blocks.CLAY) return toolClass.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, IBlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        boolean isLog = (target == Blocks.LOG || target == Blocks.LOG2);
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos nb = cur.add(dx, dy, dz);
                if (vein.contains(nb) || vein.size() >= max) continue;
                IBlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock() != target) continue;
                if (isLog && nbs != startState) continue;
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
            state.getBlock().getDrops(drops, world, pos, state, 0);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos);
            mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) { tool.shrink(1); break; }
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, origin, origState.getBlock().getSoundType(origState, world, origin, player).getBreakSound(),
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String, ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName().toString() + ":" + d.getMetadata();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key); int nc = ex.getCount() + d.getCount();
                    ex.setCount(Math.min(nc, ex.getMaxStackSize()));
                    if (nc > ex.getMaxStackSize()) { ItemStack ov = d.copy(); ov.setCount(nc - ex.getMaxStackSize()); combined.put(key + "_" + combined.size(), ov); }
                } else combined.put(key, d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) {
                EntityItem ei = new EntityItem(world, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, s);
                ei.setDefaultPickupDelay(); world.spawnEntity(ei);
            }
        }
        if (VeinMinerMod.config.consumeHunger) player.addExhaustion(0.005f * mined * VeinMinerMod.config.hungerMultiplier);
    }
}
