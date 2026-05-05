package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid   = HeartSystemMod.MOD_ID,
    name    = HeartSystemMod.MOD_NAME,
    version = HeartSystemMod.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class HeartSystemMod {

    public static final String MOD_ID   = "heartsystem";
    public static final String MOD_NAME = "Heart System";
    public static final String VERSION  = "1.0.0";

    public static Logger logger;
    public static HeartConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new HeartConfig(event.getSuggestedConfigurationFile());
        logger.info("[HeartSystem] Loaded config: startHearts={}, maxHearts={}, minHearts={}",
            config.getStartHearts(), config.getMaxHearts(), config.getMinHearts());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
