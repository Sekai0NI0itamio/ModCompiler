/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.loot.condition;

import java.util.function.Predicate;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.loot.condition.AnyOfLootCondition;
import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextAware;

/**
 * Loot conditions, officially {@index predicate}s, are JSON-based conditions to test
 * against in world. It's used in loot tables, advancements, and commands, and can be
 * defined by data packs.
 */
public interface LootCondition
extends LootContextAware,
Predicate<LootContext> {
    public LootConditionType getType();

    @FunctionalInterface
    public static interface Builder {
        public LootCondition build();

        default public Builder invert() {
            return InvertedLootCondition.builder(this);
        }

        default public AnyOfLootCondition.Builder or(Builder condition) {
            return AnyOfLootCondition.builder(this, condition);
        }

        default public AllOfLootCondition.Builder and(Builder condition) {
            return AllOfLootCondition.builder(this, condition);
        }
    }
}

