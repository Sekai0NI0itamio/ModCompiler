package com.itamio.servercore.fabric;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_1937;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
import net.minecraft.server.MinecraftServer;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static String dimensionKey(class_3218 level) {
      return normalizeKey(keyToString(level.method_27983()));
   }

   public static class_3218 resolveLevel(MinecraftServer server, String dimensionKey) {
      if (server != null && dimensionKey != null) {
         String key = normalizeKey(dimensionKey);
         if (key == null) {
            return null;
         } else if (keyEquals(class_1937.field_25179, key)) {
            return server.method_30002();
         } else if (keyEquals(class_1937.field_25180, key)) {
            return server.method_3847(class_1937.field_25180);
         } else if (keyEquals(class_1937.field_25181, key)) {
            return server.method_3847(class_1937.field_25181);
         } else {
            Object resourceKey = createDimensionKey(key);
            return resourceKey == null ? null : getLevelByKey(server, resourceKey);
         }
      } else {
         return null;
      }
   }

   public static void teleport(class_3222 player, class_3218 level, double x, double y, double z, float yaw, float pitch) {
      if (player != null && level != null) {
         try {
            Method method = player.getClass().getMethod("teleportTo", class_3218.class, double.class, double.class, double.class, float.class, float.class);
            method.invoke(player, level, x, y, z, yaw, pitch);
         } catch (ReflectiveOperationException var12) {
            try {
               Method methodx = player.getClass()
                  .getMethod("teleportTo", class_3218.class, double.class, double.class, double.class, Set.class, float.class, float.class, boolean.class);
               methodx.invoke(player, level, x, y, z, Set.of(), yaw, pitch, false);
            } catch (ReflectiveOperationException var11) {
            }
         }
      }
   }

   public static String dimensionKey(class_5321<class_1937> key) {
      return normalizeKey(keyToString(key));
   }

   private static boolean keyEquals(class_5321<class_1937> key, String value) {
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

            Method create = class_5321.class.getMethod("create", class_5321.class, id.getClass());
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

   private static class_3218 getLevelByKey(MinecraftServer server, Object key) {
      try {
         Method method = server.getClass().getMethod("getLevel", key.getClass());
         Object value = method.invoke(server, key);
         return value instanceof class_3218 ? (class_3218)value : null;
      } catch (ReflectiveOperationException var5) {
         try {
            Method methodx = server.getClass().getMethod("getWorld", key.getClass());
            Object valuex = methodx.invoke(server, key);
            return valuex instanceof class_3218 ? (class_3218)valuex : null;
         } catch (ReflectiveOperationException var4) {
            return null;
         }
      }
   }
}
