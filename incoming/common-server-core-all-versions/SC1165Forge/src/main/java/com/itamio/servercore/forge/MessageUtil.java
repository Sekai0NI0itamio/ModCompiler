package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void send(ServerPlayerEntity player, String message) {
      if (player != null) {
         ITextComponent component = new StringTextComponent(message);

         try {
            Method method = player.getClass().getMethod("sendStatusMessage", ITextComponent.class, boolean.class);
            method.invoke(player, component, false);
         } catch (ReflectiveOperationException var6) {
            try {
               Method methodx = player.getClass().getMethod("sendMessage", ITextComponent.class, UUID.class);
               UUID uuid = PlayerUtil.getUuid(player);
               methodx.invoke(player, component, uuid == null ? new UUID(0L, 0L) : uuid);
            } catch (ReflectiveOperationException var5) {
            }
         }
      }
   }
}
