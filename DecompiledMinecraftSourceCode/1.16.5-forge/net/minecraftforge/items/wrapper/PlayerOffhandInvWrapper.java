/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.items.wrapper;

import net.minecraft.entity.player.PlayerInventory;

public class PlayerOffhandInvWrapper extends RangedWrapper
{
    public PlayerOffhandInvWrapper(PlayerInventory inv)
    {
        super(new InvWrapper(inv), inv.field_70462_a.size() + inv.field_70460_b.size(),
                inv.field_70462_a.size() + inv.field_70460_b.size() + inv.field_184439_c.size());
    }
}
