package com.sonicether.soundphysics;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = SoundPhysicsMod.MODID, name = SoundPhysicsMod.NAME, version = SoundPhysicsMod.VERSION,
     acceptedMinecraftVersions = "*", clientSideOnly = true)
public class SoundPhysicsMod {
    public static final String MODID = "sound_physics";
    public static final String NAME = "Sound Physics";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(NAME);
    private static boolean efxInitialized = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SoundPhysicsConfig.load(event.getModConfigurationDirectory());
        MinecraftForge.EVENT_BUS.register(SoundEventHandler.class);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[Sound Physics] Mod loaded. EFX will initialize when OpenAL context is ready.");
    }

    public static void initializeEFX() {
        if (efxInitialized) return;
        try {
            SoundPhysics.setupEFX();
            efxInitialized = true;
            LOGGER.info("[Sound Physics] OpenAL EFX initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("[Sound Physics] Failed to initialize EFX: {}", e.getMessage());
        }
    }

    public static boolean isEFXReady() {
        return efxInitialized;
    }
}
