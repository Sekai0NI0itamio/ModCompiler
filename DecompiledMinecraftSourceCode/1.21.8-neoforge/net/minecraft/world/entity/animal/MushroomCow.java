package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class MushroomCow extends AbstractCow implements Shearable {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.INT);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    @Nullable
    private SuspiciousStewEffects stewEffects;
    @Nullable
    private UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> p_28914_, Level p_28915_) {
        super(p_28914_, p_28915_);
    }

    @Override
    public float getWalkTargetValue(BlockPos p_28933_, LevelReader p_28934_) {
        return p_28934_.getBlockState(p_28933_.below()).is(Blocks.MYCELIUM) ? 10.0F : p_28934_.getPathfindingCostFromLightLevels(p_28933_);
    }

    public static boolean checkMushroomSpawnRules(
        EntityType<MushroomCow> p_218201_, LevelAccessor p_218202_, EntitySpawnReason p_370099_, BlockPos p_218204_, RandomSource p_218205_
    ) {
        return p_218202_.getBlockState(p_218204_.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(p_218202_, p_218204_);
    }

    @Override
    public void thunderHit(ServerLevel p_28921_, LightningBolt p_28922_) {
        UUID uuid = p_28922_.getUUID();
        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_336015_) {
        super.defineSynchedData(p_336015_);
        p_336015_.define(DATA_TYPE, MushroomCow.Variant.DEFAULT.id);
    }

    @Override
    public InteractionResult mobInteract(Player p_28941_, InteractionHand p_28942_) {
        ItemStack itemstack = p_28941_.getItemInHand(p_28942_);
        if (itemstack.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemstack2;
            if (this.stewEffects != null) {
                flag = true;
                itemstack2 = new ItemStack(Items.SUSPICIOUS_STEW);
                itemstack2.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemstack2 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemstack1 = ItemUtils.createFilledResult(itemstack, p_28941_, itemstack2, false);
            p_28941_.setItemInHand(p_28942_, itemstack1);
            SoundEvent soundevent;
            if (flag) {
                soundevent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundevent = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundevent, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        } else if (false && itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel serverlevel) {
                this.shear(serverlevel, SoundSource.PLAYERS, itemstack);
                this.gameEvent(GameEvent.SHEAR, p_28941_);
                itemstack.hurtAndBreak(1, p_28941_, getSlotForHand(p_28942_));
            }

            return InteractionResult.SUCCESS;
        } else if (this.getVariant() == MushroomCow.Variant.BROWN) {
            Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemstack);
            if (optional.isEmpty()) {
                return super.mobInteract(p_28941_, p_28942_);
            } else {
                if (this.stewEffects != null) {
                    for (int i = 0; i < 2; i++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.SMOKE,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }
                } else {
                    itemstack.consume(1, p_28941_);

                    for (int j = 0; j < 4; j++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.EFFECT,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }

                    this.stewEffects = optional.get();
                    this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(p_28941_, p_28942_);
        }
    }

    @Override
    public void shear(ServerLevel p_369641_, SoundSource p_28924_, ItemStack p_364876_) {
        for (var stack : shearInternal(p_369641_, p_28924_, p_364876_)) {
            for (int i = 0; i < stack.getCount(); i++) {
                this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(1.0D), this.getZ(), stack.copyWithCount(1)));
            }
        }
    }

    private java.util.List<ItemStack> shearInternal(ServerLevel p_369641_, SoundSource p_28924_, ItemStack p_364876_) {
        var ret = new java.util.ArrayList<ItemStack>();
        if (!net.minecraftforge.event.ForgeEventFactory.canLivingConvert(this, EntityType.COW, time -> {}))
            return ret;
        p_369641_.playSound(null, this, SoundEvents.MOOSHROOM_SHEAR, p_28924_, 1.0F, 1.0F);
        this.convertTo(EntityType.COW, ConversionParams.single(this, false, false), p_405458_ -> {
            p_369641_.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            this.dropFromShearingLootTable(p_369641_, BuiltInLootTables.SHEAR_MOOSHROOM, p_364876_, (p_405454_, p_405455_) -> {
                ret.add(p_405455_);
            });
            net.minecraftforge.event.ForgeEventFactory.onLivingConvert(this, p_405458_);
        });
        return ret;
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_409610_) {
        super.addAdditionalSaveData(p_409610_);
        p_409610_.store("Type", MushroomCow.Variant.CODEC, this.getVariant());
        p_409610_.storeNullable("stew_effects", SuspiciousStewEffects.CODEC, this.stewEffects);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_405880_) {
        super.readAdditionalSaveData(p_405880_);
        this.setVariant(p_405880_.read("Type", MushroomCow.Variant.CODEC).orElse(MushroomCow.Variant.DEFAULT));
        this.stewEffects = p_405880_.read("stew_effects", SuspiciousStewEffects.CODEC).orElse(null);
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack p_298141_) {
        SuspiciousEffectHolder suspiciouseffectholder = SuspiciousEffectHolder.tryGet(p_298141_.getItem());
        return suspiciouseffectholder != null ? Optional.of(suspiciouseffectholder.getSuspiciousEffects()) : Optional.empty();
    }

    private void setVariant(MushroomCow.Variant p_363768_) {
        this.entityData.set(DATA_TYPE, p_363768_.id);
    }

    public MushroomCow.Variant getVariant() {
        return MushroomCow.Variant.byId(this.entityData.get(DATA_TYPE));
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> p_394236_) {
        return p_394236_ == DataComponents.MOOSHROOM_VARIANT ? castComponentValue((DataComponentType<T>)p_394236_, this.getVariant()) : super.get(p_394236_);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter p_392742_) {
        this.applyImplicitComponentIfPresent(p_392742_, DataComponents.MOOSHROOM_VARIANT);
        super.applyImplicitComponents(p_392742_);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> p_395522_, T p_395750_) {
        if (p_395522_ == DataComponents.MOOSHROOM_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.MOOSHROOM_VARIANT, p_395750_));
            return true;
        } else {
            return super.applyImplicitComponent(p_395522_, p_395750_);
        }
    }

    @Nullable
    public MushroomCow getBreedOffspring(ServerLevel p_148942_, AgeableMob p_148943_) {
        MushroomCow mushroomcow = EntityType.MOOSHROOM.create(p_148942_, EntitySpawnReason.BREEDING);
        if (mushroomcow != null) {
            mushroomcow.setVariant(this.getOffspringVariant((MushroomCow)p_148943_));
        }

        return mushroomcow;
    }

    private MushroomCow.Variant getOffspringVariant(MushroomCow p_28931_) {
        MushroomCow.Variant mushroomcow$variant = this.getVariant();
        MushroomCow.Variant mushroomcow$variant1 = p_28931_.getVariant();
        MushroomCow.Variant mushroomcow$variant2;
        if (mushroomcow$variant == mushroomcow$variant1 && this.random.nextInt(1024) == 0) {
            mushroomcow$variant2 = mushroomcow$variant == MushroomCow.Variant.BROWN ? MushroomCow.Variant.RED : MushroomCow.Variant.BROWN;
        } else {
            mushroomcow$variant2 = this.random.nextBoolean() ? mushroomcow$variant : mushroomcow$variant1;
        }

        return mushroomcow$variant2;
    }

    @Override
    public java.util.List<ItemStack> onSheared(@org.jetbrains.annotations.Nullable Player player, @org.jetbrains.annotations.NotNull ItemStack item, Level world, BlockPos pos, int fortune) {
        if (world instanceof ServerLevel server) {
            this.gameEvent(GameEvent.SHEAR, player);
            return shearInternal(server, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, item);
        }
        return java.util.Collections.emptyList();
    }

    public static enum Variant implements StringRepresentable {
        RED("red", 0, Blocks.RED_MUSHROOM.defaultBlockState()),
        BROWN("brown", 1, Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final MushroomCow.Variant DEFAULT = RED;
        public static final Codec<MushroomCow.Variant> CODEC = StringRepresentable.fromEnum(MushroomCow.Variant::values);
        private static final IntFunction<MushroomCow.Variant> BY_ID = ByIdMap.continuous(
            MushroomCow.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP
        );
        public static final StreamCodec<ByteBuf, MushroomCow.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, MushroomCow.Variant::id);
        private final String type;
        final int id;
        private final BlockState blockState;

        private Variant(final String p_367031_, final int p_392707_, final BlockState p_366288_) {
            this.type = p_367031_;
            this.id = p_392707_;
            this.blockState = p_366288_;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        private int id() {
            return this.id;
        }

        static MushroomCow.Variant byId(int p_395392_) {
            return BY_ID.apply(p_395392_);
        }
    }
}
