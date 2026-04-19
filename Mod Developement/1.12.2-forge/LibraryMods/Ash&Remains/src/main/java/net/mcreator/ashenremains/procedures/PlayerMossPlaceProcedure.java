package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class PlayerMossPlaceProcedure {
   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_(), event.getState());
   }

   public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate) {
      execute(null, world, x, y, z, blockstate);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, BlockState blockstate) {
      if (blockstate.m_60734_() == Blocks.f_50652_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FAKE_COBBLESTONE.get()).m_49966_(), 3);
      } else if (blockstate.m_60734_() == Blocks.f_50222_) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FAKE_STONE_BRICKS.get()).m_49966_(), 3);
      }
   }
}
