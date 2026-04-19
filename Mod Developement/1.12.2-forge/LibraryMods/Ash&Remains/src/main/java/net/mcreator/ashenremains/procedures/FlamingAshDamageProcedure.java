package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.init.AshenremainsModEntities;
import net.mcreator.ashenremains.init.AshenremainsModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class FlamingAshDamageProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_14.get()) {
            entity.m_20256_(new Vec3(entity.m_20184_().m_7096_() * 0.1, entity.m_20184_().m_7098_() * 0.9, entity.m_20184_().m_7094_() * 0.1));
            entity.f_19789_ = 0.0F;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_12.get()
            || world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH_LAYER_10.get()) {
            entity.m_20256_(new Vec3(entity.m_20184_().m_7096_() * 0.3, entity.m_20184_().m_7098_() * 0.95, entity.m_20184_().m_7094_() * 0.3));
            entity.f_19789_ = 0.0F;
         } else if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH_LAYER_8.get()
            && world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() != AshenremainsModBlocks.FLAMING_ASH_LAYER_6.get()) {
            entity.m_20256_(new Vec3(entity.m_20184_().m_7096_() * 0.7, entity.m_20184_().m_7098_(), entity.m_20184_().m_7094_() * 0.7));
         } else {
            entity.m_20256_(new Vec3(entity.m_20184_().m_7096_() * 0.5, entity.m_20184_().m_7098_() * 0.98, entity.m_20184_().m_7094_() * 0.5));
            entity.f_19789_ = 0.0F;
         }

         if (entity instanceof ItemEntity) {
            if (!(entity instanceof ItemEntity _itemEnt ? _itemEnt.m_32055_() : ItemStack.f_41583_)
               .m_204117_(ItemTags.create(new ResourceLocation("forge:fire_resist")))) {
               entity.m_20254_(15);
            }
         } else {
            if (Math.random() < 0.003
               && EnchantmentHelper.m_44843_(
                     Enchantments.f_44966_, entity instanceof LivingEntity _entGetArmorx ? _entGetArmorx.m_6844_(EquipmentSlot.FEET) : ItemStack.f_41583_
                  )
                  == 0
               && (entity instanceof LivingEntity _entGetArmor ? _entGetArmor.m_6844_(EquipmentSlot.FEET) : ItemStack.f_41583_).m_41720_() != Items.f_42483_) {
               ItemStack _ist = entity instanceof LivingEntity _entGetArmorxx ? _entGetArmorxx.m_6844_(EquipmentSlot.FEET) : ItemStack.f_41583_;
               if (_ist.m_220157_(3, RandomSource.m_216327_(), null)) {
                  _ist.m_41774_(1);
                  _ist.m_41721_(0);
               }

               if (!(entity instanceof LivingEntity _livEnt41 && _livEnt41.m_21023_(MobEffects.f_19607_))
                  && !entity.m_6095_().m_204039_(TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("forge:ash_immune")))
                  && (entity instanceof LivingEntity _entGetArmorxxx ? _entGetArmorxxx.m_6844_(EquipmentSlot.FEET) : ItemStack.f_41583_).m_41720_()
                     == Blocks.f_50016_.m_5456_()) {
                  entity.m_6469_(new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268631_)), 3.0F);
               }

               if (Math.random() < 0.3 && entity instanceof Creeper) {
                  if (!entity.m_9236_().m_5776_()) {
                     entity.m_146870_();
                  }

                  if (world instanceof ServerLevel _level) {
                     _level.m_8767_((SimpleParticleType)AshenremainsModParticleTypes.ASH_PARTICLES.get(), x, y, z, 15, 0.2, 0.2, 0.2, 0.4);
                  }

                  if (world instanceof Level _level) {
                     if (!_level.m_5776_()) {
                        _level.m_5594_(
                           null,
                           BlockPos.m_274561_(x, y, z),
                           (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.creeper.death")),
                           SoundSource.NEUTRAL,
                           2.0F,
                           1.0F
                        );
                     } else {
                        _level.m_7785_(
                           x,
                           y,
                           z,
                           (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.creeper.death")),
                           SoundSource.NEUTRAL,
                           2.0F,
                           1.0F,
                           false
                        );
                     }
                  }

                  if (world instanceof ServerLevel _levelx) {
                     Entity entityToSpawn = ((EntityType)AshenremainsModEntities.GRIEFER.get())
                        .m_262496_(_levelx, BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()), MobSpawnType.MOB_SUMMONED);
                     if (entityToSpawn != null) {
                        entityToSpawn.m_146922_(entity.m_146908_());
                        entityToSpawn.m_5618_(entity.m_146908_());
                        entityToSpawn.m_5616_(entity.m_146908_());
                        entityToSpawn.m_20334_(entity.m_20184_().m_7096_(), entity.m_20184_().m_7098_(), entity.m_20184_().m_7094_());
                     }
                  }
               }
            }

            if (Math.random() < 0.003
               && EnchantmentHelper.m_44843_(
                     Enchantments.f_44966_, entity instanceof LivingEntity _entGetArmorx ? _entGetArmorx.m_6844_(EquipmentSlot.LEGS) : ItemStack.f_41583_
                  )
                  == 0
               && (entity instanceof LivingEntity _entGetArmor ? _entGetArmor.m_6844_(EquipmentSlot.LEGS) : ItemStack.f_41583_).m_41720_() != Items.f_42482_) {
               ItemStack _istx = entity instanceof LivingEntity _entGetArmorxxx ? _entGetArmorxxx.m_6844_(EquipmentSlot.FEET) : ItemStack.f_41583_;
               if (_istx.m_220157_(3, RandomSource.m_216327_(), null)) {
                  _istx.m_41774_(1);
                  _istx.m_41721_(0);
               }

               if (!(entity instanceof LivingEntity _livEnt65 && _livEnt65.m_21023_(MobEffects.f_19607_))
                  && !entity.m_6095_().m_204039_(TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("forge:ash_immune")))
                  && (entity instanceof LivingEntity _entGetArmorxxxx ? _entGetArmorxxxx.m_6844_(EquipmentSlot.LEGS) : ItemStack.f_41583_).m_41720_()
                     == Blocks.f_50016_.m_5456_()) {
                  entity.m_6469_(new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268631_)), 3.0F);
               }
            }

            if (Math.random() < 0.04 && (entity.m_20184_().m_7094_() > 0.0 || entity.m_20184_().m_7096_() > 0.0) && world instanceof Level _levelxx) {
               if (!_levelxx.m_5776_()) {
                  _levelxx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F
                  );
               } else {
                  _levelxx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     1.0F,
                     false
                  );
               }
            }

            if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:ashblocks")))
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.ASH.get()
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_60734_() == AshenremainsModBlocks.FLAMING_ASH.get()) {
               if (entity instanceof LivingEntity _entity && !_entity.m_9236_().m_5776_()) {
                  _entity.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 300, 1, false, false));
               }

               if (Math.random() < 0.003
                  && EnchantmentHelper.m_44843_(
                        Enchantments.f_44966_, entity instanceof LivingEntity _entGetArmorx ? _entGetArmorx.m_6844_(EquipmentSlot.CHEST) : ItemStack.f_41583_
                     )
                     == 0
                  && (entity instanceof LivingEntity _entGetArmor ? _entGetArmor.m_6844_(EquipmentSlot.CHEST) : ItemStack.f_41583_).m_41720_()
                     != Items.f_42481_) {
                  ItemStack _istxx = entity instanceof LivingEntity _entGetArmorxxxx ? _entGetArmorxxxx.m_6844_(EquipmentSlot.CHEST) : ItemStack.f_41583_;
                  if (_istxx.m_220157_(3, RandomSource.m_216327_(), null)) {
                     _istxx.m_41774_(1);
                     _istxx.m_41721_(0);
                  }

                  if (!(entity instanceof LivingEntity _livEnt87 && _livEnt87.m_21023_(MobEffects.f_19607_))
                     && !entity.m_6095_().m_204039_(TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("forge:ash_immune")))
                     && (entity instanceof LivingEntity _entGetArmorxxxxx ? _entGetArmorxxxxx.m_6844_(EquipmentSlot.CHEST) : ItemStack.f_41583_).m_41720_()
                        == Blocks.f_50016_.m_5456_()) {
                     entity.m_6469_(new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268631_)), 3.0F);
                  }
               }
            }
         }
      }
   }
}
