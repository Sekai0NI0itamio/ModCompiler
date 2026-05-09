/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.MinecraftForge
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.common.Mod$EventHandler
 *  net.minecraftforge.fml.common.event.FMLInitializationEvent
 *  net.minecraftforge.fml.common.event.FMLPreInitializationEvent
 *  org.apache.logging.log4j.Logger
 */
package asd.itamio.heartsystem;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid="heartsystem", name="Heart System", version="1.0.0", acceptedMinecraftVersions="[1.12.2]")
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final String MOD_NAME = "Heart System";
    public static final String VERSION = "1.0.0";
    public static Logger logger;
    public static HeartConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new HeartConfig(event.getSuggestedConfigurationFile());
        logger.info("[HeartSystem] Loaded config: startHearts={}, maxHearts={}, minHearts={}", (Object)config.getStartHearts(), (Object)config.getMaxHearts(), (Object)config.getMinHearts());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register((Object)new HeartEventHandler());
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}

