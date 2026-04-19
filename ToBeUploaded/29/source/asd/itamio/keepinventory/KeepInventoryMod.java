package asd.itamio.keepinventory;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = KeepInventoryMod.MODID, name = KeepInventoryMod.NAME, version = KeepInventoryMod.VERSION)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    public static final String NAME = "Keep Inventory";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static KeepInventoryConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new KeepInventoryConfig(new File(event.getModConfigurationDirectory(), "keepinventory.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new KeepInventoryHandler());
        logger.info("Keep Inventory mod initialized - keepInventory gamerule will always be true!");
    }
}
