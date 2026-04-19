package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;

public class GrieferDeathProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double offset = 0.0;

      for (int index0 = 0; index0 < Mth.m_216271_(RandomSource.m_216327_(), 1, 3); index0++) {
         AshfallProcedure.execute(world, x, y, z);
      }

      for (int index1 = 0; index1 < Mth.m_216271_(RandomSource.m_216327_(), 1, 4); index1++) {
         offset = Mth.m_216271_(RandomSource.m_216327_(), 0, 3);
         if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))) {
            IgniteBlockProcedure.execute(world, x, y - 1.0, z);
         }
      }
   }
}
