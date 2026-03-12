package com.itamio.allowofflinetojoinlan;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "allowofflinetojoinlan",
   name = "Allow Offline Players (LAN)",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12,1.12.2]",
   acceptableRemoteVersions = "*"
)
public class AllowOfflineToJoinLan {
   public static final String MODID = "allowofflinetojoinlan";
   public static final String NAME = "Allow Offline Players (LAN)";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("Allow Offline Players (LAN)");

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      ModConfig.init(event.getModConfigurationDirectory());
      LOGGER.info("{} config loaded. requireMojangAuthentication = {}", "Allow Offline Players (LAN)", ModConfig.requireMojangAuthentication);
      LOGGER.warn("Reminder: Disabling Mojang authentication (online-mode = false) is insecure. Use only on trusted LAN/private sessions.");
   }

   @EventHandler
   public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
      boolean desired = ModConfig.requireMojangAuthentication;
      boolean before = event.getServer().func_71266_T();
      if (before != desired) {
         event.getServer().func_71229_d(desired);
         LOGGER.info("Set server online-mode to {} based on config (was {}).", desired, before);
      } else {
         LOGGER.info("Server online-mode remains {} (matches config).", before);
      }
   }

   @EventHandler
   public void onServerStarting(FMLServerStartingEvent event) {
      boolean mode = event.getServer().func_71266_T();
      LOGGER.info("Server starting with online-mode = {} (Mojang authentication {}).", mode, mode ? "ENABLED" : "DISABLED");
   }
}
