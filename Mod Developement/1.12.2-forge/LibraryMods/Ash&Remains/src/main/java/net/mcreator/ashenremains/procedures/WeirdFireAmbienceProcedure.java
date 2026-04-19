package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public class WeirdFireAmbienceProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (Math.random() < 0.1 && world instanceof Level _level) {
         if (!_level.m_5776_()) {
            _level.m_5594_(
               null,
               BlockPos.m_274561_(x, y, z),
               (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")),
               SoundSource.NEUTRAL,
               1.0F,
               1.0F
            );
         } else {
            _level.m_7785_(
               x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
            );
         }
      }

      if (world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))) {
         world.m_7106_(ParticleTypes.f_123755_, x + 0.5, y, z + 0.5, 0.0, 0.2, 0.0);
      }
   }
}
