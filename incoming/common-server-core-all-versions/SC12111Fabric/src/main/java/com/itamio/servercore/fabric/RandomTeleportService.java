package com.itamio.servercore.fabric;

import java.util.Random;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2266;
import net.minecraft.class_2338;
import net.minecraft.class_2358;
import net.minecraft.class_2413;
import net.minecraft.class_2680;
import net.minecraft.class_2784;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_2902.class_2903;
import net.minecraft.server.MinecraftServer;

public final class RandomTeleportService {
   public static final int SQUARE_RADIUS = 10000;
   private static final RandomTeleportService INSTANCE = new RandomTeleportService();
   private final Random random = new Random();

   private RandomTeleportService() {
   }

   public static RandomTeleportService getInstance() {
      return INSTANCE;
   }

   public RandomTeleportService.RtpResult teleport(class_3222 player, String dimensionKey) {
      MinecraftServer server = ServerCoreAccess.getServer(player);
      if (player != null && server != null) {
         class_3218 level = TeleportUtil.resolveLevel(server, dimensionKey);
         if (level == null) {
            return RandomTeleportService.RtpResult.failure("Target dimension is not loaded.");
         } else {
            int attempts = 30;

            for (int i = 0; i < attempts; i++) {
               class_2338 candidate = this.generateCandidate(level);
               if (candidate != null) {
                  class_2338 safe = this.findSafePosition(level, candidate.method_10263(), candidate.method_10260());
                  if (safe != null) {
                     TeleportUtil.teleport(
                        player, level, safe.method_10263() + 0.5, safe.method_10264(), safe.method_10260() + 0.5, player.method_36454(), player.method_36455()
                     );
                     return RandomTeleportService.RtpResult.success(safe, "Teleported to random location in " + this.dimensionName(level) + ".");
                  }
               }
            }

            return RandomTeleportService.RtpResult.failure("Could not find a safe location after " + attempts + " attempts.");
         }
      } else {
         return RandomTeleportService.RtpResult.failure("Server unavailable.");
      }
   }

   private class_2338 generateCandidate(class_3218 level) {
      class_2784 border = level.method_8621();
      int minX = -10000;
      int maxX = 10000;
      int minZ = -10000;
      int maxZ = 10000;
      int borderMinX = (int)Math.ceil(border.method_11976());
      int borderMaxX = (int)Math.floor(border.method_11963());
      int borderMinZ = (int)Math.ceil(border.method_11958());
      int borderMaxZ = (int)Math.floor(border.method_11977());
      if (borderMinX > minX) {
         minX = borderMinX;
      }

      if (borderMaxX < maxX) {
         maxX = borderMaxX;
      }

      if (borderMinZ > minZ) {
         minZ = borderMinZ;
      }

      if (borderMaxZ < maxZ) {
         maxZ = borderMaxZ;
      }

      if (minX <= maxX && minZ <= maxZ) {
         int x = this.randomBetween(minX, maxX);
         int z = this.randomBetween(minZ, maxZ);
         return new class_2338(x, level.method_8615(), z);
      } else {
         return null;
      }
   }

   private int randomBetween(int min, int max) {
      return max <= min ? min : min + this.random.nextInt(max - min + 1);
   }

   private class_2338 findSafePosition(class_3218 level, int x, int z) {
      int top = level.method_8624(class_2903.field_13203, x, z);
      int maxY = Math.min(top, ServerCoreAccess.getMaxBuildHeight(level) - 2);
      int minY = ServerCoreAccess.getMinBuildHeight(level) + 1;

      for (int y = maxY; y >= minY; y--) {
         class_2338 feet = new class_2338(x, y, z);
         if (this.isSafeStandPosition(level, feet)) {
            return feet;
         }
      }

      return null;
   }

   private boolean isSafeStandPosition(class_3218 level, class_2338 feet) {
      class_2338 head = feet.method_10084();
      class_2338 ground = feet.method_10074();
      class_2680 feetState = level.method_8320(feet);
      class_2680 headState = level.method_8320(head);
      class_2680 groundState = level.method_8320(ground);
      return this.isPassable(feetState) && this.isPassable(headState) && this.isSolidGround(groundState);
   }

   private boolean isPassable(class_2680 state) {
      return !state.method_26227().method_15769() ? false : state.method_26215();
   }

   private boolean isSolidGround(class_2680 state) {
      if (!state.method_26227().method_15769()) {
         return false;
      } else {
         return state.method_26215() ? false : !this.isDangerous(state.method_26204());
      }
   }

   private boolean isDangerous(class_2248 block) {
      return block == class_2246.field_10164
         || block == class_2246.field_10036
         || block instanceof class_2358
         || block instanceof class_2266
         || block instanceof class_2413;
   }

   private String dimensionName(class_3218 level) {
      if (level.method_27983().equals(class_1937.field_25179)) {
         return "the Overworld";
      } else if (level.method_27983().equals(class_1937.field_25180)) {
         return "the Nether";
      } else if (level.method_27983().equals(class_1937.field_25181)) {
         return "the End";
      } else {
         String key = TeleportUtil.dimensionKey(level);
         return key == null ? "unknown" : key;
      }
   }

   public static final class RtpResult {
      private final boolean success;
      private final class_2338 location;
      private final String message;

      private RtpResult(boolean success, class_2338 location, String message) {
         this.success = success;
         this.location = location;
         this.message = message;
      }

      public static RandomTeleportService.RtpResult success(class_2338 location, String message) {
         return new RandomTeleportService.RtpResult(true, location, message);
      }

      public static RandomTeleportService.RtpResult failure(String message) {
         return new RandomTeleportService.RtpResult(false, null, message);
      }

      public boolean isSuccess() {
         return this.success;
      }

      public class_2338 getLocation() {
         return this.location;
      }

      public String getMessage() {
         return this.message;
      }
   }
}
