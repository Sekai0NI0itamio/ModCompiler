package net.mcreator.ashenremains.procedures;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Comparator;
import java.util.Map.Entry;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class FieryTransformationProcedure {
   public static void execute(final LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:full_blown_wood")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_WOOD.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var182 = _bso.m_61148_().entrySet().iterator();

            while (var182.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var182.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var67) {
                  }
               }
            }

            BlockEntity _be = world.m_7702_(_bp);
            CompoundTag _bnbt = null;
            if (_be != null) {
               _bnbt = _be.m_187480_();
               _be.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbt != null) {
               BlockEntity var184 = world.m_7702_(_bp);
               if (var184 != null) {
                  try {
                     var184.m_142466_(_bnbt);
                  } catch (Exception var66) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:stripped_logs")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_STRIPPED_LOG.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var179 = _bso.m_61148_().entrySet().iterator();

            while (var179.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var179.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var65) {
                  }
               }
            }

            BlockEntity _bex = world.m_7702_(_bp);
            CompoundTag _bnbtx = null;
            if (_bex != null) {
               _bnbtx = _bex.m_187480_();
               _bex.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtx != null) {
               BlockEntity var181 = world.m_7702_(_bp);
               if (var181 != null) {
                  try {
                     var181.m_142466_(_bnbtx);
                  } catch (Exception var64) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:stripped_wood")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_STRIPPED_WOOD.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var176 = _bso.m_61148_().entrySet().iterator();

            while (var176.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var176.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var63) {
                  }
               }
            }

            BlockEntity _bexx = world.m_7702_(_bp);
            CompoundTag _bnbtxx = null;
            if (_bexx != null) {
               _bnbtxx = _bexx.m_187480_();
               _bexx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxx != null) {
               BlockEntity var178 = world.m_7702_(_bp);
               if (var178 != null) {
                  try {
                     var178.m_142466_(_bnbtxx);
                  } catch (Exception var62) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:logs")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_LOG.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var173 = _bso.m_61148_().entrySet().iterator();

            while (var173.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var173.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var61) {
                  }
               }
            }

            BlockEntity _bexxx = world.m_7702_(_bp);
            CompoundTag _bnbtxxx = null;
            if (_bexxx != null) {
               _bnbtxxx = _bexxx.m_187480_();
               _bexxx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxxx != null) {
               BlockEntity var175 = world.m_7702_(_bp);
               if (var175 != null) {
                  try {
                     var175.m_142466_(_bnbtxxx);
                  } catch (Exception var60) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:planks")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_PLANKS.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var170 = _bso.m_61148_().entrySet().iterator();

            while (var170.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var170.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var59) {
                  }
               }
            }

            BlockEntity _bexxxx = world.m_7702_(_bp);
            CompoundTag _bnbtxxxx = null;
            if (_bexxxx != null) {
               _bnbtxxxx = _bexxxx.m_187480_();
               _bexxxx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxxxx != null) {
               BlockEntity var172 = world.m_7702_(_bp);
               if (var172 != null) {
                  try {
                     var172.m_142466_(_bnbtxxxx);
                  } catch (Exception var58) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_stairs")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_STAIRS.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var167 = _bso.m_61148_().entrySet().iterator();

            while (var167.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var167.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var57) {
                  }
               }
            }

            BlockEntity _bexxxxx = world.m_7702_(_bp);
            CompoundTag _bnbtxxxxx = null;
            if (_bexxxxx != null) {
               _bnbtxxxxx = _bexxxxx.m_187480_();
               _bexxxxx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxxxxx != null) {
               BlockEntity var169 = world.m_7702_(_bp);
               if (var169 != null) {
                  try {
                     var169.m_142466_(_bnbtxxxxx);
                  } catch (Exception var56) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_220833_) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_ROOTS.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator var164 = _bso.m_61148_().entrySet().iterator();

            while (var164.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var164.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var55) {
                  }
               }
            }

            BlockEntity _bexxxxxx = world.m_7702_(_bp);
            CompoundTag _bnbtxxxxxx = null;
            if (_bexxxxxx != null) {
               _bnbtxxxxxx = _bexxxxxx.m_187480_();
               _bexxxxxx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxxxxxx != null) {
               BlockEntity var166 = world.m_7702_(_bp);
               if (var166 != null) {
                  try {
                     var166.m_142466_(_bnbtxxxxxx);
                  } catch (Exception var54) {
                  }
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_slabs")))
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60815_()) {
            BlockPos _bp = BlockPos.m_274561_(x, y, z);
            BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_SLAB.get()).m_49966_();
            BlockState _bso = world.m_8055_(_bp);
            UnmodifiableIterator _bexxxxxxx = _bso.m_61148_().entrySet().iterator();

            while (_bexxxxxxx.hasNext()) {
               Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)_bexxxxxxx.next();
               Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
               if (_property != null && _bs.m_61143_(_property) != null) {
                  try {
                     _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                  } catch (Exception var53) {
                  }
               }
            }

            BlockEntity _bexxxxxxxx = world.m_7702_(_bp);
            CompoundTag _bnbtxxxxxxx = null;
            if (_bexxxxxxxx != null) {
               _bnbtxxxxxxx = _bexxxxxxxx.m_187480_();
               _bexxxxxxxx.m_7651_();
            }

            world.m_7731_(_bp, _bs, 3);
            if (_bnbtxxxxxxx != null) {
               BlockEntity var163 = world.m_7702_(_bp);
               if (var163 != null) {
                  try {
                     var163.m_142466_(_bnbtxxxxxxx);
                  } catch (Exception var52) {
                  }
               }
            }
         } else {
            AshfallProcedure.execute(world, x, y, z);
         }

         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.firecharge.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.firecharge.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F,
                  false
               );
            }
         }
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
         AshfallProcedure.execute(world, x, y, z);
      } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != Blocks.f_50440_
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLOWERING_GRASS.get()
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != Blocks.f_50599_
         && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != Blocks.f_152481_) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_fire_ignore")))) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_slabs")))) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_SLAB.get()).m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var218 = _bso.m_61148_().entrySet().iterator();

               while (var218.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var218.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var51) {
                     }
                  }
               }

               BlockEntity _bexxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxx = null;
               if (_bexxxxxxx != null) {
                  _bnbtxxxxxxxx = _bexxxxxxx.m_187480_();
                  _bexxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxx != null) {
                  BlockEntity var220 = world.m_7702_(_bp);
                  if (var220 != null) {
                     try {
                        var220.m_142466_(_bnbtxxxxxxxx);
                     } catch (Exception var50) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_fences")))) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_FENCE.get()).m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var215 = _bso.m_61148_().entrySet().iterator();

               while (var215.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var215.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var49) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxx = null;
               if (_bexxxxxxxxx != null) {
                  _bnbtxxxxxxxxx = _bexxxxxxxxx.m_187480_();
                  _bexxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxx != null) {
                  BlockEntity var217 = world.m_7702_(_bp);
                  if (var217 != null) {
                     try {
                        var217.m_142466_(_bnbtxxxxxxxxx);
                     } catch (Exception var48) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_doors")))
               && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_doors")))) {
               if ((new Object() {
                        public Direction getDirection(BlockPos pos) {
                           BlockState _bs = world.m_8055_(pos);
                           Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                           if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                              return _dir;
                           } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                              return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                           } else {
                              return _bs.m_61138_(BlockStateProperties.f_61364_)
                                 ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                                 : Direction.NORTH;
                           }
                        }
                     })
                     .getDirection(BlockPos.m_274561_(x, y, z))
                  == Direction.NORTH) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
                  world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50016_.m_49966_(), 3);
                  if (world instanceof ServerLevel _serverworld) {
                     StructureTemplate template = _serverworld.m_215082_().m_230359_(new ResourceLocation("ashenremains", "north_door"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworld,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworld.f_46441_,
                           3
                        );
                     }
                  }
               }

               if ((new Object() {
                        public Direction getDirection(BlockPos pos) {
                           BlockState _bs = world.m_8055_(pos);
                           Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                           if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                              return _dir;
                           } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                              return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                           } else {
                              return _bs.m_61138_(BlockStateProperties.f_61364_)
                                 ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                                 : Direction.NORTH;
                           }
                        }
                     })
                     .getDirection(BlockPos.m_274561_(x, y, z))
                  == Direction.SOUTH) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
                  world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50016_.m_49966_(), 3);
                  if (world instanceof ServerLevel _serverworldx) {
                     StructureTemplate template = _serverworldx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "north_door"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworldx,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.CLOCKWISE_180).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworldx.f_46441_,
                           3
                        );
                     }
                  }
               }

               if ((new Object() {
                        public Direction getDirection(BlockPos pos) {
                           BlockState _bs = world.m_8055_(pos);
                           Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                           if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                              return _dir;
                           } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                              return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                           } else {
                              return _bs.m_61138_(BlockStateProperties.f_61364_)
                                 ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                                 : Direction.NORTH;
                           }
                        }
                     })
                     .getDirection(BlockPos.m_274561_(x, y, z))
                  == Direction.EAST) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
                  world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50016_.m_49966_(), 3);
                  if (world instanceof ServerLevel _serverworldxx) {
                     StructureTemplate template = _serverworldxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "north_door"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworldxx,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.CLOCKWISE_90).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworldxx.f_46441_,
                           3
                        );
                     }
                  }
               }

               if ((new Object() {
                        public Direction getDirection(BlockPos pos) {
                           BlockState _bs = world.m_8055_(pos);
                           Property<?> property = _bs.m_60734_().m_49965_().m_61081_("facing");
                           if (property != null && _bs.m_61143_(property) instanceof Direction _dir) {
                              return _dir;
                           } else if (_bs.m_61138_(BlockStateProperties.f_61365_)) {
                              return Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61365_), AxisDirection.POSITIVE);
                           } else {
                              return _bs.m_61138_(BlockStateProperties.f_61364_)
                                 ? Direction.m_122387_((Axis)_bs.m_61143_(BlockStateProperties.f_61364_), AxisDirection.POSITIVE)
                                 : Direction.NORTH;
                           }
                        }
                     })
                     .getDirection(BlockPos.m_274561_(x, y, z))
                  == Direction.WEST) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
                  world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50016_.m_49966_(), 3);
                  if (world instanceof ServerLevel _serverworldxxx) {
                     StructureTemplate template = _serverworldxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "north_door"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworldxxx,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.COUNTERCLOCKWISE_90).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworldxxx.f_46441_,
                           3
                        );
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_trapdoors")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.CHARRED_TRAP_DOOR.get()).m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:wooden_stairs")))) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_STAIRS.get()).m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var212 = _bso.m_61148_().entrySet().iterator();

               while (var212.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var212.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var47) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxx = null;
               if (_bexxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxx = _bexxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxx != null) {
                  BlockEntity var214 = world.m_7702_(_bp);
                  if (var214 != null) {
                     try {
                        var214.m_142466_(_bnbtxxxxxxxxxx);
                     } catch (Exception var46) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50222_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50224_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var209 = _bso.m_61148_().entrySet().iterator();

               while (var209.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var209.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var45) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxx = _bexxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxx != null) {
                  BlockEntity var211 = world.m_7702_(_bp);
                  if (var211 != null) {
                     try {
                        var211.m_142466_(_bnbtxxxxxxxxxxx);
                     } catch (Exception var44) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50176_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50178_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50607_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50609_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var206 = _bso.m_61148_().entrySet().iterator();

               while (var206.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var206.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var43) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxx = _bexxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxx != null) {
                  BlockEntity var208 = world.m_7702_(_bp);
                  if (var208 != null) {
                     try {
                        var208.m_142466_(_bnbtxxxxxxxxxxxx);
                     } catch (Exception var42) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50275_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50274_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var203 = _bso.m_61148_().entrySet().iterator();

               while (var203.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var203.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var41) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxx = _bexxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxx != null) {
                  BlockEntity var205 = world.m_7702_(_bp);
                  if (var205 != null) {
                     try {
                        var205.m_142466_(_bnbtxxxxxxxxxxxxx);
                     } catch (Exception var40) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50633_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50157_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var200 = _bso.m_61148_().entrySet().iterator();

               while (var200.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var200.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var39) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxx != null) {
                  BlockEntity var202 = world.m_7702_(_bp);
                  if (var202 != null) {
                     try {
                        var202.m_142466_(_bnbtxxxxxxxxxxxxxx);
                     } catch (Exception var38) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50647_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50409_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var197 = _bso.m_61148_().entrySet().iterator();

               while (var197.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var197.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var37) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxx != null) {
                  BlockEntity var199 = world.m_7702_(_bp);
                  if (var199 != null) {
                     try {
                        var199.m_142466_(_bnbtxxxxxxxxxxxxxxx);
                     } catch (Exception var36) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50631_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50194_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var194 = _bso.m_61148_().entrySet().iterator();

               while (var194.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var194.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var35) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var196 = world.m_7702_(_bp);
                  if (var196 != null) {
                     try {
                        var196.m_142466_(_bnbtxxxxxxxxxxxxxxxx);
                     } catch (Exception var34) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50645_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50411_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var191 = _bso.m_61148_().entrySet().iterator();

               while (var191.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var191.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var33) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var193 = world.m_7702_(_bp);
                  if (var193 != null) {
                     try {
                        var193.m_142466_(_bnbtxxxxxxxxxxxxxxxxx);
                     } catch (Exception var32) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fence_gates")))) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = ((Block)AshenremainsModBlocks.CHARRED_WOOD_FENCE_GATE.get()).m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var188 = _bso.m_61148_().entrySet().iterator();

               while (var188.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var188.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var31) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var190 = world.m_7702_(_bp);
                  if (var190 != null) {
                     try {
                        var190.m_142466_(_bnbtxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var30) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_220834_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_220864_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var185 = _bso.m_61148_().entrySet().iterator();

               while (var185.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var185.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var29) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var187 = world.m_7702_(_bp);
                  if (var187 != null) {
                     try {
                        var187.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var28) {
                     }
                  }
               }
            }

            if (world instanceof Level _levelx) {
               if (!_levelx.m_5776_()) {
                  _levelx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:leaves")))) {
            AshfallProcedure.execute(world, x, y, z);
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))) {
            AshfallProcedure.execute(world, x, y, z);
         } else {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50077_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
               if (world instanceof ServerLevel _levelxx) {
                  Entity entityToSpawn = EntityType.f_20515_.m_262496_(_levelxx, BlockPos.m_274561_(x + 0.5, y, z + 0.5), MobSpawnType.MOB_SUMMONED);
                  if (entityToSpawn != null) {
                     entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_49997_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50069_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_152469_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_152550_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50079_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50652_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50223_) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50224_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50633_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50157_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var239 = _bso.m_61148_().entrySet().iterator();

               while (var239.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var239.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var27) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var241 = world.m_7702_(_bp);
                  if (var241 != null) {
                     try {
                        var241.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var26) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50647_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50409_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var236 = _bso.m_61148_().entrySet().iterator();

               while (var236.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var236.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var25) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var238 = world.m_7702_(_bp);
                  if (var238 != null) {
                     try {
                        var238.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var24) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50631_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50194_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var233 = _bso.m_61148_().entrySet().iterator();

               while (var233.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var233.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var23) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var235 = world.m_7702_(_bp);
                  if (var235 != null) {
                     try {
                        var235.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var22) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50645_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50411_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var230 = _bso.m_61148_().entrySet().iterator();

               while (var230.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var230.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var21) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var232 = world.m_7702_(_bp);
                  if (var232 != null) {
                     try {
                        var232.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var20) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50607_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50609_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var227 = _bso.m_61148_().entrySet().iterator();

               while (var227.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var227.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var19) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var229 = world.m_7702_(_bp);
                  if (var229 != null) {
                     try {
                        var229.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var18) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50275_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50274_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var224 = _bso.m_61148_().entrySet().iterator();

               while (var224.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var224.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var17) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var226 = world.m_7702_(_bp);
                  if (var226 != null) {
                     try {
                        var226.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var16) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50177_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50178_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var221 = _bso.m_61148_().entrySet().iterator();

               while (var221.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var221.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var15) {
                     }
                  }
               }

               BlockEntity _bexxxxxxxxxxxxxxxxxxxxxxxxxx = world.m_7702_(_bp);
               CompoundTag _bnbtxxxxxxxxxxxxxxxxxxxxxxxxxx = null;
               if (_bexxxxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  _bnbtxxxxxxxxxxxxxxxxxxxxxxxxxx = _bexxxxxxxxxxxxxxxxxxxxxxxxxx.m_187480_();
                  _bexxxxxxxxxxxxxxxxxxxxxxxxxx.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbtxxxxxxxxxxxxxxxxxxxxxxxxxx != null) {
                  BlockEntity var223 = world.m_7702_(_bp);
                  if (var223 != null) {
                     try {
                        var223.m_142466_(_bnbtxxxxxxxxxxxxxxxxxxxxxxxxxx);
                     } catch (Exception var14) {
                     }
                  }
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50718_
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50717_) {
               for (int index0 = 0; index0 < Mth.m_216271_(RandomSource.m_216327_(), 0, 3); index0++) {
                  if (world instanceof ServerLevel _levelxxx) {
                     Entity entityToSpawn = EntityType.f_20550_.m_262496_(_levelxxx, BlockPos.m_274561_(x, y, z), MobSpawnType.MOB_SUMMONED);
                     if (entityToSpawn != null) {
                        entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
                     }
                  }

                  world.m_6443_(Bee.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).stream().sorted((new Object() {
                     Comparator<Entity> compareDistOf(double _x, double _y, double _z) {
                        return Comparator.comparingDouble(_entcnd -> _entcnd.m_20275_(_x, _y, _z));
                     }
                  }).compareDistOf(x, y, z)).findFirst().orElse(null).m_20254_(15);
               }

               world.m_46961_(BlockPos.m_274561_(x, y, z), false);
            }

            if (world instanceof Level _levelxxxx) {
               if (!_levelxxxx.m_5776_()) {
                  _levelxxxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxxxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.ambient")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         }
      } else {
         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:fire")))
            || world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
            || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:fire_ignore")))) {
            world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
         }

         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50493_.m_49966_(), 3);
      }
   }
}
