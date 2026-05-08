package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        config = new HeartConfig();
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
