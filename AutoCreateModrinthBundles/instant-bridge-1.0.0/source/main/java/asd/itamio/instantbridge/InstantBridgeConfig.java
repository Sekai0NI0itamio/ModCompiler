package asd.itamio.instantbridge;

import net.minecraftforge.common.config.Configuration;

public class InstantBridgeConfig {
    private final Configuration config;
    
    public boolean enableInstantBridge;
    public boolean requireSneaking;
    public boolean placeOnlyWhenMoving;
    public int placementDelay;
    
    public InstantBridgeConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        // Enable instant bridge feature
        enableInstantBridge = config.getBoolean(
            "enableInstantBridge",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable instant bridge feature. Default: true"
        );
        
        // Require sneaking to activate
        requireSneaking = config.getBoolean(
            "requireSneaking",
            Configuration.CATEGORY_GENERAL,
            true,
            "Require player to be sneaking to place blocks. Default: true"
        );
        
        // Only place when moving
        placeOnlyWhenMoving = config.getBoolean(
            "placeOnlyWhenMoving",
            Configuration.CATEGORY_GENERAL,
            true,
            "Only place blocks when player is moving. Default: true"
        );
        
        // Placement delay in ticks
        placementDelay = config.getInt(
            "placementDelay",
            Configuration.CATEGORY_GENERAL,
            5,
            1,
            20,
            "Delay between block placements in ticks (20 ticks = 1 second). Default: 5"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
