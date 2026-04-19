package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.items.ItemHandlerHelper;

public class AshGrabbedProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42446_) {
            (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
            if (entity instanceof Player _player) {
               ItemStack _setstack = new ItemStack((ItemLike)AshenremainsModItems.BUCKET_OF_ASH.get()).m_41777_();
               _setstack.m_41764_(1);
               ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
            }

            world.m_46961_(BlockPos.m_274561_(x, y, z), false);
         }
      }
   }
}
