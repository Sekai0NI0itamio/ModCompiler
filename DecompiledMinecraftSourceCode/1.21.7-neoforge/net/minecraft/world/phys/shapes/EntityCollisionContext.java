package net.minecraft.world.phys.shapes;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class EntityCollisionContext implements CollisionContext {
    protected static final CollisionContext EMPTY = new EntityCollisionContext(false, false, -Double.MAX_VALUE, ItemStack.EMPTY, p_205118_ -> false, null) {
        @Override
        public boolean isAbove(VoxelShape p_82898_, BlockPos p_82899_, boolean p_82900_) {
            return p_82900_;
        }
    };
    private final boolean descending;
    private final double entityBottom;
    private final boolean placement;
    private final ItemStack heldItem;
    private final Predicate<FluidState> canStandOnFluid;
    @Nullable
    private final Entity entity;

    protected EntityCollisionContext(
        boolean p_365888_, boolean p_396699_, double p_396474_, ItemStack p_395757_, Predicate<FluidState> p_393464_, @Nullable Entity p_82872_
    ) {
        this.descending = p_365888_;
        this.placement = p_396699_;
        this.entityBottom = p_396474_;
        this.heldItem = p_395757_;
        this.canStandOnFluid = p_393464_;
        this.entity = p_82872_;
    }

    @Deprecated
    protected EntityCollisionContext(Entity p_198920_, boolean p_198916_, boolean p_394820_) {
        this(
            p_198920_.isDescending(),
            p_394820_,
            p_198920_.getY(),
            p_198920_ instanceof LivingEntity livingentity ? livingentity.getMainHandItem() : ItemStack.EMPTY,
            p_198916_
                ? p_360701_ -> true
                : (p_198920_ instanceof LivingEntity ? p_391140_ -> ((LivingEntity)p_198920_).canStandOnFluid(p_391140_) : p_205113_ -> false),
            p_198920_
        );
    }

    @Override
    public boolean isHoldingItem(Item p_82879_) {
        return this.heldItem.is(p_82879_);
    }

    @Override
    public boolean canStandOnFluid(FluidState p_205115_, FluidState p_205116_) {
        return this.canStandOnFluid.test(p_205116_) && !p_205115_.getType().isSame(p_205116_.getType());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState p_367344_, CollisionGetter p_362064_, BlockPos p_364238_) {
        return p_367344_.getCollisionShape(p_362064_, p_364238_, this);
    }

    @Override
    public boolean isDescending() {
        return this.descending;
    }

    @Override
    public boolean isAbove(VoxelShape p_82886_, BlockPos p_82887_, boolean p_82888_) {
        return this.entityBottom > p_82887_.getY() + p_82886_.max(Direction.Axis.Y) - 1.0E-5F;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    @Override
    public boolean isPlacement() {
        return this.placement;
    }
}
