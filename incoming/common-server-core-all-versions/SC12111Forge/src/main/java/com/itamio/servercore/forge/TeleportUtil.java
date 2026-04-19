package com.itamio.servercore.forge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static String dimensionKey(ServerLevel level) {
      return normalizeKey(keyToString(level.dimension()));
   }

   public static ServerLevel resolveLevel(MinecraftServer server, String dimensionKey) {
      if (server != null && dimensionKey != null) {
         String key = normalizeKey(dimensionKey);
         if (key == null) {
            return null;
         } else if (keyEquals(Level.OVERWORLD, key)) {
            return server.overworld();
         } else if (keyEquals(Level.NETHER, key)) {
            return server.getLevel(Level.NETHER);
         } else if (keyEquals(Level.END, key)) {
            return server.getLevel(Level.END);
         } else {
            Object resourceKey = createDimensionKey(key);
            return resourceKey == null ? null : getLevelByKey(server, resourceKey);
         }
      } else {
         return null;
      }
   }

   public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
      if (player != null && level != null) {
         try {
            Method method = player.getClass().getMethod("teleportTo", ServerLevel.class, double.class, double.class, double.class, float.class, float.class);
            method.invoke(player, level, x, y, z, yaw, pitch);
         } catch (ReflectiveOperationException var12) {
            try {
               Method methodx = player.getClass()
                  .getMethod("teleportTo", ServerLevel.class, double.class, double.class, double.class, Set.class, float.class, float.class, boolean.class);
               methodx.invoke(player, level, x, y, z, Set.of(), yaw, pitch, false);
            } catch (ReflectiveOperationException var11) {
            }
         }
      }
   }

   public static String dimensionKey(ResourceKey<Level> key) {
      return normalizeKey(keyToString(key));
   }

   private static boolean keyEquals(ResourceKey<Level> key, String value) {
      String keyValue = dimensionKey(key);
      return keyValue != null && keyValue.equals(value);
   }

   private static String normalizeKey(String key) {
      return key == null ? null : key.toLowerCase(Locale.ROOT);
   }

   private static String keyToString(Object key) {
      if (key == null) {
         return null;
      } else {
         try {
            Method method = key.getClass().getMethod("location");
            Object value = method.invoke(key);
            return value == null ? null : value.toString();
         } catch (ReflectiveOperationException var4) {
            try {
               Method methodx = key.getClass().getMethod("getValue");
               Object valuex = methodx.invoke(key);
               return valuex == null ? null : valuex.toString();
            } catch (ReflectiveOperationException var3) {
               return key.toString();
            }
         }
      }
   }

   private static Object createDimensionKey(String key) {
      Object id = createResourceLocation(key);
      if (id == null) {
         return null;
      } else {
         try {
            Object dimensionRegistry;
            try {
               Class<?> registryClass = Class.forName("net.minecraft.core.registries.Registries");
               dimensionRegistry = registryClass.getField("DIMENSION").get(null);
            } catch (NoSuchFieldException | ClassNotFoundException var5) {
               Class<?> registryClassx = Class.forName("net.minecraft.core.Registry");
               dimensionRegistry = registryClassx.getField("DIMENSION_REGISTRY").get(null);
            }

            Method create = ResourceKey.class.getMethod("create", ResourceKey.class, id.getClass());
            return create.invoke(null, dimensionRegistry, id);
         } catch (ReflectiveOperationException var6) {
            return null;
         }
      }
   }

   private static Object createResourceLocation(String key) {
      try {
         Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");

         try {
            Method method = resourceLocationClass.getMethod("tryParse", String.class);
            Object value = method.invoke(null, key);
            if (value != null) {
               return value;
            }
         } catch (ReflectiveOperationException var6) {
         }

         try {
            Constructor<?> ctor = resourceLocationClass.getConstructor(String.class);
            return ctor.newInstance(key);
         } catch (ReflectiveOperationException var7) {
         }
      } catch (ClassNotFoundException var8) {
      }

      try {
         Class<?> identifierClass = Class.forName("net.minecraft.util.Identifier");

         try {
            Method method = identifierClass.getMethod("tryParse", String.class);
            Object value = method.invoke(null, key);
            if (value != null) {
               return value;
            }
         } catch (ReflectiveOperationException var4) {
         }

         Constructor<?> ctor = identifierClass.getConstructor(String.class);
         return ctor.newInstance(key);
      } catch (ReflectiveOperationException var5) {
         return null;
      }
   }

   private static ServerLevel getLevelByKey(MinecraftServer server, Object key) {
      try {
         Method method = server.getClass().getMethod("getLevel", key.getClass());
         Object value = method.invoke(server, key);
         return value instanceof ServerLevel ? (ServerLevel)value : null;
      } catch (ReflectiveOperationException var5) {
         try {
            Method methodx = server.getClass().getMethod("getWorld", key.getClass());
            Object valuex = methodx.invoke(server, key);
            return valuex instanceof ServerLevel ? (ServerLevel)valuex : null;
         } catch (ReflectiveOperationException var4) {
            return null;
         }
      }
   }
}
