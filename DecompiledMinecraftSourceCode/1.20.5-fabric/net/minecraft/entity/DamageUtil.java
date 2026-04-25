/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.entity;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.MathHelper;

public class DamageUtil {
    public static final float field_29962 = 20.0f;
    public static final float field_29963 = 25.0f;
    public static final float field_29964 = 2.0f;
    public static final float field_29965 = 0.2f;
    private static final int field_29966 = 4;

    public static float getDamageLeft(float damage, DamageSource source, float armor, float armorToughnesss) {
        float f = 2.0f + armorToughnesss / 4.0f;
        float g = MathHelper.clamp(armor - damage / f, armor * 0.2f, 20.0f);
        float h = g / 25.0f;
        float i = EnchantmentHelper.getBreachFactor(source.getAttacker(), h);
        float j = 1.0f - i;
        return damage * j;
    }

    public static float getInflictedDamage(float damageDealt, float protection) {
        float f = MathHelper.clamp(protection, 0.0f, 20.0f);
        return damageDealt * (1.0f - f / 25.0f);
    }
}

