package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static String dimensionKey(class_3218 world) {
      return world.method_27983().method_29177().toString().toLowerCase(Locale.ROOT);
   }

   public static class_3218 resolveWorld(MinecraftServer server, String dimensionKey) {
      if (server != null && dimensionKey != null) {
         class_2960 id = class_2960.method_12829(dimensionKey.toLowerCase(Locale.ROOT));
         if (id == null) {
            return null;
         } else {
            Object key = createRegistryKey(id);
            if (key == null) {
               return null;
            } else {
               try {
                  Method method = server.getClass().getMethod("getWorld", key.getClass());
                  return (class_3218)method.invoke(server, key);
               } catch (ReflectiveOperationException var5) {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static Object createRegistryKey(class_2960 id) {
      try {
         Class<?> registryKeys = Class.forName("net.minecraft.registry.RegistryKeys");
         Object worldKey = registryKeys.getField("WORLD").get(null);
         Class<?> registryKeyClass = Class.forName("net.minecraft.registry.RegistryKey");
         Method of = registryKeyClass.getMethod("of", registryKeyClass, class_2960.class);
         return of.invoke(null, worldKey, id);
      } catch (ReflectiveOperationException var6) {
         try {
            Class<?> registryClass = Class.forName("net.minecraft.util.registry.Registry");
            Object worldKeyx = registryClass.getField("WORLD_KEY").get(null);
            Class<?> registryKeyClassx = Class.forName("net.minecraft.util.registry.RegistryKey");
            Method ofx = registryKeyClassx.getMethod("of", registryKeyClassx, class_2960.class);
            return ofx.invoke(null, worldKeyx, id);
         } catch (ReflectiveOperationException var5) {
            return null;
         }
      }
   }

   public static void teleport(class_3222 player, class_3218 world, double x, double y, double z, float yaw, float pitch) {
      if (player != null && world != null) {
         try {
            Method method = player.getClass().getMethod("teleport", class_3218.class, double.class, double.class, double.class, float.class, float.class);
            method.invoke(player, world, x, y, z, yaw, pitch);
         } catch (ReflectiveOperationException var12) {
            try {
               Method methodx = player.getClass().getMethod("teleportTo", class_3218.class, double.class, double.class, double.class, float.class, float.class);
               methodx.invoke(player, world, x, y, z, yaw, pitch);
            } catch (ReflectiveOperationException var11) {
            }
         }
      }
   }

   public static class_3218 getServerWorld(class_3222 player) {
      if (player == null) {
         return null;
      } else {
         try {
            Method method = player.getClass().getMethod("getServerWorld");
            Object value = method.invoke(player);
            return value instanceof class_3218 ? (class_3218)value : null;
         } catch (ReflectiveOperationException var4) {
            try {
               return player.method_14220();
            } catch (ClassCastException var3) {
               return null;
            }
         }
      }
   }
}
