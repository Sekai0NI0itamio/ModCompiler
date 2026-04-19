package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

final class ServerCoreAccess {
   private ServerCoreAccess() {
   }

   static MinecraftServer getServer(class_3222 player) {
      if (player == null) {
         return null;
      } else {
         MinecraftServer server = invokeServer(player, "getServer");
         if (server != null) {
            return server;
         } else {
            server = invokeServer(player, "server");
            if (server != null) {
               return server;
            } else {
               class_3218 level = getServerLevel(player);
               return level != null ? level.method_8503() : null;
            }
         }
      }
   }

   static class_3218 getServerLevel(class_3222 player) {
      if (player == null) {
         return null;
      } else {
         class_3218 level = invokeLevel(player, "serverLevel");
         if (level != null) {
            return level;
         } else {
            level = invokeLevel(player, "getLevel");
            return level != null ? level : invokeLevel(player, "level");
         }
      }
   }

   static String getPlayerName(class_3222 player) {
      return player == null ? "unknown" : player.method_5477().getString();
   }

   static int getMaxBuildHeight(class_3218 level) {
      Integer value = invokeInt(level, "getMaxBuildHeight", "getMaxY");
      return value != null ? value : 320;
   }

   static int getMinBuildHeight(class_3218 level) {
      Integer value = invokeInt(level, "getMinBuildHeight", "getMinY");
      return value != null ? value : -64;
   }

   private static MinecraftServer invokeServer(class_3222 player, String name) {
      try {
         Method method = player.getClass().getMethod(name);
         return (MinecraftServer)method.invoke(player);
      } catch (ReflectiveOperationException var3) {
         return null;
      }
   }

   private static class_3218 invokeLevel(class_3222 player, String name) {
      try {
         Method method = player.getClass().getMethod(name);
         Object value = method.invoke(player);
         return value instanceof class_3218 ? (class_3218)value : null;
      } catch (ReflectiveOperationException var4) {
         return null;
      }
   }

   private static Integer invokeInt(Object target, String... names) {
      if (target == null) {
         return null;
      } else {
         for (String name : names) {
            try {
               Method method = target.getClass().getMethod(name);
               Object value = method.invoke(target);
               if (value instanceof Integer) {
                  return (Integer)value;
               }
            } catch (ReflectiveOperationException var8) {
            }
         }

         return null;
      }
   }
}
