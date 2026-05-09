package asd.itamio.snowaccumulation;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@net.neoforged.fml.common.Mod("snowaccumulation")
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
        LOGGER.info("Snow Accumulation Mod initialized");n    }
}