package net.minecraft.world.entity.animal;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

public class Chicken extends Animal {
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.CHICKEN.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    private static final EntityDataAccessor<Holder<ChickenVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Chicken.class, EntityDataSerializers.CHICKEN_VARIANT);
    private static final boolean DEFAULT_CHICKEN_JOCKEY = false;
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    public float flapping = 1.0F;
    private float nextFlap = 1.0F;
    public int eggTime;
    public boolean isChickenJockey = false;

    public Chicken(EntityType<? extends Chicken> p_28236_, Level p_28237_) {
        super(p_28236_, p_28237_);
        this.eggTime = this.random.nextInt(6000) + 6000;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, p_334579_ -> p_334579_.is(ItemTags.CHICKEN_FOOD), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose p_329747_) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(p_329747_);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed = this.flapSpeed + (this.onGround() ? -1.0F : 4.0F) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3 vec3 = this.getDeltaMovement();
        if (!this.onGround() && vec3.y < 0.0) {
            this.setDeltaMovement(vec3.multiply(1.0, 0.6, 1.0));
        }

        this.flap = this.flap + this.flapping * 2.0F;
        if (this.level() instanceof ServerLevel serverlevel && this.isAlive() && !this.isBaby() && !this.isChickenJockey() && --this.eggTime <= 0) {
            if (this.dropFromGiftLootTable(serverlevel, BuiltInLootTables.CHICKEN_LAY, this::spawnAtLocation)) {
                this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.gameEvent(GameEvent.ENTITY_PLACE);
            }

            this.eggTime = this.random.nextInt(6000) + 6000;
        }
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CHICKEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_28262_) {
        return SoundEvents.CHICKEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHICKEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos p_28254_, BlockState p_28255_) {
        this.playSound(SoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
    }

    @Nullable
    public Chicken getBreedOffspring(ServerLevel p_148884_, AgeableMob p_148885_) {
        Chicken chicken = EntityType.CHICKEN.create(p_148884_, EntitySpawnReason.BREEDING);
        if (chicken != null && p_148885_ instanceof Chicken chicken1) {
            chicken.setVariant(this.random.nextBoolean() ? this.getVariant() : chicken1.getVariant());
        }

        return chicken;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_393146_, DifficultyInstance p_396815_, EntitySpawnReason p_396100_, @Nullable SpawnGroupData p_397415_) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(p_393146_, this.blockPosition()), Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
        return super.finalizeSpawn(p_393146_, p_396815_, p_396100_, p_397415_);
    }

    @Override
    public boolean isFood(ItemStack p_28271_) {
        return p_28271_.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel p_368453_) {
        return this.isChickenJockey() ? 10 : super.getBaseExperienceReward(p_368453_);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_391730_) {
        super.defineSynchedData(p_391730_);
        p_391730_.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ChickenVariants.TEMPERATE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_410342_) {
        super.readAdditionalSaveData(p_410342_);
        this.isChickenJockey = p_410342_.getBooleanOr("IsChickenJockey", false);
        p_410342_.getInt("EggLayTime").ifPresent(p_390644_ -> this.eggTime = p_390644_);
        VariantUtils.readVariant(p_410342_, Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_407760_) {
        super.addAdditionalSaveData(p_407760_);
        p_407760_.putBoolean("IsChickenJockey", this.isChickenJockey);
        p_407760_.putInt("EggLayTime", this.eggTime);
        VariantUtils.writeVariant(p_407760_, this.getVariant());
    }

    public void setVariant(Holder<ChickenVariant> p_395086_) {
        this.entityData.set(DATA_VARIANT_ID, p_395086_);
    }

    public Holder<ChickenVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> p_396563_) {
        return p_396563_ == DataComponents.CHICKEN_VARIANT
            ? castComponentValue((DataComponentType<T>)p_396563_, new EitherHolder<>(this.getVariant()))
            : super.get(p_396563_);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter p_391794_) {
        this.applyImplicitComponentIfPresent(p_391794_, DataComponents.CHICKEN_VARIANT);
        super.applyImplicitComponents(p_391794_);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> p_394062_, T p_391824_) {
        if (p_394062_ == DataComponents.CHICKEN_VARIANT) {
            Optional<Holder<ChickenVariant>> optional = castComponentValue(DataComponents.CHICKEN_VARIANT, p_391824_).unwrap(this.registryAccess());
            if (optional.isPresent()) {
                this.setVariant(optional.get());
                return true;
            } else {
                return false;
            }
        } else {
            return super.applyImplicitComponent(p_394062_, p_391824_);
        }
    }

    @Override
    public boolean removeWhenFarAway(double p_28266_) {
        return this.isChickenJockey();
    }

    @Override
    protected void positionRider(Entity p_289537_, Entity.MoveFunction p_289541_) {
        super.positionRider(p_289537_, p_289541_);
        if (p_289537_ instanceof LivingEntity) {
            ((LivingEntity)p_289537_).yBodyRot = this.yBodyRot;
        }
    }

    public boolean isChickenJockey() {
        return this.isChickenJockey;
    }

    public void setChickenJockey(boolean p_28274_) {
        this.isChickenJockey = p_28274_;
    }
}
