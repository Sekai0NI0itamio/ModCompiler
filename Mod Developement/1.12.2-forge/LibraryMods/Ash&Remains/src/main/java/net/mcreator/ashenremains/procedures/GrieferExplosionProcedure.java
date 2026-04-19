package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;

public class GrieferExplosionProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         double xoffset = 0.0;
         double yoffset = 0.0;
         double zoffset = 0.0;
         if (!entity.m_9236_().m_5776_()) {
            entity.m_146870_();
         }

         if (world instanceof Level _level && !_level.m_5776_()) {
            _level.m_254849_(null, x, y, z, 2.0F, ExplosionInteraction.NONE);
         }

         if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_)) {
            for (int index0 = 0; index0 < Mth.m_216271_(RandomSource.m_216327_(), 12, 24); index0++) {
               xoffset = Mth.m_216271_(RandomSource.m_216327_(), -5, -5);
               yoffset = Mth.m_216271_(RandomSource.m_216327_(), -2, 4);
               xoffset = Mth.m_216271_(RandomSource.m_216327_(), -5, -5);
               if (Math.random() < 0.7
                  && world.m_46859_(BlockPos.m_274561_(xoffset, yoffset, zoffset))
                  && world.m_8055_(BlockPos.m_274561_(xoffset, yoffset - 1.0, zoffset)).m_60815_()) {
                  world.m_7731_(BlockPos.m_274561_(xoffset, yoffset, zoffset), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
               } else {
                  FieryTransformationProcedure.execute(world, xoffset, yoffset, zoffset);
               }
            }
         }
      }
   }
}
