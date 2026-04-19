package asd.itamio.autotoolswap;

import net.minecraftforge.common.config.Configuration;

public class AutoToolSwapConfig {
    private final Configuration config;
    
    public boolean enableAutoSwap;
    public boolean hotbarOnly;
    public boolean switchBack;
    public boolean preferFortune;
    public boolean preferSilkTouch;
    
    public AutoToolSwapConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        // Enable auto tool swap
        enableAutoSwap = config.getBoolean(
            "enableAutoSwap",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable automatic tool swapping. Default: true"
        );
        
        // Only search hotbar
        hotbarOnly = config.getBoolean(
            "hotbarOnly",
            Configuration.CATEGORY_GENERAL,
            true,
            "Only search hotbar for tools (slots 0-8). If false, searches entire inventory. Default: true"
        );
        
        // Switch back after breaking
        switchBack = config.getBoolean(
            "switchBack",
            Configuration.CATEGORY_GENERAL,
            true,
            "Switch back to previous item after breaking block. Default: true"
        );
        
        // Prefer fortune tools
        preferFortune = config.getBoolean(
            "preferFortune",
            Configuration.CATEGORY_GENERAL,
            true,
            "Prefer tools with Fortune enchantment when available. Default: true"
        );
        
        // Prefer silk touch tools
        preferSilkTouch = config.getBoolean(
            "preferSilkTouch",
            Configuration.CATEGORY_GENERAL,
            false,
            "Prefer tools with Silk Touch enchantment when available. Default: false"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
