package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

public class GriefersAreCowardsProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         double BlindnessDuration = 0.0;
         if (!entity.m_20071_()) {
            if (world instanceof ServerLevel _level) {
               _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x, y, z, 24, 0.2, 0.2, 0.2, 0.3);
            }

            GrieferBlindnessProcedure.execute(world, x, y, z);
            if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }

            AshenremainsMod.queueServerWork(
               20,
               () -> {
                  if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
                  }
               }
            );
         }
      }
   }
}
