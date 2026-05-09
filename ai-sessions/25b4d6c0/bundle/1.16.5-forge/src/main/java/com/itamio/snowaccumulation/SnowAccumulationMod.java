package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod (modid = SnowAccumulationMod.MODID)
public class SnowAccumulationMod
{
    public static final String MODID = "snowaccumulation";
    public static final String NAME = "Snow Accumulation";
    public static final String VERSION = "1.0";
    public static final String AUTHOR = "Itamio";
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        // Initialization code
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        // Registration code
    }
    
    @EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        // Post-initialization code
    }
}