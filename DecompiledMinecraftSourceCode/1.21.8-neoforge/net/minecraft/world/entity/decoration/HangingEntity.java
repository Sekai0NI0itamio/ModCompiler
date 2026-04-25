package net.minecraft.world.entity.decoration;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public abstract class HangingEntity extends BlockAttachedEntity {
    protected static final Predicate<Entity> HANGING_ENTITY = p_31734_ -> p_31734_ instanceof HangingEntity;
    private static final EntityDataAccessor<Direction> DATA_DIRECTION = SynchedEntityData.defineId(HangingEntity.class, EntityDataSerializers.DIRECTION);
    private static final Direction DEFAULT_DIRECTION = Direction.SOUTH;

    protected HangingEntity(EntityType<? extends HangingEntity> p_31703_, Level p_31704_) {
        super(p_31703_, p_31704_);
    }

    protected HangingEntity(EntityType<? extends HangingEntity> p_31706_, Level p_31707_, BlockPos p_31708_) {
        this(p_31706_, p_31707_);
        this.pos = p_31708_;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_408433_) {
        p_408433_.define(DATA_DIRECTION, DEFAULT_DIRECTION);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_409667_) {
        super.onSyncedDataUpdated(p_409667_);
        if (p_409667_.equals(DATA_DIRECTION)) {
            this.setDirection(this.getDirection());
        }
    }

    @Override
    public Direction getDirection() {
        return this.entityData.get(DATA_DIRECTION);
    }

    protected void setDirectionRaw(Direction p_407531_) {
        this.entityData.set(DATA_DIRECTION, p_407531_);
    }

    protected void setDirection(Direction p_31728_) {
        Objects.requireNonNull(p_31728_);
        Validate.isTrue(p_31728_.getAxis().isHorizontal());
        this.setDirectionRaw(p_31728_);
        this.setYRot(p_31728_.get2DDataValue() * 90);
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.getDirection() != null) {
            AABB aabb = this.calculateBoundingBox(this.pos, this.getDirection());
            Vec3 vec3 = aabb.getCenter();
            this.setPosRaw(vec3.x, vec3.y, vec3.z);
            this.setBoundingBox(aabb);
        }
    }

    protected abstract AABB calculateBoundingBox(BlockPos p_342672_, Direction p_343089_);

    @Override
    public boolean survives() {
        if (!this.level().noCollision(this)) {
            return false;
        } else {
            boolean flag = BlockPos.betweenClosedStream(this.calculateSupportBox()).allMatch(p_405497_ -> {
                BlockState blockstate = this.level().getBlockState(p_405497_);
                if (net.minecraft.world.level.block.Block.canSupportCenter(this.level(), p_405497_, this.getDirection()))
                    return true;
                return blockstate.isSolid() || DiodeBlock.isDiode(blockstate);
            });
            return !flag ? false : this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
        }
    }

    protected AABB calculateSupportBox() {
        return this.getBoundingBox().move(this.getDirection().step().mul(-0.5F)).deflate(1.0E-7);
    }

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ServerLevel p_366091_, ItemStack p_31722_, float p_31723_) {
        ItemEntity itementity = new ItemEntity(
            this.level(),
            this.getX() + this.getDirection().getStepX() * 0.15F,
            this.getY() + p_31723_,
            this.getZ() + this.getDirection().getStepZ() * 0.15F,
            p_31722_
        );
        itementity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itementity);
        return itementity;
    }

    @Override
    public float rotate(Rotation p_31727_) {
        Direction direction = this.getDirection();
        if (direction.getAxis() != Direction.Axis.Y) {
            switch (p_31727_) {
                case CLOCKWISE_180:
                    direction = direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    direction = direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    direction = direction.getClockWise();
            }

            this.setDirection(direction);
        }

        float f = Mth.wrapDegrees(this.getYRot());

        return switch (p_31727_) {
            case CLOCKWISE_180 -> f + 180.0F;
            case COUNTERCLOCKWISE_90 -> f + 90.0F;
            case CLOCKWISE_90 -> f + 270.0F;
            default -> f;
        };
    }

    @Override
    public float mirror(Mirror p_31725_) {
        return this.rotate(p_31725_.getRotation(this.getDirection()));
    }
}
