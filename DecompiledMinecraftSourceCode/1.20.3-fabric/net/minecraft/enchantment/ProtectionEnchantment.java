/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.MathHelper;

public class ProtectionEnchantment
extends Enchantment {
    public final Type protectionType;

    public ProtectionEnchantment(Enchantment.Properties properties, Type protectionType) {
        super(properties);
        this.protectionType = protectionType;
    }

    @Override
    public int getProtectionAmount(int level, DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return 0;
        }
        if (this.protectionType == Type.ALL) {
            return level;
        }
        if (this.protectionType == Type.FIRE && source.isIn(DamageTypeTags.IS_FIRE)) {
            return level * 2;
        }
        if (this.protectionType == Type.FALL && source.isIn(DamageTypeTags.IS_FALL)) {
            return level * 3;
        }
        if (this.protectionType == Type.EXPLOSION && source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            return level * 2;
        }
        if (this.protectionType == Type.PROJECTILE && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            return level * 2;
        }
        return 0;
    }

    @Override
    public boolean canAccept(Enchantment other) {
        if (other instanceof ProtectionEnchantment) {
            ProtectionEnchantment protectionEnchantment = (ProtectionEnchantment)other;
            if (this.protectionType == protectionEnchantment.protectionType) {
                return false;
            }
            return this.protectionType == Type.FALL || protectionEnchantment.protectionType == Type.FALL;
        }
        return super.canAccept(other);
    }

    public static int transformFireDuration(LivingEntity entity, int duration) {
        int i = EnchantmentHelper.getEquipmentLevel(Enchantments.FIRE_PROTECTION, entity);
        if (i > 0) {
            duration -= MathHelper.floor((float)duration * ((float)i * 0.15f));
        }
        return duration;
    }

    public static double transformExplosionKnockback(LivingEntity entity, double velocity) {
        int i = EnchantmentHelper.getEquipmentLevel(Enchantments.BLAST_PROTECTION, entity);
        if (i > 0) {
            velocity *= MathHelper.clamp(1.0 - (double)i * 0.15, 0.0, 1.0);
        }
        return velocity;
    }

    public static enum Type {
        ALL,
        FIRE,
        FALL,
        EXPLOSION,
        PROJECTILE;

    }
}

