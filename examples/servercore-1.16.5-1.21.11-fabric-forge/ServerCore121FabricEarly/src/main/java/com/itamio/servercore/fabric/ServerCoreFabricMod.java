package com.itamio.servercore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ServerCoreFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ServerCoreCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerCoreData data = ServerCoreData.get(server);
            if (data.hasSeen(player.getUUID())) {
                return;
            }
            data.markSeen(player.getUUID());
            RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
            if (!result.isSuccess()) {
                MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
            }
        });
    }
}
