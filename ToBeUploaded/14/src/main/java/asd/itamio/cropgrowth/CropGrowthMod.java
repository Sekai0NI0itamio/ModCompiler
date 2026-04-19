package asd.itamio.cropgrowth;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = CropGrowthMod.MODID, name = CropGrowthMod.NAME, version = CropGrowthMod.VERSION)
public class CropGrowthMod {
    public static final String MODID = "cropgrowth";
    public static final String NAME = "Crop Growth Accelerator";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static CropGrowthConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Load configuration
        File configFile = new File(event.getModConfigurationDirectory(), "cropgrowth.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new CropGrowthConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new CropGrowthHandler());
        logger.info("Crop Growth Accelerator initialized!");
    }
}
