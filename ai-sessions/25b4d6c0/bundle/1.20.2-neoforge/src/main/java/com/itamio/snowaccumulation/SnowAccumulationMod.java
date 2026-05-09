package com.itamio.snowaccumulation;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("snowaccumulation")
public class SnowAccumulationMod {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnowAccumulationMod.class);

    public SnowAccumulationMod(IEventBus modEventBus) {
        LOGGER.info("Snow Accumulation Mod initialized");
        ConfigManager.loadConfig();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Snow Accumulation Mod server starting");
    }
}