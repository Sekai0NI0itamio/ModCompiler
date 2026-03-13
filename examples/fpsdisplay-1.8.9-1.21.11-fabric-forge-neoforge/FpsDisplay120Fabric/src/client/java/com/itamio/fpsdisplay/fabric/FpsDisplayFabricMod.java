package com.itamio.fpsdisplay.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FpsDisplayFabricMod implements ClientModInitializer {
    public static final String MOD_ID = "fpsdisplay";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final FpsDisplayConfig CONFIG = new FpsDisplayConfig();

    @Override
    public void onInitializeClient() {
        CONFIG.load(LOGGER);
        HudRenderCallback.EVENT.register(new FpsDisplayOverlay()::onHudRender);
    }
}
