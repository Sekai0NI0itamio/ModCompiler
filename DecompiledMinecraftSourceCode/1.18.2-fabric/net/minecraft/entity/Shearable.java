/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity;

import net.minecraft.sound.SoundCategory;

/**
 * Represents an entity that can be sheared, either by a player or a
 * dispenser.
 */
public interface Shearable {
    public void sheared(SoundCategory var1);

    public boolean isShearable();
}

