package com.itamio.snowaccumulation;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.event.FMLPreInitializationEvent;
import net.neoforged.fml.common.event.FMLInitializationEvent;

@Mod("snowaccumulation")
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
        // Initialization
    }
}