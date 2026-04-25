package com.mojang.math;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

public enum Quadrant {
    R0(0),
    R90(1),
    R180(2),
    R270(3);

    public static final Codec<Quadrant> CODEC = Codec.INT.comapFlatMap(p_394821_ -> {
        return switch (Mth.positiveModulo(p_394821_, 360)) {
            case 0 -> DataResult.success(R0);
            case 90 -> DataResult.success(R90);
            case 180 -> DataResult.success(R180);
            case 270 -> DataResult.success(R270);
            default -> DataResult.error(() -> "Invalid rotation " + p_394821_ + " found, only 0/90/180/270 allowed");
        };
    }, p_396271_ -> {
        return switch (p_396271_) {
            case R0 -> 0;
            case R90 -> 90;
            case R180 -> 180;
            case R270 -> 270;
        };
    });
    public final int shift;

    private Quadrant(final int p_394613_) {
        this.shift = p_394613_;
    }

    @Deprecated
    public static Quadrant parseJson(int p_393135_) {
        return switch (Mth.positiveModulo(p_393135_, 360)) {
            case 0 -> R0;
            case 90 -> R90;
            case 180 -> R180;
            case 270 -> R270;
            default -> throw new JsonParseException("Invalid rotation " + p_393135_ + " found, only 0/90/180/270 allowed");
        };
    }

    public int rotateVertexIndex(int p_391588_) {
        return (p_391588_ + this.shift) % 4;
    }
}
