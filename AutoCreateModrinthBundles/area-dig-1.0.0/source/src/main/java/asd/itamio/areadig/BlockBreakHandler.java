package asd.itamio.areadig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class BlockBreakHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        
        // Only run on server side
        if (world.isRemote || player == null) {
            return;
        }

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(AreaDigMod.AREA_DIG_ENCHANTMENT, heldItem);
        
        if (enchantmentLevel <= 0) {
            return;
        }

        // Calculate radius based on enchantment level
        int radius = enchantmentLevel + 1;
        
        // Get all blocks in the cube around the broken block
        List<BlockPos> blocksToBreak = getBlocksInCube(pos, radius);
        
        // Collect all drops in one list
        List<ItemStack> allDrops = new ArrayList<>();
        
        // Break each block silently
        for (BlockPos targetPos : blocksToBreak) {
            if (targetPos.equals(pos)) {
                continue; // Skip the original block (already broken)
            }
            
            IBlockState state = world.getBlockState(targetPos);
            Block block = state.getBlock();
            
            // Don't break air
            if (block == Blocks.AIR) {
                continue;
            }
            
            // Don't break bedrock
            if (block == Blocks.BEDROCK) {
                continue;
            }
            
            // Check hardness
            float hardness = state.getBlockHardness(world, targetPos);
            if (hardness < 0) {
                continue;
            }
            
            // Check if tool can break this block
            if (!ForgeHooks.canHarvestBlock(block, player, world, targetPos)) {
                continue;
            }
            
            // Collect drops from this block
            List<ItemStack> drops = collectBlockDrops(world, targetPos, player, heldItem, state);
            allDrops.addAll(drops);
            
            // Remove the block silently (no particles, no sound)
            world.setBlockToAir(targetPos);
            
            // Damage the tool
            if (!player.capabilities.isCreativeMode) {
                heldItem.damageItem(1, player);
                
                // Stop if tool broke
                if (heldItem.getCount() == 0) {
                    break;
                }
            }
        }
        
        // Drop all items at the center position (where player broke the block)
        spawnItemsAtPosition(world, pos, allDrops);
    }

    private List<BlockPos> getBlocksInCube(BlockPos center, int radius) {
        List<BlockPos> blocks = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    blocks.add(center.add(x, y, z));
                }
            }
        }
        
        return blocks;
    }

    private List<ItemStack> collectBlockDrops(World world, BlockPos pos, EntityPlayer player, ItemStack tool, IBlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        
        try {
            // Get the drops from the block
            NonNullList<ItemStack> blockDrops = NonNullList.create();
            state.getBlock().getDrops(blockDrops, world, pos, state, 0);
            
            // Apply fortune if the tool has it
            int fortune = EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.FORTUNE, tool);
            if (fortune > 0) {
                // Re-get drops with fortune
                blockDrops.clear();
                state.getBlock().getDrops(blockDrops, world, pos, state, fortune);
            }
            
            drops.addAll(blockDrops);
        } catch (Exception e) {
            // If something goes wrong, just skip this block's drops
        }
        
        return drops;
    }

    private void spawnItemsAtPosition(World world, BlockPos pos, List<ItemStack> items) {
        // Combine stacks of the same item
        List<ItemStack> combinedStacks = combineItemStacks(items);
        
        // Spawn each combined stack at the center position
        for (ItemStack stack : combinedStacks) {
            if (!stack.isEmpty()) {
                // Spawn at center of block with slight upward offset
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5;
                
                EntityItem entityItem = new EntityItem(world, x, y, z, stack);
                
                // No motion - items spawn stationary
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;
                
                // Prevent pickup delay
                entityItem.setDefaultPickupDelay();
                
                world.spawnEntity(entityItem);
            }
        }
    }

    private List<ItemStack> combineItemStacks(List<ItemStack> items) {
        List<ItemStack> combined = new ArrayList<>();
        
        for (ItemStack newStack : items) {
            if (newStack.isEmpty()) {
                continue;
            }
            
            boolean merged = false;
            
            // Try to merge with existing stacks
            for (ItemStack existingStack : combined) {
                if (canMerge(existingStack, newStack)) {
                    int spaceLeft = existingStack.getMaxStackSize() - existingStack.getCount();
                    int toAdd = Math.min(spaceLeft, newStack.getCount());
                    
                    existingStack.grow(toAdd);
                    newStack.shrink(toAdd);
                    
                    if (newStack.isEmpty()) {
                        merged = true;
                        break;
                    }
                }
            }
            
            // If not fully merged, add remaining as new stack
            if (!merged && !newStack.isEmpty()) {
                combined.add(newStack.copy());
            }
        }
        
        return combined;
    }

    private boolean canMerge(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }
        
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        }
        
        if (stack1.getMetadata() != stack2.getMetadata()) {
            return false;
        }
        
        if (!ItemStack.areItemStackTagsEqual(stack1, stack2)) {
            return false;
        }
        
        return stack1.getCount() < stack1.getMaxStackSize();
    }
}
