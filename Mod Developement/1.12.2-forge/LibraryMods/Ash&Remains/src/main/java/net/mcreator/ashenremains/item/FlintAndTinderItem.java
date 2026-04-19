package net.mcreator.ashenremains.item;

import net.mcreator.ashenremains.procedures.QuestionableChoiceProcedure;
import net.mcreator.ashenremains.procedures.WeDidntStartTheFireProcedure;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;

public class FlintAndTinderItem extends Item {
   public FlintAndTinderItem() {
      super(new Properties().m_41503_(52).m_41497_(Rarity.COMMON));
   }

   public UseAnim m_6164_(ItemStack itemstack) {
      return UseAnim.EAT;
   }

   public int m_6473_() {
      return 14;
   }

   public InteractionResult m_6225_(UseOnContext context) {
      super.m_6225_(context);
      WeDidntStartTheFireProcedure.execute(
         context.m_43725_(),
         context.m_8083_().m_123341_(),
         context.m_8083_().m_123342_(),
         context.m_8083_().m_123343_(),
         context.m_43725_().m_8055_(context.m_8083_()),
         context.m_43719_(),
         context.m_43723_(),
         context.m_43722_()
      );
      return InteractionResult.SUCCESS;
   }

   public boolean m_7579_(ItemStack itemstack, LivingEntity entity, LivingEntity sourceentity) {
      boolean retval = super.m_7579_(itemstack, entity, sourceentity);
      QuestionableChoiceProcedure.execute(entity.m_9236_(), entity.m_20185_(), entity.m_20186_(), entity.m_20189_(), entity, itemstack);
      return retval;
   }
}
