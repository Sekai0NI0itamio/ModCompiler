package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class SproutBreakProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean success = false;
      if (!world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_
         || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_49990_) {
         BlockPos _pos = BlockPos.m_274561_(x, y, z);
         Block.m_49892_(world.m_8055_(_pos), world, BlockPos.m_274561_(x + 0.5, y, z + 0.5), null);
         world.m_46961_(_pos, false);
      }
   }
}
