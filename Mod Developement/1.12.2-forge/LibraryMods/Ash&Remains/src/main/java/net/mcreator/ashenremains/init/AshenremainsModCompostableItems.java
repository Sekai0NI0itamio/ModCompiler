package net.mcreator.ashenremains.init;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(
   bus = Bus.MOD
)
public class AshenremainsModCompostableItems {
   @SubscribeEvent
   public static void addComposterItems(FMLCommonSetupEvent event) {
      ComposterBlock.f_51914_.put((ItemLike)AshenremainsModItems.ASH_BALL.get(), 0.15F);
      ComposterBlock.f_51914_.put(((Block)AshenremainsModBlocks.ASH.get()).m_5456_(), 1.0F);
   }
}
