package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class LavaIgnitionProcedure {
   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_());
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      double sideburns = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))) {
         sx = -3.0;
         found = false;

         for (int index0 = 0; index0 < 6; index0++) {
            sy = -3.0;

            for (int index1 = 0; index1 < 6; index1++) {
               sz = -3.0;

               for (int index2 = 0; index2 < 6; index2++) {
                  if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_49991_
                     || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_49991_) {
                     found = true;
                  }

                  sz++;
               }

               sy++;
            }

            sx++;
         }

         if (found) {
            IgniteBlockProcedure.execute(world, x, y, z);
         }
      }
   }
}
