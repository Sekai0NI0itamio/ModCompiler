package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class TeleportUtil {
    private TeleportUtil() {
    }

    public static String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString().toLowerCase(Locale.ROOT);
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String dimensionKey) {
        if (server == null || dimensionKey == null) {
            return null;
        }
        String key = dimensionKey.toLowerCase(Locale.ROOT);
        if (Level.OVERWORLD.location().toString().equals(key)) {
            return server.overworld();
        }
        if (Level.NETHER.location().toString().equals(key)) {
            return server.getLevel(Level.NETHER);
        }
        if (Level.END.location().toString().equals(key)) {
            return server.getLevel(Level.END);
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) {
            return null;
        }
        try {
            ResourceKey<Level> resourceKey = createDimensionKey(id);
            return server.getLevel(resourceKey);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        if (player == null || level == null) {
            return;
        }
        try {
            Method method = player.getClass().getMethod(
                    "teleportTo",
                    ServerLevel.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    float.class
            );
            method.invoke(player, level, x, y, z, yaw, pitch);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = player.getClass().getMethod(
                    "teleportTo",
                    ServerLevel.class,
                    double.class,
                    double.class,
                    double.class,
                    Set.class,
                    float.class,
                    float.class,
                    boolean.class
            );
            method.invoke(player, level, x, y, z, Set.of(), yaw, pitch, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> createDimensionKey(ResourceLocation id) throws ReflectiveOperationException {
        Class<?> registryClass;
        try {
            registryClass = Class.forName("net.minecraft.core.registries.Registries");
            Object dimensionRegistry = registryClass.getField("DIMENSION").get(null);
            Method create = ResourceKey.class.getMethod("create", ResourceKey.class, ResourceLocation.class);
            return (ResourceKey<Level>) create.invoke(null, dimensionRegistry, id);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            registryClass = Class.forName("net.minecraft.core.Registry");
            Object dimensionRegistry = registryClass.getField("DIMENSION_REGISTRY").get(null);
            Method create = ResourceKey.class.getMethod("create", ResourceKey.class, ResourceLocation.class);
            return (ResourceKey<Level>) create.invoke(null, dimensionRegistry, id);
        }
    }
}
