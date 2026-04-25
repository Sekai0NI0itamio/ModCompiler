/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.items.wrapper;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class RecipeWrapper implements IInventory {

    protected final IItemHandlerModifiable inv;

    public RecipeWrapper(IItemHandlerModifiable inv)
    {
        this.inv = inv;
    }

    /**
     * Returns the size of this inventory.  Must be equivalent to {@link #getHeight()} * {@link #getWidth()}.
     */
    @Override
    public int func_70302_i_()
    {
        return inv.getSlots();
    }

    /**
     * Returns the stack in this slot.  This stack should be a modifiable reference, not a copy of a stack in your inventory.
     */
    @Override
    public ItemStack func_70301_a(int slot)
    {
        return inv.getStackInSlot(slot);
    }

    /**
     * Attempts to remove n items from the specified slot.  Returns the split stack that was removed.  Modifies the inventory.
     */
    @Override
    public ItemStack func_70298_a(int slot, int count)
    {
        ItemStack stack = inv.getStackInSlot(slot);
        return stack.func_190926_b() ? ItemStack.field_190927_a : stack.func_77979_a(count);
    }

    /**
     * Sets the contents of this slot to the provided stack.
     */
    @Override
    public void func_70299_a(int slot, ItemStack stack)
    {
        inv.setStackInSlot(slot, stack);
    }

    /**
     * Removes the stack contained in this slot from the underlying handler, and returns it.
     */
    @Override
    public ItemStack func_70304_b(int index)
    {
        ItemStack s = func_70301_a(index);
        if(s.func_190926_b()) return ItemStack.field_190927_a;
        func_70299_a(index, ItemStack.field_190927_a);
        return s;
    }

    @Override
    public boolean func_191420_l()
    {
        for(int i = 0; i < inv.getSlots(); i++)
        {
            if(!inv.getStackInSlot(i).func_190926_b()) return false;
        }
        return true;
    }

    @Override
    public boolean func_94041_b(int slot, ItemStack stack)
    {
        return inv.isItemValid(slot, stack);
    }

    @Override
    public void func_174888_l() 
    {
        for(int i = 0; i < inv.getSlots(); i++)
        {
            inv.setStackInSlot(i, ItemStack.field_190927_a);
        }
    }

    //The following methods are never used by vanilla in crafting.  They are defunct as mods need not override them.
    @Override
    public int func_70297_j_() { return 0; }
    @Override
    public void func_70296_d() {}
    @Override
    public boolean func_70300_a(PlayerEntity player) { return false; }
    @Override
    public void func_174889_b(PlayerEntity player) {}
    @Override
    public void func_174886_c(PlayerEntity player) {}

}
