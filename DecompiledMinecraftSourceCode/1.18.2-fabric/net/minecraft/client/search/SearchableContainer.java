/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.search;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.search.Searchable;

@Environment(value=EnvType.CLIENT)
public interface SearchableContainer<T>
extends Searchable<T> {
    public void add(T var1);

    public void clear();

    public void reload();
}

