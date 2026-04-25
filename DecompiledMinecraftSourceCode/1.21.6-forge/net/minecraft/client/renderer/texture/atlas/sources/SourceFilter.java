package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ResourceLocationPattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record SourceFilter(ResourceLocationPattern filter) implements SpriteSource {
    public static final MapCodec<SourceFilter> MAP_CODEC = RecordCodecBuilder.mapCodec(
        p_261830_ -> p_261830_.group(ResourceLocationPattern.CODEC.fieldOf("pattern").forGetter(SourceFilter::filter))
            .apply(p_261830_, SourceFilter::new)
    );

    @Override
    public void run(ResourceManager p_261888_, SpriteSource.Output p_261864_) {
        p_261864_.removeAll(this.filter.locationPredicate());
    }

    @Override
    public MapCodec<SourceFilter> codec() {
        return MAP_CODEC;
    }
}
