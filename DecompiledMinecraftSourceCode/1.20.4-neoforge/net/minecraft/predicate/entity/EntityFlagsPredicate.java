/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.predicate.entity;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public record EntityFlagsPredicate(Optional<Boolean> isOnFire, Optional<Boolean> isSneaking, Optional<Boolean> isSprinting, Optional<Boolean> isSwimming, Optional<Boolean> isBaby) {
    public static final Codec<EntityFlagsPredicate> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.BOOL.optionalFieldOf("is_on_fire").forGetter(EntityFlagsPredicate::isOnFire), Codec.BOOL.optionalFieldOf("is_sneaking").forGetter(EntityFlagsPredicate::isSneaking), Codec.BOOL.optionalFieldOf("is_sprinting").forGetter(EntityFlagsPredicate::isSprinting), Codec.BOOL.optionalFieldOf("is_swimming").forGetter(EntityFlagsPredicate::isSwimming), Codec.BOOL.optionalFieldOf("is_baby").forGetter(EntityFlagsPredicate::isBaby)).apply((Applicative<EntityFlagsPredicate, ?>)instance, EntityFlagsPredicate::new));

    public boolean test(Entity entity) {
        LivingEntity livingEntity;
        if (this.isOnFire.isPresent() && entity.isOnFire() != this.isOnFire.get().booleanValue()) {
            return false;
        }
        if (this.isSneaking.isPresent() && entity.isInSneakingPose() != this.isSneaking.get().booleanValue()) {
            return false;
        }
        if (this.isSprinting.isPresent() && entity.isSprinting() != this.isSprinting.get().booleanValue()) {
            return false;
        }
        if (this.isSwimming.isPresent() && entity.isSwimming() != this.isSwimming.get().booleanValue()) {
            return false;
        }
        return !this.isBaby.isPresent() || !(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity).isBaby() == this.isBaby.get().booleanValue();
    }

    public static class Builder {
        private Optional<Boolean> isOnFire = Optional.empty();
        private Optional<Boolean> isSneaking = Optional.empty();
        private Optional<Boolean> isSprinting = Optional.empty();
        private Optional<Boolean> isSwimming = Optional.empty();
        private Optional<Boolean> isBaby = Optional.empty();

        public static Builder create() {
            return new Builder();
        }

        public Builder onFire(Boolean onFire) {
            this.isOnFire = Optional.of(onFire);
            return this;
        }

        public Builder sneaking(Boolean sneaking) {
            this.isSneaking = Optional.of(sneaking);
            return this;
        }

        public Builder sprinting(Boolean sprinting) {
            this.isSprinting = Optional.of(sprinting);
            return this;
        }

        public Builder swimming(Boolean swimming) {
            this.isSwimming = Optional.of(swimming);
            return this;
        }

        public Builder isBaby(Boolean isBaby) {
            this.isBaby = Optional.of(isBaby);
            return this;
        }

        public EntityFlagsPredicate build() {
            return new EntityFlagsPredicate(this.isOnFire, this.isSneaking, this.isSprinting, this.isSwimming, this.isBaby);
        }
    }
}

