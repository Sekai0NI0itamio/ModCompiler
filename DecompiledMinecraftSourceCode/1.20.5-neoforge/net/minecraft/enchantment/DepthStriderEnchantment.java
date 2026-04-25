/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;

public class DepthStriderEnchantment
extends Enchantment {
    public DepthStriderEnchantment(Enchantment.Properties weight) {
        super(weight);
    }

    @Override
    public boolean canAccept(Enchantment other) {
        return super.canAccept(other) && other != Enchantments.FROST_WALKER;
    }
}

