/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.brewing;

import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionBrewing;

/**
 * Used in BrewingRecipeRegistry to maintain the vanilla behaviour.
 *
 * Most of the code was simply adapted from net.minecraft.tileentity.TileEntityBrewingStand
 */
public class VanillaBrewingRecipe implements IBrewingRecipe {

    /**
     * Code adapted from TileEntityBrewingStand.isItemValidForSlot(int index, ItemStack stack)
     */
    @Override
    public boolean isInput(ItemStack stack)
    {
        Item item = stack.func_77973_b();
        return item == Items.field_151068_bn || item == Items.field_185155_bH || item == Items.field_185156_bI || item == Items.field_151069_bo;
    }

    /**
     * Code adapted from TileEntityBrewingStand.isItemValidForSlot(int index, ItemStack stack)
     */
    @Override
    public boolean isIngredient(ItemStack stack)
    {
        return PotionBrewing.func_185205_a(stack);
    }

    /**
     * Code copied from TileEntityBrewingStand.brewPotions()
     * It brews the potion by doing the bit-shifting magic and then checking if the new PotionEffect list is different to the old one,
     * or if the new potion is a splash potion when the old one wasn't.
     */
    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient)
    {
        if (!input.func_190926_b() && !ingredient.func_190926_b() && isIngredient(ingredient))
        {
            ItemStack result = PotionBrewing.func_185212_d(ingredient, input);
            if (result != input)
            {
                return result;
            }
            return ItemStack.field_190927_a;
        }

        return ItemStack.field_190927_a;
    }
}
