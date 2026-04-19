package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;

public class RainDisintegrateProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (world.m_6106_().m_6533_() && world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z)) && Math.random() < 0.3) {
         world.m_46961_(BlockPos.m_274561_(x, y, z), false);
         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
            || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
            || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()) {
            AshenremainsMod.queueServerWork(1, () -> AshfallProcedure.execute(world, x, y + 1.0, z));
         }
      }
   }
}
