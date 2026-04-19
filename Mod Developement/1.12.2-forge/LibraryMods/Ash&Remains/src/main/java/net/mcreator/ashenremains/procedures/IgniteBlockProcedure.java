package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public class IgniteBlockProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      if (!world.m_46859_(BlockPos.m_274561_(x, y, z))) {
         if (world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
            || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
         }

         if (world.m_46859_(BlockPos.m_274561_(x, y - 1.0, z))
            || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.STRANGE_FIRE.get()).m_49966_(), 3);
         }

         if (world.m_46859_(BlockPos.m_274561_(x + 1.0, y, z))
            || world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), ((Block)AshenremainsModBlocks.EAST_FIRE.get()).m_49966_(), 3);
         }

         if (world.m_46859_(BlockPos.m_274561_(x - 1.0, y, z))
            || world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), ((Block)AshenremainsModBlocks.WEST_FIRE.get()).m_49966_(), 3);
         }

         if (world.m_46859_(BlockPos.m_274561_(x, y, z - 1.0))
            || world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), ((Block)AshenremainsModBlocks.NORTH_FIRE.get()).m_49966_(), 3);
         }

         if (world.m_46859_(BlockPos.m_274561_(x, y, z + 1.0))
            || world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), ((Block)AshenremainsModBlocks.SOUTH_FIRE.get()).m_49966_(), 3);
         }

         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.firecharge.use")),
                  SoundSource.BLOCKS,
                  0.3F,
                  Mth.m_216271_(RandomSource.m_216327_(), 0, 1)
               );
            } else {
               _level.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.firecharge.use")),
                  SoundSource.BLOCKS,
                  0.3F,
                  Mth.m_216271_(RandomSource.m_216327_(), 0, 1),
                  false
               );
            }
         }
      }
   }
}
