package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_1937;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
import net.minecraft.server.MinecraftServer;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static String dimensionKey(class_3218 level) {
      return level.method_27983().method_29177().toString().toLowerCase(Locale.ROOT);
   }

   public static class_3218 resolveLevel(MinecraftServer server, String dimensionKey) {
      if (server != null && dimensionKey != null) {
         String key = dimensionKey.toLowerCase(Locale.ROOT);
         if (class_1937.field_25179.method_29177().toString().equals(key)) {
            return server.method_30002();
         } else if (class_1937.field_25180.method_29177().toString().equals(key)) {
            return server.method_3847(class_1937.field_25180);
         } else if (class_1937.field_25181.method_29177().toString().equals(key)) {
            return server.method_3847(class_1937.field_25181);
         } else {
            class_2960 id = class_2960.method_12829(key);
            if (id == null) {
               return null;
            } else {
               try {
                  class_5321<class_1937> resourceKey = createDimensionKey(id);
                  return server.method_3847(resourceKey);
               } catch (ReflectiveOperationException var5) {
                  return null;
               }
            }
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

   private static class_5321<class_1937> createDimensionKey(class_2960 id) throws ReflectiveOperationException {
      try {
         Class<?> registryClass = Class.forName("net.minecraft.core.registries.Registries");
         Object dimensionRegistry = registryClass.getField("DIMENSION").get(null);
         Method create = class_5321.class.getMethod("create", class_5321.class, class_2960.class);
         return (class_5321<class_1937>)create.invoke(null, dimensionRegistry, id);
      } catch (NoSuchFieldException | ClassNotFoundException var5) {
         Class<?> registryClassx = Class.forName("net.minecraft.core.Registry");
         Object dimensionRegistryx = registryClassx.getField("DIMENSION_REGISTRY").get(null);
         Method createx = class_5321.class.getMethod("create", class_5321.class, class_2960.class);
         return (class_5321<class_1937>)createx.invoke(null, dimensionRegistryx, id);
      }
   }
}
