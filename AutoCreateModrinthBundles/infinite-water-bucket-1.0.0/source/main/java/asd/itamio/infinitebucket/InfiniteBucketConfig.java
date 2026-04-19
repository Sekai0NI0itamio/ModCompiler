package asd.itamio.infinitebucket;

import net.minecraftforge.common.config.Configuration;

public class InfiniteBucketConfig {
    private final Configuration config;
    
    public boolean enableInfiniteWater;
    public boolean enableInfiniteLava;
    public boolean enableInfiniteMilk;
    
    public InfiniteBucketConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        // Enable infinite water buckets
        enableInfiniteWater = config.getBoolean(
            "enableInfiniteWater",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable infinite water buckets (never empties). Default: true"
        );
        
        // Enable infinite lava buckets
        enableInfiniteLava = config.getBoolean(
            "enableInfiniteLava",
            Configuration.CATEGORY_GENERAL,
            false,
            "Enable infinite lava buckets (never empties). Default: false (can be overpowered)"
        );
        
        // Enable infinite milk buckets
        enableInfiniteMilk = config.getBoolean(
            "enableInfiniteMilk",
            Configuration.CATEGORY_GENERAL,
            false,
            "Enable infinite milk buckets (never empties). Default: false"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
