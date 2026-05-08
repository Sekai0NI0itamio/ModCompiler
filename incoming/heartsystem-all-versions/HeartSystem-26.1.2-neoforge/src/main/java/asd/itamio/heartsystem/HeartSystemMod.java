package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod(IEventBus modBus, ModContainer container) {
        config = new HeartConfig(modBus, container);
        NeoForge.EVENT_BUS.register(new HeartEventHandler());
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
