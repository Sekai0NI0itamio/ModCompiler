package asd.itamio.instantleafdecay;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

public class LeafDecayHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        Block block = state.getBlock();
        
        // Only run on server side
        if (world.isRemote) {
            return;
        }

        // Check if a log was broken
        if (block == Blocks.LOG || block == Blocks.LOG2) {
            // Schedule leaf decay check for next tick
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    decayNearbyLeaves(world, pos);
                }
            }, 100); // 100ms delay to let the log break complete
        }
    }

    private void decayNearbyLeaves(World world, BlockPos centerPos) {
        // Search for leaves in a large radius
        int radius = 8;
        Set<BlockPos> leavesToDecay = new HashSet<>();
        
        // Find all leaves that should decay
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = centerPos.add(x, y, z);
                    IBlockState state = world.getBlockState(checkPos);
                    Block block = state.getBlock();
                    
                    if (block instanceof BlockLeaves) {
                        // Check if this leaf should decay (not player-placed and no nearby logs)
                        if (!state.getValue(BlockLeaves.DECAYABLE)) {
                            continue; // Skip player-placed leaves
                        }
                        
                        if (!hasNearbyLog(world, checkPos, 4)) {
                            leavesToDecay.add(checkPos);
                        }
                    }
                }
            }
        }
        
        // Decay all leaves instantly
        if (!leavesToDecay.isEmpty()) {
            decayLeavesInstantly(world, leavesToDecay);
        }
    }

    private boolean hasNearbyLog(World world, BlockPos leafPos, int range) {
        // Check if there's a log within range
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = leafPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    if (block == Blocks.LOG || block == Blocks.LOG2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void decayLeavesInstantly(World world, Set<BlockPos> leaves) {
        // Calculate center position for item drops
        BlockPos centerPos = calculateCenter(leaves);
        
        // Collect all drops with proper loot calculation
        Map<ItemStack, Integer> itemCounts = new HashMap<>();
        Random rand = world.rand;
        
        // Break all leaves without particles
        for (BlockPos pos : leaves) {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            
            if (block instanceof BlockLeaves) {
                // Manually calculate drops to ensure all loot types work
                BlockLeaves leafBlock = (BlockLeaves) block;
                
                // Get sapling drop (5% for most, 2.5% for jungle)
                int saplingChance = 20; // 1 in 20 = 5%
                if (block == Blocks.LEAVES && state.getValue(BlockOldLeaf.VARIANT) == BlockPlanks.EnumType.JUNGLE) {
                    saplingChance = 40; // 1 in 40 = 2.5% for jungle
                }
                
                if (rand.nextInt(saplingChance) == 0) {
                    // Get the sapling item from the leaf block
                    Item saplingItem = leafBlock.getItemDropped(state, rand, 0);
                    if (saplingItem != null) {
                        int meta = leafBlock.damageDropped(state);
                        addToItemCounts(itemCounts, new ItemStack(saplingItem, 1, meta));
                    }
                }
                
                // Get apple drop (0.5% for oak and dark oak only)
                if (block == Blocks.LEAVES) {
                    BlockPlanks.EnumType type = state.getValue(BlockOldLeaf.VARIANT);
                    if (type == BlockPlanks.EnumType.OAK) {
                        if (rand.nextInt(200) == 0) { // 1 in 200 = 0.5%
                            addToItemCounts(itemCounts, new ItemStack(Items.APPLE));
                        }
                    }
                }
                
                // Get stick drop (2% chance)
                if (rand.nextInt(50) == 0) { // 1 in 50 = 2%
                    addToItemCounts(itemCounts, new ItemStack(Items.STICK, 1 + rand.nextInt(2))); // 1-2 sticks
                }
                
                // Remove the leaf block without particles (flag 2)
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
        }
        
        // Spawn stacked items at center
        for (Map.Entry<ItemStack, Integer> entry : itemCounts.entrySet()) {
            ItemStack stack = entry.getKey().copy();
            int totalCount = entry.getValue();
            
            // Split into max stack sizes
            while (totalCount > 0) {
                int stackSize = Math.min(totalCount, stack.getMaxStackSize());
                ItemStack dropStack = stack.copy();
                dropStack.setCount(stackSize);
                
                Block.spawnAsEntity(world, centerPos, dropStack);
                totalCount -= stackSize;
            }
        }
    }

    private void addToItemCounts(Map<ItemStack, Integer> itemCounts, ItemStack newStack) {
        boolean found = false;
        for (Map.Entry<ItemStack, Integer> entry : itemCounts.entrySet()) {
            if (ItemStack.areItemsEqual(entry.getKey(), newStack) && 
                ItemStack.areItemStackTagsEqual(entry.getKey(), newStack)) {
                itemCounts.put(entry.getKey(), entry.getValue() + newStack.getCount());
                found = true;
                break;
            }
        }
        if (!found) {
            itemCounts.put(newStack.copy(), newStack.getCount());
        }
    }

    private BlockPos calculateCenter(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return BlockPos.ORIGIN;
        }
        
        int sumX = 0, sumY = 0, sumZ = 0;
        for (BlockPos pos : positions) {
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }
        
        int size = positions.size();
        return new BlockPos(sumX / size, sumY / size, sumZ / size);
    }
}
