package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class SproutGrowthProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z))) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.OAK_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50746_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.BIRCH_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50748_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.DARK_OAK_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50751_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ACACIA_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50750_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.JUNGLE_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50749_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.SPRUCE_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50747_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.CHERRY_SPROUT.get()
            && Math.random() < 0.2
            && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_271334_.m_49966_(), 3);
         }
      }
   }
}
