package asd.itamio.autocrystal;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = AutoCrystalMod.MODID, name = AutoCrystalMod.NAME, version = AutoCrystalMod.VERSION)
public class AutoCrystalMod {
    public static final String MODID = "autocrystal";
    public static final String NAME = "Auto Crystal";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static AutoCrystalConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new AutoCrystalConfig(event.getSuggestedConfigurationFile());
        logger.info("Auto Crystal initialized");
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new AutoCrystalHandler());
        MinecraftForge.EVENT_BUS.register(new AutoCrystalKeyHandler());
    }
}
