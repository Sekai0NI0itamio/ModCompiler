package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.class_1937;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
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
            class_5321<class_1937> key = class_5321.method_29179(class_2378.field_25298, id);
            return server.method_3847(key);
         }
      } else {
         return null;
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
