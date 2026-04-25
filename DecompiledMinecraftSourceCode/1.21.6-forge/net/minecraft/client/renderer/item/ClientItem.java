package net.minecraft.client.renderer.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import javax.annotation.Nullable;
import net.minecraft.util.RegistryContextSwapper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ClientItem(ItemModel.Unbaked model, ClientItem.Properties properties, @Nullable RegistryContextSwapper registrySwapper) {
    public static final Codec<ClientItem> CODEC = RecordCodecBuilder.create(
        p_377165_ -> p_377165_.group(
                ItemModels.CODEC.fieldOf("model").forGetter(ClientItem::model), ClientItem.Properties.MAP_CODEC.forGetter(ClientItem::properties)
            )
            .apply(p_377165_, ClientItem::new)
    );

    public ClientItem(ItemModel.Unbaked p_376056_, ClientItem.Properties p_375669_) {
        this(p_376056_, p_375669_, null);
    }

    public ClientItem withRegistrySwapper(RegistryContextSwapper p_392250_) {
        return new ClientItem(this.model, this.properties, p_392250_);
    }

    @OnlyIn(Dist.CLIENT)
    public record Properties(boolean handAnimationOnSwap, boolean oversizedInGui) {
        public static final ClientItem.Properties DEFAULT = new ClientItem.Properties(true, false);
        public static final MapCodec<ClientItem.Properties> MAP_CODEC = RecordCodecBuilder.mapCodec(
            p_405021_ -> p_405021_.group(
                    Codec.BOOL.optionalFieldOf("hand_animation_on_swap", true).forGetter(ClientItem.Properties::handAnimationOnSwap),
                    Codec.BOOL.optionalFieldOf("oversized_in_gui", false).forGetter(ClientItem.Properties::oversizedInGui)
                )
                .apply(p_405021_, ClientItem.Properties::new)
        );
    }
}
