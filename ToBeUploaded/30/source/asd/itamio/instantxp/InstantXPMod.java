package asd.itamio.instantxp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = InstantXPMod.MODID, name = InstantXPMod.NAME, version = InstantXPMod.VERSION)
public class InstantXPMod {
    public static final String MODID = "instantxp";
    public static final String NAME = "Instant Experience Orb";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static InstantXPConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new InstantXPConfig(new File(event.getModConfigurationDirectory(), "instantxp.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new InstantXPHandler());
        logger.info("Instant Experience Orb mod initialized!");
    }
}
