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
      MinecraftServer server = ServerCoreAccess.getServer(player);
      if (player != null && server != null) {
         ServerLevel level = TeleportUtil.resolveLevel(server, dimensionKey);
         if (level == null) {
            return RandomTeleportService.RtpResult.failure("Target dimension is not loaded.");
         } else {
            int attempts = 30;

            for (int i = 0; i < attempts; i++) {
               BlockPos candidate = this.generateCandidate(level);
               if (candidate != null) {
                  BlockPos safe = this.findSafePosition(level, candidate.getX(), candidate.getZ());
                  if (safe != null) {
                     TeleportUtil.teleport(player, level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
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
      WorldBorder border = level.getWorldBorder();
      int minX = -10000;
      int maxX = 10000;
      int minZ = -10000;
      int maxZ = 10000;
      int borderMinX = (int)Math.ceil(border.getMinX());
      int borderMaxX = (int)Math.floor(border.getMaxX());
      int borderMinZ = (int)Math.ceil(border.getMinZ());
      int borderMaxZ = (int)Math.floor(border.getMaxZ());
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
         return new BlockPos(x, level.getSeaLevel(), z);
      } else {
         return null;
      }
   }

   private int randomBetween(int min, int max) {
      return max <= min ? min : min + this.random.nextInt(max - min + 1);
   }

   private BlockPos findSafePosition(ServerLevel level, int x, int z) {
      int top = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      int maxY = Math.min(top, ServerCoreAccess.getMaxBuildHeight(level) - 2);
      int minY = ServerCoreAccess.getMinBuildHeight(level) + 1;

      for (int y = maxY; y >= minY; y--) {
         BlockPos feet = new BlockPos(x, y, z);
         if (this.isSafeStandPosition(level, feet)) {
            return feet;
         }
      }

      return null;
   }

   private boolean isSafeStandPosition(ServerLevel level, BlockPos feet) {
      BlockPos head = feet.above();
      BlockPos ground = feet.below();
      BlockState feetState = level.getBlockState(feet);
      BlockState headState = level.getBlockState(head);
      BlockState groundState = level.getBlockState(ground);
      return this.isPassable(feetState) && this.isPassable(headState) && this.isSolidGround(groundState);
   }

   private boolean isPassable(BlockState state) {
      return !state.getFluidState().isEmpty() ? false : state.isAir();
   }

   private boolean isSolidGround(BlockState state) {
      if (!state.getFluidState().isEmpty()) {
         return false;
      } else {
         return state.isAir() ? false : !this.isDangerous(state.getBlock());
      }
   }

   private boolean isDangerous(Block block) {
      return block == Blocks.LAVA || block == Blocks.FIRE || block instanceof FireBlock || block instanceof CactusBlock || block instanceof MagmaBlock;
   }

   private String dimensionName(ServerLevel level) {
      if (level.dimension().equals(Level.OVERWORLD)) {
         return "the Overworld";
      } else if (level.dimension().equals(Level.NETHER)) {
         return "the Nether";
      } else {
         return level.dimension().equals(Level.END) ? "the End" : level.dimension().location().toString();
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
