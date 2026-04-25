/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import java.util.Map;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

public class ThornsEnchantment
extends Enchantment {
    private static final float ATTACK_CHANCE_PER_LEVEL = 0.15f;

    public ThornsEnchantment(Enchantment.Properties properties) {
        super(properties);
    }

    @Override
    public void onUserDamaged(LivingEntity user, Entity attacker, int level) {
        Random random = user.getRandom();
        Map.Entry<EquipmentSlot, ItemStack> entry = EnchantmentHelper.chooseEquipmentWith(Enchantments.THORNS, user);
        if (ThornsEnchantment.shouldDamageAttacker(level, random)) {
            if (attacker != null) {
                attacker.damage(user.getDamageSources().thorns(user), ThornsEnchantment.getDamageAmount(level, random));
            }
            if (entry != null) {
                entry.getValue().damage(2, user, entry.getKey());
            }
        }
    }

    public static boolean shouldDamageAttacker(int level, Random random) {
        if (level <= 0) {
            return false;
        }
        return random.nextFloat() < 0.15f * (float)level;
    }

    public static int getDamageAmount(int level, Random random) {
        if (level > 10) {
            return level - 10;
        }
        return 1 + random.nextInt(4);
    }
}

