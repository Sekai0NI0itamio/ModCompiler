package net.mcreator.ashenremains.procedures;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LevelAccessor;

public class PunchingFlamesProcedure {
   public static void execute(LevelAccessor world, Entity entity) {
      if (entity != null) {
         if ((
               !((entity instanceof LivingEntity _livEntxxxxxxx ? _livEntxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() instanceof PickaxeItem)
                  || (entity instanceof LivingEntity _livEntxxxxxx ? _livEntxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42395_
            )
            && (
               !((entity instanceof LivingEntity _livEntxxxxx ? _livEntxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() instanceof AxeItem)
                  || (entity instanceof LivingEntity _livEntxxxx ? _livEntxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42396_
            )
            && (
               !((entity instanceof LivingEntity _livEntxxx ? _livEntxxx.m_21205_() : ItemStack.f_41583_).m_41720_() instanceof ShovelItem)
                  || (entity instanceof LivingEntity _livEntxx ? _livEntxx.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42394_
            )
            && (
               !((entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41720_() instanceof HoeItem)
                  || (entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42397_
            )) {
            if ((entity instanceof LivingEntity _livEntxxxxxxxx ? _livEntxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42447_) {
               if (entity instanceof LivingEntity _entity) {
                  ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
                  _setstack.m_41764_(1);
                  _entity.m_21008_(InteractionHand.MAIN_HAND, _setstack);
                  if (_entity instanceof Player _player) {
                     _player.m_150109_().m_6596_();
                  }
               }
            } else if ((entity instanceof LivingEntity _livEntxxxxxxxxx ? _livEntxxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42589_) {
               if (entity instanceof LivingEntity _entityx) {
                  ItemStack _setstack = new ItemStack(Items.f_42590_).m_41777_();
                  _setstack.m_41764_(1);
                  _entityx.m_21008_(InteractionHand.MAIN_HAND, _setstack);
                  if (_entityx instanceof Player _player) {
                     _player.m_150109_().m_6596_();
                  }
               }
            } else if (!(entity instanceof LivingEntity _livEnt24 && _livEnt24.m_21023_(MobEffects.f_19607_))
               && EnchantmentHelper.m_44843_(
                     Enchantments.f_44966_, entity instanceof LivingEntity _entGetArmor ? _entGetArmor.m_6844_(EquipmentSlot.CHEST) : ItemStack.f_41583_
                  )
                  == 0
               && (entity instanceof LivingEntity _livEntxxxxxxxxxxxxxx ? _livEntxxxxxxxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() != Items.f_42397_
               && (entity instanceof LivingEntity _livEntxxxxxxxxxxxxx ? _livEntxxxxxxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() != Items.f_42394_
               && (entity instanceof LivingEntity _livEntxxxxxxxxxxxx ? _livEntxxxxxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() != Items.f_42396_
               && (entity instanceof LivingEntity _livEntxxxxxxxxxxx ? _livEntxxxxxxxxxxx.m_21205_() : ItemStack.f_41583_).m_41720_() != Items.f_42395_
               && (entity instanceof LivingEntity _livEntxxxxxxxxxx ? _livEntxxxxxxxxxx.m_21223_() : -1.0F) > 4.0F) {
               entity.m_6469_(
                  new DamageSource(world.m_9598_().m_175515_(Registries.f_268580_).m_246971_(DamageTypes.f_268468_)),
                  Mth.m_216271_(RandomSource.m_216327_(), 1, 3)
               );
            }
         } else {
            ItemStack _ist = entity instanceof LivingEntity _livEntxxxxxxxxxx ? _livEntxxxxxxxxxx.m_21205_() : ItemStack.f_41583_;
            if (_ist.m_220157_(2, RandomSource.m_216327_(), null)) {
               _ist.m_41774_(1);
               _ist.m_41721_(0);
            }
         }
      }
   }
}
