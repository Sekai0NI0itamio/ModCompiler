package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public final class TeleportUtil {
    private static final String OVERWORLD_KEY = "minecraft:overworld";
    private static final String NETHER_KEY = "minecraft:the_nether";
    private static final String END_KEY = "minecraft:the_end";

    private TeleportUtil() {
    }

    public static String dimensionKey(ServerWorld world) {
        if (world == null) {
            return null;
        }
        Object key = invoke(world, "getDimensionKey");
        if (key == null) {
            key = invoke(world, "dimension");
        }
        if (key == null) {
            key = invoke(world, "getDimension");
        }
        if (key == null) {
            key = invoke(world, "getDimensionType");
        }
        String keyString = keyToString(key);
        return keyString == null ? null : keyString.toLowerCase(Locale.ROOT);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, String dimensionKey) {
        if (server == null || dimensionKey == null) {
            return null;
        }
        String key = dimensionKey.toLowerCase(Locale.ROOT);
        if (OVERWORLD_KEY.equals(key)) {
            return getWorldByKey(server, World.OVERWORLD);
        }
        if (NETHER_KEY.equals(key)) {
            return getWorldByKey(server, World.THE_NETHER);
        }
        if (END_KEY.equals(key)) {
            return getWorldByKey(server, World.THE_END);
        }
        ServerWorld candidate = findWorldByKey(server, key);
        if (candidate != null) {
            return candidate;
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

    private static ServerWorld getWorldByKey(MinecraftServer server, Object key) {
        if (server == null || key == null) {
            return null;
        }
        Object world = invoke(server, "getWorld", key.getClass(), key);
        if (world == null) {
            world = invoke(server, "getLevel", key.getClass(), key);
        }
        return world instanceof ServerWorld ? (ServerWorld) world : null;
    }

    private static ServerWorld findWorldByKey(MinecraftServer server, String key) {
        Object worlds = invoke(server, "getWorlds");
        if (worlds == null) {
            worlds = invoke(server, "getAllLevels");
        }
        if (worlds instanceof Iterable) {
            for (Object candidate : (Iterable<?>) worlds) {
                if (!(candidate instanceof ServerWorld)) {
                    continue;
                }
                String candidateKey = dimensionKey((ServerWorld) candidate);
                if (candidateKey != null && candidateKey.equalsIgnoreCase(key)) {
                    return (ServerWorld) candidate;
                }
            }
        }
        return null;
    }

    private static String keyToString(Object key) {
        if (key == null) {
            return null;
        }
        if (key instanceof ResourceLocation) {
            return key.toString();
        }
        Object location = invoke(key, "getLocation");
        if (location == null) {
            location = invoke(key, "location");
        }
        if (location == null) {
            location = invoke(key, "getRegistryName");
        }
        if (location instanceof ResourceLocation) {
            return location.toString();
        }
        if (location instanceof String) {
            return (String) location;
        }
        return key.toString();
    }

    private static Object invoke(Object target, String name, Class<?> param, Object arg) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name, param);
            return method.invoke(target, arg);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
