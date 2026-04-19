package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public class FireAdjacentStuffProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean success = false;
      success = false;
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.WEIRD_FIRE.get()
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()
            && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
            && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.EAST_FIRE.get()
            || !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
               && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60815_()
               && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
               && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.WEST_FIRE.get()
               || !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
                  && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60815_()
                  && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
                  && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_) {
               if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.NORTH_FIRE.get()
                  || !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
                     && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60815_()
                     && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
                     && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_) {
                  if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.SOUTH_FIRE.get()
                     || !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
                        && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60815_()
                        && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_
                        && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() != Blocks.f_49990_) {
                     if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.STRANGE_FIRE.get()
                        && (
                           !world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60815_()
                              || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_
                              || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_
                        )) {
                        success = true;
                     }
                  } else {
                     success = true;
                  }
               } else {
                  success = true;
               }
            } else {
               success = true;
            }
         } else {
            success = true;
         }
      } else {
         success = true;
      }

      if (success) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F,
                  false
               );
            }
         }

         for (int index0 = 0; index0 < Mth.m_216271_(RandomSource.m_216327_(), 5, 10); index0++) {
            world.m_7106_(ParticleTypes.f_123762_, x, y, z, 0.0, 1.0, 0.0);
         }
      }
   }
}
