package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WitherExplosionTweaksProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_)
         && !world.m_6443_(WitherSkull.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()) {
         for (int index0 = 0; index0 < 32; index0++) {
            sx = Mth.m_216271_(RandomSource.m_216327_(), -3, 3);
            sy = Mth.m_216271_(RandomSource.m_216327_(), 0, 3);
            sz = Mth.m_216271_(RandomSource.m_216327_(), -3, 3);
            if (world.m_8055_(BlockPos.m_274561_(x + sx, y - sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:ghast_replaceable")))) {
               if (Math.random() < 0.05) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), ((Block)AshenremainsModBlocks.ACTIVATED_SOUL_SAND.get()).m_49966_(), 3);
               } else if (Math.random() < 0.75) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50135_.m_49966_(), 3);
                  if (Math.random() < 0.75
                     && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))
                     && world.m_8055_(BlockPos.m_274561_(x + sx, y - sy, z + sz)).m_60815_()) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), Blocks.f_50084_.m_49966_(), 3);
                  }
               } else {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50136_.m_49966_(), 3);
                  if (Math.random() < 0.75
                     && Math.random() < 0.75
                     && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))
                     && world.m_8055_(BlockPos.m_274561_(x + sx, y - sy, z + sz)).m_60815_()) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), Blocks.f_50084_.m_49966_(), 3);
                  }
               }
            }
         }
      }
   }
}
