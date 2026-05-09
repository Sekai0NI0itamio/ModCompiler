package com.itamio.snowaccumulation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("snowaccumulation")
public class SnowAccumulationMod
{
    private static final Logger LOGGER = LogManager.getLogger();
    
    public SnowAccumulationMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        ConfigManager.loadConfig();
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        // Some preinit code
        LOGGER.info("Snow Accumulation Mod setup complete");
    }
}