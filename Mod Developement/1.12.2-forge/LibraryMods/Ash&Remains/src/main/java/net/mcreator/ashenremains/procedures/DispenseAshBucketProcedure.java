package net.mcreator.ashenremains.procedures;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;

public class DispenseAshBucketProcedure {
   public static void execute(final LevelAccessor world, double x, double y, double z) {
      ItemStack itemstate = ItemStack.f_41583_;
      if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.DOWN) {
         for (int index0 = 0; index0 < 3; index0++) {
            AshfallProcedure.execute(world, x, y - 1.0, z);
         }
      } else if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.UP) {
         for (int index1 = 0; index1 < 3; index1++) {
            AshfallProcedure.execute(world, x, y + 1.0, z);
         }
      } else if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.NORTH) {
         for (int index2 = 0; index2 < 3; index2++) {
            AshfallProcedure.execute(world, x, y, z - 1.0);
         }
      } else if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.SOUTH) {
         for (int index3 = 0; index3 < 3; index3++) {
            AshfallProcedure.execute(world, x, y, z + 1.0);
         }
      } else if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.WEST) {
         for (int index4 = 0; index4 < 3; index4++) {
            AshfallProcedure.execute(world, x - 1.0, y, z);
         }
      } else if ((new Object() {
               public Direction getDirection(BlockPos pos) {
                  BlockState _bs = world.m_8055_(pos);
                  Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                  if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                     return _dir;
                  } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                     return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                  } else {
                     return _bs.m_61138_(BlockStateProperties.f_61364_)
                        ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                        : Direction.NORTH;
                  }
               }
            })
            .getDirection(BlockPos.m_274561_(x, y, z))
         == Direction.EAST) {
         for (int index5 = 0; index5 < 3; index5++) {
            AshfallProcedure.execute(world, x + 1.0, y, z);
         }
      }

      if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 0).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 0;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(0, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 1).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 1;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(1, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 2).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 2;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(2, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 3).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 3;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(3, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 4).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 4;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(4, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 5).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 5;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(5, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 6).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 6;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(6, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 7).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 7;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(7, _setstack);
               }
            });
         }
      } else if ((new Object() {
         public ItemStack getItemStack(LevelAccessor world, BlockPos pos, int slotid) {
            AtomicReference<ItemStack> _retval = new AtomicReference<>(ItemStack.f_41583_);
            BlockEntity _ent = world.m_7702_(pos);
            if (_ent != null) {
               _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> _retval.set(capability.getStackInSlot(slotid).m_41777_()));
            }

            return _retval.get();
         }
      }).getItemStack(world, BlockPos.m_274561_(x, y, z), 8).m_41720_() == ItemStack.f_41583_.m_41720_()) {
         BlockEntity _ent = world.m_7702_(BlockPos.m_274561_(x, y, z));
         if (_ent != null) {
            int _slotid = 8;
            ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
            _setstack.m_41764_(1);
            _ent.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
               if (capability instanceof IItemHandlerModifiable) {
                  ((IItemHandlerModifiable)capability).setStackInSlot(8, _setstack);
               }
            });
         }
      }
   }
}
