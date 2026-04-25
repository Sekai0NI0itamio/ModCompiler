/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.BannerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public class BannerDuplicateRecipe
extends SpecialCraftingRecipe {
    public BannerDuplicateRecipe(CraftingRecipeCategory craftingRecipeCategory) {
        super(craftingRecipeCategory);
    }

    @Override
    public boolean matches(RecipeInputInventory recipeInputInventory, World world) {
        DyeColor dyeColor = null;
        ItemStack itemStack = null;
        ItemStack itemStack2 = null;
        for (int i = 0; i < recipeInputInventory.size(); ++i) {
            ItemStack itemStack3 = recipeInputInventory.getStack(i);
            if (itemStack3.isEmpty()) continue;
            Item item = itemStack3.getItem();
            if (!(item instanceof BannerItem)) {
                return false;
            }
            BannerItem bannerItem = (BannerItem)item;
            if (dyeColor == null) {
                dyeColor = bannerItem.getColor();
            } else if (dyeColor != bannerItem.getColor()) {
                return false;
            }
            int j = itemStack3.getOrDefault(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent.DEFAULT).layers().size();
            if (j > 6) {
                return false;
            }
            if (j > 0) {
                if (itemStack == null) {
                    itemStack = itemStack3;
                    continue;
                }
                return false;
            }
            if (itemStack2 == null) {
                itemStack2 = itemStack3;
                continue;
            }
            return false;
        }
        return itemStack != null && itemStack2 != null;
    }

    @Override
    public ItemStack craft(RecipeInputInventory recipeInputInventory, RegistryWrapper.WrapperLookup wrapperLookup) {
        for (int i = 0; i < recipeInputInventory.size(); ++i) {
            int j;
            ItemStack itemStack = recipeInputInventory.getStack(i);
            if (itemStack.isEmpty() || (j = itemStack.getOrDefault(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent.DEFAULT).layers().size()) <= 0 || j > 6) continue;
            return itemStack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public DefaultedList<ItemStack> getRemainder(RecipeInputInventory recipeInputInventory) {
        DefaultedList<ItemStack> defaultedList = DefaultedList.ofSize(recipeInputInventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < defaultedList.size(); ++i) {
            ItemStack itemStack = recipeInputInventory.getStack(i);
            if (itemStack.isEmpty()) continue;
            if (itemStack.getItem().hasRecipeRemainder()) {
                defaultedList.set(i, new ItemStack(itemStack.getItem().getRecipeRemainder()));
                continue;
            }
            if (itemStack.getOrDefault(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent.DEFAULT).layers().isEmpty()) continue;
            defaultedList.set(i, itemStack.copyWithCount(1));
        }
        return defaultedList;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.BANNER_DUPLICATE;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }
}

