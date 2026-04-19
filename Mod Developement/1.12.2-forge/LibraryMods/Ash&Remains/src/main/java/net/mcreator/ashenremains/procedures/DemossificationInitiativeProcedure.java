package net.mcreator.ashenremains.procedures;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
public class DemossificationInitiativeProcedure {
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
         if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() instanceof HoeItem) {
            if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50079_
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FAKE_COBBLESTONE.get()
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.BLOSSOMING_COBBLESTONE.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50652_.m_49966_(), 3);
               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50223_
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FAKE_STONE_BRICKS.get()
               || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS.get()) {
               world.m_7731_(BlockPos.m_274561_(x, y, z), Blocks.f_50222_.m_49966_(), 3);
               if (world instanceof Level _levelx) {
                  if (!_levelx.m_5776_()) {
                     _levelx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50633_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50157_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator _be = _bso.m_61148_().entrySet().iterator();

               while (_be.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)_be.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var28) {
                     }
                  }
               }

               BlockEntity _bex = world.m_7702_(_bp);
               CompoundTag _bnbt = null;
               if (_bex != null) {
                  _bnbt = _bex.m_187480_();
                  _bex.m_7651_();
               }

               world.m_7731_(_bp, _bs, 3);
               if (_bnbt != null) {
                  BlockEntity var69 = world.m_7702_(_bp);
                  if (var69 != null) {
                     try {
                        var69.m_142466_(_bnbt);
                     } catch (Exception var27) {
                     }
                  }
               }

               if (world instanceof Level _levelxx) {
                  if (!_levelxx.m_5776_()) {
                     _levelxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50631_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50194_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var70 = _bso.m_61148_().entrySet().iterator();

               while (var70.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var70.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var26) {
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
                  BlockEntity var72 = world.m_7702_(_bp);
                  if (var72 != null) {
                     try {
                        var72.m_142466_(_bnbtx);
                     } catch (Exception var25) {
                     }
                  }
               }

               if (world instanceof Level _levelxxx) {
                  if (!_levelxxx.m_5776_()) {
                     _levelxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50647_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50409_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var73 = _bso.m_61148_().entrySet().iterator();

               while (var73.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var73.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var24) {
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
                  BlockEntity var75 = world.m_7702_(_bp);
                  if (var75 != null) {
                     try {
                        var75.m_142466_(_bnbtxx);
                     } catch (Exception var23) {
                     }
                  }
               }

               if (world instanceof Level _levelxxxx) {
                  if (!_levelxxxx.m_5776_()) {
                     _levelxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50645_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50411_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var76 = _bso.m_61148_().entrySet().iterator();

               while (var76.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var76.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var22) {
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
                  BlockEntity var78 = world.m_7702_(_bp);
                  if (var78 != null) {
                     try {
                        var78.m_142466_(_bnbtxxx);
                     } catch (Exception var21) {
                     }
                  }
               }

               if (world instanceof Level _levelxxxxx) {
                  if (!_levelxxxxx.m_5776_()) {
                     _levelxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50275_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50274_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var79 = _bso.m_61148_().entrySet().iterator();

               while (var79.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var79.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var20) {
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
                  BlockEntity var81 = world.m_7702_(_bp);
                  if (var81 != null) {
                     try {
                        var81.m_142466_(_bnbtxxxx);
                     } catch (Exception var19) {
                     }
                  }
               }

               if (world instanceof Level _levelxxxxxx) {
                  if (!_levelxxxxxx.m_5776_()) {
                     _levelxxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50607_) {
               BlockPos _bp = BlockPos.m_274561_(x, y, z);
               BlockState _bs = Blocks.f_50609_.m_49966_();
               BlockState _bso = world.m_8055_(_bp);
               UnmodifiableIterator var82 = _bso.m_61148_().entrySet().iterator();

               while (var82.hasNext()) {
                  Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var82.next();
                  Property _property = _bs.m_60734_().m_49965_().m_61081_(entry.getKey().m_61708_());
                  if (_property != null && _bs.m_61143_(_property) != null) {
                     try {
                        _bs = (BlockState)_bs.m_61124_(_property, entry.getValue());
                     } catch (Exception var18) {
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
                  BlockEntity var84 = world.m_7702_(_bp);
                  if (var84 != null) {
                     try {
                        var84.m_142466_(_bnbtxxxxx);
                     } catch (Exception var17) {
                     }
                  }
               }

               if (world instanceof Level _levelxxxxxxx) {
                  if (!_levelxxxxxxx.m_5776_()) {
                     _levelxxxxxxx.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _levelxxxxxxx.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.sheep.shear")),
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }

               ItemStack _ist = entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_;
               if (_ist.m_220157_(Mth.m_216271_(RandomSource.m_216327_(), 0, 1), RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }
            }
         }
      }
   }
}
