package net.minecraft.data.recipes;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

public interface RecipeOutput {
    default void accept(ResourceKey<Recipe<?>> p_367162_, Recipe<?> p_360758_, @Nullable AdvancementHolder p_361155_) {
        if (p_361155_ == null) {
            accept(p_367162_, p_360758_, null, null);
        } else {
            var ops = registry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var json = Advancement.CODEC.encodeStart(ops, p_361155_.value()).getOrThrow(IllegalStateException::new);
            accept(p_367162_, p_360758_, p_361155_.id(), json);
        }
    }

    void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, net.minecraft.resources.@Nullable Identifier advancementId, com.google.gson.@Nullable JsonElement advancement);

    net.minecraft.core.HolderLookup.Provider registry();

    Advancement.Builder advancement();

    void includeRootAdvancement();
}
