/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RecipeCache {
    private final CachedRecipe[] cache;
    private WeakReference<RecipeManager> recipeManagerRef = new WeakReference<Object>(null);

    public RecipeCache(int size) {
        this.cache = new CachedRecipe[size];
    }

    public Optional<RecipeEntry<CraftingRecipe>> getRecipe(World world, RecipeInputInventory inputInventory) {
        if (inputInventory.isEmpty()) {
            return Optional.empty();
        }
        this.validateRecipeManager(world);
        for (int i = 0; i < this.cache.length; ++i) {
            CachedRecipe cachedRecipe = this.cache[i];
            if (cachedRecipe == null || !cachedRecipe.matches(inputInventory.getHeldStacks())) continue;
            this.sendToFront(i);
            return Optional.ofNullable(cachedRecipe.value());
        }
        return this.getAndCacheRecipe(inputInventory, world);
    }

    private void validateRecipeManager(World world) {
        RecipeManager recipeManager = world.getRecipeManager();
        if (recipeManager != this.recipeManagerRef.get()) {
            this.recipeManagerRef = new WeakReference<RecipeManager>(recipeManager);
            Arrays.fill(this.cache, null);
        }
    }

    private Optional<RecipeEntry<CraftingRecipe>> getAndCacheRecipe(RecipeInputInventory inputInventory, World world) {
        Optional<RecipeEntry<CraftingRecipe>> optional = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inputInventory, world);
        this.cache(inputInventory.getHeldStacks(), optional.orElse(null));
        return optional;
    }

    private void sendToFront(int index) {
        if (index > 0) {
            CachedRecipe cachedRecipe = this.cache[index];
            System.arraycopy(this.cache, 0, this.cache, 1, index);
            this.cache[0] = cachedRecipe;
        }
    }

    private void cache(List<ItemStack> inputStacks, @Nullable RecipeEntry<CraftingRecipe> recipe) {
        DefaultedList<ItemStack> defaultedList = DefaultedList.ofSize(inputStacks.size(), ItemStack.EMPTY);
        for (int i = 0; i < inputStacks.size(); ++i) {
            defaultedList.set(i, inputStacks.get(i).copyWithCount(1));
        }
        System.arraycopy(this.cache, 0, this.cache, 1, this.cache.length - 1);
        this.cache[0] = new CachedRecipe(defaultedList, recipe);
    }

    record CachedRecipe(DefaultedList<ItemStack> key, @Nullable RecipeEntry<CraftingRecipe> value) {
        public boolean matches(List<ItemStack> inputs) {
            if (this.key.size() != inputs.size()) {
                return false;
            }
            for (int i = 0; i < this.key.size(); ++i) {
                if (ItemStack.areItemsAndComponentsEqual(this.key.get(i), inputs.get(i))) continue;
                return false;
            }
            return true;
        }

        @Nullable
        public RecipeEntry<CraftingRecipe> value() {
            return this.value;
        }
    }
}

