package com.itamio.servercore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerCoreFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                ServerCoreCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            ServerCoreData data = ServerCoreData.get(server);
            if (data.hasSeen(player.getUuid())) {
                return;
            }
            data.markSeen(player.getUuid());
            RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
            if (!result.isSuccess()) {
                MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
            }
        });
    }
}
