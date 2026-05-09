package com.itamio.snowaccumulation;

import net.fabricmc.api.ModInitializer;

public class SnowAccumulationMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-able state
        ConfigManager.loadConfig();
    }
}