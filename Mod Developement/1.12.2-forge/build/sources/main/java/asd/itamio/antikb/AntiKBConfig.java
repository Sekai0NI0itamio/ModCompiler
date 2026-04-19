package asd.itamio.antikb;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.io.File;

@SideOnly(Side.CLIENT)
public class AntiKBConfig {
    private Configuration config;
    
    public static boolean enabled = true;
    
    public AntiKBConfig(File configFile) {
        config = new Configuration(configFile);
        loadConfig();
    }
    
    private void loadConfig() {
        try {
            config.load();
            
            enabled = config.getBoolean("enabled", "general", true, 
                "Enable or disable the Anti KB mod");
            
        } catch (Exception e) {
            AntiKBMod.logger.error("Failed to load config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
