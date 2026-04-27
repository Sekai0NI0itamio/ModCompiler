package asd.itamio.veinminer;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        EntityPlayer player = event.getPlayer();
        World world = event.world;
        BlockPos pos = event.pos;
        IBlockState state = event.state;
        Block block = state.getBlock();
        if (world.isRemote) return;
        if (player.capabilities.isCreativeMode) return;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTotalWorldTime();
            Long last = cooldowns.get(player.getUniqueID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUniqueID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getHeldItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }

    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b == Blocks.coal_ore || b == Blocks.iron_ore ||
            b == Blocks.gold_ore || b == Blocks.diamond_ore || b == Blocks.emerald_ore ||
            b == Blocks.lapis_ore || b == Blocks.redstone_ore || b == Blocks.lit_redstone_ore ||
            b == Blocks.quartz_ore)) return true;
        if (VeinMinerMod.config.mineLogs && (b == Blocks.log || b == Blocks.log2)) return true;
        if (VeinMinerMod.config.mineStone && (b == Blocks.stone || b == Blocks.cobblestone)) return true;
        if (VeinMinerMod.config.mineDirt && (b == Blocks.dirt || b == Blocks.grass)) return true;
        if (VeinMinerMod.config.mineGravel && b == Blocks.gravel) return true;
        if (VeinMinerMod.config.mineSand && b == Blocks.sand) return true;
        if (VeinMinerMod.config.mineClay && b == Blocks.clay) return true;
        if (VeinMinerMod.config.mineNetherrack && b == Blocks.netherrack) return true;
        if (VeinMinerMod.config.mineEndStone && b == Blocks.end_stone) return true;
        if (VeinMinerMod.config.mineGlowstone && b == Blocks.glowstone) return true;
        return false;
    }

    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool == null) return false;
        String toolClass = tool.getItem().getToolClasses(tool).isEmpty() ? "" :
                          tool.getItem().getToolClasses(tool).iterator().next();
        if (b == Blocks.coal_ore || b == Blocks.iron_ore || b == Blocks.gold_ore ||
            b == Blocks.diamond_ore || b == Blocks.emerald_ore || b == Blocks.lapis_ore ||
            b == Blocks.redstone_ore || b == Blocks.lit_redstone_ore || b == Blocks.quartz_ore ||
            b == Blocks.stone || b == Blocks.cobblestone || b == Blocks.netherrack ||
            b == Blocks.end_stone || b == Blocks.glowstone) return toolClass.equals("pickaxe");
        if (b == Blocks.log || b == Blocks.log2) return toolClass.equals("axe");
        if (b == Blocks.dirt || b == Blocks.grass || b == Blocks.gravel ||
            b == Blocks.sand || b == Blocks.clay) return toolClass.equals("shovel");
        return true;
    }

    private Set<BlockPos> findVein(World world, BlockPos start, Block target, IBlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        boolean isLog = (target == Blocks.log || target == Blocks.log2);
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
        ItemStack tool = player.getHeldItem();
        List<ItemStack> allDrops = new ArrayList<>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            IBlockState state = world.getBlockState(pos);
            List<ItemStack> drops = state.getBlock().getDrops(world, pos, state, 0);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos);
            mined++;
            if (VeinMinerMod.config.consumeDurability && tool != null) {
                tool.damageItem(1, player);
                if (tool.stackSize <= 0) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) {
            world.playSoundEffect(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5,
                origState.getBlock().stepSound.getBreakSound(), 1.0f, 1.0f);
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String, ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = Block.blockRegistry.getNameForObject(d.getItem()) + ":" + d.getItemDamage();
                if (combined.containsKey(key)) {
                    ItemStack ex = combined.get(key);
                    int nc = ex.stackSize + d.stackSize;
                    ex.stackSize = Math.min(nc, ex.getMaxStackSize());
                    if (nc > ex.getMaxStackSize()) {
                        ItemStack ov = d.copy(); ov.stackSize = nc - ex.getMaxStackSize();
                        combined.put(key + "_" + combined.size(), ov);
                    }
                } else combined.put(key, d.copy());
            }
            for (ItemStack s : combined.values()) {
                if (s != null && s.stackSize > 0) {
                    EntityItem ei = new EntityItem(world, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, s);
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
