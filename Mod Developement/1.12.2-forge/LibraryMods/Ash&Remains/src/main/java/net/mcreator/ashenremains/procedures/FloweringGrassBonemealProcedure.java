package net.mcreator.ashenremains.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;

public class FloweringGrassBonemealProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if ((entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_).m_41720_() == Items.f_42499_) {
            GrowthProcedureProcedure.execute(world, x, y + 1.0, z);
            if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
               (entity instanceof LivingEntity _livEntx ? _livEntx.m_21205_() : ItemStack.f_41583_).m_41774_(1);
            }
         }
      }
   }
}
