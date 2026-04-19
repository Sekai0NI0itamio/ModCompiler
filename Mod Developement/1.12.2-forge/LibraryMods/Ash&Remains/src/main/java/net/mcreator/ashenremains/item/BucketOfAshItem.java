package net.mcreator.ashenremains.item;

import net.mcreator.ashenremains.procedures.BucketOfAshPlacedProcedure;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class BucketOfAshItem extends Item {
   public BucketOfAshItem() {
      super(new Properties().m_41487_(1).m_41497_(Rarity.COMMON));
   }

   public UseAnim m_6164_(ItemStack itemstack) {
      return UseAnim.EAT;
   }

   public InteractionResultHolder<ItemStack> m_7203_(Level world, Player entity, InteractionHand hand) {
      InteractionResultHolder<ItemStack> ar = super.m_7203_(world, entity, hand);
      BucketOfAshPlacedProcedure.execute(world, entity.m_20185_(), entity.m_20186_(), entity.m_20189_(), entity, (ItemStack)ar.m_19095_());
      return ar;
   }
}
