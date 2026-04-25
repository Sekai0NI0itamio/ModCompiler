/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.items;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;

public class VanillaHopperItemHandler extends InvWrapper
{
    private final HopperTileEntity hopper;

    public VanillaHopperItemHandler(HopperTileEntity hopper)
    {
        super(hopper);
        this.hopper = hopper;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        if (simulate)
        {
            return super.insertItem(slot, stack, simulate);
        }
        else
        {
            boolean wasEmpty = getInv().func_191420_l();

            int originalStackSize = stack.func_190916_E();
            stack = super.insertItem(slot, stack, simulate);

            if (wasEmpty && originalStackSize > stack.func_190916_E())
            {
                if (!hopper.func_174914_o())
                {
                    // This cooldown is always set to 8 in vanilla with one exception:
                    // Hopper -> Hopper transfer sets this cooldown to 7 when this hopper
                    // has not been updated as recently as the one pushing items into it.
                    // This vanilla behavior is preserved by VanillaInventoryCodeHooks#insertStack,
                    // the cooldown is set properly by the hopper that is pushing items into this one.
                    hopper.func_145896_c(8);
                }
            }

            return stack;
        }
    }
}
