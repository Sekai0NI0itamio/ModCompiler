package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class WeDidntStartTheFireProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate, Direction direction, Entity entity, ItemStack itemstack) {
      if (direction != null && entity != null) {
         if (entity instanceof LivingEntity _entity) {
            _entity.m_21011_(InteractionHand.MAIN_HAND, true);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:candles")))) {
            CandleIgnitionProcedure.execute(world, x, y, z, blockstate, entity, itemstack);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50683_) {
            if (world instanceof ServerLevel _level) {
               _level.m_7654_()
                  .m_129892_()
                  .m_230957_(
                     new CommandSourceStack(
                           CommandSource.f_80164_, new Vec3(x, y, z), Vec2.f_82462_, _level, 4, "", Component.m_237113_(""), _level.m_7654_(), null
                        )
                        .m_81324_(),
                     "setblock ~ ~ ~ campfire"
                  );
            }

            if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_) && itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
               itemstack.m_41774_(1);
               itemstack.m_41721_(0);
            }

            if (world instanceof Level _level) {
               if (!_level.m_5776_()) {
                  _level.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _level.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50684_) {
            if (world instanceof ServerLevel _levelx) {
               _levelx.m_7654_()
                  .m_129892_()
                  .m_230957_(
                     new CommandSourceStack(
                           CommandSource.f_80164_, new Vec3(x, y, z), Vec2.f_82462_, _levelx, 4, "", Component.m_237113_(""), _levelx.m_7654_(), null
                        )
                        .m_81324_(),
                     "setblock ~ ~ ~ soul_campfire"
                  );
            }

            if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_) && itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
               itemstack.m_41774_(1);
               itemstack.m_41721_(0);
            }

            if (world instanceof Level _levelx) {
               if (!_levelx.m_5776_()) {
                  _levelx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50077_) {
            if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_) && itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
               itemstack.m_41774_(1);
               itemstack.m_41721_(0);
            }

            world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50016_.m_49966_(), 3);
            if (world instanceof ServerLevel _levelxx) {
               Entity entityToSpawn = EntityType.f_20515_.m_262496_(_levelxx, BlockPos.m_274561_(x + 0.5, y, z + 0.5), MobSpawnType.MOB_SUMMONED);
               if (entityToSpawn != null) {
                  entityToSpawn.m_146922_(world.m_213780_().m_188501_() * 360.0F);
               }
            }

            if (world instanceof Level _levelxxx) {
               if (!_levelxxx.m_5776_()) {
                  _levelxxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }

            if (world instanceof Level _levelxxxx) {
               if (!_levelxxxx.m_5776_()) {
                  _levelxxxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.tnt.primed")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxxxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.tnt.primed")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         }

         if (world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
            && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60815_()
            && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))
               && direction == Direction.UP) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50084_.m_49966_(), 3);
               if (world instanceof Level _levelxxxxx) {
                  if (!_levelxxxxx.m_5776_()) {
                     _levelxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }
            } else if (direction == Direction.UP) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), Blocks.f_50083_.m_49966_(), 3);
               if (world instanceof Level _levelxxxxxx) {
                  if (!_levelxxxxxx.m_5776_()) {
                     _levelxxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }
            }
         } else if ((
               world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                  || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
            )
            && (!world.m_6106_().m_6533_() || !world.m_46861_(BlockPos.m_274561_(x, y + 1.0, z)))
            && Math.random() < 1.0) {
            if (direction == Direction.UP
               && world.m_46859_(BlockPos.m_274561_(x, y + 1.0, z))
               && !world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            } else if (direction == Direction.DOWN
               && world.m_46859_(BlockPos.m_274561_(x, y - 1.0, z))
               && !world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.STRANGE_FIRE.get()).m_49966_(), 3);
            } else if (direction == Direction.NORTH
               && world.m_46859_(BlockPos.m_274561_(x, y, z - 1.0))
               && !world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), ((Block)AshenremainsModBlocks.NORTH_FIRE.get()).m_49966_(), 3);
            } else if (direction == Direction.SOUTH
               && world.m_46859_(BlockPos.m_274561_(x, y, z + 1.0))
               && !world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), ((Block)AshenremainsModBlocks.SOUTH_FIRE.get()).m_49966_(), 3);
            } else if (direction == Direction.WEST
               && world.m_46859_(BlockPos.m_274561_(x - 1.0, y, z))
               && !world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), ((Block)AshenremainsModBlocks.WEST_FIRE.get()).m_49966_(), 3);
            } else if (direction == Direction.EAST
               && world.m_46859_(BlockPos.m_274561_(x + 1.0, y, z))
               && !world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60819_().m_76170_()
               && !(world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() instanceof LiquidBlock)) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), ((Block)AshenremainsModBlocks.EAST_FIRE.get()).m_49966_(), 3);
            }

            if (world instanceof Level _levelxxxxxxx) {
               if (!_levelxxxxxxx.m_5776_()) {
                  _levelxxxxxxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.BLOCKS,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxxxxxxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                     SoundSource.BLOCKS,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }
         } else if (world instanceof Level _levelxxxxxxxx) {
            if (!_levelxxxxxxxx.m_5776_()) {
               _levelxxxxxxxx.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.BLOCKS,
                  1.0F,
                  1.0F
               );
            } else {
               _levelxxxxxxxx.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.BLOCKS,
                  1.0F,
                  1.0F,
                  false
               );
            }
         }

         if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_) && itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
            itemstack.m_41774_(1);
            itemstack.m_41721_(0);
         }
      }
   }
}
