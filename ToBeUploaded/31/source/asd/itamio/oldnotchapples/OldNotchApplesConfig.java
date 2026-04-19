package asd.itamio.oldnotchapples;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class OldNotchApplesConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    public static boolean enableCrafting = true;
    public static boolean use189Effects = true;
    
    public OldNotchApplesConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable Old Notch Apples features");
        
        enableCrafting = config.getBoolean("enableCrafting", "general", true,
            "Enable crafting recipe for Enchanted Golden Apples (8 gold blocks + 1 apple)");
        
        use189Effects = config.getBoolean("use189Effects", "general", true,
            "Use 1.8.9 effects: Regeneration V (30s), Absorption IV (2m), Resistance (5m), Fire Resistance (5m)");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void save() {
        config.get("general", "enabled", true).set(enabled);
        config.get("general", "enableCrafting", true).set(enableCrafting);
        config.get("general", "use189Effects", true).set(use189Effects);
        
        if (config.hasChanged()) {
            config.save();
        }
    }
}
