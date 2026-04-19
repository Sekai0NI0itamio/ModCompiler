package asd.itamio.autotorch;

import net.minecraftforge.common.config.Configuration;

public class AutoTorchConfig {
    private final Configuration config;
    
    public boolean enableAutoTorch;
    public int lightThreshold;
    public int placementCooldown;
    public String placementMode;
    public int minTorchDistance;
    public boolean enableInNether;
    public boolean enableInEnd;
    public boolean playSound;
    public boolean showMessage;
    
    public AutoTorchConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        enableAutoTorch = config.getBoolean(
            "enableAutoTorch",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable automatic torch placement. Default: true"
        );
        
        lightThreshold = config.getInt(
            "lightThreshold",
            Configuration.CATEGORY_GENERAL,
            7,
            0,
            15,
            "Light level threshold for torch placement (0-15). Torches place when light is below this value. Default: 7"
        );
        
        placementCooldown = config.getInt(
            "placementCooldown",
            Configuration.CATEGORY_GENERAL,
            2,
            1,
            100,
            "Cooldown in ticks between torch placements when moving (2 ticks = 0.1 second). Default: 2"
        );
        
        placementMode = config.getString(
            "placementMode",
            Configuration.CATEGORY_GENERAL,
            "auto",
            "Torch placement mode: 'auto' (walls and ground), 'wall' (walls only), 'ground' (ground only). Default: auto"
        );
        
        minTorchDistance = config.getInt(
            "minTorchDistance",
            Configuration.CATEGORY_GENERAL,
            8,
            1,
            16,
            "Minimum distance in blocks between placed torches. Default: 8"
        );
        
        enableInNether = config.getBoolean(
            "enableInNether",
            Configuration.CATEGORY_GENERAL,
            false,
            "Enable torch placement in the Nether. Default: false"
        );
        
        enableInEnd = config.getBoolean(
            "enableInEnd",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable torch placement in the End. Default: true"
        );
        
        playSound = config.getBoolean(
            "playSound",
            Configuration.CATEGORY_GENERAL,
            true,
            "Play sound when torch is placed. Default: true"
        );
        
        showMessage = config.getBoolean(
            "showMessage",
            Configuration.CATEGORY_GENERAL,
            false,
            "Show on-screen message when torch is placed. Default: false"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
