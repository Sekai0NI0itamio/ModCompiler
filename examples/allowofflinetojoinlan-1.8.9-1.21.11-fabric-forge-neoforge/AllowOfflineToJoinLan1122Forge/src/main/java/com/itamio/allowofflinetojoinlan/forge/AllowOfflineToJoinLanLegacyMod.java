package com.itamio.allowofflinetojoinlan.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = AllowOfflineToJoinLanLegacyMod.MOD_ID,
        name = AllowOfflineToJoinLanLegacyMod.MOD_NAME,
        version = AllowOfflineToJoinLanLegacyMod.MOD_VERSION,
        acceptedMinecraftVersions = "*",
        acceptableRemoteVersions = "*"
)
public class AllowOfflineToJoinLanLegacyMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    public static final String MOD_NAME = "Allow Offline Players (LAN)";
    public static final String MOD_VERSION = "1.0.0";

    private static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(event.getServer(), AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
