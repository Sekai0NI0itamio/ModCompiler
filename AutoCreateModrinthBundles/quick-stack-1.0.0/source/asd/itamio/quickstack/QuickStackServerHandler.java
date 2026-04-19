package asd.itamio.quickstack;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuickStackServerHandler {
    
    public static void performQuickStack(EntityPlayerMP player) {
        if (!QuickStackMod.config.enableQuickStack) {
            return;
        }
        
        if (player == null || player.world == null) {
            return;
        }
        
        // Find nearby containers
        List<IInventory> nearbyContainers = findNearbyContainers(player);
        
        if (nearbyContainers.isEmpty()) {
            return;
        }
        
        // Track what items were moved
        Map<String, Integer> movedItems = new HashMap<>();
        int totalItemsMoved = 0;
        
        // Get inventory range (skip hotbar if configured)
        int startSlot = QuickStackMod.config.includeHotbar ? 0 : 9;
        int endSlot = player.inventory.getSizeInventory();
        
        // Go through player inventory
        for (int playerSlot = startSlot; playerSlot < endSlot; playerSlot++) {
            ItemStack playerStack = player.inventory.getStackInSlot(playerSlot);
            
            if (playerStack.isEmpty()) {
                continue;
            }
            
            // Try to stack this item to containers
            for (IInventory container : nearbyContainers) {
                if (playerStack.isEmpty()) {
                    break;
                }
                
                // Check if container has this item type
                boolean containerHasItem = false;
                for (int containerSlot = 0; containerSlot < container.getSizeInventory(); containerSlot++) {
                    ItemStack containerStack = container.getStackInSlot(containerSlot);
                    if (!containerStack.isEmpty() && areItemsStackable(playerStack, containerStack)) {
                        containerHasItem = true;
                        break;
                    }
                }
                
                if (!containerHasItem) {
                    continue;
                }
                
                // Stack items to this container
                for (int containerSlot = 0; containerSlot < container.getSizeInventory(); containerSlot++) {
                    if (playerStack.isEmpty()) {
                        break;
                    }
                    
                    ItemStack containerStack = container.getStackInSlot(containerSlot);
                    
                    if (containerStack.isEmpty()) {
                        // Empty slot - create new stack
                        ItemStack toMove = playerStack.copy();
                        container.setInventorySlotContents(containerSlot, toMove);
                        
                        String itemName = toMove.getDisplayName();
                        int count = toMove.getCount();
                        movedItems.put(itemName, movedItems.getOrDefault(itemName, 0) + count);
                        totalItemsMoved += count;
                        
                        player.inventory.setInventorySlotContents(playerSlot, ItemStack.EMPTY);
                        playerStack = ItemStack.EMPTY;
                        
                    } else if (areItemsStackable(playerStack, containerStack)) {
                        // Stack with existing items
                        int maxStackSize = containerStack.getMaxStackSize();
                        int currentSize = containerStack.getCount();
                        int spaceAvailable = maxStackSize - currentSize;
                        
                        if (spaceAvailable > 0) {
                            int amountToMove = Math.min(spaceAvailable, playerStack.getCount());
                            
                            containerStack.grow(amountToMove);
                            playerStack.shrink(amountToMove);
                            
                            String itemName = containerStack.getDisplayName();
                            movedItems.put(itemName, movedItems.getOrDefault(itemName, 0) + amountToMove);
                            totalItemsMoved += amountToMove;
                            
                            if (playerStack.isEmpty()) {
                                player.inventory.setInventorySlotContents(playerSlot, ItemStack.EMPTY);
                            }
                        }
                    }
                }
            }
        }
        
        // Provide feedback if items were moved
        if (totalItemsMoved > 0) {
            if (QuickStackMod.config.playSound) {
                player.world.playSound(
                    null,
                    player.getPosition(),
                    SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS,
                    0.2F,
                    ((player.world.rand.nextFloat() - player.world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F
                );
            }
            
            if (QuickStackMod.config.showMessage) {
                showStackMessage(player, movedItems, totalItemsMoved);
            }
        }
    }
    
    private static List<IInventory> findNearbyContainers(EntityPlayerMP player) {
        List<IInventory> containers = new ArrayList<>();
        BlockPos playerPos = player.getPosition();
        int range = QuickStackMod.config.searchRange;
        
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    TileEntity te = player.world.getTileEntity(checkPos);
                    
                    if (te instanceof IInventory) {
                        IInventory inventory = (IInventory) te;
                        
                        // Check if this container type is enabled
                        if (isContainerAllowed(te)) {
                            containers.add(inventory);
                        }
                    }
                }
            }
        }
        
        return containers;
    }
    
    private static boolean isContainerAllowed(TileEntity te) {
        String className = te.getClass().getSimpleName().toLowerCase();
        
        if (QuickStackMod.config.stackToChests && 
            (te instanceof TileEntityChest || className.contains("chest"))) {
            return true;
        }
        
        if (QuickStackMod.config.stackToBarrels && className.contains("barrel")) {
            return true;
        }
        
        if (QuickStackMod.config.stackToShulkerBoxes && className.contains("shulker")) {
            return true;
        }
        
        return false;
    }
    
    private static boolean areItemsStackable(ItemStack stack1, ItemStack stack2) {
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
        
        return true;
    }
    
    private static void showStackMessage(EntityPlayerMP player, Map<String, Integer> movedItems, int totalCount) {
        StringBuilder message = new StringBuilder();
        message.append(TextFormatting.GREEN).append("Quick Stacked: ");
        
        if (movedItems.size() <= 3) {
            // Show individual items if 3 or fewer types
            boolean first = true;
            for (Map.Entry<String, Integer> entry : movedItems.entrySet()) {
                if (!first) {
                    message.append(", ");
                }
                message.append(entry.getValue()).append("x ").append(entry.getKey());
                first = false;
            }
        } else {
            // Show total count if more than 3 types
            message.append(totalCount).append(" items (").append(movedItems.size()).append(" types)");
        }
        
        player.sendStatusMessage(new TextComponentString(message.toString()), true);
    }
}
