package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public record BlockPredicate(
    Optional<HolderSet<Block>> blocks, Optional<StatePropertiesPredicate> properties, Optional<NbtPredicate> nbt, DataComponentMatchers components
) {
    public static final Codec<BlockPredicate> CODEC = RecordCodecBuilder.create(
        p_389119_ -> p_389119_.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("blocks").forGetter(BlockPredicate::blocks),
                StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(BlockPredicate::properties),
                NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(BlockPredicate::nbt),
                DataComponentMatchers.CODEC.forGetter(BlockPredicate::components)
            )
            .apply(p_389119_, BlockPredicate::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPredicate> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(ByteBufCodecs.holderSet(Registries.BLOCK)),
        BlockPredicate::blocks,
        ByteBufCodecs.optional(StatePropertiesPredicate.STREAM_CODEC),
        BlockPredicate::properties,
        ByteBufCodecs.optional(NbtPredicate.STREAM_CODEC),
        BlockPredicate::nbt,
        DataComponentMatchers.STREAM_CODEC,
        BlockPredicate::components,
        BlockPredicate::new
    );

    public boolean matches(ServerLevel p_17915_, BlockPos p_17916_) {
        if (!p_17915_.isLoaded(p_17916_)) {
            return false;
        } else if (!this.matchesState(p_17915_.getBlockState(p_17916_))) {
            return false;
        } else {
            if (this.nbt.isPresent() || !this.components.isEmpty()) {
                BlockEntity blockentity = p_17915_.getBlockEntity(p_17916_);
                if (this.nbt.isPresent() && !matchesBlockEntity(p_17915_, blockentity, this.nbt.get())) {
                    return false;
                }

                if (!this.components.isEmpty() && !matchesComponents(blockentity, this.components)) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean matches(BlockInWorld p_335665_) {
        return !this.matchesState(p_335665_.getState())
            ? false
            : !this.nbt.isPresent() || matchesBlockEntity(p_335665_.getLevel(), p_335665_.getEntity(), this.nbt.get());
    }

    private boolean matchesState(BlockState p_334077_) {
        return this.blocks.isPresent() && !p_334077_.is(this.blocks.get())
            ? false
            : !this.properties.isPresent() || this.properties.get().matches(p_334077_);
    }

    private static boolean matchesBlockEntity(LevelReader p_330206_, @Nullable BlockEntity p_327732_, NbtPredicate p_335422_) {
        return p_327732_ != null && p_335422_.matches(p_327732_.saveWithFullMetadata(p_330206_.registryAccess()));
    }

    private static boolean matchesComponents(@Nullable BlockEntity p_391686_, DataComponentMatchers p_395336_) {
        return p_391686_ != null && p_395336_.test((DataComponentGetter)p_391686_.collectComponents());
    }

    public boolean requiresNbt() {
        return this.nbt.isPresent();
    }

    public static class Builder {
        private Optional<HolderSet<Block>> blocks = Optional.empty();
        private Optional<StatePropertiesPredicate> properties = Optional.empty();
        private Optional<NbtPredicate> nbt = Optional.empty();
        private DataComponentMatchers components = DataComponentMatchers.ANY;

        private Builder() {
        }

        public static BlockPredicate.Builder block() {
            return new BlockPredicate.Builder();
        }

        public BlockPredicate.Builder of(HolderGetter<Block> p_362819_, Block... p_367576_) {
            return this.of(p_362819_, Arrays.asList(p_367576_));
        }

        public BlockPredicate.Builder of(HolderGetter<Block> p_362597_, Collection<Block> p_298036_) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, p_298036_));
            return this;
        }

        public BlockPredicate.Builder of(HolderGetter<Block> p_368525_, TagKey<Block> p_361542_) {
            this.blocks = Optional.of(p_368525_.getOrThrow(p_361542_));
            return this;
        }

        public BlockPredicate.Builder hasNbt(CompoundTag p_146725_) {
            this.nbt = Optional.of(new NbtPredicate(p_146725_));
            return this;
        }

        public BlockPredicate.Builder setProperties(StatePropertiesPredicate.Builder p_299418_) {
            this.properties = p_299418_.build();
            return this;
        }

        public BlockPredicate.Builder components(DataComponentMatchers p_391944_) {
            this.components = p_391944_;
            return this;
        }

        public BlockPredicate build() {
            return new BlockPredicate(this.blocks, this.properties, this.nbt, this.components);
        }
    }
}
