package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.util.datafix.DataFixTypes;

public record SavedDataType<T extends SavedData>(
    String id, Function<SavedData.Context, T> constructor, Function<SavedData.Context, Codec<T>> codec, DataFixTypes dataFixType
) {
    public SavedDataType(String p_394401_, Supplier<T> p_392396_, Codec<T> p_392738_, DataFixTypes p_395974_) {
        this(p_394401_, p_393677_ -> p_392396_.get(), p_393917_ -> p_392738_, p_395974_);
    }

    @Override
    public boolean equals(Object p_393064_) {
        return p_393064_ instanceof SavedDataType<?> saveddatatype && this.id.equals(saveddatatype.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "SavedDataType[" + this.id + "]";
    }
}
