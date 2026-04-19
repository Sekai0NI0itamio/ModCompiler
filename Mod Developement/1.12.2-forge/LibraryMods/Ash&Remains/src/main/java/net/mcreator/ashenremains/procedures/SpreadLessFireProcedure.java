package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;

public class SpreadLessFireProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double sx = 0.0;
      double cx = 0.0;
      double sy = 0.0;
      double cy = 0.0;
      double sz = 0.0;
      double cz = 0.0;
      double frequency = 0.0;
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")))
         && !world.m_6106_().m_6533_()
         && world.m_6106_().m_5470_().m_46207_(AshenremainsModGameRules.DOFIRESPREAD)) {
         frequency = 0.05;
         if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("desert"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("wooded_badlands"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("eroded_badlands"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("badlands"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("savanna"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_savanna"))) {
            if ((world instanceof Level _lvl ? _lvl.m_46472_() : (world instanceof WorldGenLevel _wgl ? _wgl.m_6018_().m_46472_() : Level.f_46428_))
               == Level.f_46429_) {
               frequency += 0.2;
            } else if (world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z)) && world.m_6106_().m_6533_()) {
               frequency -= 0.025;
            } else if (world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("mangrove_swamp"))
               || world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("jungle"))
               || world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("lush_caves"))) {
               frequency -= 0.025;
            } else if (world.m_46791_() == Difficulty.HARD) {
               frequency += 0.1;
            }
         } else {
            frequency += 0.1;
         }

         sx = -2.0;

         for (int index0 = 0; index0 < 3; index0++) {
            sy = -2.0;

            for (int index1 = 0; index1 < 3; index1++) {
               sz = -2.0;

               for (int index2 = 0; index2 < 3; index2++) {
                  if ((
                        world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     )
                     && Math.random() < frequency) {
                     IgniteBlockProcedure.execute(world, x + sx, y + sy, z + sz);
                  } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz))
                        .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_fire_ignore")))
                     && Math.random() < frequency) {
                     FieryTransformationProcedure.execute(world, x + sx, y + sy, z + sz);
                  } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz))
                     .m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
                     IgniteBlockProcedure.execute(world, x + sx, y + sy, z + sz);
                  }

                  sz++;
               }

               sy++;
            }

            sx++;
         }

         sx = -1.0;
      }
   }
}
