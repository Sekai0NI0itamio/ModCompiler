/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

public class WindBurstEnchantment
extends Enchantment {
    public WindBurstEnchantment() {
        super(Enchantment.properties(ItemTags.MACE_ENCHANTABLE, 2, 3, Enchantment.leveledCost(15, 9), Enchantment.leveledCost(65, 9), 4, FeatureSet.of(FeatureFlags.UPDATE_1_21), EquipmentSlot.MAINHAND));
    }

    @Override
    public void onAttack(LivingEntity attacket, Entity target, int level) {
        float f = 0.25f + 0.25f * (float)level;
        attacket.getWorld().createExplosion(null, null, new ExplosionBehaviour(f), attacket.getX(), attacket.getY(), attacket.getZ(), 3.5f, false, World.ExplosionSourceType.BLOW, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST);
    }

    @Override
    public boolean isAvailableForEnchantedBookOffer() {
        return false;
    }

    @Override
    public boolean isAvailableForRandomSelection() {
        return false;
    }

    static final class ExplosionBehaviour
    extends AbstractWindChargeEntity.WindChargeExplosionBehavior {
        private final float knockbackModifier;

        public ExplosionBehaviour(float knockbackModifier) {
            this.knockbackModifier = knockbackModifier;
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        @Override
        public float getKnockbackModifier(Entity entity) {
            if (entity instanceof PlayerEntity) {
                PlayerEntity playerEntity = (PlayerEntity)entity;
                if (playerEntity.getAbilities().flying) {
                    return 0.0f;
                }
            }
            boolean bl = false;
            boolean bl2 = bl;
            if (bl2) return 0.0f;
            float f = this.knockbackModifier;
            return f;
        }
    }
}

