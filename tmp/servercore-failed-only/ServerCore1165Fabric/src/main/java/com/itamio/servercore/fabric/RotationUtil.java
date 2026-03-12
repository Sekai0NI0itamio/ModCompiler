package com.itamio.servercore.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.network.ServerPlayerEntity;

final class RotationUtil {
    private RotationUtil() {
    }

    static float getYaw(ServerPlayerEntity player) {
        Float value = readFloat(player, "getYaw", "getYaw", "yaw", "rotationYaw", "yRot");
        return value == null ? 0.0f : value;
    }

    static float getPitch(ServerPlayerEntity player) {
        Float value = readFloat(player, "getPitch", "getPitch", "pitch", "rotationPitch", "xRot");
        return value == null ? 0.0f : value;
    }

    private static Float readFloat(ServerPlayerEntity player, String noArgMethod, String argMethod, String fieldA, String fieldB, String fieldC) {
        try {
            Method method = player.getClass().getMethod(noArgMethod);
            Object result = method.invoke(player);
            if (result instanceof Float) {
                return (Float) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = player.getClass().getMethod(argMethod, float.class);
            Object result = method.invoke(player, 0.0f);
            if (result instanceof Float) {
                return (Float) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        Float value = readField(player, fieldA);
        if (value != null) {
            return value;
        }
        value = readField(player, fieldB);
        if (value != null) {
            return value;
        }
        return readField(player, fieldC);
    }

    private static Float readField(ServerPlayerEntity player, String fieldName) {
        try {
            Field field = player.getClass().getField(fieldName);
            return field.getFloat(player);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
