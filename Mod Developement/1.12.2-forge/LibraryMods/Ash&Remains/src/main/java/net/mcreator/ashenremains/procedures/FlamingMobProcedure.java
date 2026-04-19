package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.entity.GrieferEntity;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class FlamingMobProcedure {
   @SubscribeEvent
   public static void onEntityAttacked(LivingHurtEvent event) {
      if (event != null && event.getEntity() != null) {
         execute(
            event,
            event.getEntity().m_9236_(),
            event.getEntity().m_20185_(),
            event.getEntity().m_20186_(),
            event.getEntity().m_20189_(),
            event.getSource(),
            event.getEntity()
         );
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z, DamageSource damagesource, Entity entity) {
      execute(null, world, x, y, z, damagesource, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, DamageSource damagesource, Entity entity) {
      if (damagesource != null && entity != null) {
         if (damagesource.m_276093_(DamageTypes.f_268468_) && entity.m_20096_() && world.m_6106_().m_5470_().m_46207_(AshenremainsModGameRules.MOB_FIRES)) {
            if (!entity.m_6060_()
               || !(entity instanceof Player)
               || (
                     !(Math.random() < 0.05)
                        || !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                  )
                  && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               if (!entity.m_6060_()
                  || !(entity instanceof GrieferEntity)
                  || (
                        !(Math.random() < 0.2)
                           || !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                              && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     )
                     && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
                  if (entity.m_6060_()
                     && (
                        Math.random() < 0.1
                              && (
                                 world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                                    || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z))
                                       .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                              )
                           || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
                     )) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
                  }
               } else {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
               }
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         }
      }
   }
}
