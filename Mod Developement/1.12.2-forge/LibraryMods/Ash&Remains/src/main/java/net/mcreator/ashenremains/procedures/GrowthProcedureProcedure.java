package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class GrowthProcedureProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      boolean found = false;
      double Plantnumber = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double SecondaryPlantNumber = 0.0;
      if (world instanceof ServerLevel _level) {
         _level.m_8767_(ParticleTypes.f_123749_, x + 0.5, y + 0.5, z + 0.5, 15, 0.3, 0.3, 0.3, 0.3);
      }

      Plantnumber = Mth.m_216271_(RandomSource.m_216327_(), 0, 8);
      SecondaryPlantNumber = Mth.m_216271_(RandomSource.m_216327_(), 0, 3);
      found = false;
      sx = -4.0;

      for (int index0 = 0; index0 < 8; index0++) {
         sy = -4.0;

         for (int index1 = 0; index1 < 8; index1++) {
            sz = -4.0;

            for (int index2 = 0; index2 < 8; index2++) {
               if (world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:leaves")))
                  || world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:saplings")))) {
                  found = true;
               } else if (Plantnumber < 8.0
                  && Math.random() < 0.3
                  && world.m_8055_(BlockPos.m_274561_(x + sx, y + sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:flowers")))) {
                  Plantnumber++;
               }

               sz++;
            }

            sy++;
         }

         sx++;
      }

      if (world.m_46861_(BlockPos.m_274561_(x, y + 2.0, z))) {
         if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("forest"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
            if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("birch_forest"))
               && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))
               && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("old_growth_birch_forest"))
               && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
               if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("dark_forest"))
                  && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
                  if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_forest"))
                     && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_gravelly_hills"))
                     && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_hills"))) {
                     if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("meadow"))
                        && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("flower_forest"))) {
                        if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("swamp"))
                           && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
                           if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                              .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_savanna")))) {
                              if (Plantnumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 1.0) {
                                 if (world instanceof ServerLevel _serverworld) {
                                    StructureTemplate template = _serverworld.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
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
                              } else if (Plantnumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 4.0) {
                                 if (world instanceof ServerLevel _serverworldx) {
                                    StructureTemplate template = _serverworldx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 5.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 6.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ACACIA_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 7.0) {
                                 if (world instanceof ServerLevel _serverworldxx) {
                                    StructureTemplate template = _serverworldxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 8.0) {
                                 if (SecondaryPlantNumber == 0.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 1.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 2.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 3.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                                 }
                              }
                           } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                              .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_taiga")))) {
                              if (Plantnumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                              } else if (Plantnumber == 2.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 4.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 5.0) {
                                 if (world instanceof ServerLevel _serverworldxxx) {
                                    StructureTemplate template = _serverworldxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "large_fern"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 6.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 7.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 8.0) {
                                 if (SecondaryPlantNumber == 0.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 1.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 2.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50685_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 3.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50685_.m_49966_(), 3);
                                 }
                              }
                           } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                              .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_jungle")))) {
                              if (Plantnumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 1.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.JUNGLE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50570_.m_49966_(), 3);
                              } else if (Plantnumber == 3.0) {
                                 if (world instanceof ServerLevel _serverworldxxxx) {
                                    StructureTemplate template = _serverworldxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "large_fern"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 4.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                              } else if (Plantnumber == 5.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.JUNGLE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 6.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 7.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.JUNGLE_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 8.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
                              }
                           } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                              .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_badlands")))) {
                              if (Plantnumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 1.0) {
                                 if (world instanceof ServerLevel _serverworldxxxxx) {
                                    StructureTemplate template = _serverworldxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 2.0) {
                                 if (world instanceof ServerLevel _serverworldxxxxxx) {
                                    StructureTemplate template = _serverworldxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxxxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxxxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 4.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 5.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50036_.m_49966_(), 3);
                              } else if (Plantnumber == 6.0 && !found) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
                              } else if (Plantnumber == 7.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 8.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50036_.m_49966_(), 3);
                              }
                           } else if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))
                              && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("sunflower_plains"))) {
                              if (world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("cherry_grove"))) {
                                 if (Plantnumber == 0.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                                 } else if (Plantnumber == 1.0) {
                                    if (world instanceof ServerLevel _serverworldxxxxxxx) {
                                       StructureTemplate template = _serverworldxxxxxxx.m_215082_()
                                          .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                       if (template != null) {
                                          template.m_230328_(
                                             _serverworldxxxxxxx,
                                             BlockPos.m_274561_(x, y, z),
                                             BlockPos.m_274561_(x, y, z),
                                             new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                             _serverworldxxxxxxx.f_46441_,
                                             3
                                          );
                                       }
                                    }
                                 } else if (Plantnumber == 2.0) {
                                    if (world instanceof ServerLevel _serverworldxxxxxxxx) {
                                       StructureTemplate template = _serverworldxxxxxxxx.m_215082_()
                                          .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                       if (template != null) {
                                          template.m_230328_(
                                             _serverworldxxxxxxxx,
                                             BlockPos.m_274561_(x, y, z),
                                             BlockPos.m_274561_(x, y, z),
                                             new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                             _serverworldxxxxxxxx.f_46441_,
                                             3
                                          );
                                       }
                                    }
                                 } else if (Plantnumber == 3.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                                 } else if (Plantnumber == 4.0 && !found) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.CHERRY_SPROUT.get()).m_49966_(), 3);
                                 } else if (Plantnumber == 5.0) {
                                    if (world instanceof ServerLevel _level) {
                                       _level.m_7654_()
                                          .m_129892_()
                                          .m_230957_(
                                             new CommandSourceStack(
                                                   CommandSource.f_80164_,
                                                   new Vec3(x, y, z),
                                                   Vec2.f_82462_,
                                                   _level,
                                                   4,
                                                   "",
                                                   Component.m_237113_(""),
                                                   _level.m_7654_(),
                                                   null
                                                )
                                                .m_81324_(),
                                             "/setblock ~ ~ ~ pink_petals[flower_amount=2]"
                                          );
                                    }
                                 } else if (Plantnumber == 6.0 && !found) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.CHERRY_SPROUT.get()).m_49966_(), 3);
                                 } else if (Plantnumber == 7.0) {
                                    if (world instanceof ServerLevel _level) {
                                       _level.m_7654_()
                                          .m_129892_()
                                          .m_230957_(
                                             new CommandSourceStack(
                                                   CommandSource.f_80164_,
                                                   new Vec3(x, y, z),
                                                   Vec2.f_82462_,
                                                   _level,
                                                   4,
                                                   "",
                                                   Component.m_237113_(""),
                                                   _level.m_7654_(),
                                                   null
                                                )
                                                .m_81324_(),
                                             "/setblock ~ ~ ~ pink_petals[flower_amount=3]"
                                          );
                                    }
                                 } else if (Plantnumber == 8.0 && world instanceof ServerLevel _level) {
                                    _level.m_7654_()
                                       .m_129892_()
                                       .m_230957_(
                                          new CommandSourceStack(
                                                CommandSource.f_80164_,
                                                new Vec3(x, y, z),
                                                Vec2.f_82462_,
                                                _level,
                                                4,
                                                "",
                                                Component.m_237113_(""),
                                                _level.m_7654_(),
                                                null
                                             )
                                             .m_81324_(),
                                          "/setblock ~ ~ ~ pink_petals[flower_amount=4]"
                                       );
                                 }
                              } else if (Plantnumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 3.0) {
                                 if (world instanceof ServerLevel _serverworldxxxxxxxxx) {
                                    StructureTemplate template = _serverworldxxxxxxxxx.m_215082_()
                                       .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxxxxxxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxxxxxxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 4.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 5.0) {
                                 if (world instanceof ServerLevel _serverworldxxxxxxxxxx) {
                                    StructureTemplate template = _serverworldxxxxxxxxxx.m_215082_()
                                       .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                    if (template != null) {
                                       template.m_230328_(
                                          _serverworldxxxxxxxxxx,
                                          BlockPos.m_274561_(x, y, z),
                                          BlockPos.m_274561_(x, y, z),
                                          new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                          _serverworldxxxxxxxxxx.f_46441_,
                                          3
                                       );
                                    }
                                 }
                              } else if (Plantnumber == 6.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 7.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                              } else if (Plantnumber == 8.0) {
                                 if (SecondaryPlantNumber == 0.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 1.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 2.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                                 } else if (SecondaryPlantNumber == 3.0) {
                                    world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                                 }
                              }
                           } else if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 2.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 4.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 6.0 && !found) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 8.0) {
                              if (SecondaryPlantNumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                              }
                           }
                        } else if (Plantnumber == 0.0) {
                           if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxx) {
                              StructureTemplate template = _serverworldxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                              if (template != null) {
                                 template.m_230328_(
                                    _serverworldxxxxxxxxxxxxx,
                                    BlockPos.m_274561_(x, y, z),
                                    BlockPos.m_274561_(x, y, z),
                                    new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                    _serverworldxxxxxxxxxxxxx.f_46441_,
                                    3
                                 );
                              }
                           }
                        } else if (Plantnumber == 1.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                        } else if (Plantnumber == 2.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 3.0) {
                           if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxx) {
                              StructureTemplate template = _serverworldxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                              if (template != null) {
                                 template.m_230328_(
                                    _serverworldxxxxxxxxxxxxxx,
                                    BlockPos.m_274561_(x, y, z),
                                    BlockPos.m_274561_(x, y, z),
                                    new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                    _serverworldxxxxxxxxxxxxxx.f_46441_,
                                    3
                                 );
                              }
                           }
                        } else if (Plantnumber == 4.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                        } else if (Plantnumber == 5.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 6.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
                        } else if (Plantnumber == 7.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 8.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50113_.m_49966_(), 3);
                        }
                     } else if (Plantnumber == 0.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 1.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 2.0) {
                        if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxx) {
                           StructureTemplate template = _serverworldxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                           if (template != null) {
                              template.m_230328_(
                                 _serverworldxxxxxxxxxxxxxxx,
                                 BlockPos.m_274561_(x, y, z),
                                 BlockPos.m_274561_(x, y, z),
                                 new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                 _serverworldxxxxxxxxxxxxxxx.f_46441_,
                                 3
                              );
                           }
                        }
                     } else if (Plantnumber == 3.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 4.0 && !found) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
                     } else if (Plantnumber == 5.0) {
                        if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxx) {
                           StructureTemplate template = _serverworldxxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                           if (template != null) {
                              template.m_230328_(
                                 _serverworldxxxxxxxxxxxxxxxx,
                                 BlockPos.m_274561_(x, y, z),
                                 BlockPos.m_274561_(x, y, z),
                                 new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                 _serverworldxxxxxxxxxxxxxxxx.f_46441_,
                                 3
                              );
                           }
                        }
                     } else if (Plantnumber == 6.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 7.0) {
                        if (SecondaryPlantNumber == 0.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 1.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 2.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 3.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                        }
                     } else if (Plantnumber == 8.0) {
                        if (SecondaryPlantNumber == 0.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 1.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50115_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 2.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
                        } else if (SecondaryPlantNumber == 3.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                        }
                     }
                  } else if (Plantnumber == 0.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 1.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 2.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                  } else if (Plantnumber == 3.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 4.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 5.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                  } else if (Plantnumber == 6.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
                  } else if (Plantnumber == 7.0) {
                     if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxx) {
                        StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                        if (template != null) {
                           template.m_230328_(
                              _serverworldxxxxxxxxxxxxxxxxx,
                              BlockPos.m_274561_(x, y, z),
                              BlockPos.m_274561_(x, y, z),
                              new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                              _serverworldxxxxxxxxxxxxxxxxx.f_46441_,
                              3
                           );
                        }
                     }
                  } else if (Plantnumber == 8.0 && world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxx) {
                     StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworldxxxxxxxxxxxxxxxxxx,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworldxxxxxxxxxxxxxxxxxx.f_46441_,
                           3
                        );
                     }
                  }
               } else if (Plantnumber == 0.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 1.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 2.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 3.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
               } else if (Plantnumber == 4.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
               } else if (Plantnumber == 5.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
               } else if (Plantnumber == 6.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
               } else if (Plantnumber == 7.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
               } else if (Plantnumber == 8.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
               }
            } else if (Plantnumber == 0.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
            } else if (Plantnumber == 1.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
            } else if (Plantnumber == 2.0) {
               if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxx) {
                  StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                  if (template != null) {
                     template.m_230328_(
                        _serverworldxxxxxxxxxxxxxxxxxxx,
                        BlockPos.m_274561_(x, y, z),
                        BlockPos.m_274561_(x, y, z),
                        new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                        _serverworldxxxxxxxxxxxxxxxxxxx.f_46441_,
                        3
                     );
                  }
               }
            } else if (Plantnumber == 3.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
            } else if (Plantnumber == 4.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 5.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 6.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 7.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
            } else if (Plantnumber == 8.0) {
               if (SecondaryPlantNumber == 0.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
               } else if (SecondaryPlantNumber == 1.0 && !found) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
               } else if (SecondaryPlantNumber == 2.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
               } else if (SecondaryPlantNumber == 3.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50115_.m_49966_(), 3);
               }
            }
         } else if (Plantnumber == 0.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 1.0) {
            if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxx) {
               StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxx.m_215082_().m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
               if (template != null) {
                  template.m_230328_(
                     _serverworldxxxxxxxxxxxxxxxxxxxx,
                     BlockPos.m_274561_(x, y, z),
                     BlockPos.m_274561_(x, y, z),
                     new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                     _serverworldxxxxxxxxxxxxxxxxxxxx.f_46441_,
                     3
                  );
               }
            }
         } else if (Plantnumber == 2.0 && !found) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
         } else if (Plantnumber == 3.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 4.0 && !found) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
         } else if (Plantnumber == 5.0 && !found) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
         } else if (Plantnumber == 6.0 && !found) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
         } else if (Plantnumber == 7.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 8.0) {
            if (SecondaryPlantNumber == 0.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
            } else if (SecondaryPlantNumber == 1.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
            } else if (SecondaryPlantNumber == 2.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
            } else if (SecondaryPlantNumber == 3.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
            }
         }
      } else if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("forest"))
         && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
         if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("birch_forest"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("old_growth_birch_forest"))
            && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
            if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("dark_forest"))
               && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
               if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_forest"))
                  && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_gravelly_hills"))
                  && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("windswept_hills"))) {
                  if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("meadow"))
                     && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("flower_forest"))) {
                     if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("swamp"))
                        && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))) {
                        if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                           .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_savanna")))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 2.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 4.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 5.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 6.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 8.0) {
                              if (SecondaryPlantNumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                              }
                           }
                        } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                           .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_taiga")))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                           } else if (Plantnumber == 2.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "large_fern"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 4.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "large_fern"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 6.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 8.0) {
                              if (SecondaryPlantNumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50072_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50073_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50685_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50685_.m_49966_(), 3);
                              }
                           }
                        } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                           .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_jungle")))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 2.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50570_.m_49966_(), 3);
                           } else if (Plantnumber == 3.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "large_fern"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 4.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50570_.m_49966_(), 3);
                           } else if (Plantnumber == 6.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50570_.m_49966_(), 3);
                           } else if (Plantnumber == 8.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           }
                        } else if (world.m_204166_(BlockPos.m_274561_(x, y, z))
                           .m_203656_(TagKey.m_203882_(Registries.f_256952_, new ResourceLocation("minecraft:is_badlands")))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 2.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 4.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50036_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50036_.m_49966_(), 3);
                           } else if (Plantnumber == 6.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 8.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50036_.m_49966_(), 3);
                           }
                        } else if (world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("cherry_grove"))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 2.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 4.0 && !found) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              if (world instanceof ServerLevel _level) {
                                 _level.m_7654_()
                                    .m_129892_()
                                    .m_230957_(
                                       new CommandSourceStack(
                                             CommandSource.f_80164_,
                                             new Vec3(x, y, z),
                                             Vec2.f_82462_,
                                             _level,
                                             4,
                                             "",
                                             Component.m_237113_(""),
                                             _level.m_7654_(),
                                             null
                                          )
                                          .m_81324_(),
                                       "/setblock ~ ~ ~ pink_petals[flower_amount=1]"
                                    );
                              }
                           } else if (Plantnumber == 6.0 && !found) {
                              if (world instanceof ServerLevel _level) {
                                 _level.m_7654_()
                                    .m_129892_()
                                    .m_230957_(
                                       new CommandSourceStack(
                                             CommandSource.f_80164_,
                                             new Vec3(x, y, z),
                                             Vec2.f_82462_,
                                             _level,
                                             4,
                                             "",
                                             Component.m_237113_(""),
                                             _level.m_7654_(),
                                             null
                                          )
                                          .m_81324_(),
                                       "/setblock ~ ~ ~ pink_petals[flower_amount=2]"
                                    );
                              }
                           } else if (Plantnumber == 7.0) {
                              if (world instanceof ServerLevel _level) {
                                 _level.m_7654_()
                                    .m_129892_()
                                    .m_230957_(
                                       new CommandSourceStack(
                                             CommandSource.f_80164_,
                                             new Vec3(x, y, z),
                                             Vec2.f_82462_,
                                             _level,
                                             4,
                                             "",
                                             Component.m_237113_(""),
                                             _level.m_7654_(),
                                             null
                                          )
                                          .m_81324_(),
                                       "/setblock ~ ~ ~ pink_petals[flower_amount=3]"
                                    );
                              }
                           } else if (Plantnumber == 8.0 && world instanceof ServerLevel _level) {
                              _level.m_7654_()
                                 .m_129892_()
                                 .m_230957_(
                                    new CommandSourceStack(
                                          CommandSource.f_80164_,
                                          new Vec3(x, y, z),
                                          Vec2.f_82462_,
                                          _level,
                                          4,
                                          "",
                                          Component.m_237113_(""),
                                          _level.m_7654_(),
                                          null
                                       )
                                       .m_81324_(),
                                    "/setblock ~ ~ ~ pink_petals[flower_amount=4]"
                                 );
                           }
                        } else if (!world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("plains"))
                           && !world.m_204166_(BlockPos.m_274561_(x, y, z)).m_203373_(new ResourceLocation("sunflower_plains"))) {
                           if (Plantnumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 1.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 2.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 3.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 4.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 5.0) {
                              if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                                 StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                    .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                                 if (template != null) {
                                    template.m_230328_(
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                       BlockPos.m_274561_(x, y, z),
                                       BlockPos.m_274561_(x, y, z),
                                       new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                       _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                       3
                                    );
                                 }
                              }
                           } else if (Plantnumber == 6.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 7.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                           } else if (Plantnumber == 8.0) {
                              if (SecondaryPlantNumber == 0.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 1.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 2.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                              } else if (SecondaryPlantNumber == 3.0) {
                                 world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                              }
                           }
                        } else if (Plantnumber == 0.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 1.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 2.0) {
                           if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                              StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                 .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                              if (template != null) {
                                 template.m_230328_(
                                    _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                    BlockPos.m_274561_(x, y, z),
                                    BlockPos.m_274561_(x, y, z),
                                    new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                    _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                    3
                                 );
                              }
                           }
                        } else if (Plantnumber == 3.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 4.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 5.0) {
                           if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                              StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                                 .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                              if (template != null) {
                                 template.m_230328_(
                                    _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                    BlockPos.m_274561_(x, y, z),
                                    BlockPos.m_274561_(x, y, z),
                                    new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                    _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                    3
                                 );
                              }
                           }
                        } else if (Plantnumber == 6.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 7.0) {
                           world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                        } else if (Plantnumber == 8.0) {
                           if (SecondaryPlantNumber == 0.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                           } else if (SecondaryPlantNumber == 1.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                           } else if (SecondaryPlantNumber == 2.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                           } else if (SecondaryPlantNumber == 3.0) {
                              world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                           }
                        }
                     } else if (Plantnumber == 0.0) {
                        if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                           StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                              .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                           if (template != null) {
                              template.m_230328_(
                                 _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                 BlockPos.m_274561_(x, y, z),
                                 BlockPos.m_274561_(x, y, z),
                                 new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                 _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                 3
                              );
                           }
                        }
                     } else if (Plantnumber == 1.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                     } else if (Plantnumber == 2.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 3.0) {
                        if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                           StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                              .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                           if (template != null) {
                              template.m_230328_(
                                 _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                                 BlockPos.m_274561_(x, y, z),
                                 BlockPos.m_274561_(x, y, z),
                                 new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                                 _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                                 3
                              );
                           }
                        }
                     } else if (Plantnumber == 4.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
                     } else if (Plantnumber == 5.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 6.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 7.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                     } else if (Plantnumber == 8.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50113_.m_49966_(), 3);
                     }
                  } else if (Plantnumber == 0.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 1.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 2.0) {
                     if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                        StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                           .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                        if (template != null) {
                           template.m_230328_(
                              _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                              BlockPos.m_274561_(x, y, z),
                              BlockPos.m_274561_(x, y, z),
                              new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                              _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                              3
                           );
                        }
                     }
                  } else if (Plantnumber == 3.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 4.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 5.0) {
                     if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                        StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                           .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                        if (template != null) {
                           template.m_230328_(
                              _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                              BlockPos.m_274561_(x, y, z),
                              BlockPos.m_274561_(x, y, z),
                              new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                              _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                              3
                           );
                        }
                     }
                  } else if (Plantnumber == 6.0) {
                     world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
                  } else if (Plantnumber == 7.0) {
                     if (SecondaryPlantNumber == 0.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50121_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 1.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 2.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 3.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                     }
                  } else if (Plantnumber == 8.0) {
                     if (SecondaryPlantNumber == 0.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 1.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50115_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 2.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
                     } else if (SecondaryPlantNumber == 3.0) {
                        world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
                     }
                  }
               } else if (Plantnumber == 0.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 1.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 2.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
               } else if (Plantnumber == 3.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 4.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 5.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
               } else if (Plantnumber == 6.0) {
                  world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
               } else if (Plantnumber == 7.0) {
                  if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                     StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                        .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                     if (template != null) {
                        template.m_230328_(
                           _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                           BlockPos.m_274561_(x, y, z),
                           BlockPos.m_274561_(x, y, z),
                           new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                           _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                           3
                        );
                     }
                  }
               } else if (Plantnumber == 8.0 && world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
                  StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                     .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
                  if (template != null) {
                     template.m_230328_(
                        _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                        BlockPos.m_274561_(x, y, z),
                        BlockPos.m_274561_(x, y, z),
                        new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                        _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                        3
                     );
                  }
               }
            } else if (Plantnumber == 0.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 1.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
            } else if (Plantnumber == 3.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50073_.m_49966_(), 3);
            } else if (Plantnumber == 4.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
            } else if (Plantnumber == 5.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
            } else if (Plantnumber == 6.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
            } else if (Plantnumber == 7.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50072_.m_49966_(), 3);
            } else if (Plantnumber == 8.0 && !found) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
            }
         } else if (Plantnumber == 0.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 1.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 2.0) {
            if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
               StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                  .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
               if (template != null) {
                  template.m_230328_(
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                     BlockPos.m_274561_(x, y, z),
                     BlockPos.m_274561_(x, y, z),
                     new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                     3
                  );
               }
            }
         } else if (Plantnumber == 3.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 4.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 5.0) {
            if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
               StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                  .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
               if (template != null) {
                  template.m_230328_(
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                     BlockPos.m_274561_(x, y, z),
                     BlockPos.m_274561_(x, y, z),
                     new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                     3
                  );
               }
            }
         } else if (Plantnumber == 6.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
         } else if (Plantnumber == 7.0) {
            if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
               StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
                  .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
               if (template != null) {
                  template.m_230328_(
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                     BlockPos.m_274561_(x, y, z),
                     BlockPos.m_274561_(x, y, z),
                     new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                     _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                     3
                  );
               }
            }
         } else if (Plantnumber == 8.0) {
            if (SecondaryPlantNumber == 0.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50114_.m_49966_(), 3);
            } else if (SecondaryPlantNumber == 1.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50115_.m_49966_(), 3);
            } else if (SecondaryPlantNumber == 2.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50120_.m_49966_(), 3);
            } else if (SecondaryPlantNumber == 3.0) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50071_.m_49966_(), 3);
            }
         }
      } else if (Plantnumber == 0.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
      } else if (Plantnumber == 1.0) {
         if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
            StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
               .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
            if (template != null) {
               template.m_230328_(
                  _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                  BlockPos.m_274561_(x, y, z),
                  BlockPos.m_274561_(x, y, z),
                  new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                  _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                  3
               );
            }
         }
      } else if (Plantnumber == 2.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
      } else if (Plantnumber == 3.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
      } else if (Plantnumber == 4.0) {
         if (world instanceof ServerLevel _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx) {
            StructureTemplate template = _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.m_215082_()
               .m_230359_(new ResourceLocation("ashenremains", "tall_grass"));
            if (template != null) {
               template.m_230328_(
                  _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx,
                  BlockPos.m_274561_(x, y, z),
                  BlockPos.m_274561_(x, y, z),
                  new StructurePlaceSettings().m_74379_(Rotation.NONE).m_74377_(Mirror.NONE).m_74392_(false),
                  _serverworldxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.f_46441_,
                  3
               );
            }
         }
      } else if (Plantnumber == 5.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50035_.m_49966_(), 3);
      } else if (Plantnumber == 6.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
      } else if (Plantnumber == 7.0) {
         world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50034_.m_49966_(), 3);
      } else if (Plantnumber == 8.0) {
         if (SecondaryPlantNumber == 0.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
         } else if (SecondaryPlantNumber == 1.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50112_.m_49966_(), 3);
         } else if (SecondaryPlantNumber == 2.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
         } else if (SecondaryPlantNumber == 3.0) {
            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50111_.m_49966_(), 3);
         }
      }
   }
}
