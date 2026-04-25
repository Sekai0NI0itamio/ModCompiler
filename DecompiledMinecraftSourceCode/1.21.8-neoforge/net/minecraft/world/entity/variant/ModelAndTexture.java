package net.minecraft.world.entity.variant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ModelAndTexture<T>(T model, ClientAsset asset) {
    public ModelAndTexture(T p_395013_, ResourceLocation p_396789_) {
        this(p_395013_, new ClientAsset(p_396789_));
    }

    public static <T> MapCodec<ModelAndTexture<T>> codec(Codec<T> p_392423_, T p_395386_) {
        return RecordCodecBuilder.mapCodec(
            p_394042_ -> p_394042_.group(
                    p_392423_.optionalFieldOf("model", p_395386_).forGetter(ModelAndTexture::model),
                    ClientAsset.DEFAULT_FIELD_CODEC.forGetter(ModelAndTexture::asset)
                )
                .apply(p_394042_, ModelAndTexture::new)
        );
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, ModelAndTexture<T>> streamCodec(StreamCodec<? super RegistryFriendlyByteBuf, T> p_391447_) {
        return StreamCodec.composite(p_391447_, ModelAndTexture::model, ClientAsset.STREAM_CODEC, ModelAndTexture::asset, ModelAndTexture::new);
    }
}
