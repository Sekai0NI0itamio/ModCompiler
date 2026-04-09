package asd.itamio.autototem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = AutoTotemMod.MODID, name = AutoTotemMod.NAME, version = AutoTotemMod.VERSION)
public class AutoTotemMod {
    public static final String MODID = "autototem";
    public static final String NAME = "Auto Totem";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static AutoTotemConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "autototem.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new AutoTotemConfig(configuration);
        
        logger.info("Auto Totem initialized");
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new AutoTotemHandler());
        MinecraftForge.EVENT_BUS.register(new AutoTotemKeyHandler());
        
        logger.info("Auto Totem event handlers registered");
    }
}
