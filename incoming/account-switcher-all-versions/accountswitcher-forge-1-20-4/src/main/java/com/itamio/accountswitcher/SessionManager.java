package com.itamio.accountswitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Uses reflection to access Minecraft.session — works across all versions
 * without any version-specific imports.
 */
public class SessionManager {

    public static boolean switchToAccount(String accountName) {
        try {
            Object mc = getMinecraft();
            if (mc == null) {
                AccountSwitcherMod.getLogger().error("Minecraft instance is null");
                return false;
            }
            // Build a new Session object via reflection — constructor varies by version
            Object newSession = buildSession(accountName);
            if (newSession == null) {
                AccountSwitcherMod.getLogger().error("Could not create Session object");
                return false;
            }
            // Try known field names for the session field across all versions
            String[] fieldNames = {"session", "field_71449_j", "theSession"};
            Field sessionField = null;
            for (String name : fieldNames) {
                try {
                    sessionField = mc.getClass().getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (sessionField == null) {
                // Fallback: search all fields for a Session-typed field
                for (Field f : mc.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("Session")
                            || f.getType().getSimpleName().equals("GameProfile")) {
                        sessionField = f;
                        break;
                    }
                }
            }
            if (sessionField == null) {
                AccountSwitcherMod.getLogger().error("Could not find session field in Minecraft class");
                return false;
            }
            sessionField.setAccessible(true);
            sessionField.set(mc, newSession);
            AccountSwitcherMod.getLogger().info("Successfully switched to account: " + accountName);
            return true;
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to switch account to: " + accountName, e);
            return false;
        }
    }

    private static Object buildSession(String accountName) {
        // Try modern Session (1.16+): Session(String username, UUID uuid, String token, Optional<ProfileKeys>, Type)
        // Try intermediate Session (1.12-1.15): Session(String username, String uuid, String token, String type)
        // Try legacy Session (1.8.9): Session(String username, String uuid, String token, String type)
        String[] sessionClassNames = {
            "net.minecraft.client.Session",
            "net.minecraft.util.Session"
        };
        for (String className : sessionClassNames) {
            try {
                Class<?> sessionClass = Class.forName(className);
                // Try 4-arg String constructor (legacy)
                try {
                    return sessionClass.getConstructor(String.class, String.class, String.class, String.class)
                        .newInstance(accountName, accountName, "", "legacy");
                } catch (Exception ignored) {}
                // Try 3-arg constructor
                try {
                    return sessionClass.getConstructor(String.class, String.class, String.class)
                        .newInstance(accountName, accountName, "");
                } catch (Exception ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    public static String getCurrentAccount() {
        try {
            Object mc = getMinecraft();
            if (mc == null) return "Unknown";
            // Try getSession() method
            try {
                Method getSession = mc.getClass().getMethod("getSession");
                Object session = getSession.invoke(mc);
                if (session != null) {
                    try { return (String) session.getClass().getMethod("getUsername").invoke(session); }
                    catch (Exception ignored) {}
                    try { return (String) session.getClass().getMethod("func_111285_a").invoke(session); }
                    catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            // Try field access
            String[] fieldNames = {"session", "field_71449_j", "theSession"};
            for (String name : fieldNames) {
                try {
                    Field f = mc.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object session = f.get(mc);
                    if (session != null) {
                        try { return (String) session.getClass().getMethod("getUsername").invoke(session); }
                        catch (Exception ignored) {}
                        try { return (String) session.getClass().getMethod("func_111285_a").invoke(session); }
                        catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to get current account", e);
        }
        return "Unknown";
    }

    public static boolean isMinecraftReady() {
        try {
            Object mc = getMinecraft();
            if (mc == null) return false;
            // Check session exists
            try {
                Method getSession = mc.getClass().getMethod("getSession");
                return getSession.invoke(mc) != null;
            } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getMinecraft() {
        // Try all known static accessor method names
        String[] classNames = {"net.minecraft.client.Minecraft"};
        String[] methodNames = {"getInstance", "getMinecraft", "func_71410_x"};
        for (String cls : classNames) {
            try {
                Class<?> mcClass = Class.forName(cls);
                for (String method : methodNames) {
                    try {
                        return mcClass.getMethod(method).invoke(null);
                    } catch (Exception ignored) {}
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
