package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public class AdditionalCharredSoundProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      for (int index0 = 0; index0 < 5; index0++) {
         world.m_7106_(
            ParticleTypes.f_123755_,
            x + 0.5 + Mth.m_216271_(RandomSource.m_216327_(), 0, 0),
            y + 0.5,
            z + 0.5 + Mth.m_216271_(RandomSource.m_216327_(), 0, 0),
            0.0,
            0.1,
            0.0
         );
      }

      if (world instanceof Level _level) {
         if (!_level.m_5776_()) {
            _level.m_5594_(
               null,
               BlockPos.m_274561_(x, y, z),
               (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
               SoundSource.NEUTRAL,
               2.0F,
               1.0F
            );
         } else {
            _level.m_7785_(
               x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 2.0F, 1.0F, false
            );
         }
      }

      CharredDisintegrateProcedure.execute(world, x, y, z);
   }
}
