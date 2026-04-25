/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity;

import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an entity that can be saddled, either by a player or a
 * dispenser.
 */
public interface Saddleable {
    public boolean canBeSaddled();

    public void saddle(@Nullable SoundCategory var1);

    public boolean isSaddled();
}

