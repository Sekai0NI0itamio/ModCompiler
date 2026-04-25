/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.loot.function;

import net.minecraft.loot.function.LootFunction;

public interface LootFunctionConsumingBuilder<T> {
    public T apply(LootFunction.Builder var1);

    public T getThis();
}

