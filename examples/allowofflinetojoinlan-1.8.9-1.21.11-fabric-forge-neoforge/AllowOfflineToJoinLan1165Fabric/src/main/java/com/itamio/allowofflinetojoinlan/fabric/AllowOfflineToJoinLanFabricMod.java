package com.itamio.allowofflinetojoinlan.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AllowOfflineToJoinLanFabricMod implements ModInitializer {
    public static final String MOD_ID = "allowofflinetojoinlan";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    }

    private void onServerStarting(MinecraftServer server) {
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
