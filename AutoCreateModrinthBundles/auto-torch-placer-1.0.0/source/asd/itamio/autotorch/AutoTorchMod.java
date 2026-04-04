package asd.itamio.autotorch;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = AutoTorchMod.MODID, name = AutoTorchMod.NAME, version = AutoTorchMod.VERSION)
public class AutoTorchMod {
    public static final String MODID = "autotorch";
    public static final String NAME = "Auto Torch Placer";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static AutoTorchConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        File configFile = new File(event.getModConfigurationDirectory(), "autotorch.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new AutoTorchConfig(configuration);
        
        MinecraftForge.EVENT_BUS.register(new AutoTorchHandler());
        
        logger.info("Auto Torch Placer initialized!");
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new AutoTorchKeyHandler());
        }
    }
}
