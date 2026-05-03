package com.itamio.allowofflinetojoinlan.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Forge 26.1.2 uses EventBus 7: ServerStartingEvent.BUS.addListener(...)
@Mod(AllowOfflineToJoinLanForgeMod.MOD_ID)
public final class AllowOfflineToJoinLanForgeMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    public static final String MOD_NAME = "Allow Offline Players (LAN)";

    private static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public AllowOfflineToJoinLanForgeMod(FMLJavaModLoadingContext context) {
        ServerStartingEvent.BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
