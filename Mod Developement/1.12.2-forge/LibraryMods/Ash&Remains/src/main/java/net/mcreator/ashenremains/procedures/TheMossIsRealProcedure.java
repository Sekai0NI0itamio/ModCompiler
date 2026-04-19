package net.mcreator.ashenremains.procedures;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Map.Entry;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class TheMossIsRealProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      sx = -3.0;

      for (int index0 = 0; index0 < 6; index0++) {
         sy = -3.0;

         for (int index1 = 0; index1 < 6; index1++) {
            sz = -3.0;

            for (int index2 = 0; index2 < 6; index2++) {
               if (Math.random() < 0.7) {
                  if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != Blocks.f_50652_
                     && world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != AshenremainsModBlocks.FAKE_COBBLESTONE.get()) {
                     if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50157_) {
                        BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                        BlockState _bs = Blocks.f_50633_.m_49966_();
                        BlockState _bso = world.m_8055_(_bp);
                        UnmodifiableIterator var68 = _bso.m_61148_().entrySet().iterator();

                        while (var68.hasNext()) {
                           Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var68.next();
                           Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                           if (_property != null && _bs.m_61143_(_property) != null) {
                              try {
                                 _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                              } catch (Exception var23) {
                              }
                           }
                        }

                        world.m_7731_(_bp, _bs, 3);
                     } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50409_) {
                        BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                        BlockState _bs = Blocks.f_50647_.m_49966_();
                        BlockState _bso = world.m_8055_(_bp);
                        UnmodifiableIterator var69 = _bso.m_61148_().entrySet().iterator();

                        while (var69.hasNext()) {
                           Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var69.next();
                           Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                           if (_property != null && _bs.m_61143_(_property) != null) {
                              try {
                                 _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                              } catch (Exception var31) {
                              }
                           }
                        }

                        world.m_7731_(_bp, _bs, 3);
                     } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50274_) {
                        BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                        BlockState _bs = Blocks.f_50275_.m_49966_();
                        BlockState _bso = world.m_8055_(_bp);
                        UnmodifiableIterator var70 = _bso.m_61148_().entrySet().iterator();

                        while (var70.hasNext()) {
                           Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var70.next();
                           Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                           if (_property != null && _bs.m_61143_(_property) != null) {
                              try {
                                 _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                              } catch (Exception var30) {
                              }
                           }
                        }

                        world.m_7731_(_bp, _bs, 3);
                     } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != Blocks.f_50222_
                        && world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() != AshenremainsModBlocks.FAKE_STONE_BRICKS.get()) {
                        if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50176_) {
                           BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                           BlockState _bs = Blocks.f_50177_.m_49966_();
                           BlockState _bso = world.m_8055_(_bp);
                           UnmodifiableIterator var73 = _bso.m_61148_().entrySet().iterator();

                           while (var73.hasNext()) {
                              Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var73.next();
                              Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                              if (_property != null && _bs.m_61143_(_property) != null) {
                                 try {
                                    _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                                 } catch (Exception var27) {
                                 }
                              }
                           }

                           world.m_7731_(_bp, _bs, 3);
                        } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50609_) {
                           BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                           BlockState _bs = Blocks.f_50607_.m_49966_();
                           BlockState _bso = world.m_8055_(_bp);
                           UnmodifiableIterator var74 = _bso.m_61148_().entrySet().iterator();

                           while (var74.hasNext()) {
                              Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var74.next();
                              Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                              if (_property != null && _bs.m_61143_(_property) != null) {
                                 try {
                                    _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                                 } catch (Exception var26) {
                                 }
                              }
                           }

                           world.m_7731_(_bp, _bs, 3);
                        } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50411_) {
                           BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                           BlockState _bs = Blocks.f_50645_.m_49966_();
                           BlockState _bso = world.m_8055_(_bp);
                           UnmodifiableIterator var75 = _bso.m_61148_().entrySet().iterator();

                           while (var75.hasNext()) {
                              Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var75.next();
                              Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                              if (_property != null && _bs.m_61143_(_property) != null) {
                                 try {
                                    _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                                 } catch (Exception var25) {
                                 }
                              }
                           }

                           world.m_7731_(_bp, _bs, 3);
                        } else if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_60734_() == Blocks.f_50194_) {
                           BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                           BlockState _bs = Blocks.f_50631_.m_49966_();
                           BlockState _bso = world.m_8055_(_bp);
                           UnmodifiableIterator var76 = _bso.m_61148_().entrySet().iterator();

                           while (var76.hasNext()) {
                              Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var76.next();
                              Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                              if (_property != null && _bs.m_61143_(_property) != null) {
                                 try {
                                    _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                                 } catch (Exception var24) {
                                 }
                              }
                           }

                           world.m_7731_(_bp, _bs, 3);
                        }
                     } else if (Math.random() < 0.7) {
                        BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                        BlockState _bs = ((Block)AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS.get()).m_49966_();
                        BlockState _bso = world.m_8055_(_bp);
                        UnmodifiableIterator var71 = _bso.m_61148_().entrySet().iterator();

                        while (var71.hasNext()) {
                           Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var71.next();
                           Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                           if (_property != null && _bs.m_61143_(_property) != null) {
                              try {
                                 _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                              } catch (Exception var29) {
                              }
                           }
                        }

                        world.m_7731_(_bp, _bs, 3);
                     } else {
                        BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                        BlockState _bs = Blocks.f_50223_.m_49966_();
                        BlockState _bso = world.m_8055_(_bp);
                        UnmodifiableIterator var72 = _bso.m_61148_().entrySet().iterator();

                        while (var72.hasNext()) {
                           Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var72.next();
                           Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                           if (_property != null && _bs.m_61143_(_property) != null) {
                              try {
                                 _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                              } catch (Exception var28) {
                              }
                           }
                        }

                        world.m_7731_(_bp, _bs, 3);
                     }
                  } else if (Math.random() < 0.7) {
                     BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                     BlockState _bs = ((Block)AshenremainsModBlocks.BLOSSOMING_COBBLESTONE.get()).m_49966_();
                     BlockState _bso = world.m_8055_(_bp);
                     UnmodifiableIterator var19 = _bso.m_61148_().entrySet().iterator();

                     while (var19.hasNext()) {
                        Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var19.next();
                        Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                        if (_property != null && _bs.m_61143_(_property) != null) {
                           try {
                              _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                           } catch (Exception var33) {
                           }
                        }
                     }

                     world.m_7731_(_bp, _bs, 3);
                  } else {
                     BlockPos _bp = BlockPos.m_274561_(x + sx, y + sy, z + sz);
                     BlockState _bs = Blocks.f_50079_.m_49966_();
                     BlockState _bso = world.m_8055_(_bp);
                     UnmodifiableIterator var67 = _bso.m_61148_().entrySet().iterator();

                     while (var67.hasNext()) {
                        Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var67.next();
                        Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                        if (_property != null && _bs.m_61143_(_property) != null) {
                           try {
                              _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                           } catch (Exception var32) {
                           }
                        }
                     }

                     world.m_7731_(_bp, _bs, 3);
                  }

                  sz++;
               }

               sz++;
            }

            sy++;
         }

         sx++;
      }
   }
}
