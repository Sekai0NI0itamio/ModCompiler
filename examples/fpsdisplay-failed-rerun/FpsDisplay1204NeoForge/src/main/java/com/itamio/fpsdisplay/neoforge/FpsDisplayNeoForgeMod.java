package com.itamio.fpsdisplay.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FpsDisplayNeoForgeMod.MOD_ID)
public final class FpsDisplayNeoForgeMod {
    public static final String MOD_ID = "fpsdisplay";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final FpsDisplayConfig CONFIG = new FpsDisplayConfig();

    public FpsDisplayNeoForgeMod() {
        CONFIG.load(LOGGER);
        NeoForge.EVENT_BUS.addListener(new FpsDisplayOverlay()::onRenderOverlay);
    }
}
