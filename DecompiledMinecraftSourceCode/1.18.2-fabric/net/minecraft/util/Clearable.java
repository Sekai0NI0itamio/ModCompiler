/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.util;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an object which can be cleared.
 */
public interface Clearable {
    public void clear();

    public static void clear(@Nullable Object o) {
        if (o instanceof Clearable) {
            ((Clearable)o).clear();
        }
    }
}

