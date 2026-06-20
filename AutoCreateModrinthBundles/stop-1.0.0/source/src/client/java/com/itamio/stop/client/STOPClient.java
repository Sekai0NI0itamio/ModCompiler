package com.itamio.stop.client;

import com.itamio.stop.STOPMod;
import net.fabricmc.api.ClientModInitializer;

public class STOPClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        STOPMod.LOGGER.info("STOP client initialized - privacy protection active");
    }
}
