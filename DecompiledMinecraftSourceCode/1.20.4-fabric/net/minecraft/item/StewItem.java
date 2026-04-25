/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class StewItem
extends Item {
    public StewItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        PlayerEntity playerEntity;
        ItemStack itemStack = super.finishUsing(stack, world, user);
        if (user instanceof PlayerEntity && (playerEntity = (PlayerEntity)user).isInCreativeMode()) {
            return itemStack;
        }
        return new ItemStack(Items.BOWL);
    }
}

