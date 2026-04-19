package asd.itamio.instantleafdecay;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = InstantLeafDecayMod.MODID, name = InstantLeafDecayMod.NAME, version = InstantLeafDecayMod.VERSION)
public class InstantLeafDecayMod {
    public static final String MODID = "instantleafdecay";
    public static final String NAME = "Instant Leaf Decay";
    public static final String VERSION = "1.0.0";

    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(new LeafDecayHandler());
        logger.info("Instant Leaf Decay initialized - leaves will decay instantly!");
    }
}
