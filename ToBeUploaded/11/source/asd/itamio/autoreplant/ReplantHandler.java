package asd.itamio.autoreplant;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ReplantHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.HarvestDropsEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        EntityPlayer player = event.getHarvester();
        
        // Only run on server side
        if (world.isRemote || player == null) {
            return;
        }

        Block block = state.getBlock();
        
        // Handle crops (wheat, carrots, potatoes, beetroot)
        if (block instanceof BlockCrops) {
            handleCropReplant(world, pos, state, block, player);
        }
        // Handle cocoa beans
        else if (block instanceof BlockCocoa) {
            handleCocoaReplant(world, pos, state, player);
        }
        // Handle saplings (trees)
        else if (block == Blocks.LOG || block == Blocks.LOG2) {
            handleTreeReplant(world, pos, player);
        }
    }

    private void handleCropReplant(World world, BlockPos pos, IBlockState state, Block block, EntityPlayer player) {
        BlockCrops crop = (BlockCrops) block;
        
        // Get the correct age property for this crop
        int age;
        int maxAge;
        
        if (block == Blocks.BEETROOTS) {
            // Beetroot uses BlockBeetroot.BEETROOT_AGE (0-3)
            age = state.getValue(net.minecraft.block.BlockBeetroot.BEETROOT_AGE);
            maxAge = 3;
        } else {
            // Other crops use BlockCrops.AGE (0-7)
            age = state.getValue(BlockCrops.AGE);
            maxAge = 7;
        }
        
        // Only replant if crop is fully grown
        if (age >= maxAge) {
            // Determine what seed to use and what block to plant
            Item seedItem = null;
            Block blockToPlant = null;
            
            if (block == Blocks.WHEAT) {
                seedItem = Items.WHEAT_SEEDS;
                blockToPlant = Blocks.WHEAT;
            } else if (block == Blocks.CARROTS) {
                seedItem = Items.CARROT;
                blockToPlant = Blocks.CARROTS;
            } else if (block == Blocks.POTATOES) {
                seedItem = Items.POTATO;
                blockToPlant = Blocks.POTATOES;
            } else if (block == Blocks.BEETROOTS) {
                seedItem = Items.BEETROOT_SEEDS;
                blockToPlant = Blocks.BEETROOTS;
            }
            
            if (seedItem != null && blockToPlant != null && consumeItemFromInventory(player, seedItem)) {
                // Schedule replant for next tick to avoid conflicts with block breaking
                final Block finalBlock = blockToPlant;
                
                // Use a runnable to delay the replant
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if (world.isAirBlock(pos)) {
                            world.setBlockState(pos, finalBlock.getDefaultState(), 3);
                        }
                    }
                }, 50); // 50ms delay (1 tick)
            }
        }
    }

    private void handleCocoaReplant(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        // Only replant if cocoa is fully grown
        int age = state.getValue(BlockCocoa.AGE);
        
        if (age >= 2) { // Cocoa is fully grown at age 2
            // Cocoa beans are Items.DYE with metadata 3 in 1.12.2
            if (consumeItemFromInventory(player, Items.DYE, 3)) {
                // Get the facing direction from the old state
                EnumFacing facing = state.getValue(BlockCocoa.FACING);
                
                // Schedule replant for next tick
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if (world.isAirBlock(pos)) {
                            IBlockState newState = Blocks.COCOA.getDefaultState()
                                .withProperty(BlockCocoa.FACING, facing)
                                .withProperty(BlockCocoa.AGE, 0);
                            world.setBlockState(pos, newState, 3);
                        }
                    }
                }, 50); // 50ms delay
            }
        }
    }

    private void handleTreeReplant(World world, BlockPos pos, EntityPlayer player) {
        // Find the bottom log of the tree
        BlockPos groundPos = pos;
        while (world.getBlockState(groundPos.down()).getBlock() == Blocks.LOG || 
               world.getBlockState(groundPos.down()).getBlock() == Blocks.LOG2) {
            groundPos = groundPos.down();
        }
        
        // Check if there's dirt/grass below
        Block belowBlock = world.getBlockState(groundPos.down()).getBlock();
        if (belowBlock == Blocks.DIRT || belowBlock == Blocks.GRASS) {
            // Try to plant a sapling
            if (consumeItemFromInventory(player, Item.getItemFromBlock(Blocks.SAPLING))) {
                // Plant oak sapling (can be expanded to detect tree type)
                world.setBlockState(groundPos, Blocks.SAPLING.getDefaultState());
            }
        }
    }

    private boolean consumeItemFromInventory(EntityPlayer player, Item item) {
        return consumeItemFromInventory(player, item, -1);
    }

    private boolean consumeItemFromInventory(EntityPlayer player, Item item, int metadata) {
        // Check if player is in creative mode
        if (player.capabilities.isCreativeMode) {
            return true; // Don't consume in creative
        }

        // Search player's inventory for the item
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            
            if (!stack.isEmpty() && stack.getItem() == item) {
                // If metadata matters (like for cocoa beans), check it
                if (metadata >= 0 && stack.getMetadata() != metadata) {
                    continue;
                }
                
                // Consume one item
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        
        return false; // Item not found in inventory
    }
}
