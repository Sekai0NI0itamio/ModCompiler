package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class CrystalExplosionProcedure {
   @SubscribeEvent
   public static void onEntityAttacked(LivingHurtEvent event) {
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
         if (entity instanceof EndCrystal) {
            sx = -3.0;

            for (int index0 = 0; index0 < 6; index0++) {
               sy = -3.0;

               for (int index1 = 0; index1 < 6; index1++) {
                  sz = -3.0;

                  for (int index2 = 0; index2 < 6; index2++) {
                     if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50080_ && Math.random() < 0.1) {
                        world.m_7731_(BlockPos.m_274561_(x + sx, y + sy, z + sz), Blocks.f_50723_.m_49966_(), 3);
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
