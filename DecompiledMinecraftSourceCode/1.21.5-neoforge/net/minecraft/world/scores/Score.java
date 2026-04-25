package net.minecraft.world.scores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;

public class Score implements ReadOnlyScoreInfo {
    public static final MapCodec<Score> MAP_CODEC = RecordCodecBuilder.mapCodec(
        p_391144_ -> p_391144_.group(
                Codec.INT.optionalFieldOf("Score", 0).forGetter(Score::value),
                Codec.BOOL.optionalFieldOf("Locked", false).forGetter(Score::isLocked),
                ComponentSerialization.CODEC.optionalFieldOf("display").forGetter(p_391142_ -> Optional.ofNullable(p_391142_.display)),
                NumberFormatTypes.CODEC.optionalFieldOf("format").forGetter(p_391143_ -> Optional.ofNullable(p_391143_.numberFormat))
            )
            .apply(p_391144_, Score::new)
    );
    private int value;
    private boolean locked = true;
    @Nullable
    private Component display;
    @Nullable
    private NumberFormat numberFormat;

    public Score() {
    }

    private Score(int p_394607_, boolean p_396920_, Optional<Component> p_393165_, Optional<NumberFormat> p_395439_) {
        this.value = p_394607_;
        this.locked = p_396920_;
        this.display = p_393165_.orElse(null);
        this.numberFormat = p_395439_.orElse(null);
    }

    @Override
    public int value() {
        return this.value;
    }

    public void value(int p_313056_) {
        this.value = p_313056_;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean p_83399_) {
        this.locked = p_83399_;
    }

    @Nullable
    public Component display() {
        return this.display;
    }

    public void display(@Nullable Component p_312952_) {
        this.display = p_312952_;
    }

    @Nullable
    @Override
    public NumberFormat numberFormat() {
        return this.numberFormat;
    }

    public void numberFormat(@Nullable NumberFormat p_310093_) {
        this.numberFormat = p_310093_;
    }
}
