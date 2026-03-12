package com.itamio.allowofflinetojoinlan;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public class ModConfig {
   private static Configuration CONFIG;
   public static boolean requireMojangAuthentication = true;

   public static void init(File configDir) {
      File file = new File(configDir, "allowofflinetojoinlan.cfg");
      CONFIG = new Configuration(file);
      sync();
   }

   private static void sync() {
      try {
         CONFIG.load();
         String cat = "general";
         CONFIG.addCustomCategoryComment(
            "general",
            "Allow Offline Players (LAN) - Configuration\nAuthor: Itamio\nNote: Disabling Mojang authentication (online-mode) lets offline players join, but is insecure.\nUse only on trusted private LAN sessions or servers not exposed to the internet.\nChanges take effect on next world/server start."
         );
         requireMojangAuthentication = CONFIG.getBoolean(
            "requireMojangAuthentication",
            "general",
            false,
            "If true, server runs in online-mode (Mojang account authentication required).\nIf false, server runs in offline-mode (authentication disabled) so offline players can join."
         );
      } finally {
         if (CONFIG.hasChanged()) {
            CONFIG.save();
         }
      }
   }
}
