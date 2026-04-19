package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
public class ManualPlantingProcedure {
   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      if (event.getHand() == event.getEntity().m_7655_()) {
         execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_(), event.getEntity());
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      execute(null, world, x, y, z, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50599_
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50493_
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_152549_
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50440_
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLOWERING_GRASS.get()) {
            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.OAK_ACORN.get()) {
               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.OAK_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.DARK_ACORN.get()) {
               if (world instanceof Level _levelx) {
                  if (!_levelx.m_5776_()) {
                     _levelx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.DARK_OAK_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.BIRCH_FROND.get()) {
               if (world instanceof Level _levelxx) {
                  if (!_levelxx.m_5776_()) {
                     _levelxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.BIRCH_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.ACACIA_FROND.get()) {
               if (world instanceof Level _levelxxx) {
                  if (!_levelxxx.m_5776_()) {
                     _levelxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.ACACIA_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.JUNGLE_POD.get()) {
               if (world instanceof Level _levelxxxx) {
                  if (!_levelxxxx.m_5776_()) {
                     _levelxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.JUNGLE_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.PINE_CONE.get()) {
               if (world instanceof Level _levelxxxxx) {
                  if (!_levelxxxxx.m_5776_()) {
                     _levelxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.SPRUCE_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }

            if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == AshenremainsModItems.CHERRY_SEED.get()) {
               if (world instanceof Level _levelxxxxxx) {
                  if (!_levelxxxxxx.m_5776_()) {
                     _levelxxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.crop.plant")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z), ((Block)AshenremainsModBlocks.CHERRY_SPROUT.get()).m_49966_(), 3);
               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
               }
            }
         }
      }
   }
}
