package net.mcreator.ashenremains.procedures;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class AshGrassConversionProcedure {
   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_(), event.getState());
   }

   public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate) {
      execute(null, world, x, y, z, blockstate);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, BlockState blockstate) {
      if ((blockstate.m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks"))) || blockstate.m_60734_() == AshenremainsModBlocks.ASH.get())
         && (
            world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50440_
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == AshenremainsModBlocks.FLOWERING_GRASS.get()
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50599_
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50195_
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_152481_
         )) {
         BlockPos _bp = BlockPos.m_274561_(x, y - 1.0, z);
         BlockState _bs = ((Block)AshenremainsModBlocks.ASHY_GRASS.get()).m_49966_();
         BlockState _bso = world.m_8055_(_bp);
         UnmodifiableIterator var12 = _bso.m_61148_().entrySet().iterator();

         while (var12.hasNext()) {
            Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var12.next();
            Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
            if (_property != null && _bs.m_61143_(_property) != null) {
               try {
                  _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
               } catch (Exception var16) {
               }
            }
         }

         world.m_7731_(_bp, _bs, 3);
      }
   }
}
