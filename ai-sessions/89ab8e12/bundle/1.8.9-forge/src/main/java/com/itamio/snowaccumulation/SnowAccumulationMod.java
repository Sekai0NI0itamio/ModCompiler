package com.itamio.snowaccumulation;

import java.util.Random;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.Subscribe;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.BlockEvent.PlaceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0", acceptedMakers = "Itamio")
public class SnowAccumulationMod {
    private static final Logger LOGGER = LogManager.getLogger("Snow Accumulation");
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.loadConfig();
        LOGGER.info("Snow Accumulation mod loaded");
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new SnowAccumulationHandler());
        LOGGER.info("Snow Accumulation mod initialized");
    }
    
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // Register the event handler
        MinecraftForge.EVENT_BUS.register(new SnowAccumulationHandler());
    }
}