package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = VeinMinerMod.MODID, name = VeinMinerMod.NAME, version = VeinMinerMod.VERSION)
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static final String NAME = "Vein Miner";
    public static final String VERSION = "1.0.0";
    public static Logger logger;
    public static VeinMinerConfig config = new VeinMinerConfig();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
