package net.mcreator.ashenremains.procedures;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;

public class AshBallBlindnessChanceProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, Entity sourceentity) {
      if (entity != null && sourceentity != null) {
         if (entity != sourceentity) {
            if (entity instanceof LivingEntity _entity && !_entity.m_9236_().m_5776_()) {
               _entity.m_7292_(new MobEffectInstance(MobEffects.f_19610_, Mth.m_216271_(RandomSource.m_216327_(), 60, 240), 0, false, false));
            }

            AshBallParticlesProcedure.execute(world, x, y, z);
         }
      }
   }
}
