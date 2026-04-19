package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

public class GrieferBlastProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         double offset = 0.0;
         if (entity.m_20069_()) {
            entity.m_6469_(new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268722_)), 3.0F);
         }

         if (world.m_6106_().m_6533_() && world.m_46861_(BlockPos.m_274561_(x, y, z))) {
            entity.m_6469_(new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268722_)), 1.0F);
         }

         if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_) && Math.random() <= 0.002) {
            AshfallProcedure.execute(world, x, y, z);
         }

         if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_) && Math.random() <= 3.0E-4) {
            offset = Mth.m_216271_(RandomSource.m_216327_(), 0, 3);
            if (offset == 0.0 && world.m_46859_(BlockPos.m_274561_(x + 1.0, y, z))) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            } else if (offset == 1.0 && world.m_46859_(BlockPos.m_274561_(x - 1.0, y, z))) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            } else if (offset == 2.0 && world.m_46859_(BlockPos.m_274561_(x, y, z - 1.0))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            } else if (offset == 3.0 && world.m_46859_(BlockPos.m_274561_(x, y, z + 1.0))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         }
      }
   }
}
