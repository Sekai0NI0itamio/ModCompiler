package com.itamio.snowaccumulation.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

@Mod(SnowAccumulationForgeMod.MOD_ID)
public final class SnowAccumulationForgeMod {
    public static final String MOD_ID = "snowaccumulation";

    public SnowAccumulationForgeMod() {
        SnowAccumulationConfig.load();
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        System.out.println("[Snow Accumulation] Forge server logic loaded.");
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        SnowAccumulationHandler.onServerTick(ServerLifecycleHooks.getCurrentServer());
    }
}
