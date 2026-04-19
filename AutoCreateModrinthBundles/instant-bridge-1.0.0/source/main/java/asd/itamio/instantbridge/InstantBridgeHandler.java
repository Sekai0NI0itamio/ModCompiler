package asd.itamio.instantbridge;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InstantBridgeHandler {
    
    private final Map<UUID, Integer> playerCooldowns = new HashMap<>();
    private final Map<UUID, BlockPos> lastPlayerPositions = new HashMap<>();
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        
        if (!InstantBridgeMod.config.enableInstantBridge) {
            return;
        }
        
        EntityPlayer player = event.player;
        UUID playerId = player.getUniqueID();
        
        // Update cooldown
        if (playerCooldowns.containsKey(playerId)) {
            int cooldown = playerCooldowns.get(playerId);
            if (cooldown > 0) {
                playerCooldowns.put(playerId, cooldown - 1);
                return;
            }
        }
        
        // Check if player should place blocks
        if (!shouldPlaceBlock(player, playerId)) {
            return;
        }
        
        // Try to place block beneath player
        if (placeBlockBeneath(player)) {
            // Set cooldown
            playerCooldowns.put(playerId, InstantBridgeMod.config.placementDelay);
        }
    }
    
    private boolean shouldPlaceBlock(EntityPlayer player, UUID playerId) {
        // Check if sneaking is required
        if (InstantBridgeMod.config.requireSneaking && !player.isSneaking()) {
            return false;
        }
        
        // Check if player needs to be moving
        if (InstantBridgeMod.config.placeOnlyWhenMoving) {
            BlockPos currentPos = player.getPosition();
            BlockPos lastPos = lastPlayerPositions.get(playerId);
            
            // Update last position
            lastPlayerPositions.put(playerId, currentPos);
            
            // Check if player moved
            if (lastPos != null && lastPos.equals(currentPos)) {
                return false; // Player hasn't moved
            }
        }
        
        return true;
    }
    
    private boolean placeBlockBeneath(EntityPlayer player) {
        World world = player.world;
        BlockPos playerPos = player.getPosition();
        BlockPos belowPos = playerPos.down();
        
        // Check if there's already a block below
        IBlockState belowState = world.getBlockState(belowPos);
        if (!belowState.getBlock().isReplaceable(world, belowPos)) {
            return false; // Already a solid block
        }
        
        // Find a block in player's inventory
        ItemStack blockStack = findBlockInInventory(player);
        if (blockStack.isEmpty()) {
            return false; // No blocks available
        }
        
        // Get the block to place
        if (!(blockStack.getItem() instanceof ItemBlock)) {
            return false;
        }
        
        ItemBlock itemBlock = (ItemBlock) blockStack.getItem();
        Block block = itemBlock.getBlock();
        
        // Place the block
        IBlockState stateToPlace = block.getDefaultState();
        world.setBlockState(belowPos, stateToPlace, 3);
        
        // Play placement sound
        world.playSound(
            null,
            belowPos,
            block.getSoundType(stateToPlace, world, belowPos, player).getPlaceSound(),
            net.minecraft.util.SoundCategory.BLOCKS,
            1.0F,
            1.0F
        );
        
        // Consume one block from inventory (if not creative)
        if (!player.capabilities.isCreativeMode) {
            blockStack.shrink(1);
            if (blockStack.isEmpty()) {
                // Remove empty stack
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    if (player.inventory.getStackInSlot(i) == blockStack) {
                        player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }
        }
        
        return true;
    }
    
    private ItemStack findBlockInInventory(EntityPlayer player) {
        // Search through player's inventory for a placeable block
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            
            if (stack.isEmpty()) {
                continue;
            }
            
            // Check if it's a block item
            if (stack.getItem() instanceof ItemBlock) {
                ItemBlock itemBlock = (ItemBlock) stack.getItem();
                Block block = itemBlock.getBlock();
                
                // Skip certain blocks that shouldn't be used for bridging
                if (block == Blocks.TNT || 
                    block == Blocks.SAND || 
                    block == Blocks.GRAVEL ||
                    block == Blocks.BEDROCK) {
                    continue;
                }
                
                return stack;
            }
        }
        
        return ItemStack.EMPTY;
    }
}
