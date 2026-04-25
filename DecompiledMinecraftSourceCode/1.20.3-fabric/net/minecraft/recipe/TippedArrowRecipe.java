/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

public class TippedArrowRecipe
extends SpecialCraftingRecipe {
    public TippedArrowRecipe(CraftingRecipeCategory craftingRecipeCategory) {
        super(craftingRecipeCategory);
    }

    @Override
    public boolean matches(RecipeInputInventory recipeInputInventory, World world) {
        if (recipeInputInventory.getWidth() != 3 || recipeInputInventory.getHeight() != 3) {
            return false;
        }
        for (int i = 0; i < recipeInputInventory.getWidth(); ++i) {
            for (int j = 0; j < recipeInputInventory.getHeight(); ++j) {
                ItemStack itemStack = recipeInputInventory.getStack(i + j * recipeInputInventory.getWidth());
                if (itemStack.isEmpty()) {
                    return false;
                }
                if (!(i == 1 && j == 1 ? !itemStack.isOf(Items.LINGERING_POTION) : !itemStack.isOf(Items.ARROW))) continue;
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack craft(RecipeInputInventory recipeInputInventory, RegistryWrapper.WrapperLookup wrapperLookup) {
        ItemStack itemStack = recipeInputInventory.getStack(1 + recipeInputInventory.getWidth());
        if (!itemStack.isOf(Items.LINGERING_POTION)) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack2 = new ItemStack(Items.TIPPED_ARROW, 8);
        itemStack2.set(DataComponentTypes.POTION_CONTENTS, itemStack.get(DataComponentTypes.POTION_CONTENTS));
        return itemStack2;
    }

    @Override
    public boolean fits(int width, int height) {
        return width >= 2 && height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.TIPPED_ARROW;
    }
}

