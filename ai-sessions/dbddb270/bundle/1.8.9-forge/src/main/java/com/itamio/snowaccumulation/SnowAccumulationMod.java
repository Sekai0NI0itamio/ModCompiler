package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0")
public class SnowAccumulationMod {
    
    public static final String MODID = "snowaccumulation";
    public static final String NAME = "Snow Accumulation";
    public static final String VERSION = "1.0.0";
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.loadConfig();
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Initialization handled in handler
    }
    
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Server starting event
    }
}
