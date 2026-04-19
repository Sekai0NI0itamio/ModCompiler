package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AshRainDissipateProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      boolean path = false;
      boolean pathfound = false;
      double Layerdecrease = 0.0;
      double Flowerchance = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double uy = 0.0;
      if (world.m_6106_().m_6533_() && world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z))) {
         Layerdecrease = Mth.m_216271_(RandomSource.m_216327_(), 1, 2);
         Flowerchance = Mth.m_216271_(RandomSource.m_216327_(), 1, 16);
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_14.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_14.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_12.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_10.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_8.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_6.get()) {
            if (Layerdecrease == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_4.get()) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_2.get()) {
            world.m_46961_(BlockPos.m_274561_(x, y, z), false);
            if (Flowerchance == 1.0 && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50493_) {
               world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.FLOWERING_GRASS.get()).m_49966_(), 3);
            }
         }
      } else {
         if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.FLOWERING_GRASS.get()
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50440_
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50599_
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50195_
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_152481_) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.ASHY_GRASS.get()).m_49966_(), 3);
         }

         found = false;
         sx = -3.0;

         for (int index0 = 0; index0 < 6; index0++) {
            sy = -3.0;

            for (int index1 = 0; index1 < 6; index1++) {
               sz = -3.0;

               for (int index2 = 0; index2 < 6; index2++) {
                  if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")))) {
                     found = true;
                  }

                  sz++;
               }

               sy++;
            }

            sx++;
         }

         if (found || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50450_) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_14.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_12.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_10.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_8.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_6.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_4.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_4.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_2.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_2.get()).m_49966_(), 3);
            }
         }
      }
   }
}
