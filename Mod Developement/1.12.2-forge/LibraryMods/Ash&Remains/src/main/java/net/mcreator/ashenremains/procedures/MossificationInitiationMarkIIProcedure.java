package net.mcreator.ashenremains.procedures;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Map.Entry;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

public class MossificationInitiationMarkIIProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      Direction spreadside = Direction.NORTH;
      double adjacencies = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      boolean found = false;
      if (Math.random() < 0.1) {
         if (Math.random() < 0.5) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.BLOSSOMING_COBBLESTONE.get()) {
               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.place")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.place")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50079_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS.get()) {
               if (world instanceof Level _levelx) {
                  if (!_levelx.m_5776_()) {
                     _levelx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.place")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F
                     );
                  } else {
                     _levelx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.place")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50223_.m_49966_(), 3);
            }
         }

         for (int index0 = 0; index0 < 3; index0++) {
            spreadside = Direction.m_235672_(RandomSource.m_216327_());
            if (spreadside == Direction.UP) {
               sx = x;
               sy = y + 1.0;
               sz = z;
            } else if (spreadside == Direction.DOWN) {
               sx = x;
               sy = y - 1.0;
               sz = z;
            } else if (spreadside == Direction.NORTH) {
               sx = x;
               sy = y;
               sz = z - 1.0;
            } else if (spreadside == Direction.SOUTH) {
               sx = x;
               sy = y;
               sz = z + 1.0;
            } else if (spreadside == Direction.WEST) {
               sx = x - 1.0;
               sy = y;
               sz = z;
            } else if (spreadside == Direction.EAST) {
               sx = x + 1.0;
               sy = y;
               sz = z;
            }

            if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:mossable")))) {
               if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50157_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50633_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var53 = _bso.m_61148_().entrySet().iterator();

                  while (var53.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var53.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var30) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               } else if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50409_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50647_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var52 = _bso.m_61148_().entrySet().iterator();

                  while (var52.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var52.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var29) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               } else if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50274_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50275_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var51 = _bso.m_61148_().entrySet().iterator();

                  while (var51.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var51.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var28) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               } else if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50609_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50607_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var50 = _bso.m_61148_().entrySet().iterator();

                  while (var50.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var50.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var27) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               } else if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50411_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50645_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var49 = _bso.m_61148_().entrySet().iterator();

                  while (var49.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var49.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var26) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               } else if (world.m_8055_(BlockPos.m_274561_(sx, sy, sz)).m_60734_() == Blocks.f_50194_) {
                  BlockPos _bp = BlockPos.m_274561_(sx, sy, sz);
                  BlockState _bs = Blocks.f_50631_.m_49966_();
                  BlockState _bso = world.m_8055_(_bp);
                  UnmodifiableIterator var21 = _bso.m_61148_().entrySet().iterator();

                  while (var21.hasNext()) {
                     Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var21.next();
                     Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                     if (_property != null && _bs.m_61143_(_property) != null) {
                        try {
                           _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                        } catch (Exception var25) {
                        }
                     }
                  }

                  world.m_7731_(_bp, _bs, 3);
               }
            }
         }
      }
   }
}
