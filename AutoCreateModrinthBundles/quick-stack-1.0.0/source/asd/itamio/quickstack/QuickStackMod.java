package asd.itamio.quickstack;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = QuickStackMod.MODID, name = QuickStackMod.NAME, version = QuickStackMod.VERSION)
public class QuickStackMod {
    public static final String MODID = "quickstack";
    public static final String NAME = "Quick Stack";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static QuickStackConfig config;
    public static SimpleNetworkWrapper network;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        File configFile = new File(event.getModConfigurationDirectory(), "quickstack.cfg");
        Configuration configuration = new Configuration(configFile);
        config = new QuickStackConfig(configuration);
        
        // Register network
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(QuickStackPacket.Handler.class, QuickStackPacket.class, 0, Side.SERVER);
        
        logger.info("Quick Stack initialized!");
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new QuickStackKeyHandler());
        }
    }
}
