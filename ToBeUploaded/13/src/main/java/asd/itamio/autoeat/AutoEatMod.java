package asd.itamio.autoeat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = AutoEatMod.MODID, name = AutoEatMod.NAME, version = AutoEatMod.VERSION)
public class AutoEatMod {
    public static final String MODID = "autoeat";
    public static final String NAME = "Auto Eat";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static AutoEatConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "autoeat.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new AutoEatConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new AutoEatHandler());
        logger.info("Auto Eat initialized - will automatically eat when hungry!");
    }
}
