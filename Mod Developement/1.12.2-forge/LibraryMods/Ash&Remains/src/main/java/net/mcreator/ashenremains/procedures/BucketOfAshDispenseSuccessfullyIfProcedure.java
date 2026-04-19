package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

public class BucketOfAshDispenseSuccessfullyIfProcedure {
   public static boolean execute(final LevelAccessor world, double x, double y, double z) {
      if ((
            world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_50016_
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         )
         && (new Object() {
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
         return true;
      } else if ((
            world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50016_
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         )
         && (new Object() {
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
         return true;
      } else if ((
            world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
               || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         )
         && (new Object() {
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
         return true;
      } else if ((
            world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
               || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         )
         && (new Object() {
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
         return true;
      } else {
         return (
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
               )
               && (new Object() {
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
                  == Direction.EAST
            ? true
            : (
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
               )
               && (new Object() {
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
                  == Direction.WEST;
      }
   }
}
