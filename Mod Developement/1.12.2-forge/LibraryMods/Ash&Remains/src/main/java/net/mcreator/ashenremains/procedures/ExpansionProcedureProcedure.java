package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class ExpansionProcedureProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      double Xvalue = 0.0;
      double ZValue = 0.0;
      double Yvalue = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double Plants = 0.0;
      double Regrowth = 0.0;
      if (Math.random() < 0.3) {
         Plants = 0.0;
         found = false;

         for (int index0 = 0; index0 < 8; index0++) {
            sy = -4.0;

            for (int index1 = 0; index1 < 8; index1++) {
               sz = -4.0;

               for (int index2 = 0; index2 < 8; index2++) {
                  if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50723_) {
                     found = true;
                  } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))) {
                     Plants += 0.07;
                  }

                  sz++;
               }

               sy++;
            }

            sx++;
         }

         if (Math.random() < 1.0 - Plants && !found) {
            sx = -4.0;
            if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_50016_
               && world.m_8055_(BlockPos.m_274561_(x, y + 2.0, z)).m_60734_() == Blocks.f_50016_) {
               if (world.m_6106_().m_5470_().m_46207_(AshenremainsModGameRules.DOPLANTGROWTH)) {
                  GrowthProcedureProcedure.execute(world, x, y + 1.0, z);
               }
            } else {
               if (Math.random() < 0.5) {
                  Xvalue = x + Mth.m_216271_(RandomSource.m_216327_(), 2, 6);
               } else {
                  Xvalue = x - Mth.m_216271_(RandomSource.m_216327_(), 2, 6);
               }

               if (Math.random() < 0.5) {
                  ZValue = z + Mth.m_216271_(RandomSource.m_216327_(), 2, 6);
               } else {
                  ZValue = z - Mth.m_216271_(RandomSource.m_216327_(), 2, 6);
               }

               Yvalue = world.m_6924_(Types.MOTION_BLOCKING_NO_LEAVES, (int)Xvalue, (int)ZValue) - 1;
               if (world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue, ZValue)).m_60734_() == Blocks.f_50440_
                  && world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue + 1.0, ZValue)).m_60734_() == Blocks.f_50016_
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue + 1.0, Yvalue + 1.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue - 1.0, Yvalue + 1.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue + 1.0, ZValue - 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue + 1.0, ZValue + 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue + 1.0, Yvalue + 0.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue - 1.0, Yvalue + 0.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue + 0.0, ZValue - 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue + 0.0, ZValue + 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue + 1.0, Yvalue - 1.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue - 1.0, Yvalue - 1.0, ZValue))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue - 1.0, ZValue - 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))
                  && !world.m_8055_(BlockPos.m_274561_(Xvalue, Yvalue - 1.0, ZValue + 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:growth_preventing")))) {
                  world.m_7731_(BlockPos.m_274561_(Xvalue, Yvalue, ZValue), ((Block)AshenremainsModBlocks.FLOWERING_GRASS.get()).m_49966_(), 3);
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50440_.m_49966_(), 3);
               }
            }
         }
      }
   }
}
