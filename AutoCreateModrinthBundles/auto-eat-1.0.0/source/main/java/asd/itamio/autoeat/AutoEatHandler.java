package asd.itamio.autoeat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AutoEatHandler {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20; // Check every second (20 ticks)
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only run on server side and during END phase
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        
        EntityPlayer player = event.player;
        
        // Only check every second to reduce performance impact
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        
        // Check if player needs food
        FoodStats foodStats = player.getFoodStats();
        int currentHunger = foodStats.getFoodLevel();
        
        if (currentHunger >= AutoEatMod.config.hungerThreshold) {
            return; // Player has enough food
        }
        
        // Find and eat the best food
        ItemStack bestFood = findBestFood(player);
        if (bestFood != null && !bestFood.isEmpty()) {
            eatFood(player, bestFood);
        }
    }
    
    private ItemStack findBestFood(EntityPlayer player) {
        ItemStack bestFood = ItemStack.EMPTY;
        float bestSaturation = 0;
        int bestSlot = -1;
        
        // Search through player's inventory
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            
            if (stack.isEmpty()) {
                continue;
            }
            
            Item item = stack.getItem();
            
            // Check if it's food
            if (!(item instanceof ItemFood)) {
                continue;
            }
            
            // Check if it's blacklisted
            String itemId = item.getRegistryName().toString();
            
            // Get food properties
            ItemFood foodItem = (ItemFood) item;
            int healAmount = foodItem.getHealAmount(stack);
            float saturationModifier = foodItem.getSaturationModifier(stack);
            float totalSaturation = healAmount * saturationModifier * 2.0F;
            
            if (AutoEatMod.config.blacklistedFoods.contains(itemId)) {
                continue;
            }
            
            // Prefer food with higher saturation
            if (totalSaturation > bestSaturation) {
                bestSaturation = totalSaturation;
                bestFood = stack;
                bestSlot = i;
            }
        }
        
        return bestFood;
    }
    
    private void eatFood(EntityPlayer player, ItemStack foodStack) {
        if (foodStack.isEmpty() || !(foodStack.getItem() instanceof ItemFood)) {
            return;
        }
        
        // Find the slot containing this food
        int slot = -1;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (ItemStack.areItemsEqual(stack, foodStack) && 
                ItemStack.areItemStackTagsEqual(stack, foodStack)) {
                slot = i;
                break;
            }
        }
        
        if (slot == -1) {
            return;
        }
        
        ItemStack stack = player.inventory.getStackInSlot(slot);
        ItemFood foodItem = (ItemFood) stack.getItem();
        
        // Apply food effects
        int healAmount = foodItem.getHealAmount(stack);
        float saturationModifier = foodItem.getSaturationModifier(stack);
        
        player.getFoodStats().addStats(healAmount, saturationModifier);
        
        // Consume one item
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
        }
        
        // Play eating sound
        player.world.playSound(
            null,
            player.posX,
            player.posY,
            player.posZ,
            net.minecraft.util.SoundEvent.REGISTRY.getObject(
                new net.minecraft.util.ResourceLocation("minecraft", "entity.generic.eat")
            ),
            net.minecraft.util.SoundCategory.PLAYERS,
            0.5F,
            player.world.rand.nextFloat() * 0.1F + 0.9F
        );
    }
}
