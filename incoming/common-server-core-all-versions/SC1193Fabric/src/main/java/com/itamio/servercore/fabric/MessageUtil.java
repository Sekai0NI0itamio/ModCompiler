package com.itamio.servercore.fabric;

import net.minecraft.class_2561;
import net.minecraft.class_3222;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void send(class_3222 player, String message) {
      if (player != null) {
         player.method_7353(class_2561.method_43470(message), false);
      }
   }
}
