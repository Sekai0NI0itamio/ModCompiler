package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class FlamingAshUpdateTickProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      double sidecheck = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      sx = -3.0;
      found = false;

      for (int index0 = 0; index0 < 6; index0++) {
         sy = -3.0;

         for (int index1 = 0; index1 < 6; index1++) {
            sz = -3.0;

            for (int index2 = 0; index2 < 6; index2++) {
               if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")))
                  || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_49991_
                  || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_49991_) {
                  found = true;
               }

               sz++;
            }

            sy++;
         }

         sx++;
      }

      if (!world.m_6106_().m_6533_() && found) {
         if (world.m_6106_().m_5470_().m_46207_(AshenremainsModGameRules.DOFIRESPREAD)) {
            if (Math.random() < 0.3) {
               if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))) {
                  FieryTransformationProcedure.execute(world, x, y - 1.0, z);
               } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))) {
                  if (Math.random() < 0.6) {
                     FieryTransformationProcedure.execute(world, x, y - 1.0, z);
                  } else {
                     IgniteBlockProcedure.execute(world, x, y - 1.0, z);
                  }
               } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
                  AshfallProcedure.execute(world, x, y - 1.0, z);
               }
            }

            if (Math.random() < 0.6
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y + 1.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y + 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y + 2.0, z);
            }

            if (Math.random() < 0.4
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y + 3.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 3.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 3.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y + 3.0, z);
            }

            if (Math.random() < 0.2
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y + 4.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 4.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 4.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y + 4.0, z);
            }

            if (Math.random() < 0.1
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y + 5.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 5.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y + 5.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y + 5.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x + 1.0, y, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x - 1.0, y, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y, z - 1.0);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y, z + 1.0);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x + 1.0, y - 1.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x - 1.0, y - 1.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y - 1.0, z - 1.0);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y - 1.0, z + 1.0);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x + 1.0, y - 2.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x - 1.0, y - 2.0, z);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y - 2.0, z - 1.0);
            }

            if (Math.random() < 0.5
               && (
                  world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               )) {
               IgniteBlockProcedure.execute(world, x, y - 2.0, z + 1.0);
            }
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() != Blocks.f_50450_) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_14.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_4.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_2.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50440_
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.FLOWERING_GRASS.get()
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50599_
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50195_
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_152481_) {
         world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.ASHY_GRASS.get()).m_49966_(), 3);
      }
   }
}
