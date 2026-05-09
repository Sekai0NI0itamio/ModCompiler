package com.itamio.snowaccumulation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0", 
      acceptedMinecraftVersions = "[26.1,26.2]")
public class SnowAccumulationMod {
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.loadConfig();
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Initialization
    }
}