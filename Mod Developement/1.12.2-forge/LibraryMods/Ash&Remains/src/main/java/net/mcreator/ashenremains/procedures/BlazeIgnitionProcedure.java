package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class BlazeIgnitionProcedure {
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
         if (entity instanceof Blaze) {
            if ((
                  world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               )
               && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               )
               && world.m_46859_(BlockPos.m_274561_(x - 1.0, y, z))) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               )
               && world.m_46859_(BlockPos.m_274561_(x + 1.0, y, z))) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               )
               && world.m_46859_(BlockPos.m_274561_(x, y, z + 1.0))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               )
               && world.m_46859_(BlockPos.m_274561_(x, y, z - 1.0))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         }
      }
   }
}
