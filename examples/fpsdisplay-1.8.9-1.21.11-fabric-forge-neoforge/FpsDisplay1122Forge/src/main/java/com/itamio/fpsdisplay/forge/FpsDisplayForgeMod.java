package com.itamio.fpsdisplay.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = FpsDisplayForgeMod.MOD_ID,
        name = FpsDisplayForgeMod.NAME,
        version = FpsDisplayForgeMod.VERSION,
        acceptedMinecraftVersions = "[1.12,1.12.2]"
)
public class FpsDisplayForgeMod {
    public static final String MOD_ID = "fpsdisplay";
    public static final String NAME = "FPS Display";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static FpsDisplayConfig CONFIG;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CONFIG = new FpsDisplayConfig();
        CONFIG.load(LOGGER);
        LOGGER.info("FPS Display loaded (1.12.2).");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FpsDisplayOverlay());
    }
}
