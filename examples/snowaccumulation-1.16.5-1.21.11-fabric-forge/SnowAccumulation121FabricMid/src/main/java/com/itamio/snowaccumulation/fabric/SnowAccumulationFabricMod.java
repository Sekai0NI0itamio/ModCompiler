package com.itamio.snowaccumulation.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class SnowAccumulationFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        SnowAccumulationConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(SnowAccumulationHandler::onServerTick);
        System.out.println("[Snow Accumulation] Fabric server logic loaded.");
    }
}
