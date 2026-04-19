package asd.itamio.noparticles;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class NoParticlesConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    
    public NoParticlesConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable no particles (completely disables all particle spawning)");
        
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
