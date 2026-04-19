package asd.itamio.servercore.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class ServerCoreConfig {
   private static final String CATEGORY_RTP = "rtp";
   public static int rtpMaxAttempts = 64;
   public static int rtpBorderPadding = 16;
   public static String endMode = "outer_islands";
   public static int endOuterMinRadius = 1200;
   public static int endOuterMaxRadius = 7000;
   public static int endMainIslandRadius = 256;

   private ServerCoreConfig() {
   }

   public static synchronized void load(File file) {
      Configuration config = new Configuration(file);

      try {
         config.load();
         rtpMaxAttempts = config.getInt("maxAttempts", "rtp", rtpMaxAttempts, 1, 10000, "How many candidate locations RTP will try before failing.");
         rtpBorderPadding = config.getInt("borderPadding", "rtp", rtpBorderPadding, 0, 2048, "Keep RTP this many blocks away from world border edges.");
         endMode = config.getString("endMode", "rtp", endMode, "RTP mode for The End: outer_islands or main_island.");
         endOuterMinRadius = config.getInt(
            "endOuterMinRadius", "rtp", endOuterMinRadius, 0, 30000000, "Minimum radial distance from origin for outer-islands End RTP."
         );
         endOuterMaxRadius = config.getInt(
            "endOuterMaxRadius", "rtp", endOuterMaxRadius, 0, 30000000, "Maximum radial distance from origin for outer-islands End RTP."
         );
         endMainIslandRadius = config.getInt("endMainIslandRadius", "rtp", endMainIslandRadius, 16, 4096, "Radius used when endMode=main_island.");
         if (endOuterMaxRadius < endOuterMinRadius) {
            int swap = endOuterMinRadius;
            endOuterMinRadius = endOuterMaxRadius;
            endOuterMaxRadius = swap;
         }
      } finally {
         if (config.hasChanged()) {
            config.save();
         }
      }
   }

   public static boolean isEndOuterIslandsMode() {
      return !"main_island".equalsIgnoreCase(endMode);
   }
}
