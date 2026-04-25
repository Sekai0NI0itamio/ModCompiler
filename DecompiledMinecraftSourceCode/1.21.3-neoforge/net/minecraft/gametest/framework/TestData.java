package net.minecraft.gametest.framework;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Rotation;

public record TestData<EnvironmentType>(
    EnvironmentType environment,
    ResourceLocation structure,
    int maxTicks,
    int setupTicks,
    boolean required,
    Rotation rotation,
    boolean manualOnly,
    int maxAttempts,
    int requiredSuccesses,
    boolean skyAccess
) {
    public static final MapCodec<TestData<Holder<TestEnvironmentDefinition>>> CODEC = RecordCodecBuilder.mapCodec(
        p_394785_ -> p_394785_.group(
                TestEnvironmentDefinition.CODEC.fieldOf("environment").forGetter(TestData::environment),
                ResourceLocation.CODEC.fieldOf("structure").forGetter(TestData::structure),
                ExtraCodecs.POSITIVE_INT.fieldOf("max_ticks").forGetter(TestData::maxTicks),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("setup_ticks", 0).forGetter(TestData::setupTicks),
                Codec.BOOL.optionalFieldOf("required", true).forGetter(TestData::required),
                Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(TestData::rotation),
                Codec.BOOL.optionalFieldOf("manual_only", false).forGetter(TestData::manualOnly),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_attempts", 1).forGetter(TestData::maxAttempts),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("required_successes", 1).forGetter(TestData::requiredSuccesses),
                Codec.BOOL.optionalFieldOf("sky_access", false).forGetter(TestData::skyAccess)
            )
            .apply(p_394785_, TestData::new)
    );

    public TestData(EnvironmentType p_395932_, ResourceLocation p_397933_, int p_396112_, int p_397698_, boolean p_392962_, Rotation p_396745_) {
        this(p_395932_, p_397933_, p_396112_, p_397698_, p_392962_, p_396745_, false, 1, 1, false);
    }

    public TestData(EnvironmentType p_391572_, ResourceLocation p_393548_, int p_392429_, int p_393447_, boolean p_392718_) {
        this(p_391572_, p_393548_, p_392429_, p_393447_, p_392718_, Rotation.NONE);
    }

    public <T> TestData<T> map(Function<EnvironmentType, T> p_396192_) {
        return new TestData<>(
            p_396192_.apply(this.environment),
            this.structure,
            this.maxTicks,
            this.setupTicks,
            this.required,
            this.rotation,
            this.manualOnly,
            this.maxAttempts,
            this.requiredSuccesses,
            this.skyAccess
        );
    }
}
