package com.itamio.servercore.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;

public final class PlayerUtil {
   private PlayerUtil() {
   }

   public static UUID getUuid(ServerPlayerEntity player) {
      if (player == null) {
         return null;
      } else {
         UUID uuid = readUuid(player, "getUUID");
         if (uuid != null) {
            return uuid;
         } else {
            uuid = readUuid(player, "getUniqueID");
            if (uuid != null) {
               return uuid;
            } else {
               try {
                  return player.func_146103_bH().getId();
               } catch (RuntimeException var3) {
                  return null;
               }
            }
         }
      }
   }

   public static double getX(ServerPlayerEntity player) {
      return readDouble(player, new String[]{"getX", "getPosX", "getXPos"}, "posX", "x");
   }

   public static double getY(ServerPlayerEntity player) {
      return readDouble(player, new String[]{"getY", "getPosY", "getYPos"}, "posY", "y");
   }

   public static double getZ(ServerPlayerEntity player) {
      return readDouble(player, new String[]{"getZ", "getPosZ", "getZPos"}, "posZ", "z");
   }

   public static ServerWorld getServerWorld(ServerPlayerEntity player) {
      if (player == null) {
         return null;
      } else {
         Object world = invoke(player, "getServerWorld");
         if (world == null) {
            world = invoke(player, "getCommandSenderWorld");
         }

         if (world == null) {
            world = invoke(player, "getEntityWorld");
         }

         if (world == null) {
            world = invoke(player, "getLevel");
         }

         if (world == null) {
            world = invoke(player, "getWorld");
         }

         return world instanceof ServerWorld ? (ServerWorld)world : null;
      }
   }

   public static ServerPlayerEntity getPlayerByUuid(MinecraftServer server, UUID uuid) {
      if (server != null && uuid != null) {
         Object list = invoke(server, "getPlayerList");
         if (list == null) {
            return null;
         } else {
            Object player = invoke(list, "getPlayer", uuid);
            if (player == null) {
               player = invoke(list, "getPlayerByUUID", uuid);
            }

            return player instanceof ServerPlayerEntity ? (ServerPlayerEntity)player : null;
         }
      } else {
         return null;
      }
   }

   private static UUID readUuid(ServerPlayerEntity player, String methodName) {
      Object result = invoke(player, methodName);
      return result instanceof UUID ? (UUID)result : null;
   }

   private static double readDouble(ServerPlayerEntity player, String[] methodNames, String... fieldNames) {
      for (String name : methodNames) {
         Object result = invoke(player, name);
         if (result instanceof Number) {
            return ((Number)result).doubleValue();
         }
      }

      for (String fieldName : fieldNames) {
         Double fieldValue = readFieldDouble(player, fieldName);
         if (fieldValue != null) {
            return fieldValue;
         }
      }

      return 0.0;
   }

   private static Double readFieldDouble(ServerPlayerEntity player, String fieldName) {
      try {
         Field field = player.getClass().getField(fieldName);
         return field.getDouble(player);
      } catch (ReflectiveOperationException var3) {
         return null;
      }
   }

   private static Object invoke(Object target, String methodName, Object... args) {
      if (target == null) {
         return null;
      } else {
         Class<?>[] params = new Class[args.length];

         for (int i = 0; i < args.length; i++) {
            params[i] = args[i].getClass();
         }

         try {
            Method method = target.getClass().getMethod(methodName, params);
            return method.invoke(target, args);
         } catch (ReflectiveOperationException var5) {
            return null;
         }
      }
   }
}
