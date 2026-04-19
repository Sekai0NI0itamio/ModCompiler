package com.itamio.servercore.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@Mod("servercore")
@EventBusSubscriber(
   modid = "servercore",
   bus = Bus.FORGE
)
public final class ServerCoreForgeMod {
   public static final String MOD_ID = "servercore";

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      ServerCoreCommands.register(event.getDispatcher());
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         MinecraftServer server = ServerCoreAccess.getServer(player);
         if (server != null) {
            ServerCoreData data = ServerCoreData.get(server);
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
