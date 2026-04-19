package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
public class AshShrinkProcedure {
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
         if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42589_) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_14.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelx) {
                  _levelx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 1.0, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_14.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_12.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelx) {
                  if (!_levelx.m_5776_()) {
                     _levelx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxx) {
                  _levelxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.85, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_12.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_10.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelxx) {
                  if (!_levelxx.m_5776_()) {
                     _levelxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxxx) {
                  _levelxxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.7, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_10.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_8.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelxxx) {
                  if (!_levelxxx.m_5776_()) {
                     _levelxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxxxx) {
                  _levelxxxx.m_8767_(ParticleTypes.f_123804_, x, y, z, 9, 0.2, 0.2, 0.2, 1.0);
               }

               if (world instanceof ServerLevel _levelxxxx) {
                  _levelxxxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.6, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_8.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_6.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelxxxx) {
                  if (!_levelxxxx.m_5776_()) {
                     _levelxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxxxxx) {
                  _levelxxxxx.m_8767_(ParticleTypes.f_123804_, x, y, z, 9, 0.2, 0.2, 0.2, 1.0);
               }

               if (world instanceof ServerLevel _levelxxxxx) {
                  _levelxxxxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.5, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_6.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_4.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelxxxxx) {
                  if (!_levelxxxxx.m_5776_()) {
                     _levelxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxxxxxx) {
                  _levelxxxxxx.m_8767_(ParticleTypes.f_123804_, x, y, z, 9, 0.2, 0.2, 0.2, 1.0);
               }

               if (world instanceof ServerLevel _levelxxxxxx) {
                  _levelxxxxxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.3, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.ASH_LAYER_4.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), ((Block)AshenremainsModBlocks.ASH_LAYER_2.get()).m_49966_(), 3);
               if (!(new Object() {
                     public boolean checkGamemode(Entity _ent) {
                        if (_ent instanceof ServerPlayer _serverPlayer) {
                           return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                        } else {
                           return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                              ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                                 && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                              : false;
                        }
                     }
                  })
                  .checkGamemode(entity)) {
                  (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
                  if (entity instanceof Player _player) {
                     ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                     _setstack.m_41764_(1);
                     ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                  }
               }

               if (world instanceof Level _levelxxxxxx) {
                  if (!_levelxxxxxx.m_5776_()) {
                     _levelxxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.splash")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               if (world instanceof ServerLevel _levelxxxxxxx) {
                  _levelxxxxxxx.m_8767_(ParticleTypes.f_123804_, x, y, z, 9, 0.2, 0.2, 0.2, 1.0);
               }

               if (world instanceof ServerLevel _levelxxxxxxx) {
                  _levelxxxxxxx.m_8767_(ParticleTypes.f_123804_, x + 0.5, y + 0.2, z + 0.5, 9, 0.2, 0.2, 0.2, 1.0);
               }
            }
         }
      }
   }
}
