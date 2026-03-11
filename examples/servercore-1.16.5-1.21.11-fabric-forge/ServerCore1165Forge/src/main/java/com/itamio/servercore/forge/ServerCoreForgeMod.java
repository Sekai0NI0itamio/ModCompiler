package com.itamio.servercore.forge;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ServerCoreForgeMod.MOD_ID)
public final class ServerCoreForgeMod {
    public static final String MOD_ID = "servercore";

    public ServerCoreForgeMod() {
        MinecraftForge.EVENT_BUS.addListener(ServerCoreForgeMod::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ServerCoreForgeMod::onPlayerLogin);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        ServerCoreCommands.register(event.getDispatcher());
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
        if (player.getServer() == null) {
            return;
        }
        ServerCoreData data = ServerCoreData.get(player.getServer());
        if (data.hasSeen(player.getUniqueID())) {
            return;
        }
        data.markSeen(player.getUniqueID());
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
        if (!result.isSuccess()) {
            MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
        }
    }
}
