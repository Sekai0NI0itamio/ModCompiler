package com.itamio.servercore.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.class_3222;

final class RotationUtil {
   private RotationUtil() {
   }

   static float getYaw(class_3222 player) {
      Float value = readFloat(player, "getYaw", "getYaw", "yaw", "rotationYaw", "yRot");
      return value == null ? 0.0F : value;
   }

   static float getPitch(class_3222 player) {
      Float value = readFloat(player, "getPitch", "getPitch", "pitch", "rotationPitch", "xRot");
      return value == null ? 0.0F : value;
   }

   private static Float readFloat(class_3222 player, String noArgMethod, String argMethod, String fieldA, String fieldB, String fieldC) {
      try {
         Method method = player.getClass().getMethod(noArgMethod);
         Object result = method.invoke(player);
         if (result instanceof Float) {
            return (Float)result;
         }
      } catch (ReflectiveOperationException var9) {
      }

      try {
         Method method = player.getClass().getMethod(argMethod, float.class);
         Object result = method.invoke(player, 0.0F);
         if (result instanceof Float) {
            return (Float)result;
         }
      } catch (ReflectiveOperationException var8) {
      }

      Float value = readField(player, fieldA);
      if (value != null) {
         return value;
      } else {
         value = readField(player, fieldB);
         return value != null ? value : readField(player, fieldC);
      }
   }

   private static Float readField(class_3222 player, String fieldName) {
      try {
         Field field = player.getClass().getField(fieldName);
         return field.getFloat(player);
      } catch (ReflectiveOperationException var3) {
         return null;
      }
   }
}
