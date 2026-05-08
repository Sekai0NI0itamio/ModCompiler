package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = HeartSystemMod.MOD_ID, name = "Heart System", version = "1.0.0",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static Logger logger;
    public static HeartConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new HeartConfig(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
