package com.itamio.snowaccumulation;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowAccumulationMod implements ModInitializer {
	public static final String MOD_ID = "snowaccumulation";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.loadConfig();
		LOGGER.info("Snow Accumulation Mod initialized");
	}
}