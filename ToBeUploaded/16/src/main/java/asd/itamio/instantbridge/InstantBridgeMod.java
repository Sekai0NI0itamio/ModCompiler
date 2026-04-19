package asd.itamio.instantbridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = InstantBridgeMod.MODID, name = InstantBridgeMod.NAME, version = InstantBridgeMod.VERSION)
public class InstantBridgeMod {
    public static final String MODID = "instantbridge";
    public static final String NAME = "Instant Bridge";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static InstantBridgeConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "instantbridge.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new InstantBridgeConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new InstantBridgeHandler());
        logger.info("Instant Bridge initialized!");
    }
}
