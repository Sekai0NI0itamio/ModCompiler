package com.itamio.snowaccumulation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@Mod (modid = SnowAccumulationMod.MODID, version = SnowAccumulationMod.VERSION, name = "Snow Accumulation")
public class SnowAccumulationMod
{
    public static final String MODID = "snowaccumulation";
    public static final String NAME = "Snow Accumulation";
    public static final String VERSION = "1.0";
    public static final String AUTHOR = "Itamio";
    
    private static final Logger LOGGER = LogManager.getLogger(MODID);
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        ConfigManager.loadConfig();
        printModInfo();
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        // Post-init code if needed
    }
    
    private void printModInfo() {
        LOGGER.info("Snow Accumulation Mod initialized");
    }
}