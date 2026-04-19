package net.mcreator.ashenremains.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LevelAccessor;

public class CharredSilkProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)
            && EnchantmentHelper.m_44843_(Enchantments.f_44985_, entity instanceof LivingEntity _livEnt ? _livEnt.m_21205_() : ItemStack.f_41583_) == 0) {
            AshfallProcedure.execute(world, x, y, z);
         }

         AdditionalCharredSoundProcedure.execute(world, x, y, z);
      }
   }
}
