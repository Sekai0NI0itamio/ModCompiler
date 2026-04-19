package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.entity.AshBallProjectileEntity;
import net.mcreator.ashenremains.init.AshenremainsModEntities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ShootAshProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Direction direction) {
      if (direction != null) {
         if (direction == Direction.DOWN) {
            if (world instanceof ServerLevel projectileLevel) {
               Projectile _entityToSpawn = (new Object() {
                     public Projectile getArrow(Level level, float damage, int knockback) {
                        AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                           (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                        );
                        entityToSpawn.m_36781_(damage);
                        entityToSpawn.m_36735_(knockback);
                        entityToSpawn.m_20225_(true);
                        return entityToSpawn;
                     }
                  })
                  .getArrow(projectileLevel, 1.0F, 1);
               _entityToSpawn.m_6034_(x + 0.5, y - 0.1, z + 0.5);
               _entityToSpawn.m_6686_(0.0, -2.0, 0.0, 0.3F, 0.1F);
               projectileLevel.m_7967_(_entityToSpawn);
            }
         } else if (direction == Direction.UP) {
            if (world instanceof ServerLevel projectileLevel) {
               Projectile _entityToSpawn = (new Object() {
                     public Projectile getArrow(Level level, float damage, int knockback) {
                        AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                           (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                        );
                        entityToSpawn.m_36781_(damage);
                        entityToSpawn.m_36735_(knockback);
                        entityToSpawn.m_20225_(true);
                        return entityToSpawn;
                     }
                  })
                  .getArrow(projectileLevel, 1.0F, 1);
               _entityToSpawn.m_6034_(x + 0.5, y + 1.1, z + 0.5);
               _entityToSpawn.m_6686_(0.0, 2.0, 0.0, 0.3F, 0.1F);
               projectileLevel.m_7967_(_entityToSpawn);
            }
         } else if (direction == Direction.NORTH) {
            if (world instanceof ServerLevel projectileLevel) {
               Projectile _entityToSpawn = (new Object() {
                     public Projectile getArrow(Level level, float damage, int knockback) {
                        AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                           (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                        );
                        entityToSpawn.m_36781_(damage);
                        entityToSpawn.m_36735_(knockback);
                        entityToSpawn.m_20225_(true);
                        return entityToSpawn;
                     }
                  })
                  .getArrow(projectileLevel, 1.0F, 1);
               _entityToSpawn.m_6034_(x + 0.5, y + 0.5, z - 0.5);
               _entityToSpawn.m_6686_(0.0, 0.0, -2.0, 0.3F, 0.1F);
               projectileLevel.m_7967_(_entityToSpawn);
            }
         } else if (direction == Direction.SOUTH) {
            if (world instanceof ServerLevel projectileLevel) {
               Projectile _entityToSpawn = (new Object() {
                     public Projectile getArrow(Level level, float damage, int knockback) {
                        AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                           (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                        );
                        entityToSpawn.m_36781_(damage);
                        entityToSpawn.m_36735_(knockback);
                        entityToSpawn.m_20225_(true);
                        return entityToSpawn;
                     }
                  })
                  .getArrow(projectileLevel, 1.0F, 1);
               _entityToSpawn.m_6034_(x + 0.5, y + 0.5, z + 1.5);
               _entityToSpawn.m_6686_(0.0, 0.0, 2.0, 0.3F, 0.1F);
               projectileLevel.m_7967_(_entityToSpawn);
            }
         } else if (direction == Direction.WEST) {
            if (world instanceof ServerLevel projectileLevel) {
               Projectile _entityToSpawn = (new Object() {
                     public Projectile getArrow(Level level, float damage, int knockback) {
                        AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                           (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                        );
                        entityToSpawn.m_36781_(damage);
                        entityToSpawn.m_36735_(knockback);
                        entityToSpawn.m_20225_(true);
                        return entityToSpawn;
                     }
                  })
                  .getArrow(projectileLevel, 1.0F, 1);
               _entityToSpawn.m_6034_(x + 0.5, y + 0.5, z - 0.5);
               _entityToSpawn.m_6686_(-2.0, 0.0, 0.0, 0.3F, 0.1F);
               projectileLevel.m_7967_(_entityToSpawn);
            }
         } else if (direction == Direction.EAST && world instanceof ServerLevel projectileLevel) {
            Projectile _entityToSpawn = (new Object() {
                  public Projectile getArrow(Level level, float damage, int knockback) {
                     AbstractArrow entityToSpawn = new AshBallProjectileEntity(
                        (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), level
                     );
                     entityToSpawn.m_36781_(damage);
                     entityToSpawn.m_36735_(knockback);
                     entityToSpawn.m_20225_(true);
                     return entityToSpawn;
                  }
               })
               .getArrow(projectileLevel, 1.0F, 1);
            _entityToSpawn.m_6034_(x + 0.5, y + 0.5, z + 1.5);
            _entityToSpawn.m_6686_(2.0, 0.0, 0.0, 0.3F, 0.1F);
            projectileLevel.m_7967_(_entityToSpawn);
         }
      }
   }
}
