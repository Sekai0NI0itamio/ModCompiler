package com.itamio.servercore.fabric;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static void send(ServerPlayerEntity player, String message) {
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }
}
