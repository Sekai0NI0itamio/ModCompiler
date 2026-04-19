package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;

public class AshWaterWorkaroundProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (!world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() != AshenremainsModBlocks.ASH.get()
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH.get()
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         AshfallProcedure.execute(world, x, y, z);
      }
   }
}
