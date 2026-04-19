package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public class AshAccumulateProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH.get()).m_49966_(), 3);
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_4.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_2.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_4.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_14.get()) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH.get()).m_49966_(), 3);
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_12.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_14.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_10.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_14.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_8.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_6.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_4.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_2.get()) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()
         && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
         if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_2.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLAMING_ASH_LAYER_4.get()).m_49966_(), 3);
         }
      } else if (world.m_46859_(BlockPos.m_274561_(x, y, z))
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
         if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ash_permeable")))) {
            AshfallProcedure.execute(world, x, y, z);
         } else if (Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
         }
      }

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
         _levelx.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x, y, z, 10, 0.2, 0.2, 0.2, 0.1);
      }
   }
}
