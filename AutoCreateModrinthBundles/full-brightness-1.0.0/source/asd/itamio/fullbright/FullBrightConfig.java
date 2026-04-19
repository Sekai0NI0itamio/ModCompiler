package asd.itamio.fullbright;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class FullBrightConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    
    public FullBrightConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable full brightness (no darkness anywhere)");
        
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
