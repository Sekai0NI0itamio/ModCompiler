package asd.itamio.snowaccumulation;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("snowaccumulation")
public class SnowaccumulationMod {
    public static final String MODID = "snowaccumulation";
    public static final String NAME = "Snow Accumulation";
    public static final String VERSION = "1.0.0";
    
    private static final Logger LOGGER = LogManager.getLogger();
    
    public SnowaccumulationMod(IEventBus modEventBus) {
        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(new SnowAccumulationHandler());
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        printModInfo();
    }
    
    private void printModInfo() {
        LOGGER.info("\u001b[36m\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
        LOGGER.info("\u001b[36m\u2551                   Snow Accumulation                       \u2551");
        LOGGER.info("\u001b[36m\u2551                       v1.0.0                            \u2551");
        LOGGER.info("\u001b[36m\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
        LOGGER.info("\u2728 Initializing Snow Accumulation Mod \u2728");
        LOGGER.info("-------------------------------------------------------");
        LOGGER.info("\u001b[36mMod Name: \u001b[0mSnow Accumulation");
        LOGGER.info("\u001b[36mVersion: \u001b[0m1.0.0");
        LOGGER.info("\u001b[36mMod ID: \u001b[0msnowaccumulation");
        LOGGER.info("\u001b[36mAuthor: \u001b[0mitamio");
        LOGGER.info("-------------------------------------------------------");
    }
}