package asd.itamio.bettersprinting;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = BetterSprintingMod.MODID, name = BetterSprintingMod.NAME, version = BetterSprintingMod.VERSION, clientSideOnly = true)
public class BetterSprintingMod {
    public static final String MODID = "bettersprinting";
    public static final String NAME = "Better Sprinting";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static BetterSprintingConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "bettersprinting.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new BetterSprintingConfig(configuration);
        
        logger.info("Better Sprinting initialized");
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new BetterSprintingHandler());
        MinecraftForge.EVENT_BUS.register(new BetterSprintingKeyHandler());
        
        logger.info("Better Sprinting event handlers registered");
    }
}
