package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static String dimensionKey(ServerLevel level) {
      return level.m_46472_().m_135782_().toString().toLowerCase(Locale.ROOT);
   }

   public static ServerLevel resolveLevel(MinecraftServer server, String dimensionKey) {
      if (server != null && dimensionKey != null) {
         String key = dimensionKey.toLowerCase(Locale.ROOT);
         if (Level.f_46428_.m_135782_().toString().equals(key)) {
            return server.m_129783_();
         } else if (Level.f_46429_.m_135782_().toString().equals(key)) {
            return server.m_129880_(Level.f_46429_);
         } else if (Level.f_46430_.m_135782_().toString().equals(key)) {
            return server.m_129880_(Level.f_46430_);
         } else {
            ResourceLocation id = ResourceLocation.m_135820_(key);
            if (id == null) {
               return null;
            } else {
               try {
                  ResourceKey<Level> resourceKey = createDimensionKey(id);
                  return server.m_129880_(resourceKey);
               } catch (ReflectiveOperationException var5) {
                  return null;
               }
            }
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

   private static ResourceKey<Level> createDimensionKey(ResourceLocation id) throws ReflectiveOperationException {
      try {
         Class<?> registryClass = Class.forName("net.minecraft.core.registries.Registries");
         Object dimensionRegistry = registryClass.getField("DIMENSION").get(null);
         Method create = ResourceKey.class.getMethod("create", ResourceKey.class, ResourceLocation.class);
         return (ResourceKey<Level>)create.invoke(null, dimensionRegistry, id);
      } catch (NoSuchFieldException | ClassNotFoundException var5) {
         Class<?> registryClassx = Class.forName("net.minecraft.core.Registry");
         Object dimensionRegistryx = registryClassx.getField("DIMENSION_REGISTRY").get(null);
         Method createx = ResourceKey.class.getMethod("create", ResourceKey.class, ResourceLocation.class);
         return (ResourceKey<Level>)createx.invoke(null, dimensionRegistryx, id);
      }
   }
}
