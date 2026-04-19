package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;

public class AshBallTrailProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world instanceof ServerLevel _level) {
         _level.m_8767_(
            (SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(),
            x + 0.3,
            y,
            z + 0.3,
            Mth.m_216271_(RandomSource.m_216327_(), 0, 1),
            0.2,
            0.2,
            0.2,
            1.0
         );
      }
   }
}
