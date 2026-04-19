package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class SickBurnsProcedure {
   @SubscribeEvent
   public static void onEntityDeath(LivingDeathEvent event) {
      if (event != null && event.getEntity() != null) {
         execute(
            event, event.getEntity().m_9236_(), event.getEntity().m_20185_(), event.getEntity().m_20186_(), event.getEntity().m_20189_(), event.getEntity()
         );
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      execute(null, world, x, y, z, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         boolean found = false;
         double sx = 0.0;
         double sy = 0.0;
         double sz = 0.0;
         if (entity instanceof WitherBoss) {
            sx = -6.0;
            found = false;

            for (int index0 = 0; index0 < 12; index0++) {
               sy = -6.0;

               for (int index1 = 0; index1 < 12; index1++) {
                  sz = -6.0;

                  for (int index2 = 0; index2 < 12; index2++) {
                     if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50084_) {
                        world.m_7731_(BlockPos.m_274561_(x + sx, y + sy, z + sz), Blocks.f_50016_.m_49966_(), 3);
                     }

                     sz++;
                  }

                  sy++;
               }

               sx++;
            }
         }
      }
   }
}
