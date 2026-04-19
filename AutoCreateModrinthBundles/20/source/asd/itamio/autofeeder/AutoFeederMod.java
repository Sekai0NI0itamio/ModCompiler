package asd.itamio.autofeeder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = AutoFeederMod.MODID, name = AutoFeederMod.NAME, version = AutoFeederMod.VERSION)
public class AutoFeederMod {
    public static final String MODID = "autofeeder";
    public static final String NAME = "Auto Feeder";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static AutoFeederConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        File configFile = new File(event.getModConfigurationDirectory(), "autofeeder.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new AutoFeederConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new AutoFeederHandler());
        
        logger.info("Auto Feeder initialized!");
    }
}
