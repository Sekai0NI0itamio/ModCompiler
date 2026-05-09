package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0")
public class SnowAccumulationMod {
    public static final String MODID = "snowaccumulation";
    public static final String VERSION = "1.0.0";
    public static final String NAME = "Snow Accumulation";

    public SnowAccumulationMod() {
        // Constructor
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Initialization code
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Pre-initialization code
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Post-initialization code
    }
}