package net.minecraft.world.entity.variant;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.advancements.critereon.MinMaxBounds;

public record MoonBrightnessCheck(MinMaxBounds.Doubles range) implements SpawnCondition {
    public static final MapCodec<MoonBrightnessCheck> MAP_CODEC = RecordCodecBuilder.mapCodec(
        p_398010_ -> p_398010_.group(MinMaxBounds.Doubles.CODEC.fieldOf("range").forGetter(MoonBrightnessCheck::range))
            .apply(p_398010_, MoonBrightnessCheck::new)
    );

    public boolean test(SpawnContext p_397532_) {
        return this.range.matches(p_397532_.level().getLevel().getMoonBrightness());
    }

    @Override
    public MapCodec<MoonBrightnessCheck> codec() {
        return MAP_CODEC;
    }
}
