package net.minecraft.world.entity.animal.frog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;

public record FrogVariant(ClientAsset assetInfo, SpawnPrioritySelectors spawnConditions) implements PriorityProvider<SpawnContext, SpawnCondition> {
    public static final Codec<FrogVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        p_394256_ -> p_394256_.group(
                ClientAsset.DEFAULT_FIELD_CODEC.forGetter(FrogVariant::assetInfo),
                SpawnPrioritySelectors.CODEC.fieldOf("spawn_conditions").forGetter(FrogVariant::spawnConditions)
            )
            .apply(p_394256_, FrogVariant::new)
    );
    public static final Codec<FrogVariant> NETWORK_CODEC = RecordCodecBuilder.create(
        p_395092_ -> p_395092_.group(ClientAsset.DEFAULT_FIELD_CODEC.forGetter(FrogVariant::assetInfo)).apply(p_395092_, FrogVariant::new)
    );
    public static final Codec<Holder<FrogVariant>> CODEC = RegistryFixedCodec.create(Registries.FROG_VARIANT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<FrogVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.FROG_VARIANT);

    private FrogVariant(ClientAsset p_397297_) {
        this(p_397297_, SpawnPrioritySelectors.EMPTY);
    }

    @Override
    public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
        return this.spawnConditions.selectors();
    }
}
