package com.itamio.allowofflinetojoinlan.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

public final class OnlineModeHelper {
    private OnlineModeHelper() {
    }

    public static void apply(MinecraftServer server, boolean requireAuth, Logger logger) {
        if (server == null) {
            return;
        }
        Boolean before = getOnlineMode(server);
        if (before != null && before.booleanValue() == requireAuth) {
            logger.info("Server online-mode already set to {}.", requireAuth);
            return;
        }
        boolean updated = setOnlineMode(server, requireAuth);
        Boolean after = getOnlineMode(server);
        if (updated || (after != null && after.booleanValue() == requireAuth)) {
            logger.warn("Set server online-mode to {} (previous: {}).", requireAuth, before);
            if (!requireAuth) {
                logger.warn("Warning: online-mode=false disables Mojang authentication. Use only on trusted LAN sessions.");
            }
        } else {
            logger.error("Failed to update server online-mode. It remains {}.", before);
        }
    }

    private static Boolean getOnlineMode(Object server) {
        Boolean result = invokeBoolean(server, "usesAuthentication");
        if (result != null) {
            return result;
        }
        result = invokeBoolean(server, "isOnlineMode");
        if (result != null) {
            return result;
        }
        result = invokeBoolean(server, "getOnlineMode");
        if (result != null) {
            return result;
        }
        result = readBooleanField(server, "onlineMode");
        if (result != null) {
            return result;
        }
        return readBooleanField(server, "usesAuthentication");
    }

    private static boolean setOnlineMode(Object server, boolean value) {
        if (invokeSetter(server, "setUsesAuthentication", value)) {
            return true;
        }
        if (invokeSetter(server, "setOnlineMode", value)) {
            return true;
        }
        if (setBooleanField(server, "onlineMode", value)) {
            return true;
        }
        return setBooleanField(server, "usesAuthentication", value);
    }

    private static Boolean invokeBoolean(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    private static boolean invokeSetter(Object target, String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Boolean readBooleanField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    private static boolean setBooleanField(Object target, String fieldName, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(target, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
