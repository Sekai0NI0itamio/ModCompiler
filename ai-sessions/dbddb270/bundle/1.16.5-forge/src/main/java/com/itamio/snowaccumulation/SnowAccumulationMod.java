package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(modid = SnowAccumulationMod.MODID, name = SnowAccumulationMod.NAME, version = SnowAccumulationMod.VERSION, 
      acceptedMinecraftVersions = "[1.16,1.17)")
public class SnowAccumulationMod
{
    public static final String MODID = "snowaccumulation";
    public static final String NAME = "Snow Accumulation";
    public static final String VERSION = "1.0.0";
    
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.loadConfig();
    }
    
    public void init(FMLInitializationEvent event) {
        // Initialization
    }
}