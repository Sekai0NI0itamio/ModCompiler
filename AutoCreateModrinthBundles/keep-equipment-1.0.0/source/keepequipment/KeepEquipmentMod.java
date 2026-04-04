package asd.itamio.keepequipment;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = KeepEquipmentMod.MODID, name = KeepEquipmentMod.NAME, version = KeepEquipmentMod.VERSION)
public class KeepEquipmentMod {
    public static final String MODID = "keepequipment";
    public static final String NAME = "Keep Equipment";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static KeepEquipmentConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        File configFile = new File(event.getModConfigurationDirectory(), "keepequipment.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new KeepEquipmentConfig(configuration);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new KeepEquipmentHandler());
        MinecraftForge.EVENT_BUS.register(new KeepEquipmentKeyHandler());
    }
}
