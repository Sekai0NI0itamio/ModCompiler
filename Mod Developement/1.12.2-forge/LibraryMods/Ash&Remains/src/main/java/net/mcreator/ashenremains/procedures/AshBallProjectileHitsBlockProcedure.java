package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

public class AshBallProjectileHitsBlockProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60783_(world, BlockPos.m_274561_(x, y, z), Direction.UP)
         && (
            world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         )) {
         AshAccumulateProcedure.execute(world, x, y + 1.0, z);
         world.m_46796_(2001, BlockPos.m_274561_(x, y + 1.0, z), Block.m_49956_(((Block)AshenremainsModBlocks.ASH.get()).m_49966_()));
      }
   }
}
