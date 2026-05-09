package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0")
public class SnowAccumulationMod {
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.loadConfig();
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register event handlers
    }
    
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Server starting event
    }
}