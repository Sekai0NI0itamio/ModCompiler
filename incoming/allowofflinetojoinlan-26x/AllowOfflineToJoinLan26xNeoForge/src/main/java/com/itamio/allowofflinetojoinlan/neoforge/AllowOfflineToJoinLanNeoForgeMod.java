package com.itamio.allowofflinetojoinlan.neoforge;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// NeoForge 26.1+: constructor injection with IEventBus and ModContainer.
// FMLJavaModLoadingContext removed in 26.1.
@Mod(AllowOfflineToJoinLanNeoForgeMod.MOD_ID)
public final class AllowOfflineToJoinLanNeoForgeMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    public static final String MOD_NAME = "Allow Offline Players (LAN)";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AllowOfflineToJoinLanNeoForgeMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
