package net.mcreator.ashenremains.procedures;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class FireEntityDamageProcedure {
   public static void execute(Entity entity) {
      if (entity != null) {
         if (!(entity instanceof LivingEntity _livEnt0 && _livEnt0.m_21023_(MobEffects.f_19607_))
            && !(entity instanceof ItemEntity _itemEnt ? _itemEnt.m_32055_() : ItemStack.f_41583_)
               .m_204117_(ItemTags.create(new ResourceLocation("forge:fire_resist")))) {
            entity.m_20254_(Mth.m_216271_(RandomSource.m_216327_(), 6, 10));
         }
      }
   }
}
