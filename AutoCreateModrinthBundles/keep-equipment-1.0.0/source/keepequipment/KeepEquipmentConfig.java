package asd.itamio.keepequipment;

import net.minecraftforge.common.config.Configuration;

public class KeepEquipmentConfig {
    public boolean enableKeepEquipment;
    
    // Armor slots
    public boolean keepHelmet;
    public boolean keepChestplate;
    public boolean keepLeggings;
    public boolean keepBoots;
    
    // Hotbar
    public boolean keepHotbar;
    public boolean keepOnlyTools;
    
    // Offhand
    public boolean keepOffhand;
    
    // Main inventory
    public boolean keepMainInventory;
    
    // Experience
    public boolean keepAllXP;
    public float xpKeptPercentage;
    
    // Potion effects
    public boolean keepPotionEffects;
    
    // Death message
    public boolean showDeathMessage;
    public boolean showKeptItems;

    public KeepEquipmentConfig(Configuration config) {
        config.load();

        enableKeepEquipment = config.getBoolean("Enable Keep Equipment", "general", true,
                "Enable or disable the keep equipment feature");
        
        // Armor
        keepHelmet = config.getBoolean("Keep Helmet", "armor", true,
                "Keep helmet on death");
        
        keepChestplate = config.getBoolean("Keep Chestplate", "armor", true,
                "Keep chestplate on death");
        
        keepLeggings = config.getBoolean("Keep Leggings", "armor", true,
                "Keep leggings on death");
        
        keepBoots = config.getBoolean("Keep Boots", "armor", true,
                "Keep boots on death");
        
        // Hotbar
        keepHotbar = config.getBoolean("Keep Hotbar", "hotbar", true,
                "Keep hotbar items on death");
        
        keepOnlyTools = config.getBoolean("Keep Only Tools", "hotbar", false,
                "If true, only keep tools/weapons in hotbar. If false, keep all hotbar items");
        
        // Offhand
        keepOffhand = config.getBoolean("Keep Offhand", "offhand", true,
                "Keep offhand item on death");
        
        // Main inventory
        keepMainInventory = config.getBoolean("Keep Main Inventory", "inventory", true,
                "Keep all main inventory items (rows 1-3) on death");
        
        // Experience
        keepAllXP = config.getBoolean("Keep All XP", "experience", true,
                "Keep all experience on death");
        
        xpKeptPercentage = config.getFloat("XP Kept Percentage", "experience", 0.0f, 0.0f, 100.0f,
                "Percentage of XP to keep on death (0-100). Only used if Keep All XP is false");
        
        // Potion effects
        keepPotionEffects = config.getBoolean("Keep Potion Effects", "effects", true,
                "Keep all potion effects on death");
        
        // Messages
        showDeathMessage = config.getBoolean("Show Death Message", "messages", true,
                "Show a message when you die indicating what was kept");
        
        showKeptItems = config.getBoolean("Show Kept Items", "messages", true,
                "Show which items were kept in the death message");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
