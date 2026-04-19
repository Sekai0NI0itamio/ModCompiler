package com.itamio.servercore.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("servercore")
public final class ServerCoreForgeMod {
   public static final String MOD_ID = "servercore";

   public ServerCoreForgeMod() {
      MinecraftForge.EVENT_BUS.addListener(ServerCoreForgeMod::onRegisterCommands);
      MinecraftForge.EVENT_BUS.addListener(ServerCoreForgeMod::onPlayerLogin);
   }

   private static void onRegisterCommands(RegisterCommandsEvent event) {
      ServerCoreCommands.register(event.getDispatcher());
   }

   private static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         if (player.getServer() != null) {
            ServerCoreData data = ServerCoreData.get(player.getServer());
            if (!data.hasSeen(player.getUUID())) {
               data.markSeen(player.getUUID());
               RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
               if (!result.isSuccess()) {
                  MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
               }
            }
         }
      }
   }
}
