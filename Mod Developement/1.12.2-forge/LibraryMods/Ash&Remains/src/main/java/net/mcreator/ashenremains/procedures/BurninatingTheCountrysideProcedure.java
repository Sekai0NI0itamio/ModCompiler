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

public class BurninatingTheCountrysideProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      double cx = 0.0;
      double cz = 0.0;
      double cy = 0.0;
      double frequency = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double Fire = 0.0;
      FireRainExtinguishProcedure.execute(world, x, y, z);
      Fire = 0.0;
      sx = -5.0;
      found = false;

      for (int index0 = 0; index0 < 10; index0++) {
         sy = -5.0;

         for (int index1 = 0; index1 < 10; index1++) {
            sz = -5.0;

            for (int index2 = 0; index2 < 10; index2++) {
               if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_152540_) {
                  found = true;
               } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")))) {
                  Fire++;
               }

               sz++;
            }

            sy++;
         }

         sx++;
      }

      if (found) {
         world.m_46961_(BlockPos.m_274561_(x, y, z), false);

         for (int index3 = 0; index3 < Mth.m_216271_(RandomSource.m_216327_(), 6, 10); index3++) {
            world.m_7106_(ParticleTypes.f_175833_, x, y, z, 0.2, 0.3, 0.2);
         }

         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.break")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.break")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }
      } else {
         if ((
               world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
            )
            && !world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))) {
            IgniteBlockProcedure.execute(world, x, y + 1.0, z);
         }

         if (Fire <= 14.0) {
            SpreadFireProcedure.execute(world, x, y, z);
         } else {
            SpreadLessFireProcedure.execute(world, x, y, z);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.WEIRD_FIRE.get()) {
            cx = x;
            cy = y - 1.0;
            cz = z;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.EAST_FIRE.get()) {
            cx = x - 1.0;
            cy = y;
            cz = z;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.WEST_FIRE.get()) {
            cx = x + 1.0;
            cy = y;
            cz = z;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.NORTH_FIRE.get()) {
            cx = x;
            cy = y;
            cz = z + 1.0;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.SOUTH_FIRE.get()) {
            cx = x;
            cy = y;
            cz = z - 1.0;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.STRANGE_FIRE.get()) {
            cx = x;
            cy = y + 1.0;
            cz = z;
         }

         if ((
               world.m_8055_(BlockPos.m_274561_(cx, cy + 1.0, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(cx, cy + 1.0, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                  || world.m_8055_(BlockPos.m_274561_(cx, cy + 1.0, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(cx, cy + 1.0, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
            )
            && !world.m_46859_(BlockPos.m_274561_(cx, cy + 1.0, cz))) {
            IgniteBlockProcedure.execute(world, cx, cy + 1.0, cz);
         }

         if (world.m_8055_(BlockPos.m_274561_(cx, cy, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))) {
            FieryTransformationProcedure.execute(world, cx, cy, cz);
         } else if (!world.m_8055_(BlockPos.m_274561_(cx, cy, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
            && !world.m_8055_(BlockPos.m_274561_(cx, cy, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))) {
            if (world.m_8055_(BlockPos.m_274561_(cx, cy, cz)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               SpreadFireProcedure.execute(world, x, y, z);
               AshfallProcedure.execute(world, cx, cy, cz);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
               if (world instanceof Level _levelx) {
                  if (!_levelx.m_5776_()) {
                     _levelx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                        SoundSource.BLOCKS,
                        0.6F,
                        1.0F
                     );
                  } else {
                     _levelx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                        SoundSource.BLOCKS,
                        0.6F,
                        1.0F,
                        false
                     );
                  }
               }
            }
         } else if (Math.random() < 0.3 + frequency) {
            FieryTransformationProcedure.execute(world, cx, cy, cz);
         } else if (Math.random() < 0.5) {
            if (Fire <= 14.0) {
               SpreadFireProcedure.execute(world, x, y, z);
            } else {
               SpreadLessFireProcedure.execute(world, x, y, z);
            }

            AshfallProcedure.execute(world, cx, cy, cz);
         }
      }
   }
}
