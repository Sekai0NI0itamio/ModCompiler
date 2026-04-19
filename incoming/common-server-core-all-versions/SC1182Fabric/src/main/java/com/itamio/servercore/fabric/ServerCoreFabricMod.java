package com.itamio.servercore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.class_3222;

public final class ServerCoreFabricMod implements ModInitializer {
   public void onInitialize() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, dedicated) -> ServerCoreCommands.register(dispatcher));
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         class_3222 player = handler.method_32311();
         ServerCoreData data = ServerCoreData.get(server);
         if (!data.hasSeen(player.method_5667())) {
            data.markSeen(player.method_5667());
            RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
            if (!result.isSuccess()) {
               MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
            }
         }
      });
   }
}
