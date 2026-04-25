package net.minecraft.client.resources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record WaypointStyle(int nearDistance, int farDistance, List<ResourceLocation> sprites, List<ResourceLocation> spriteLocations) {
    public static final int DEFAULT_NEAR_DISTANCE = 128;
    public static final int DEFAULT_FAR_DISTANCE = 332;
    private static final Codec<Integer> DISTANCE_CODEC = Codec.intRange(0, 60000000);
    public static final Codec<WaypointStyle> CODEC = RecordCodecBuilder.<WaypointStyle>create(
            p_406973_ -> p_406973_.group(
                    DISTANCE_CODEC.optionalFieldOf("near_distance", 128).forGetter(WaypointStyle::nearDistance),
                    DISTANCE_CODEC.optionalFieldOf("far_distance", 332).forGetter(WaypointStyle::farDistance),
                    ExtraCodecs.nonEmptyList(ResourceLocation.CODEC.listOf()).fieldOf("sprites").forGetter(WaypointStyle::sprites)
                )
                .apply(p_406973_, WaypointStyle::new)
        )
        .validate(WaypointStyle::validate);

    public WaypointStyle(int p_407902_, int p_407519_, List<ResourceLocation> p_407571_) {
        this(p_407902_, p_407519_, p_407571_, p_407571_.stream().map(p_409360_ -> p_409360_.withPrefix("hud/locator_bar_dot/")).toList());
    }

    private DataResult<WaypointStyle> validate() {
        return this.nearDistance >= this.farDistance
            ? DataResult.error(() -> "Far distance (" + this.farDistance + ") cannot be closer or equal to near distance (" + this.nearDistance + ")")
            : DataResult.success(this);
    }

    public ResourceLocation sprite(float p_407304_) {
        if (p_407304_ <= this.nearDistance) {
            return this.spriteLocations.getFirst();
        } else if (p_407304_ >= this.farDistance) {
            return this.spriteLocations.getLast();
        } else {
            int i = Mth.lerpInt((p_407304_ - this.nearDistance) / (this.farDistance - this.nearDistance), 0, this.spriteLocations.size());
            return this.spriteLocations.get(i);
        }
    }
}
