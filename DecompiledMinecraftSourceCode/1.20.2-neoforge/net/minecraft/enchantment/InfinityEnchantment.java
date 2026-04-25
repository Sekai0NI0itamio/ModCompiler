/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.MendingEnchantment;

public class InfinityEnchantment
extends Enchantment {
    public InfinityEnchantment(Enchantment.Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAccept(Enchantment other) {
        if (other instanceof MendingEnchantment) {
            return false;
        }
        return super.canAccept(other);
    }
}

