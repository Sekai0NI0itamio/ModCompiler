/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import com.mojang.datafixers.util.Pair;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RepairItemRecipe
extends SpecialCraftingRecipe {
    public RepairItemRecipe(CraftingRecipeCategory craftingRecipeCategory) {
        super(craftingRecipeCategory);
    }

    @Nullable
    private Pair<ItemStack, ItemStack> findPair(RecipeInputInventory inventory) {
        ItemStack itemStack = null;
        ItemStack itemStack2 = null;
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack3 = inventory.getStack(i);
            if (itemStack3.isEmpty()) continue;
            if (itemStack == null) {
                itemStack = itemStack3;
                continue;
            }
            if (itemStack2 == null) {
                itemStack2 = itemStack3;
                continue;
            }
            return null;
        }
        if (itemStack != null && itemStack2 != null && RepairItemRecipe.canCombineStacks(itemStack, itemStack2)) {
            return Pair.of(itemStack, itemStack2);
        }
        return null;
    }

    private static boolean canCombineStacks(ItemStack first, ItemStack second) {
        return second.isOf(first.getItem()) && first.getCount() == 1 && second.getCount() == 1 && first.contains(DataComponentTypes.MAX_DAMAGE) && second.contains(DataComponentTypes.MAX_DAMAGE) && first.contains(DataComponentTypes.DAMAGE) && second.contains(DataComponentTypes.DAMAGE);
    }

    @Override
    public boolean matches(RecipeInputInventory recipeInputInventory, World world) {
        return this.findPair(recipeInputInventory) != null;
    }

    @Override
    public ItemStack craft(RecipeInputInventory recipeInputInventory, RegistryWrapper.WrapperLookup wrapperLookup) {
        Pair<ItemStack, ItemStack> pair = this.findPair(recipeInputInventory);
        if (pair == null) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack = pair.getFirst();
        ItemStack itemStack2 = pair.getSecond();
        int i = Math.max(itemStack.getMaxDamage(), itemStack2.getMaxDamage());
        int j = itemStack.getMaxDamage() - itemStack.getDamage();
        int k = itemStack2.getMaxDamage() - itemStack2.getDamage();
        int l = j + k + i * 5 / 100;
        ItemStack itemStack3 = new ItemStack(itemStack.getItem());
        itemStack3.set(DataComponentTypes.MAX_DAMAGE, i);
        itemStack3.setDamage(Math.max(i - l, 0));
        ItemEnchantmentsComponent itemEnchantmentsComponent = EnchantmentHelper.getEnchantments(itemStack);
        ItemEnchantmentsComponent itemEnchantmentsComponent2 = EnchantmentHelper.getEnchantments(itemStack2);
        EnchantmentHelper.apply(itemStack3, builder -> wrapperLookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().map(RegistryEntry::value).filter(Enchantment::isCursed).forEach(enchantment -> {
            int i = Math.max(itemEnchantmentsComponent.getLevel((Enchantment)enchantment), itemEnchantmentsComponent2.getLevel((Enchantment)enchantment));
            if (i > 0) {
                builder.add((Enchantment)enchantment, i);
            }
        }));
        return itemStack3;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}

