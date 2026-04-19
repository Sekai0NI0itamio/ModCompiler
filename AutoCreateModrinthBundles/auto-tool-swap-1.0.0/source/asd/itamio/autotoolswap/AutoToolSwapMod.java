package asd.itamio.autotoolswap;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = AutoToolSwapMod.MODID, name = AutoToolSwapMod.NAME, version = AutoToolSwapMod.VERSION)
public class AutoToolSwapMod {
    public static final String MODID = "autotoolswap";
    public static final String NAME = "Auto Tool Swap";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static AutoToolSwapConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "autotoolswap.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new AutoToolSwapConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new AutoToolSwapHandler());
        logger.info("Auto Tool Swap initialized!");
    }
}
