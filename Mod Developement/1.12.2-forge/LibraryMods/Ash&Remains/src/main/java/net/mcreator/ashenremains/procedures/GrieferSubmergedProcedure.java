package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;

public class GrieferSubmergedProcedure {
   public static boolean execute(LevelAccessor world, double x, double y, double z) {
      return !world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH.get()
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.ASH.get()
         && !world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")));
   }
}
