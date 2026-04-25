/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.data;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.block.Blocks;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.data.RecipeProvider;
import net.minecraft.data.ShapedRecipeBuilder;
import net.minecraft.data.ShapelessRecipeBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.Ingredient.IItemList;
import net.minecraft.item.crafting.Ingredient.TagList;
import net.minecraft.item.crafting.Ingredient.SingleItemList;
import net.minecraft.tags.ITag;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags;

public class ForgeRecipeProvider extends RecipeProvider
{
    private Map<Item, ITag<Item>> replacements = new HashMap<>();
    private Set<ResourceLocation> excludes = new HashSet<>();

    public ForgeRecipeProvider(DataGenerator generatorIn)
    {
        super(generatorIn);
    }

    private void exclude(IItemProvider item)
    {
        excludes.add(item.func_199767_j().getRegistryName());
    }

    private void replace(IItemProvider item, ITag<Item> tag)
    {
        replacements.put(item.func_199767_j(), tag);
    }

    @Override
    protected void func_200404_a(Consumer<IFinishedRecipe> consumer)
    {
        replace(Items.field_151055_y,        Tags.Items.RODS_WOODEN);
        replace(Items.field_151043_k,   Tags.Items.INGOTS_GOLD);
        replace(Items.field_151042_j,   Tags.Items.INGOTS_IRON);
        replace(Items.field_234759_km_, Tags.Items.INGOTS_NETHERITE);
        replace(Items.field_151045_i,      Tags.Items.GEMS_DIAMOND);
        replace(Items.field_151166_bC,      Tags.Items.GEMS_EMERALD);
        replace(Items.field_221675_bZ,        Tags.Items.CHESTS_WOODEN);
        replace(Blocks.field_150347_e, Tags.Items.COBBLESTONE);

        exclude(Blocks.field_150340_R);
        exclude(Items.field_151074_bl);
        exclude(Blocks.field_150339_S);
        exclude(Items.field_191525_da);
        exclude(Blocks.field_150484_ah);
        exclude(Blocks.field_150475_bE);
        exclude(Blocks.field_235397_ng_);

        exclude(Blocks.field_196659_cl);
        exclude(Blocks.field_196646_bz);
        exclude(Blocks.field_150463_bK);

        super.func_200404_a(vanilla -> {
            IFinishedRecipe modified = enhance(vanilla);
            if (modified != null)
                consumer.accept(modified);
        });
    }

    @Override
    protected void func_208310_b(DirectoryCache cache, JsonObject advancementJson, Path pathIn) {
        //NOOP - We dont replace any of the advancement things yet...
    }

    private IFinishedRecipe enhance(IFinishedRecipe vanilla)
    {
        if (vanilla instanceof ShapelessRecipeBuilder.Result)
            return enhance((ShapelessRecipeBuilder.Result)vanilla);
        if (vanilla instanceof ShapedRecipeBuilder.Result)
            return enhance((ShapedRecipeBuilder.Result)vanilla);
        return null;
    }

    private IFinishedRecipe enhance(ShapelessRecipeBuilder.Result vanilla)
    {
        List<Ingredient> ingredients = getField(ShapelessRecipeBuilder.Result.class, vanilla, 4);
        boolean modified = false;
        for (int x = 0; x < ingredients.size(); x++)
        {
            Ingredient ing = enhance(vanilla.func_200442_b(), ingredients.get(x));
            if (ing != null)
            {
                ingredients.set(x, ing);
                modified = true;
            }
        }
        return modified ? vanilla : null;
    }

    private IFinishedRecipe enhance(ShapedRecipeBuilder.Result vanilla)
    {
        Map<Character, Ingredient> ingredients = getField(ShapedRecipeBuilder.Result.class, vanilla, 5);
        boolean modified = false;
        for (Character x : ingredients.keySet())
        {
            Ingredient ing = enhance(vanilla.func_200442_b(), ingredients.get(x));
            if (ing != null)
            {
                ingredients.put(x, ing);
                modified = true;
            }
        }
        return modified ? vanilla : null;
    }

    private Ingredient enhance(ResourceLocation name, Ingredient vanilla)
    {
        if (excludes.contains(name))
            return null;

        boolean modified = false;
        List<IItemList> items = new ArrayList<>();
        IItemList[] vanillaItems = getField(Ingredient.class, vanilla, 2); //This will probably crash between versions, if null fix index
        for (IItemList entry : vanillaItems)
        {
            if (entry instanceof SingleItemList)
            {
                ItemStack stack = entry.func_199799_a().stream().findFirst().orElse(ItemStack.field_190927_a);
                ITag<Item> replacement = replacements.get(stack.func_77973_b());
                if (replacement != null)
                {
                    items.add(new TagList(replacement));
                    modified = true;
                }
                else
                    items.add(entry);
            }
            else
                items.add(entry);
        }
        return modified ? Ingredient.func_209357_a(items.stream()) : null;
    }

    @SuppressWarnings("unchecked")
    private <T, R> R getField(Class<T> clz, T inst, int index)
    {
        Field fld = clz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try
        {
            return (R)fld.get(inst);
        }
        catch (IllegalArgumentException | IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }
}
