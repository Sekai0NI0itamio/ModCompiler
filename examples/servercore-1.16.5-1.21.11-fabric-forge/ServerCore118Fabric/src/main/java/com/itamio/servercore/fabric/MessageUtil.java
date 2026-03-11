package com.itamio.servercore.fabric;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static void send(ServerPlayerEntity player, String message) {
        if (player != null) {
            player.sendMessage(new LiteralText(message), false);
        }
    }
}
