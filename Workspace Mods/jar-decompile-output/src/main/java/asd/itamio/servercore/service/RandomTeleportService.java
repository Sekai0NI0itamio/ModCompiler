package asd.itamio.servercore.service;

import asd.itamio.servercore.config.ServerCoreConfig;
import asd.itamio.servercore.util.TeleportUtil;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockMagma;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;

public final class RandomTeleportService {
   public static final int SQUARE_RADIUS = 10000;
   private static final RandomTeleportService INSTANCE = new RandomTeleportService();
   private final Random random = new Random();

   private RandomTeleportService() {
   }

   public static RandomTeleportService getInstance() {
      return INSTANCE;
   }

   public RandomTeleportService.RtpResult teleport(EntityPlayerMP player, int targetDimension) {
      if (player != null && player.func_184102_h() != null) {
         MinecraftServer server = player.func_184102_h();
         WorldServer world = server.func_71218_a(targetDimension);
         if (world == null) {
            return RandomTeleportService.RtpResult.failure("Target dimension is not loaded.");
         } else {
            int attempts = Math.max(1, ServerCoreConfig.rtpMaxAttempts);

            for (int i = 0; i < attempts; i++) {
               BlockPos candidate = this.generateCandidate(world, targetDimension);
               if (candidate != null) {
                  world.func_72863_F().func_186025_d(candidate.func_177958_n() >> 4, candidate.func_177952_p() >> 4);
                  BlockPos safe = this.findSafePosition(world, candidate.func_177958_n(), candidate.func_177952_p());
                  if (safe != null) {
                     double x = safe.func_177958_n() + 0.5;
                     double y = safe.func_177956_o();
                     double z = safe.func_177952_p() + 0.5;
                     TeleportUtil.teleportPlayer(player, targetDimension, x, y, z, player.field_70177_z, player.field_70125_A);
                     return RandomTeleportService.RtpResult.success(
                        safe, "Teleported to random location in " + TeleportUtil.dimensionName(targetDimension) + "."
                     );
                  }
               }
            }

            return RandomTeleportService.RtpResult.failure("Could not find a safe location after " + attempts + " attempts.");
         }
      } else {
         return RandomTeleportService.RtpResult.failure("Server unavailable.");
      }
   }

   private BlockPos generateCandidate(WorldServer world, int dimension) {
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
         return new BlockPos(x, 64, z);
      } else {
         return null;
      }
   }

   private int randomBetween(int min, int max) {
      return max <= min ? min : min + this.random.nextInt(max - min + 1);
   }

   private BlockPos findSafePosition(WorldServer world, int x, int z) {
      int maxY = Math.max(2, world.func_72940_L() - 2);

      for (int y = maxY; y >= 2; y--) {
         BlockPos feet = new BlockPos(x, y, z);
         if (this.isSafeStandPosition(world, feet)) {
            return feet;
         }
      }

      return null;
   }

   private boolean isSafeStandPosition(WorldServer world, BlockPos feet) {
      BlockPos head = feet.func_177984_a();
      BlockPos ground = feet.func_177977_b();
      IBlockState feetState = world.func_180495_p(feet);
      IBlockState headState = world.func_180495_p(head);
      IBlockState groundState = world.func_180495_p(ground);
      if (!this.isPassable(feetState) || !this.isPassable(headState)) {
         return false;
      } else {
         return !this.isSolidGround(groundState) ? false : !this.isDangerous(groundState.func_177230_c());
      }
   }

   private boolean isPassable(IBlockState state) {
      if (state.func_185904_a().func_76224_d()) {
         return false;
      } else {
         return state.func_185904_a().func_76230_c() ? false : !this.isDangerous(state.func_177230_c());
      }
   }

   private boolean isSolidGround(IBlockState state) {
      if (state.func_185904_a().func_76224_d()) {
         return false;
      } else {
         return !state.func_185904_a().func_76230_c() ? false : !this.isDangerous(state.func_177230_c());
      }
   }

   private boolean isDangerous(Block block) {
      return block == Blocks.field_150353_l
         || block == Blocks.field_150356_k
         || block == Blocks.field_150480_ab
         || block instanceof BlockFire
         || block instanceof BlockCactus
         || block instanceof BlockMagma;
   }

   public static class RtpResult {
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
