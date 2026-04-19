package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MossificationCheckProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate) {
      Direction spreadside = Direction.NORTH;
      double adjacencies = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      boolean found = false;
      if (Math.random() < 0.08) {
         sx = -3.0;
         found = false;

         for (int index0 = 0; index0 < 6; index0++) {
            sy = -3.0;

            for (int index1 = 0; index1 < 6; index1++) {
               sz = -3.0;

               for (int index2 = 0; index2 < 6; index2++) {
                  if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))
                     && world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS.get()
                     && world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != AshenremainsModBlocks.BLOSSOMING_COBBLESTONE.get()) {
                     found = true;
                  }

                  sz++;
               }

               sy++;
            }

            sx++;
         }

         if (found) {
            adjacencies = 0.0;
            if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossy")))) {
               adjacencies++;
            }

            if (adjacencies <= 2.0) {
               if (blockstate.m_60734_() == AshenremainsModBlocks.FAKE_COBBLESTONE.get()) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BLOSSOMING_COBBLESTONE.get()).m_49966_(), 3);
               } else if (blockstate.m_60734_() == AshenremainsModBlocks.FAKE_STONE_BRICKS.get()) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS.get()).m_49966_(), 3);
               }
            }
         }
      }
   }
}
