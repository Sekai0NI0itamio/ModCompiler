package com.itamio.servercore.fabric;

import net.minecraft.class_2585;
import net.minecraft.class_3222;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void send(class_3222 player, String message) {
      if (player != null) {
         player.method_7353(new class_2585(message), false);
      }
   }
}
