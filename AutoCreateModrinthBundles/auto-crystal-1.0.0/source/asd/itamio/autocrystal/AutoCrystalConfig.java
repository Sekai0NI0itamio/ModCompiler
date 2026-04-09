package asd.itamio.autocrystal;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class AutoCrystalConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    public static int attackDelay = 0; // 0 = instant (0-tick)
    public static double attackRange = 6.0;
    
    public AutoCrystalConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable auto crystal attack");
        attackDelay = config.getInt("attackDelay", "general", 0, 0, 20, 
            "Delay in ticks before attacking crystal (0 = instant)");
        attackRange = config.getFloat("attackRange", "general", 6.0f, 1.0f, 10.0f, 
            "Maximum range to attack crystals");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void save() {
        config.get("general", "enabled", true).set(enabled);
        config.get("general", "attackDelay", 0).set(attackDelay);
        config.get("general", "attackRange", 6.0f).set(attackRange);
        
        if (config.hasChanged()) {
            config.save();
        }
    }
}
