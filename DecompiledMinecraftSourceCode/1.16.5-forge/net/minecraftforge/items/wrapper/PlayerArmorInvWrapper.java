/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.items.wrapper;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class PlayerArmorInvWrapper extends RangedWrapper
{
    private final PlayerInventory inventoryPlayer;

    public PlayerArmorInvWrapper(PlayerInventory inv)
    {
        super(new InvWrapper(inv), inv.field_70462_a.size(), inv.field_70462_a.size() + inv.field_70460_b.size());
        inventoryPlayer = inv;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        EquipmentSlotType equ = null;
        for (EquipmentSlotType s : EquipmentSlotType.values())
        {
            if (s.func_188453_a() == EquipmentSlotType.Group.ARMOR && s.func_188454_b() == slot)
            {
                equ = s;
                break;
            }
        }
        // check if it's valid for the armor slot
        if (equ != null && slot < 4 && !stack.func_190926_b() && stack.canEquip(equ, getInventoryPlayer().field_70458_d))
        {
            return super.insertItem(slot, stack, simulate);
        }
        return stack;
    }

    public PlayerInventory getInventoryPlayer()
    {
        return inventoryPlayer;
    }
}
