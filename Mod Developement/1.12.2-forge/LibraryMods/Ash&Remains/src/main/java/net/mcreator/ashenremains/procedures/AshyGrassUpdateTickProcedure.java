package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AshyGrassUpdateTickProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean path = false;
      boolean pathfound = false;
      boolean found = false;
      double uy = 0.0;
      double sx = 0.0;
      double Flowerchance = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double Layerdecrease = 0.0;
      if (world.m_6106_().m_6533_()) {
         path = false;
         pathfound = false;
         if (!world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z))) {
            uy = y + 1.0;

            while (!pathfound) {
               if (world.m_46859_(BlockPos.m_274561_(x, uy, z)) && world.m_46861_(BlockPos.m_274561_(x, uy, z))) {
                  pathfound = true;
                  path = true;
               } else if ((!world.m_46859_(BlockPos.m_274561_(x, uy, z)) || world.m_46861_(BlockPos.m_274561_(x, uy, z)))
                  && world.m_8055_(BlockPos.m_274561_(x, uy, z)).m_60783_(world, BlockPos.m_274561_(x, uy, z), Direction.UP)) {
                  pathfound = true;
               } else {
                  uy++;
               }
            }
         }

         if (world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z)) || path) {
            if (Math.random() < 0.8) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50493_.m_49966_(), 3);
            } else if (Math.random() < 0.8) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50440_.m_49966_(), 3);
            } else {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.FLOWERING_GRASS.get()).m_49966_(), 3);
            }
         }
      }
   }
}
