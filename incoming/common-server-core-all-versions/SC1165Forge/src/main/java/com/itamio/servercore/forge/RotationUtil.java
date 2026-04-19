package com.itamio.servercore.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.player.ServerPlayerEntity;

public final class RotationUtil {
   private RotationUtil() {
   }

   public static float getYaw(ServerPlayerEntity player) {
      Float value = readFloat(player, "getYRot", "yRot", "rotationYaw");
      return value == null ? 0.0F : value;
   }

   public static float getPitch(ServerPlayerEntity player) {
      Float value = readFloat(player, "getXRot", "xRot", "rotationPitch");
      return value == null ? 0.0F : value;
   }

   private static Float readFloat(ServerPlayerEntity player, String methodName, String fieldA, String fieldB) {
      try {
         Method method = player.getClass().getMethod(methodName);
         Object result = method.invoke(player);
         if (result instanceof Float) {
            return (Float)result;
         }
      } catch (ReflectiveOperationException var8) {
      }

      try {
         Field field = player.getClass().getField(fieldA);
         return field.getFloat(player);
      } catch (ReflectiveOperationException var7) {
         try {
            Field fieldx = player.getClass().getField(fieldB);
            return fieldx.getFloat(player);
         } catch (ReflectiveOperationException var6) {
            return null;
         }
      }
   }
}
