package net.mcreator.ashenremains.item;

import net.mcreator.ashenremains.procedures.ManualPlantingProcedure;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;

public class JunglePodItem extends Item {
   public JunglePodItem() {
      super(new Properties().m_41487_(64).m_41497_(Rarity.COMMON).m_41489_(new Builder().m_38760_(1).m_38758_(0.2F).m_38767_()));
   }

   public InteractionResult m_6225_(UseOnContext context) {
      super.m_6225_(context);
      ManualPlantingProcedure.execute(
         context.m_43725_(), context.m_8083_().m_123341_(), context.m_8083_().m_123342_(), context.m_8083_().m_123343_(), context.m_43723_()
      );
      return InteractionResult.SUCCESS;
   }
}
