package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class ActivatedAmbienceProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world instanceof Level _level) {
         if (!_level.m_5776_()) {
            _level.m_5594_(
               null,
               BlockPos.m_274561_(x, y, z),
               (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.beacon.ambient")),
               SoundSource.NEUTRAL,
               1.0F,
               1.0F
            );
         } else {
            _level.m_7785_(
               x, y, z, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.beacon.ambient")), SoundSource.NEUTRAL, 1.0F, 1.0F, false
            );
         }
      }

      if ((
            !world.m_6443_(Player.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()
               || !world.m_6443_(WitherBoss.class, AABB.m_165882_(new Vec3(x, y, z), 14.0, 14.0, 14.0), e -> true).isEmpty()
         )
         && world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))) {
         if (Math.random() < 0.008) {
            if (world instanceof ServerLevel _levelx) {
               Entity entityToSpawn = EntityType.f_20497_.m_262496_(_levelx, BlockPos.m_274561_(x, y + 1.0, z), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }

            for (int index0 = 0; index0 < 6; index0++) {
               world.m_7106_(ParticleTypes.f_123746_, x, y + 1.0, z, 0.0, 1.0, 0.0);
            }

            if (world instanceof Level _levelxx) {
               if (!_levelxx.m_5776_()) {
                  _levelxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         }
      } else {
         AshenremainsMod.queueServerWork(
            800,
            () -> {
               if ((
                     world.m_6443_(Player.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()
                           && world.m_6443_(WitherBoss.class, AABB.m_165882_(new Vec3(x, y, z), 14.0, 14.0, 14.0), e -> true).isEmpty()
                        || !world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
                  )
                  && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != Blocks.f_50312_) {
                  if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ACTIVATED_SOUL_SAND.get()) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50135_.m_49966_(), 3);
                  } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ACTIVATED_SOUL_SOIL.get()) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50136_.m_49966_(), 3);
                  }
               }
            }
         );
      }
   }
}
