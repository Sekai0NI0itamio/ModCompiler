package net.minecraft.world.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Cow extends AbstractCow {
    private static final EntityDataAccessor<Holder<CowVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Cow.class, EntityDataSerializers.COW_VARIANT);

    public Cow(EntityType<? extends Cow> p_28285_, Level p_28286_) {
        super(p_28285_, p_28286_);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_392843_) {
        super.defineSynchedData(p_392843_);
        p_392843_.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), CowVariants.TEMPERATE));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_406945_) {
        super.addAdditionalSaveData(p_406945_);
        VariantUtils.writeVariant(p_406945_, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_410015_) {
        super.readAdditionalSaveData(p_410015_);
        VariantUtils.readVariant(p_410015_, Registries.COW_VARIANT).ifPresent(this::setVariant);
    }

    @Nullable
    public Cow getBreedOffspring(ServerLevel p_148890_, AgeableMob p_148891_) {
        Cow cow = EntityType.COW.create(p_148890_, EntitySpawnReason.BREEDING);
        if (cow != null && p_148891_ instanceof Cow cow1) {
            cow.setVariant(this.random.nextBoolean() ? this.getVariant() : cow1.getVariant());
        }

        return cow;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_397036_, DifficultyInstance p_393484_, EntitySpawnReason p_392067_, @Nullable SpawnGroupData p_397060_) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(p_397036_, this.blockPosition()), Registries.COW_VARIANT).ifPresent(this::setVariant);
        return super.finalizeSpawn(p_397036_, p_393484_, p_392067_, p_397060_);
    }

    public void setVariant(Holder<CowVariant> p_394975_) {
        this.entityData.set(DATA_VARIANT_ID, p_394975_);
    }

    public Holder<CowVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> p_396914_) {
        return p_396914_ == DataComponents.COW_VARIANT ? castComponentValue((DataComponentType<T>)p_396914_, this.getVariant()) : super.get(p_396914_);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter p_395565_) {
        this.applyImplicitComponentIfPresent(p_395565_, DataComponents.COW_VARIANT);
        super.applyImplicitComponents(p_395565_);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> p_394391_, T p_394054_) {
        if (p_394391_ == DataComponents.COW_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.COW_VARIANT, p_394054_));
            return true;
        } else {
            return super.applyImplicitComponent(p_394391_, p_394054_);
        }
    }
}
