/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;

public class PiercingEnchantment
extends Enchantment {
    public PiercingEnchantment(Enchantment.Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAccept(Enchantment other) {
        return super.canAccept(other) && other != Enchantments.MULTISHOT;
    }
}

