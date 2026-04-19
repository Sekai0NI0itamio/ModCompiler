package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class CandleIgnitionProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate, Entity entity, ItemStack itemstack) {
      if (entity != null) {
         if (!(blockstate.m_60734_().m_49965_().m_61081_("lit") instanceof BooleanProperty _getbp1 && (Boolean)blockstate.m_61143_(_getbp1))) {
            if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_) && itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
               itemstack.m_41774_(1);
               itemstack.m_41721_(0);
            }

            AshenremainsMod.queueServerWork(1, () -> {
               BlockPos _pos = BlockPos.m_274561_(x, y, z);
               BlockState _bs = world.m_8055_(_pos);
               if (_bs.m_60734_().m_49965_().m_61081_("lit") instanceof BooleanProperty _booleanProp) {
                  world.m_7731_(_pos, (BlockState)_bs.m_61124_(_booleanProp, true), 3);
               }
            });
         }
      }
   }
}
