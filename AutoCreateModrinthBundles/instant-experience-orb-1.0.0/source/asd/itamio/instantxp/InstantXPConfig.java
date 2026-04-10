package asd.itamio.instantxp;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class InstantXPConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    public static boolean instantAbsorption = true;
    public static boolean clumpOrbs = true;
    public static boolean simplifiedLeveling = true;
    public static double clumpRadius = 3.0;
    
    public InstantXPConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }
    
    public void load() {
        config.load();
        
        enabled = config.getBoolean("enabled", "general", true, 
            "Enable instant experience orb features");
        
        instantAbsorption = config.getBoolean("instantAbsorption", "general", true,
            "Instantly absorb XP orbs without delay");
        
        clumpOrbs = config.getBoolean("clumpOrbs", "general", true,
            "Clump nearby XP orbs together to reduce lag");
        
        simplifiedLeveling = config.getBoolean("simplifiedLeveling", "general", true,
            "Use simplified leveling: 1 XP orb = 1 level");
        
        clumpRadius = config.getFloat("clumpRadius", "general", 3.0f, 1.0f, 10.0f,
            "Radius in blocks for clumping XP orbs together");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void save() {
        config.get("general", "enabled", true).set(enabled);
        config.get("general", "instantAbsorption", true).set(instantAbsorption);
        config.get("general", "clumpOrbs", true).set(clumpOrbs);
        config.get("general", "simplifiedLeveling", true).set(simplifiedLeveling);
        config.get("general", "clumpRadius", 3.0f).set(clumpRadius);
        
        if (config.hasChanged()) {
            config.save();
        }
    }
}
