package com.itamio.servercore.fabric;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.class_2561;
import net.minecraft.class_3222;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void send(class_3222 player, String message) {
      if (player != null) {
         class_2561 component = class_2561.method_43470(message);

         try {
            Method method = player.getClass().getMethod("sendSystemMessage", class_2561.class);
            method.invoke(player, component);
         } catch (ReflectiveOperationException var6) {
            try {
               Method methodx = player.getClass().getMethod("sendMessage", class_2561.class, UUID.class);
               methodx.invoke(player, component, player.method_5667());
            } catch (ReflectiveOperationException var5) {
               try {
                  Method methodxx = player.getClass().getMethod("sendMessage", class_2561.class);
                  methodxx.invoke(player, component);
               } catch (ReflectiveOperationException var4) {
               }
            }
         }
      }
   }
}
