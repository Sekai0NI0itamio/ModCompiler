package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class VeryVeryFrighteningProcedure {
   @SubscribeEvent
   public static void onEntitySpawned(EntityJoinLevelEvent event) {
      execute(event, event.getLevel(), event.getEntity().m_20185_(), event.getEntity().m_20186_(), event.getEntity().m_20189_(), event.getEntity());
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      execute(null, world, x, y, z, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if (entity instanceof WitherBoss) {
            if (world instanceof ServerLevel _level) {
               Entity entityToSpawn = EntityType.f_20465_.m_262496_(_level, BlockPos.m_274561_(x + 6.0, y, z + 6.0), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }

            if (world instanceof ServerLevel _levelx) {
               Entity entityToSpawn = EntityType.f_20465_.m_262496_(_levelx, BlockPos.m_274561_(x - 6.0, y, z - 6.0), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }

            if (world instanceof ServerLevel _levelxx) {
               Entity entityToSpawn = EntityType.f_20465_.m_262496_(_levelxx, BlockPos.m_274561_(x + 6.0, y, z - 6.0), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }

            if (world instanceof ServerLevel _levelxxx) {
               Entity entityToSpawn = EntityType.f_20465_.m_262496_(_levelxxx, BlockPos.m_274561_(x - 6.0, y, z + 6.0), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }
         }
      }
   }
}
