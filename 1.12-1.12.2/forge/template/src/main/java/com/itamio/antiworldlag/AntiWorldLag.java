package com.itamio.antiworldlag;

import com.itamio.antiworldlag.config.AntiWorldLagConfig;
import com.itamio.antiworldlag.handler.ChunkLoadHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AntiWorldLag.MOD_ID,
    name = AntiWorldLag.MOD_NAME,
    version = AntiWorldLag.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class AntiWorldLag {

    public static final String MOD_ID  = "antiworldlag";
    public static final String MOD_NAME = "Anti World Lag";
    public static final String VERSION  = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    public static AntiWorldLag INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AntiWorldLagConfig.init(event.getSuggestedConfigurationFile());
        LOGGER.info("[AntiWorldLag] Configuration loaded.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChunkLoadHandler());
        LOGGER.info("[AntiWorldLag] Chunk protection active.");
    }
}
