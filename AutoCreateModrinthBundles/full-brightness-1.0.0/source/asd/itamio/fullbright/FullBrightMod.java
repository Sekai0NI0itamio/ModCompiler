package asd.itamio.fullbright;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = FullBrightMod.MODID, name = FullBrightMod.NAME, version = FullBrightMod.VERSION, clientSideOnly = true)
public class FullBrightMod {
    public static final String MODID = "fullbright";
    public static final String NAME = "Full Brightness";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static FullBrightConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new FullBrightConfig(new File(event.getModConfigurationDirectory(), "fullbright.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FullBrightHandler());
        logger.info("Full Brightness mod initialized!");
    }
}
