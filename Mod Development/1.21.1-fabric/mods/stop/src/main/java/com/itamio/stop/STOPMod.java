package com.itamio.stop;

import com.itamio.stop.config.DecoyConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class STOPMod implements ModInitializer {
    public static final String MOD_ID = "stop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DecoyConfig.initialize();
        LOGGER.info("STOP mod initialized - protecting your privacy");
    }
}
