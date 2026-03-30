package com.tntduper;

import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BehaviorDispenseTNTDuper extends BehaviorDefaultDispenseItem {
   protected ItemStack func_82487_b(IBlockSource source, ItemStack stack) {
      World world = source.func_82618_k();
      BlockPos blockpos = source.func_180699_d().func_177972_a((EnumFacing)source.func_189992_e().func_177229_b(BlockDispenser.field_176441_a));
      EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(
         world, blockpos.func_177958_n() + 0.5, blockpos.func_177956_o(), blockpos.func_177952_p() + 0.5, null
      );
      world.func_72838_d(entitytntprimed);
      world.func_184148_a(
         null,
         entitytntprimed.field_70165_t,
         entitytntprimed.field_70163_u,
         entitytntprimed.field_70161_v,
         SoundEvents.field_187904_gd,
         SoundCategory.BLOCKS,
         1.0F,
         1.0F
      );
      return stack;
   }
}
