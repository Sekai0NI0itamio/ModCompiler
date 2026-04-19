package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;

public class FlintAndTinderDispenseSuccessfullyIfProcedure {
   public static boolean execute(LevelAccessor world, double x, double y, double z, Direction direction) {
      if (direction == null) {
         return false;
      } else if (world.m_46859_(BlockPos.m_274561_(x, y - 1.0, z))
         && (
            world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
         )
         && direction == Direction.DOWN) {
         return true;
      } else if (world.m_46859_(BlockPos.m_274561_(x, y + 2.0, z))
         && (
            world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
         )
         && direction == Direction.UP) {
         return true;
      } else if (world.m_46859_(BlockPos.m_274561_(x, y, z - 1.0))
         && (
            world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
         )
         && direction == Direction.NORTH) {
         return true;
      } else if (world.m_46859_(BlockPos.m_274561_(x, y, z + 1.0))
         && (
            world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
         )
         && direction == Direction.SOUTH) {
         return true;
      } else {
         return world.m_46859_(BlockPos.m_274561_(x + 1.0, y, z))
               && (
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               )
               && direction == Direction.EAST
            ? true
            : world.m_46859_(BlockPos.m_274561_(x - 1.0, y, z))
               && (
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               )
               && direction == Direction.WEST;
      }
   }
}
