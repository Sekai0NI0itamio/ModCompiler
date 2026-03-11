package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static void send(ServerPlayer player, String message) {
        if (player == null) {
            return;
        }
        Component component = Component.literal(message);
        try {
            Method method = player.getClass().getMethod("sendSystemMessage", Component.class);
            method.invoke(player, component);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = player.getClass().getMethod("sendMessage", Component.class, UUID.class);
            method.invoke(player, component, player.getUUID());
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = player.getClass().getMethod("sendMessage", Component.class);
            method.invoke(player, component);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
