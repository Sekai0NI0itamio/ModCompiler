package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

public final class TeleportUtil {
    private TeleportUtil() {
    }

    public static String dimensionKey(ServerWorld world) {
        return world.getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, String dimensionKey) {
        if (server == null || dimensionKey == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(dimensionKey.toLowerCase(Locale.ROOT));
        if (id == null) {
            return null;
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
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
