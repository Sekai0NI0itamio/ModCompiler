package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public final class TeleportUtil {
    private TeleportUtil() {
    }

    public static String dimensionKey(ServerWorld world) {
        return world.getDimensionKey().getLocation().toString().toLowerCase(Locale.ROOT);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, String dimensionKey) {
        if (server == null || dimensionKey == null) {
            return null;
        }
        String key = dimensionKey.toLowerCase(Locale.ROOT);
        if (World.OVERWORLD.getLocation().toString().equals(key)) {
            return server.getWorld(World.OVERWORLD);
        }
        if (World.THE_NETHER.getLocation().toString().equals(key)) {
            return server.getWorld(World.THE_NETHER);
        }
        if (World.THE_END.getLocation().toString().equals(key)) {
            return server.getWorld(World.THE_END);
        }
        return null;
    }

    public static void teleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
        if (player == null || world == null) {
            return;
        }
        try {
            Method method = player.getClass().getMethod("teleport", ServerWorld.class, double.class, double.class, double.class, float.class, float.class);
            method.invoke(player, world, x, y, z, yaw, pitch);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = player.getClass().getMethod("teleportTo", ServerWorld.class, double.class, double.class, double.class, float.class, float.class);
            method.invoke(player, world, x, y, z, yaw, pitch);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
