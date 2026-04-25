/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.loot.condition;

import net.minecraft.loot.condition.LootCondition;

public interface LootConditionConsumingBuilder<T> {
    public T conditionally(LootCondition.Builder var1);

    public T getThis();
}

