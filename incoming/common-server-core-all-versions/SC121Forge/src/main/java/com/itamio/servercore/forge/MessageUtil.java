package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void send(ServerPlayer player, String message) {
      if (player != null) {
         Component component = Component.literal(message);

         try {
            Method method = player.getClass().getMethod("sendSystemMessage", Component.class);
            method.invoke(player, component);
         } catch (ReflectiveOperationException var6) {
            try {
               Method methodx = player.getClass().getMethod("sendMessage", Component.class, UUID.class);
               methodx.invoke(player, component, player.getUUID());
            } catch (ReflectiveOperationException var5) {
               try {
                  Method methodxx = player.getClass().getMethod("sendMessage", Component.class);
                  methodxx.invoke(player, component);
               } catch (ReflectiveOperationException var4) {
               }
            }
         }
      }
   }
}
