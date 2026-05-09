package com.itamio.snowaccumulation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "snowaccumulation", name = "Snow Accumulation", version = "1.0.0", clientSideOnly = false, serverSideOnly = false)
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