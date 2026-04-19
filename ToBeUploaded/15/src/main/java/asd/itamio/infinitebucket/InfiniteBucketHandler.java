package asd.itamio.infinitebucket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InfiniteBucketHandler {
    
    // Track players who just used a bucket
    private final Map<UUID, BucketReplacement> pendingReplacements = new HashMap<>();
    
    private static class BucketReplacement {
        ItemStack bucket;
        int ticksLeft;
        
        BucketReplacement(ItemStack bucket) {
            this.bucket = bucket;
            this.ticksLeft = 1; // Replace after 1 tick
        }
    }
    
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote) {
            return;
        }
        
        EntityPlayer player = event.getEntityPlayer();
        ItemStack heldItem = player.getHeldItem(event.getHand());
        
        if (heldItem.isEmpty()) {
            return;
        }
        
        // Check if player is using a water bucket
        if (heldItem.getItem() == Items.WATER_BUCKET && InfiniteBucketMod.config.enableInfiniteWater) {
            // Schedule replacement after the bucket is used
            pendingReplacements.put(player.getUniqueID(), new BucketReplacement(new ItemStack(Items.WATER_BUCKET)));
        }
        // Check if player is using a lava bucket
        else if (heldItem.getItem() == Items.LAVA_BUCKET && InfiniteBucketMod.config.enableInfiniteLava) {
            pendingReplacements.put(player.getUniqueID(), new BucketReplacement(new ItemStack(Items.LAVA_BUCKET)));
        }
    }
    
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote) {
            return;
        }
        
        EntityPlayer player = event.getEntityPlayer();
        ItemStack heldItem = player.getHeldItem(event.getHand());
        
        if (heldItem.isEmpty()) {
            return;
        }
        
        // Check if player is drinking milk
        if (heldItem.getItem() == Items.MILK_BUCKET && InfiniteBucketMod.config.enableInfiniteMilk) {
            pendingReplacements.put(player.getUniqueID(), new BucketReplacement(new ItemStack(Items.MILK_BUCKET)));
        }
    }
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        
        EntityPlayer player = event.player;
        UUID playerId = player.getUniqueID();
        
        if (pendingReplacements.containsKey(playerId)) {
            BucketReplacement replacement = pendingReplacements.get(playerId);
            replacement.ticksLeft--;
            
            if (replacement.ticksLeft <= 0) {
                // Find and replace the empty bucket with the filled bucket
                boolean replaced = false;
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack stack = player.inventory.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() == Items.BUCKET) {
                        player.inventory.setInventorySlotContents(i, replacement.bucket.copy());
                        replaced = true;
                        break;
                    }
                }
                
                // If no empty bucket found, add to inventory
                if (!replaced) {
                    player.inventory.addItemStackToInventory(replacement.bucket.copy());
                }
                
                pendingReplacements.remove(playerId);
            }
        }
    }
}
