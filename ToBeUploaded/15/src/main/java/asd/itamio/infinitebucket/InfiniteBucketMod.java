package asd.itamio.infinitebucket;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = InfiniteBucketMod.MODID, name = InfiniteBucketMod.NAME, version = InfiniteBucketMod.VERSION)
public class InfiniteBucketMod {
    public static final String MODID = "infinitebucket";
    public static final String NAME = "Infinite Water Bucket";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static InfiniteBucketConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "infinitebucket.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new InfiniteBucketConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new InfiniteBucketHandler());
        logger.info("Infinite Water Bucket initialized!");
    }
}
