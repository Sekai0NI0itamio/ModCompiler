package com.itamio.servercore.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ServerCoreForgeMod.MOD_ID)
@Mod.EventBusSubscriber(modid = ServerCoreForgeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerCoreForgeMod {
    public static final String MOD_ID = "servercore";

    public ServerCoreForgeMod() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ServerCoreCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        MinecraftServer server = ServerCoreAccess.getServer(player);
        if (server == null) {
            return;
        }
        ServerCoreData data = ServerCoreData.get(server);
        if (data.hasSeen(player.getUUID())) {
            return;
        }
        data.markSeen(player.getUUID());
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
        if (!result.isSuccess()) {
            MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
        }
    }
}
