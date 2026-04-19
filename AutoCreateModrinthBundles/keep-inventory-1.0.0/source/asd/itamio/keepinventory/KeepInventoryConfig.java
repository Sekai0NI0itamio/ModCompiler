package asd.itamio.keepinventory;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class KeepInventoryConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    
    public KeepInventoryConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable keep inventory enforcement (keepInventory gamerule always true)");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void save() {
        config.get("general", "enabled", true).set(enabled);
        
        if (config.hasChanged()) {
            config.save();
        }
    }
}
