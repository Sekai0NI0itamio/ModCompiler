/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;

public class MendingEnchantment
extends Enchantment {
    public MendingEnchantment(Enchantment.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isTreasure() {
        return true;
    }
}

