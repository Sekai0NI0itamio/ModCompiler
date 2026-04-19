package asd.itamio.autofastxp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = AutoFastXPMod.MODID, name = AutoFastXPMod.NAME, version = AutoFastXPMod.VERSION, clientSideOnly = true)
public class AutoFastXPMod {
    public static final String MODID = "autofastxp";
    public static final String NAME = "Auto Fast XP";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static AutoFastXPConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new AutoFastXPConfig(new File(event.getModConfigurationDirectory(), "autofastxp.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new AutoFastXPHandler());
        logger.info("Auto Fast XP mod initialized!");
    }
}
