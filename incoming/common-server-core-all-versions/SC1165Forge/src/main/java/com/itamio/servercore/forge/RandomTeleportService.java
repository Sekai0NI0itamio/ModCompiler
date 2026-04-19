package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.MagmaBlock;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraft.world.server.ServerWorld;

public final class RandomTeleportService {
   public static final int SQUARE_RADIUS = 10000;
   private static final RandomTeleportService INSTANCE = new RandomTeleportService();
   private final Random random = new Random();

   private RandomTeleportService() {
   }

   public static RandomTeleportService getInstance() {
      return INSTANCE;
   }

   public RandomTeleportService.RtpResult teleport(ServerPlayerEntity player, String dimensionKey) {
      if (player != null && player.func_184102_h() != null) {
         MinecraftServer server = player.func_184102_h();
         ServerWorld world = TeleportUtil.resolveWorld(server, dimensionKey);
         if (world == null) {
            return RandomTeleportService.RtpResult.failure("Target dimension is not loaded.");
         } else {
            int attempts = 30;

            for (int i = 0; i < attempts; i++) {
               BlockPos candidate = this.generateCandidate(world);
               if (candidate != null) {
                  BlockPos safe = this.findSafePosition(world, candidate.func_177958_n(), candidate.func_177952_p());
                  if (safe != null) {
                     TeleportUtil.teleport(
                        player,
                        world,
                        safe.func_177958_n() + 0.5,
                        safe.func_177956_o(),
                        safe.func_177952_p() + 0.5,
                        RotationUtil.getYaw(player),
                        RotationUtil.getPitch(player)
                     );
                     return RandomTeleportService.RtpResult.success(safe, "Teleported to random location in " + this.dimensionName(world) + ".");
                  }
               }
            }

            return RandomTeleportService.RtpResult.failure("Could not find a safe location after " + attempts + " attempts.");
         }
      } else {
         return RandomTeleportService.RtpResult.failure("Server unavailable.");
      }
   }

   private BlockPos generateCandidate(ServerWorld world) {
      WorldBorder border = world.func_175723_af();
      int minX = -10000;
      int maxX = 10000;
      int minZ = -10000;
      int maxZ = 10000;
      int borderMinX = (int)Math.ceil(border.func_177726_b());
      int borderMaxX = (int)Math.floor(border.func_177728_d());
      int borderMinZ = (int)Math.ceil(border.func_177736_c());
      int borderMaxZ = (int)Math.floor(border.func_177733_e());
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
         return new BlockPos(x, world.func_181545_F(), z);
      } else {
         return null;
      }
   }

   private int randomBetween(int min, int max) {
      return max <= min ? min : min + this.random.nextInt(max - min + 1);
   }

   private BlockPos findSafePosition(ServerWorld world, int x, int z) {
      int top = world.func_201676_a(Type.MOTION_BLOCKING_NO_LEAVES, x, z);
      int maxY = Math.min(top, this.getMaxHeight(world) - 2);
      int minY = 2;

      for (int y = maxY; y >= minY; y--) {
         BlockPos feet = new BlockPos(x, y, z);
         if (this.isSafeStandPosition(world, feet)) {
            return feet;
         }
      }

      return null;
   }

   private boolean isSafeStandPosition(ServerWorld world, BlockPos feet) {
      BlockPos head = feet.func_177984_a();
      BlockPos ground = feet.func_177977_b();
      BlockState feetState = world.func_180495_p(feet);
      BlockState headState = world.func_180495_p(head);
      BlockState groundState = world.func_180495_p(ground);
      return this.isPassable(feetState) && this.isPassable(headState) && this.isSolidGround(groundState);
   }

   private boolean isPassable(BlockState state) {
      return !state.func_204520_s().func_206888_e() ? false : state.func_196958_f();
   }

   private boolean isSolidGround(BlockState state) {
      if (!state.func_204520_s().func_206888_e()) {
         return false;
      } else {
         return state.func_196958_f() ? false : !this.isDangerous(state.func_177230_c());
      }
   }

   private boolean isDangerous(Block block) {
      return block == Blocks.field_150353_l
         || block == Blocks.field_150480_ab
         || block instanceof FireBlock
         || block instanceof CactusBlock
         || block instanceof MagmaBlock;
   }

   private String dimensionName(ServerWorld world) {
      String dimensionKey = TeleportUtil.dimensionKey(world);
      if ("minecraft:overworld".equals(dimensionKey)) {
         return "the Overworld";
      } else if ("minecraft:the_nether".equals(dimensionKey)) {
         return "the Nether";
      } else if ("minecraft:the_end".equals(dimensionKey)) {
         return "the End";
      } else {
         return dimensionKey == null ? "unknown dimension" : dimensionKey;
      }
   }

   private int getMaxHeight(ServerWorld world) {
      Integer value = this.getInt(world, "getMaxBuildHeight");
      if (value == null) {
         value = this.getInt(world, "getMaxHeight");
      }

      return value == null ? 256 : value;
   }

   private Integer getInt(Object target, String methodName) {
      if (target == null) {
         return null;
      } else {
         try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result instanceof Number ? ((Number)result).intValue() : null;
         } catch (ReflectiveOperationException var5) {
            return null;
         }
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
