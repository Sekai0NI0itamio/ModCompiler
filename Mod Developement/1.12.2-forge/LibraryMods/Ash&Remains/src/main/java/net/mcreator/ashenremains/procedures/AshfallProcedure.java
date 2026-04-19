package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class AshfallProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double FallSurface = 0.0;
      double Quantity = 0.0;
      boolean Surfacefound = false;
      boolean Blocked = false;
      if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         AshenremainsMod.queueServerWork(1, () -> execute(world, x, y + 1.0, z));
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()) {
         Quantity = Mth.m_216271_(RandomSource.m_216327_(), 4, 5);
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_14.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_12.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_10.get()
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()) {
         Quantity = Mth.m_216271_(RandomSource.m_216327_(), 3, 4);
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.ASH_LAYER_8.get()
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.ASH_LAYER_6.get()
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()) {
         Quantity = 1.0;
      } else {
         Quantity = 2.0;
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:leaves")))) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
      } else {
         world.m_46961_(BlockPos.m_274561_(x, y, z), false);
      }

      Blocked = false;

      while (!Blocked && !Surfacefound && FallSurface <= 32.0) {
         if (world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ash_permeable")))) {
            FallSurface++;
            if (world instanceof ServerLevel _level) {
               _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x + 0.5, y - FallSurface, z + 0.5, 2, 0.2, 0.2, 0.2, 0.1);
            }
         } else if (!world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z)).m_60783_(world, BlockPos.m_274561_(x, y - FallSurface, z), Direction.UP)
               && world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z)).m_60734_() != AshenremainsModBlocks.ASH.get()
               && world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH.get()
            || !world.m_8055_(BlockPos.m_274561_(x, y - FallSurface + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
               && !world.m_8055_(BlockPos.m_274561_(x, y - FallSurface + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))
               && !world.m_46859_(BlockPos.m_274561_(x, y - FallSurface + 1.0, z))) {
            Blocked = true;
         } else {
            Surfacefound = true;
         }
      }

      FallSurface--;
      if (Surfacefound) {
         for (int index1 = 0; index1 < (int)Quantity; index1++) {
            AshAccumulateProcedure.execute(world, x, y - FallSurface, z);
            if (Quantity > 1.0) {
               if (Math.random() < 0.3
                  && (
                     world.m_46859_(BlockPos.m_274561_(x - 1.0, y - FallSurface, z))
                        || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - FallSurface, z))
                           .m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))
                  )) {
                  AshAccumulateProcedure.execute(world, x - 1.0, y - FallSurface, z);
               }

               if (Math.random() < 0.3
                  && (
                     world.m_46859_(BlockPos.m_274561_(x + 1.0, y - FallSurface, z))
                        || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - FallSurface, z))
                           .m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))
                  )) {
                  AshAccumulateProcedure.execute(world, x + 1.0, y - FallSurface, z);
               }

               if (Math.random() < 0.3
                  && (
                     world.m_46859_(BlockPos.m_274561_(x, y - FallSurface, z + 1.0))
                        || world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z + 1.0))
                           .m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))
                  )) {
                  AshAccumulateProcedure.execute(world, x, y - FallSurface, z + 1.0);
               }

               if (Math.random() < 0.3
                  && (
                     world.m_46859_(BlockPos.m_274561_(x, y - FallSurface, z - 1.0))
                        || world.m_8055_(BlockPos.m_274561_(x, y - FallSurface, z - 1.0))
                           .m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))
                  )) {
                  AshAccumulateProcedure.execute(world, x, y - FallSurface, z - 1.0);
               }
            }
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
         world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50016_.m_49966_(), 3);
      }
   }
}
