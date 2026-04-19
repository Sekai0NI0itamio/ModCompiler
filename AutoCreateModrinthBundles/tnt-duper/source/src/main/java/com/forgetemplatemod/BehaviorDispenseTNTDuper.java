package com.forgetemplatemod;

import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BehaviorDispenseTNTDuper extends BehaviorDefaultDispenseItem {
    @Override
    protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
        World world = source.getWorld();
        BlockPos blockpos = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
        EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double)blockpos.getX() + 0.5D, (double)blockpos.getY(), (double)blockpos.getZ() + 0.5D, null);
        world.spawnEntity(entitytntprimed);
        world.playSound(null, entitytntprimed.posX, entitytntprimed.posY, entitytntprimed.posZ, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
        
        // Return the stack unchanged! We intentionally DO NOT call stack.shrink(1)
        // This makes the dispenser act as a TNT Duper without consuming the item.
        return stack;
    }
}