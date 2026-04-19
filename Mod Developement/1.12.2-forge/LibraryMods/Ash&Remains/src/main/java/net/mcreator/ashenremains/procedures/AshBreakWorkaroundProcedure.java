package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class AshBreakWorkaroundProcedure {
   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_());
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         AshenremainsMod.queueServerWork(1, () -> AshWaterWorkaroundProcedure.execute(world, x, y + 1.0, z));
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         AshenremainsMod.queueServerWork(1, () -> AshWaterWorkaroundProcedure.execute(world, x, y - 1.0, z));
      }
   }
}
