package asd.itamio.instantleafdecay;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
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
        
        // Collect all drops with proper loot tables
        Map<ItemStack, Integer> itemCounts = new HashMap<>();
        Random rand = world.rand;
        
        // Break all leaves without particles
        for (BlockPos pos : leaves) {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            
            if (block instanceof BlockLeaves) {
                // Use the block's proper drop method with fortune 0
                // This respects the leaf's drop chances (saplings, apples, sticks, etc.)
                List<ItemStack> drops = block.getDrops(world, pos, state, 0);
                
                // Count items
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        boolean found = false;
                        for (Map.Entry<ItemStack, Integer> entry : itemCounts.entrySet()) {
                            if (ItemStack.areItemsEqual(entry.getKey(), drop) && 
                                ItemStack.areItemStackTagsEqual(entry.getKey(), drop)) {
                                itemCounts.put(entry.getKey(), entry.getValue() + drop.getCount());
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            itemCounts.put(drop.copy(), drop.getCount());
                        }
                    }
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
