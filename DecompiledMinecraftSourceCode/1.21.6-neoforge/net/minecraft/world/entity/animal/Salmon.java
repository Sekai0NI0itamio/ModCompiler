package net.minecraft.world.entity.animal;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Salmon extends AbstractSchoolingFish {
    private static final String TAG_TYPE = "type";
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(Salmon.class, EntityDataSerializers.INT);

    public Salmon(EntityType<? extends Salmon> p_29790_, Level p_29791_) {
        super(p_29790_, p_29791_);
        this.refreshDimensions();
    }

    @Override
    public int getMaxSchoolSize() {
        return 5;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.SALMON_BUCKET);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SALMON_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SALMON_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_29795_) {
        return SoundEvents.SALMON_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.SALMON_FLOP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_368809_) {
        super.defineSynchedData(p_368809_);
        p_368809_.define(DATA_TYPE, Salmon.Variant.DEFAULT.id());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_366689_) {
        super.onSyncedDataUpdated(p_366689_);
        if (DATA_TYPE.equals(p_366689_)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_409814_) {
        super.addAdditionalSaveData(p_409814_);
        p_409814_.store("type", Salmon.Variant.CODEC, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_407457_) {
        super.readAdditionalSaveData(p_407457_);
        this.setVariant(p_407457_.read("type", Salmon.Variant.CODEC).orElse(Salmon.Variant.DEFAULT));
    }

    @Override
    public void saveToBucketTag(ItemStack p_362100_) {
        Bucketable.saveDefaultDataToBucketTag(this, p_362100_);
        p_362100_.copyFrom(DataComponents.SALMON_SIZE, this);
    }

    private void setVariant(Salmon.Variant p_361475_) {
        this.entityData.set(DATA_TYPE, p_361475_.id);
    }

    public Salmon.Variant getVariant() {
        return Salmon.Variant.BY_ID.apply(this.entityData.get(DATA_TYPE));
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> p_397176_) {
        return p_397176_ == DataComponents.SALMON_SIZE ? castComponentValue((DataComponentType<T>)p_397176_, this.getVariant()) : super.get(p_397176_);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter p_396270_) {
        this.applyImplicitComponentIfPresent(p_396270_, DataComponents.SALMON_SIZE);
        super.applyImplicitComponents(p_396270_);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> p_396569_, T p_395048_) {
        if (p_396569_ == DataComponents.SALMON_SIZE) {
            this.setVariant(castComponentValue(DataComponents.SALMON_SIZE, p_395048_));
            return true;
        } else {
            return super.applyImplicitComponent(p_396569_, p_395048_);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_363387_, DifficultyInstance p_367892_, EntitySpawnReason p_368788_, @Nullable SpawnGroupData p_362214_) {
        WeightedList.Builder<Salmon.Variant> builder = WeightedList.builder();
        builder.add(Salmon.Variant.SMALL, 30);
        builder.add(Salmon.Variant.MEDIUM, 50);
        builder.add(Salmon.Variant.LARGE, 15);
        builder.build().getRandom(this.random).ifPresent(this::setVariant);
        return super.finalizeSpawn(p_363387_, p_367892_, p_368788_, p_362214_);
    }

    public float getSalmonScale() {
        return this.getVariant().boundingBoxScale;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose p_368896_) {
        return super.getDefaultDimensions(p_368896_).scale(this.getSalmonScale());
    }

    public static enum Variant implements StringRepresentable {
        SMALL("small", 0, 0.5F),
        MEDIUM("medium", 1, 1.0F),
        LARGE("large", 2, 1.5F);

        public static final Salmon.Variant DEFAULT = MEDIUM;
        public static final StringRepresentable.EnumCodec<Salmon.Variant> CODEC = StringRepresentable.fromEnum(Salmon.Variant::values);
        static final IntFunction<Salmon.Variant> BY_ID = ByIdMap.continuous(Salmon.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final StreamCodec<ByteBuf, Salmon.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Salmon.Variant::id);
        private final String name;
        final int id;
        final float boundingBoxScale;

        private Variant(final String p_364669_, final int p_375656_, final float p_368051_) {
            this.name = p_364669_;
            this.id = p_375656_;
            this.boundingBoxScale = p_368051_;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        int id() {
            return this.id;
        }
    }
}
