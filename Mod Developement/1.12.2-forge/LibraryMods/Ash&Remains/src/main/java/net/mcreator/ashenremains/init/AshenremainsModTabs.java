package net.mcreator.ashenremains.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;

@EventBusSubscriber(
   bus = Bus.MOD
)
public class AshenremainsModTabs {
   public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.f_279569_, "ashenremains");

   @SubscribeEvent
   public static void buildTabContentsVanilla(BuildCreativeModeTabContentsEvent tabData) {
      if (tabData.getTabKey() == CreativeModeTabs.f_256788_) {
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_WOOD.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_LOG.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_PLANKS.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_STAIRS.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_SLAB.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_FENCE.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_STRIPPED_WOOD.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_STRIPPED_LOG.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.FLAMING_ASH.get()).m_5456_());
      } else if (tabData.getTabKey() == CreativeModeTabs.f_257028_) {
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_WOOD_FENCE_GATE.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_TRAP_DOOR.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_DOOR.get()).m_5456_());
      } else if (tabData.getTabKey() == CreativeModeTabs.f_256731_) {
         tabData.m_246326_((ItemLike)AshenremainsModItems.GRIEFER_SPAWN_EGG.get());
      } else if (tabData.getTabKey() == CreativeModeTabs.f_256968_) {
         tabData.m_246326_((ItemLike)AshenremainsModItems.OAK_ACORN.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.PINE_CONE.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.BIRCH_FROND.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.JUNGLE_POD.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.ACACIA_FROND.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.DARK_ACORN.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.BUCKET_OF_ASH.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.CHERRY_SEED.get());
         tabData.m_246326_((ItemLike)AshenremainsModItems.ASH_BALL.get());
      } else if (tabData.getTabKey() == CreativeModeTabs.f_256776_) {
         tabData.m_246326_(((Block)AshenremainsModBlocks.FLOWERING_GRASS.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.JUNGLE_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.ACACIA_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.ASH.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHARRED_ROOTS.get()).m_5456_());
         tabData.m_246326_(((Block)AshenremainsModBlocks.CHERRY_SPROUT.get()).m_5456_());
      } else if (tabData.getTabKey() == CreativeModeTabs.f_256869_) {
         tabData.m_246326_((ItemLike)AshenremainsModItems.FLINT_AND_TINDER.get());
      }
   }
}
