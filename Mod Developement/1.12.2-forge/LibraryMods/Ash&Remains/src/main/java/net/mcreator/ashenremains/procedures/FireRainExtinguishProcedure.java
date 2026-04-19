package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public class FireRainExtinguishProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean path = false;
      boolean pathfound = false;
      boolean found = false;
      double uy = 0.0;
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

         if (!world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z)) && !path) {
            if (Math.random() < 0.5) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }
            }
         } else {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
            if (world instanceof Level _levelx) {
               if (!_levelx.m_5776_()) {
                  _levelx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         }
      }
   }
}
