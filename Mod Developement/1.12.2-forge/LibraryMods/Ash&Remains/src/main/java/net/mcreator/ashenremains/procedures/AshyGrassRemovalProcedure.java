package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AshyGrassRemovalProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != Blocks.f_50016_
         && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != AshenremainsModBlocks.ASH.get()
         && world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH.get()
         && !world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50493_.m_49966_(), 3);
         if (world.m_6106_().m_6533_() && world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z))) {
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50440_.m_49966_(), 3);
            } else if (Math.random() < 0.02) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLOWERING_GRASS.get()).m_49966_(), 3);
            }
         }
      }
   }
}
