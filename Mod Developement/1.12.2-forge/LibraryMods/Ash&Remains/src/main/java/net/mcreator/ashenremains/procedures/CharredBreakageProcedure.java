package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class CharredBreakageProcedure {
   @SubscribeEvent
   public static void onEntityFall(LivingFallEvent event) {
      if (event != null && event.getEntity() != null) {
         execute(
            event,
            event.getEntity().m_9236_(),
            event.getEntity().m_20185_(),
            event.getEntity().m_20186_(),
            event.getEntity().m_20189_(),
            event.getEntity(),
            event.getDistance()
         );
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, double distance) {
      execute(null, world, x, y, z, entity, distance);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity, double distance) {
      if (entity != null) {
         if (entity instanceof LivingEntity && distance >= 4.0) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:charred_blocks")))) {
               world.m_46961_(BlockPos.m_274561_(x, y, z), false);
            }

            if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:charred_blocks")))) {
               world.m_46961_(BlockPos.m_274561_(x, y - 1.0, z), false);
            }
         }
      }
   }
}
