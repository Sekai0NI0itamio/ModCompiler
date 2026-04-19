package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class ServerCoreAccess {
   private ServerCoreAccess() {
   }

   static MinecraftServer getServer(ServerPlayer player) {
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
               ServerLevel level = getServerLevel(player);
               return level != null ? level.getServer() : null;
            }
         }
      }
   }

   static ServerLevel getServerLevel(ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         ServerLevel level = invokeLevel(player, "serverLevel");
         if (level != null) {
            return level;
         } else {
            level = invokeLevel(player, "getLevel");
            return level != null ? level : invokeLevel(player, "level");
         }
      }
   }

   static String getPlayerName(ServerPlayer player) {
      return player == null ? "unknown" : player.getName().getString();
   }

   static int getMaxBuildHeight(ServerLevel level) {
      Integer value = invokeInt(level, "getMaxBuildHeight", "getMaxY");
      return value != null ? value : 320;
   }

   static int getMinBuildHeight(ServerLevel level) {
      Integer value = invokeInt(level, "getMinBuildHeight", "getMinY");
      return value != null ? value : -64;
   }

   private static MinecraftServer invokeServer(ServerPlayer player, String name) {
      try {
         Method method = player.getClass().getMethod(name);
         return (MinecraftServer)method.invoke(player);
      } catch (ReflectiveOperationException var3) {
         return null;
      }
   }

   private static ServerLevel invokeLevel(ServerPlayer player, String name) {
      try {
         Method method = player.getClass().getMethod(name);
         Object value = method.invoke(player);
         return value instanceof ServerLevel ? (ServerLevel)value : null;
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
