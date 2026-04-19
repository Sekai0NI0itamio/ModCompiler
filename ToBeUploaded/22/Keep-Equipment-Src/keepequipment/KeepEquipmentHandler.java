package asd.itamio.keepequipment;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class KeepEquipmentHandler {
    
    // Store items to keep temporarily
    private static class DeathInventory {
        List<ItemStack> armor = new ArrayList<>();
        List<ItemStack> hotbar = new ArrayList<>();
        ItemStack offhand = ItemStack.EMPTY;
        List<ItemStack> mainInventory = new ArrayList<>();
        int xpLevel = 0;
        int xpTotal = 0;
        float xp = 0;
        Collection<PotionEffect> potionEffects = new ArrayList<>();
    }
    
    private DeathInventory lastDeath = null;
    
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        KeepEquipmentMod.logger.info("[Keep Equipment] LivingDropsEvent triggered");
        
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            KeepEquipmentMod.logger.info("[Keep Equipment] Not a player, ignoring");
            return;
        }
        
        KeepEquipmentMod.logger.info("[Keep Equipment] Player death detected");
        
        if (!KeepEquipmentMod.config.enableKeepEquipment) {
            KeepEquipmentMod.logger.info("[Keep Equipment] Mod is disabled in config");
            return;
        }
        
        KeepEquipmentMod.logger.info("[Keep Equipment] Processing death...");
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        KeepEquipmentMod.logger.info("[Keep Equipment] Player: " + player.getName());
        
        // Store what we want to keep - capture from DROPS, not inventory
        lastDeath = new DeathInventory();
        KeepEquipmentMod.logger.info("[Keep Equipment] Created death inventory storage");
        KeepEquipmentMod.logger.info("[Keep Equipment] Total drops: " + event.getDrops().size());
        
        // Store XP first (before we modify drops)
        if (KeepEquipmentMod.config.keepAllXP) {
            lastDeath.xpLevel = player.experienceLevel;
            lastDeath.xpTotal = player.experienceTotal;
            lastDeath.xp = player.experience;
        } else if (KeepEquipmentMod.config.xpKeptPercentage > 0) {
            float percentage = KeepEquipmentMod.config.xpKeptPercentage / 100.0f;
            lastDeath.xpLevel = (int)(player.experienceLevel * percentage);
            lastDeath.xpTotal = (int)(player.experienceTotal * percentage);
            lastDeath.xp = player.experience * percentage;
        }
        
        // Store potion effects
        if (KeepEquipmentMod.config.keepPotionEffects) {
            Collection<PotionEffect> effects = player.getActivePotionEffects();
            for (PotionEffect effect : effects) {
                // Create a copy of each effect
                lastDeath.potionEffects.add(new PotionEffect(effect));
            }
            KeepEquipmentMod.logger.info("[Keep Equipment] Stored " + lastDeath.potionEffects.size() + " potion effects");
        }
        
        // Process drops and capture items we want to keep
        int removedCount = 0;
        Iterator<EntityItem> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            EntityItem entityItem = iterator.next();
            ItemStack drop = entityItem.getItem();
            
            KeepEquipmentMod.logger.info("[Keep Equipment] Checking drop: " + drop.getDisplayName() + " x" + drop.getCount());
            
            // Check if this is armor
            if (drop.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) drop.getItem();
                boolean shouldKeep = false;
                
                if (armor.armorType == EntityEquipmentSlot.HEAD && KeepEquipmentMod.config.keepHelmet) {
                    shouldKeep = true;
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping helmet");
                } else if (armor.armorType == EntityEquipmentSlot.CHEST && KeepEquipmentMod.config.keepChestplate) {
                    shouldKeep = true;
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping chestplate");
                } else if (armor.armorType == EntityEquipmentSlot.LEGS && KeepEquipmentMod.config.keepLeggings) {
                    shouldKeep = true;
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping leggings");
                } else if (armor.armorType == EntityEquipmentSlot.FEET && KeepEquipmentMod.config.keepBoots) {
                    shouldKeep = true;
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping boots");
                }
                
                if (shouldKeep) {
                    lastDeath.armor.add(drop.copy());
                    iterator.remove();
                    removedCount++;
                    continue;
                }
            }
            
            // Check if this should be kept in hotbar
            if (KeepEquipmentMod.config.keepHotbar) {
                boolean shouldKeep = false;
                
                if (KeepEquipmentMod.config.keepOnlyTools) {
                    if (isToolOrWeapon(drop)) {
                        shouldKeep = true;
                        KeepEquipmentMod.logger.info("[Keep Equipment] Keeping hotbar tool: " + drop.getDisplayName());
                    }
                } else {
                    // Keep all items for hotbar
                    shouldKeep = true;
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping hotbar item: " + drop.getDisplayName());
                }
                
                if (shouldKeep && lastDeath.hotbar.size() < 9) {
                    lastDeath.hotbar.add(drop.copy());
                    iterator.remove();
                    removedCount++;
                    continue;
                }
            }
            
            // Check if this should be kept in main inventory
            if (KeepEquipmentMod.config.keepMainInventory) {
                KeepEquipmentMod.logger.info("[Keep Equipment] Keeping main inventory item: " + drop.getDisplayName());
                lastDeath.mainInventory.add(drop.copy());
                iterator.remove();
                removedCount++;
                continue;
            }
            
            // Check offhand (shields, torches, etc.)
            if (KeepEquipmentMod.config.keepOffhand && lastDeath.offhand.isEmpty()) {
                // Keep first non-armor, non-hotbar item as offhand
                if (!(drop.getItem() instanceof ItemArmor)) {
                    KeepEquipmentMod.logger.info("[Keep Equipment] Keeping offhand: " + drop.getDisplayName());
                    lastDeath.offhand = drop.copy();
                    iterator.remove();
                    removedCount++;
                    continue;
                }
            }
        }
        
        KeepEquipmentMod.logger.info("[Keep Equipment] Removed " + removedCount + " items from drops");
        KeepEquipmentMod.logger.info("[Keep Equipment] Total drops after filtering: " + event.getDrops().size());
        KeepEquipmentMod.logger.info("[Keep Equipment] Stored armor: " + lastDeath.armor.size());
        KeepEquipmentMod.logger.info("[Keep Equipment] Stored hotbar: " + lastDeath.hotbar.size());
        KeepEquipmentMod.logger.info("[Keep Equipment] Stored offhand: " + (!lastDeath.offhand.isEmpty()));
        KeepEquipmentMod.logger.info("[Keep Equipment] Stored main inv: " + lastDeath.mainInventory.size());
    }
    
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        KeepEquipmentMod.logger.info("[Keep Equipment] PlayerClone event triggered");
        
        if (!KeepEquipmentMod.config.enableKeepEquipment) {
            KeepEquipmentMod.logger.info("[Keep Equipment] Mod is disabled in config");
            return;
        }
        
        // Only handle death (not return from End)
        if (!event.isWasDeath()) {
            KeepEquipmentMod.logger.info("[Keep Equipment] Not a death (End portal return), ignoring");
            return;
        }
        
        if (lastDeath == null) {
            KeepEquipmentMod.logger.info("[Keep Equipment] ERROR: No death inventory stored!");
            return;
        }
        
        KeepEquipmentMod.logger.info("[Keep Equipment] Restoring items to respawned player");
        EntityPlayer newPlayer = event.getEntityPlayer();
        
        // Track what we kept for the message
        List<String> keptItems = new ArrayList<>();
        int keptCount = 0;
        
        // Restore armor
        int armorIndex = 3;
        for (ItemStack armor : lastDeath.armor) {
            if (!armor.isEmpty()) {
                newPlayer.inventory.armorInventory.set(armorIndex, armor);
                keptCount++;
            }
            armorIndex--;
        }
        if (!lastDeath.armor.isEmpty()) {
            keptItems.add("Armor");
        }
        
        // Restore hotbar
        int hotbarIndex = 0;
        for (ItemStack item : lastDeath.hotbar) {
            if (!item.isEmpty()) {
                newPlayer.inventory.mainInventory.set(hotbarIndex, item);
                keptCount++;
                hotbarIndex++;
            }
        }
        if (!lastDeath.hotbar.isEmpty()) {
            if (KeepEquipmentMod.config.keepOnlyTools) {
                keptItems.add("Hotbar Tools");
            } else {
                keptItems.add("Hotbar");
            }
        }
        
        // Restore offhand
        if (!lastDeath.offhand.isEmpty()) {
            newPlayer.inventory.offHandInventory.set(0, lastDeath.offhand);
            keptItems.add("Offhand");
            keptCount++;
        }
        
        // Restore main inventory
        if (!lastDeath.mainInventory.isEmpty()) {
            int invIndex = 9;
            for (ItemStack item : lastDeath.mainInventory) {
                if (!item.isEmpty() && invIndex < 36) {
                    newPlayer.inventory.mainInventory.set(invIndex, item);
                    keptCount++;
                    invIndex++;
                }
            }
            keptItems.add("Main Inventory");
        }
        
        // Restore experience
        if (lastDeath.xpLevel > 0 || lastDeath.xpTotal > 0) {
            newPlayer.experienceLevel = lastDeath.xpLevel;
            newPlayer.experienceTotal = lastDeath.xpTotal;
            newPlayer.experience = lastDeath.xp;
            
            if (KeepEquipmentMod.config.keepAllXP) {
                keptItems.add("All XP");
            } else if (KeepEquipmentMod.config.xpKeptPercentage > 0) {
                keptItems.add((int)KeepEquipmentMod.config.xpKeptPercentage + "% XP");
            }
        }
        
        // Restore potion effects
        if (!lastDeath.potionEffects.isEmpty()) {
            for (PotionEffect effect : lastDeath.potionEffects) {
                newPlayer.addPotionEffect(new PotionEffect(effect));
            }
            keptItems.add("Potion Effects (" + lastDeath.potionEffects.size() + ")");
            KeepEquipmentMod.logger.info("[Keep Equipment] Restored " + lastDeath.potionEffects.size() + " potion effects");
        }
        
        // Send death message
        KeepEquipmentMod.logger.info("[Keep Equipment] Kept " + keptCount + " items total");
        if (KeepEquipmentMod.config.showDeathMessage && keptCount > 0) {
            String message = "§6[Keep Equipment] §aKept " + keptCount + " item(s)";
            
            if (KeepEquipmentMod.config.showKeptItems && !keptItems.isEmpty()) {
                message += ": §7" + String.join(", ", keptItems);
            }
            
            newPlayer.sendMessage(new TextComponentString(message));
        }
        
        // Clear stored death data
        KeepEquipmentMod.logger.info("[Keep Equipment] Clearing death inventory storage");
        lastDeath = null;
    }
    
    private boolean isToolOrWeapon(ItemStack stack) {
        Item item = stack.getItem();
        
        // Tools
        if (item instanceof ItemTool) {
            return true;
        }
        
        // Weapons
        if (item instanceof ItemSword || item instanceof ItemBow) {
            return true;
        }
        
        // Shields
        if (item instanceof ItemShield) {
            return true;
        }
        
        // Shears
        if (item instanceof ItemShears) {
            return true;
        }
        
        // Flint and steel
        if (item instanceof ItemFlintAndSteel) {
            return true;
        }
        
        // Fishing rod
        if (item instanceof ItemFishingRod) {
            return true;
        }
        
        // Hoe
        if (item instanceof ItemHoe) {
            return true;
        }
        
        return false;
    }
}
