package asd.itamio.autofastxp;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class AutoFastXPConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    public static int throwDelay = 1; // Ticks between throws (1 = super fast, 20 = 1 second)
    
    public AutoFastXPConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable auto fast XP bottle throwing");
        
        throwDelay = config.getInt("throwDelay", "general", 1, 1, 20,
            "Delay in ticks between XP bottle throws (1 = fastest, 20 = 1 second)");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void save() {
        config.get("general", "enabled", true).set(enabled);
        config.get("general", "throwDelay", 1).set(throwDelay);
        
        if (config.hasChanged()) {
            config.save();
        }
    }
}
