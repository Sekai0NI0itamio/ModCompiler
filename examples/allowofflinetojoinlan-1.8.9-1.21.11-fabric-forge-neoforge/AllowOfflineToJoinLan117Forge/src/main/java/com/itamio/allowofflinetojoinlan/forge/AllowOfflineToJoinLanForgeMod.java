package com.itamio.allowofflinetojoinlan.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AllowOfflineToJoinLanForgeMod.MOD_ID)
public final class AllowOfflineToJoinLanForgeMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    public static final String MOD_NAME = "Allow Offline Players (LAN)";

    private static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public AllowOfflineToJoinLanForgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
