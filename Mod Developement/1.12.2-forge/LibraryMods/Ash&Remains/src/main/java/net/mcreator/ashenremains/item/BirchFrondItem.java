package net.mcreator.ashenremains.item;

import net.mcreator.ashenremains.procedures.ManualPlantingProcedure;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;

public class BirchFrondItem extends Item {
   public BirchFrondItem() {
      super(new Properties().m_41487_(64).m_41497_(Rarity.COMMON));
   }

   public UseAnim m_6164_(ItemStack itemstack) {
      return UseAnim.EAT;
   }

   public InteractionResult m_6225_(UseOnContext context) {
      super.m_6225_(context);
      ManualPlantingProcedure.execute(
         context.m_43725_(), context.m_8083_().m_123341_(), context.m_8083_().m_123342_(), context.m_8083_().m_123343_(), context.m_43723_()
      );
      return InteractionResult.SUCCESS;
   }
}
