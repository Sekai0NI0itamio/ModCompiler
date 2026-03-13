package com.itamio.fpsdisplay.forge;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FpsDisplayForgeMod.MOD_ID)
public final class FpsDisplayForgeMod {
    public static final String MOD_ID = "fpsdisplay";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final FpsDisplayConfig CONFIG = new FpsDisplayConfig();

    public FpsDisplayForgeMod() {
        CONFIG.load(LOGGER);
    }
}
