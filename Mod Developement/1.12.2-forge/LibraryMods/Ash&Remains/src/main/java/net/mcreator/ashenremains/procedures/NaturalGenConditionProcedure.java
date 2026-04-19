package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class NaturalGenConditionProcedure {
   public static boolean execute(LevelAccessor world, double x, double y, double z) {
      return world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == Blocks.f_50016_ && world.m_46861_(BlockPos.m_274561_(x, y, z));
   }
}
