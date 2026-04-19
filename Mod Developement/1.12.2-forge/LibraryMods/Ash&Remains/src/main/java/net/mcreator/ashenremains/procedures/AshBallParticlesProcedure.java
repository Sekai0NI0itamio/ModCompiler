package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

public class AshBallParticlesProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world instanceof ServerLevel _level) {
         _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x + 0.5, y + 1.0, z + 0.5, 20, 0.1, 0.1, 0.1, 1.0);
      }

      if (world instanceof ServerLevel _level) {
         _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x + 0.5, y + 1.0, z + 0.5, 20, 0.1, 0.1, 0.1, 1.0);
      }

      if (world instanceof ServerLevel _level) {
         _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x + 0.5, y + 1.0, z + 0.5, 20, 0.1, 0.1, 0.1, 1.0);
      }
   }
}
