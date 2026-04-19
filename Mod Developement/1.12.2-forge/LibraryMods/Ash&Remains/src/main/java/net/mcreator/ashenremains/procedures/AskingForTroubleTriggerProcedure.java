package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class AskingForTroubleTriggerProcedure {
   @SubscribeEvent
   public static void onEntitySetsAttackTarget(LivingChangeTargetEvent event) {
      execute(
         event,
         event.getEntity().m_9236_(),
         event.getEntity().m_20185_(),
         event.getEntity().m_20186_(),
         event.getEntity().m_20189_(),
         event.getOriginalTarget(),
         event.getEntity()
      );
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, Entity sourceentity) {
      execute(null, world, x, y, z, entity, sourceentity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity, Entity sourceentity) {
      if (entity != null && sourceentity != null) {
         if (sourceentity instanceof Blaze
            && (
               world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("jungle"))
                  || world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("dark_forest"))
                  || world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("taiga"))
            )
            && entity instanceof ServerPlayer _player) {
            Advancement _adv = _player.f_8924_.m_129889_().m_136041_(new ResourceLocation("ashenremains:asking_for_trouble"));
            AdvancementProgress _ap = _player.m_8960_().m_135996_(_adv);
            if (!_ap.m_8193_()) {
               for (String criteria : _ap.m_8219_()) {
                  _player.m_8960_().m_135988_(_adv, criteria);
               }
            }
         }
      }
   }
}
