package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

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
        Object key = createRegistryKey(id);
        if (key == null) {
            return null;
        }
        try {
            Method method = server.getClass().getMethod("getWorld", key.getClass());
            return (ServerWorld) method.invoke(server, key);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object createRegistryKey(Identifier id) {
        try {
            Class<?> registryKeys = Class.forName("net.minecraft.registry.RegistryKeys");
            Object worldKey = registryKeys.getField("WORLD").get(null);
            Class<?> registryKeyClass = Class.forName("net.minecraft.registry.RegistryKey");
            Method of = registryKeyClass.getMethod("of", registryKeyClass, Identifier.class);
            return of.invoke(null, worldKey, id);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Class<?> registryClass = Class.forName("net.minecraft.util.registry.Registry");
            Object worldKey = registryClass.getField("WORLD_KEY").get(null);
            Class<?> registryKeyClass = Class.forName("net.minecraft.util.registry.RegistryKey");
            Method of = registryKeyClass.getMethod("of", registryKeyClass, Identifier.class);
            return of.invoke(null, worldKey, id);
        } catch (ReflectiveOperationException ignored) {
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

    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        try {
            Method method = player.getClass().getMethod("getServerWorld");
            Object value = method.invoke(player);
            return value instanceof ServerWorld ? (ServerWorld) value : null;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return (ServerWorld) player.getWorld();
        } catch (ClassCastException ignored) {
            return null;
        }
    }
}
