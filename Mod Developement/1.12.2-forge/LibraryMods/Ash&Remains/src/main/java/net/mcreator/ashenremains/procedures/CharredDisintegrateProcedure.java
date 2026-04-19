package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public class CharredDisintegrateProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }

         if (world instanceof ServerLevel _levelx) {
            _levelx.m_8767_(ParticleTypes.f_123777_, x, y, z, 3, 0.2, 0.2, 0.2, 0.2);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _levelx) {
            if (!_levelx.m_5776_()) {
               _levelx.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _levelx.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }

         if (world instanceof ServerLevel _levelxx) {
            _levelxx.m_8767_(ParticleTypes.f_123777_, x, y, z, 3, 0.2, 0.2, 0.2, 0.2);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _levelxx) {
            if (!_levelxx.m_5776_()) {
               _levelxx.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _levelxx.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }

         if (world instanceof ServerLevel _levelxxx) {
            _levelxxx.m_8767_(ParticleTypes.f_123777_, x, y, z, 3, 0.2, 0.2, 0.2, 0.2);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _levelxxx) {
            if (!_levelxxx.m_5776_()) {
               _levelxxx.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _levelxxx.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }

         if (world instanceof ServerLevel _levelxxxx) {
            _levelxxxx.m_8767_(ParticleTypes.f_123777_, x, y, z, 3, 0.2, 0.2, 0.2, 0.2);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
         if (world instanceof Level _levelxxxx) {
            if (!_levelxxxx.m_5776_()) {
               _levelxxxx.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _levelxxxx.m_7785_(
                  x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
               );
            }
         }

         if (world instanceof ServerLevel _levelxxxxx) {
            _levelxxxxx.m_8767_(ParticleTypes.f_123777_, x, y, z, 3, 0.2, 0.2, 0.2, 0.2);
         }
      }

      if ((
            world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.CHARRED_WOOD_WOOD.get()
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.CHARRED_WOOD_LOG.get()
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.CHARRED_STRIPPED_WOOD.get()
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.CHARRED_STRIPPED_LOG.get()
         )
         && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ash_permeable")))) {
         AshfallProcedure.execute(world, x, y, z);
      }
   }
}
