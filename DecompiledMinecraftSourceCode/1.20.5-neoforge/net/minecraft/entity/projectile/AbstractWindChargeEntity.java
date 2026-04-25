/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.entity.projectile;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractWindChargeEntity
extends ExplosiveProjectileEntity
implements FlyingItemEntity {
    public static final WindChargeExplosionBehavior EXPLOSION_BEHAVIOR = new WindChargeExplosionBehavior();

    public AbstractWindChargeEntity(EntityType<? extends AbstractWindChargeEntity> entityType, World world) {
        super((EntityType<? extends ExplosiveProjectileEntity>)entityType, world);
    }

    public AbstractWindChargeEntity(EntityType<? extends AbstractWindChargeEntity> type, World world, Entity owner, double x, double y, double z) {
        super(type, x, y, z, world);
        this.setOwner(owner);
    }

    AbstractWindChargeEntity(EntityType<? extends AbstractWindChargeEntity> entityType, double d, double e, double f, double g, double h, double i, World world) {
        super(entityType, d, e, f, g, h, i, world);
    }

    @Override
    protected Box calculateBoundingBox() {
        float f = this.getType().getDimensions().width() / 2.0f;
        float g = this.getType().getDimensions().height();
        float h = 0.15f;
        return new Box(this.getPos().x - (double)f, this.getPos().y - (double)0.15f, this.getPos().z - (double)f, this.getPos().x + (double)f, this.getPos().y - (double)0.15f + (double)g, this.getPos().z + (double)f);
    }

    @Override
    public boolean collidesWith(Entity other) {
        if (other instanceof AbstractWindChargeEntity) {
            return false;
        }
        return super.collidesWith(other);
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (entity instanceof AbstractWindChargeEntity) {
            return false;
        }
        if (entity.getType() == EntityType.END_CRYSTAL) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        LivingEntity livingEntity;
        super.onEntityHit(entityHitResult);
        if (this.getWorld().isClient) {
            return;
        }
        Entity entity = this.getOwner();
        LivingEntity livingEntity2 = entity instanceof LivingEntity ? (livingEntity = (LivingEntity)entity) : null;
        Entity entity2 = entityHitResult.getEntity().getPassengerNearestTo(entityHitResult.getPos()).orElse(entityHitResult.getEntity());
        if (livingEntity2 != null) {
            livingEntity2.onAttacking(entity2);
        }
        entity2.damage(this.getDamageSources().windCharge(this, livingEntity2), 1.0f);
        this.createExplosion();
    }

    @Override
    public void addVelocity(double deltaX, double deltaY, double deltaZ) {
    }

    protected abstract void createExplosion();

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!this.getWorld().isClient) {
            this.createExplosion();
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            this.discard();
        }
    }

    @Override
    protected boolean isBurning() {
        return false;
    }

    @Override
    public ItemStack getStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getDrag() {
        return 1.0f;
    }

    @Override
    protected float getDragInWater() {
        return this.getDrag();
    }

    @Override
    @Nullable
    protected ParticleEffect getParticleType() {
        return null;
    }

    @Override
    public void tick() {
        if (!this.getWorld().isClient && this.getBlockY() > this.getWorld().getTopY() + 30) {
            this.createExplosion();
            this.discard();
        } else {
            super.tick();
        }
    }

    public static class WindChargeExplosionBehavior
    extends ExplosionBehavior {
        @Override
        public boolean shouldDamage(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public Optional<Float> getBlastResistance(Explosion explosion, BlockView world, BlockPos pos, BlockState blockState, FluidState fluidState) {
            if (blockState.isIn(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS)) {
                return Optional.of(Float.valueOf(3600000.0f));
            }
            return Optional.empty();
        }
    }
}

