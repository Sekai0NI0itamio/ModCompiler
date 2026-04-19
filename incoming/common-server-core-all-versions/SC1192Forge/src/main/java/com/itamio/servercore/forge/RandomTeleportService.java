package com.itamio.servercore.forge;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class RandomTeleportService {
   public static final int SQUARE_RADIUS = 10000;
   private static final RandomTeleportService INSTANCE = new RandomTeleportService();
   private final Random random = new Random();

   private RandomTeleportService() {
   }

   public static RandomTeleportService getInstance() {
      return INSTANCE;
   }

   public RandomTeleportService.RtpResult teleport(ServerPlayer player, String dimensionKey) {
      if (player != null && player.m_20194_() != null) {
         MinecraftServer server = player.m_20194_();
         ServerLevel level = TeleportUtil.resolveLevel(server, dimensionKey);
         if (level == null) {
            return RandomTeleportService.RtpResult.failure("Target dimension is not loaded.");
         } else {
            int attempts = 30;

            for (int i = 0; i < attempts; i++) {
               BlockPos candidate = this.generateCandidate(level);
               if (candidate != null) {
                  BlockPos safe = this.findSafePosition(level, candidate.m_123341_(), candidate.m_123343_());
                  if (safe != null) {
                     TeleportUtil.teleport(
                        player, level, safe.m_123341_() + 0.5, safe.m_123342_(), safe.m_123343_() + 0.5, player.m_146908_(), player.m_146909_()
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

   private BlockPos generateCandidate(ServerLevel level) {
      WorldBorder border = level.m_6857_();
      int minX = -10000;
      int maxX = 10000;
      int minZ = -10000;
      int maxZ = 10000;
      int borderMinX = (int)Math.ceil(border.m_61955_());
      int borderMaxX = (int)Math.floor(border.m_61957_());
      int borderMinZ = (int)Math.ceil(border.m_61956_());
      int borderMaxZ = (int)Math.floor(border.m_61958_());
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
         return new BlockPos(x, level.m_5736_(), z);
      } else {
         return null;
      }
   }

   private int randomBetween(int min, int max) {
      return max <= min ? min : min + this.random.nextInt(max - min + 1);
   }

   private BlockPos findSafePosition(ServerLevel level, int x, int z) {
      int top = level.m_6924_(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      int maxY = Math.min(top, level.m_151558_() - 2);
      int minY = level.m_141937_() + 1;

      for (int y = maxY; y >= minY; y--) {
         BlockPos feet = new BlockPos(x, y, z);
         if (this.isSafeStandPosition(level, feet)) {
            return feet;
         }
      }

      return null;
   }

   private boolean isSafeStandPosition(ServerLevel level, BlockPos feet) {
      BlockPos head = feet.m_7494_();
      BlockPos ground = feet.m_7495_();
      BlockState feetState = level.m_8055_(feet);
      BlockState headState = level.m_8055_(head);
      BlockState groundState = level.m_8055_(ground);
      return this.isPassable(feetState) && this.isPassable(headState) && this.isSolidGround(groundState);
   }

   private boolean isPassable(BlockState state) {
      return !state.m_60819_().m_76178_() ? false : state.m_60795_();
   }

   private boolean isSolidGround(BlockState state) {
      if (!state.m_60819_().m_76178_()) {
         return false;
      } else {
         return state.m_60795_() ? false : !this.isDangerous(state.m_60734_());
      }
   }

   private boolean isDangerous(Block block) {
      return block == Blocks.f_49991_ || block == Blocks.f_50083_ || block instanceof FireBlock || block instanceof CactusBlock || block instanceof MagmaBlock;
   }

   private String dimensionName(ServerLevel level) {
      if (level.m_46472_().equals(Level.f_46428_)) {
         return "the Overworld";
      } else if (level.m_46472_().equals(Level.f_46429_)) {
         return "the Nether";
      } else {
         return level.m_46472_().equals(Level.f_46430_) ? "the End" : level.m_46472_().m_135782_().toString();
      }
   }

   public static final class RtpResult {
      private final boolean success;
      private final BlockPos location;
      private final String message;

      private RtpResult(boolean success, BlockPos location, String message) {
         this.success = success;
         this.location = location;
         this.message = message;
      }

      public static RandomTeleportService.RtpResult success(BlockPos location, String message) {
         return new RandomTeleportService.RtpResult(true, location, message);
      }

      public static RandomTeleportService.RtpResult failure(String message) {
         return new RandomTeleportService.RtpResult(false, null, message);
      }

      public boolean isSuccess() {
         return this.success;
      }

      public BlockPos getLocation() {
         return this.location;
      }

      public String getMessage() {
         return this.message;
      }
   }
}
