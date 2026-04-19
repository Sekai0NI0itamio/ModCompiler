package asd.itamio.quickstack;

import net.minecraftforge.common.config.Configuration;

public class QuickStackConfig {
    private final Configuration config;
    
    public boolean enableQuickStack;
    public int searchRange;
    public boolean includeHotbar;
    public boolean playSound;
    public boolean showMessage;
    public boolean stackToChests;
    public boolean stackToBarrels;
    public boolean stackToShulkerBoxes;
    
    public QuickStackConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        enableQuickStack = config.getBoolean(
            "enableQuickStack",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable Quick Stack feature. Default: true"
        );
        
        searchRange = config.getInt(
            "searchRange",
            Configuration.CATEGORY_GENERAL,
            8,
            1,
            16,
            "Range in blocks to search for containers. Default: 8"
        );
        
        includeHotbar = config.getBoolean(
            "includeHotbar",
            Configuration.CATEGORY_GENERAL,
            false,
            "Include hotbar items in quick stack. Default: false (hotbar items are kept safe)"
        );
        
        playSound = config.getBoolean(
            "playSound",
            Configuration.CATEGORY_GENERAL,
            true,
            "Play sound when items are stacked. Default: true"
        );
        
        showMessage = config.getBoolean(
            "showMessage",
            Configuration.CATEGORY_GENERAL,
            true,
            "Show on-screen message when items are stacked. Default: true"
        );
        
        stackToChests = config.getBoolean(
            "stackToChests",
            Configuration.CATEGORY_GENERAL,
            true,
            "Stack items to chests and trapped chests. Default: true"
        );
        
        stackToBarrels = config.getBoolean(
            "stackToBarrels",
            Configuration.CATEGORY_GENERAL,
            true,
            "Stack items to barrels (if available). Default: true"
        );
        
        stackToShulkerBoxes = config.getBoolean(
            "stackToShulkerBoxes",
            Configuration.CATEGORY_GENERAL,
            true,
            "Stack items to shulker boxes. Default: true"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
