package com.itamio.servercore.forge;

import net.minecraft.entity.player.ServerPlayerEntity;
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
      if (event.getEntity() instanceof ServerPlayerEntity) {
         ServerPlayerEntity player = (ServerPlayerEntity)event.getEntity();
         if (player.func_184102_h() != null) {
            ServerCoreData data = ServerCoreData.get(player.func_184102_h());
            if (!data.hasSeen(PlayerUtil.getUuid(player))) {
               data.markSeen(PlayerUtil.getUuid(player));
               RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
               if (!result.isSuccess()) {
                  MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
               }
            }
         }
      }
   }
}
