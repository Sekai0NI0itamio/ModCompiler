package net.mcreator.ashenremains.procedures;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.items.IItemHandlerModifiable;

@EventBusSubscriber
public class FlintSteelChestScannerProcedure {
   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      if (event.getHand() == event.getEntity().m_7655_()) {
         execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_());
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      double SlotNumber = 0.0;
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50087_
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50325_
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50618_
         || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50332_) {
         SlotNumber = 0.0;
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50332_) {
            for (int index0 = 0; index0 < 5; index0++) {
               if ((new Object() {
                        public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
                           AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
                           BlockEntity _ent = world.m_7702_(pos);
                           if (_ent != null) {
                              _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                                 .ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
                           }

                           return _retval.get();
                        }
                     })
                     .getItemStack(world, BlockPos.m_274561_(x, y, z), (int)SlotNumber)
                     .m_41720_()
                  == Items.f_42409_) {
                  BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
                  if (_ent != null) {
                     int _slotid = (int)SlotNumber;
                     ItemStack _setstack = new ItemStack((ItemLike)AshenremainsModItems.FLINT_AND_TINDER.get()).m_41777_();
                     _setstack.m_41764_(1);
                     _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
                        if (capability instanceof IItemHandlerModifiable) {
                           ((IItemHandlerModifiable)capability).setStackInSlot(_slotid, _setstack);
                        }
                     });
                  }
               }

               SlotNumber++;
            }
         } else {
            for (int index1 = 0; index1 < 27; index1++) {
               if ((new Object() {
                        public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
                           AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
                           BlockEntity _ent = world.m_7702_(pos);
                           if (_ent != null) {
                              _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                                 .ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
                           }

                           return _retval.get();
                        }
                     })
                     .getItemStack(world, BlockPos.m_274561_(x, y, z), (int)SlotNumber)
                     .m_41720_()
                  == Items.f_42409_) {
                  BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
                  if (_ent != null) {
                     int _slotid = (int)SlotNumber;
                     ItemStack _setstack = new ItemStack((ItemLike)AshenremainsModItems.FLINT_AND_TINDER.get()).m_41777_();
                     _setstack.m_41764_(1);
                     _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
                        if (capability instanceof IItemHandlerModifiable) {
                           ((IItemHandlerModifiable)capability).setStackInSlot(_slotid, _setstack);
                        }
                     });
                  }
               }

               SlotNumber++;
            }
         }
      }
   }
}
