/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.item;

import net.minecraft.item.Item;

/**
 * Represents an object that has an item form.
 */
public interface ItemConvertible {
    /**
     * Gets this object in its item form.
     */
    public Item asItem();
}

