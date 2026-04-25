/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.data;

import com.google.gson.JsonElement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Advancement.Builder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Ingredient.ItemValue;
import net.minecraft.world.item.crafting.Ingredient.TagValue;
import net.minecraft.world.item.crafting.Ingredient.Value;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ForgeRecipeProvider extends VanillaRecipeProvider {
    private final Map<Item, TagKey<Item>> replacements = new HashMap<>();
    private final Set<ResourceLocation> excludes = new HashSet<>();

    public ForgeRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup) {
        super(packOutput, lookup);
    }

    private void exclude(ItemLike item) {
        excludes.add(ForgeRegistries.ITEMS.getKey(item.m_5456_()));
    }

    private void exclude(String name) {
        excludes.add(new ResourceLocation(name));
    }

    private void replace(ItemLike item, TagKey<Item> tag) {
        replacements.put(item.m_5456_(), tag);
    }

    @Override
    protected void m_245200_(RecipeOutput consumer) {
        replace(Items.f_42398_, Tags.Items.RODS_WOODEN);
        replace(Items.f_42417_, Tags.Items.INGOTS_GOLD);
        replace(Items.f_42416_, Tags.Items.INGOTS_IRON);
        replace(Items.f_42418_, Tags.Items.INGOTS_NETHERITE);
        replace(Items.f_151052_, Tags.Items.INGOTS_COPPER);
        replace(Items.f_151049_, Tags.Items.GEMS_AMETHYST);
        replace(Items.f_42415_, Tags.Items.GEMS_DIAMOND);
        replace(Items.f_42616_, Tags.Items.GEMS_EMERALD);
        replace(Items.f_42009_, Tags.Items.CHESTS_WOODEN);
        replace(Blocks.f_50652_, Tags.Items.COBBLESTONE_NORMAL);
        replace(Blocks.f_152551_, Tags.Items.COBBLESTONE_DEEPSLATE);

        replace(Items.f_42401_, Tags.Items.STRING);
        exclude(m_176517_(Blocks.f_50041_, Items.f_42401_));

        exclude(Blocks.f_50074_);
        exclude(Items.f_42587_);
        exclude(Blocks.f_50075_);
        exclude(Items.f_42749_);
        exclude(Blocks.f_50090_);
        exclude(Blocks.f_50268_);
        exclude(Blocks.f_50721_);
        exclude(Blocks.f_152504_);
        exclude(Blocks.f_152490_);

        exclude(Blocks.f_50157_);
        exclude(Blocks.f_50409_);
        exclude(Blocks.f_50274_);
        exclude(Blocks.f_152552_);
        exclude(Blocks.f_152553_);
        exclude(Blocks.f_152554_);

        super.m_245200_(new RecipeOutput() {
            @Override
            public void m_292927_(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement) {
                var modified = enhance(id, recipe);
                if (modified != null)
                    consumer.m_292927_(id, modified, advancement);
            }

            @Override
            public Builder m_293552_() {
                return consumer.m_293552_();
            }

            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, ResourceLocation advancementId, JsonElement advancement) {
            }

            @Override
            public Provider registry() {
                return consumer.registry();
            }
        });
    }

    @Nullable
    private Recipe<?> enhance(ResourceLocation id, Recipe<?> vanilla) {
        if (vanilla instanceof ShapelessRecipe shapeless)
            return enhance(id, shapeless);
        if (vanilla instanceof ShapedRecipe shaped)
            return enhance(id, shaped);
        return null;
    }

    @Nullable
    private Recipe<?> enhance(ResourceLocation id, ShapelessRecipe vanilla) {
        List<Ingredient> ingredients = getField(ShapelessRecipe.class, vanilla, 3);
        boolean modified = false;
        for (int x = 0; x < ingredients.size(); x++) {
            Ingredient ing = enhance(id, ingredients.get(x));
            if (ing != null) {
                ingredients.set(x, ing);
                modified = true;
            }
        }
        return modified ? vanilla : null;
    }

    @Nullable
    @Override
    protected CompletableFuture<?> saveAdvancement(CachedOutput output, ResourceLocation advancementId, JsonElement advancement) {
        // NOOP - We don't replace any of the advancement things yet...
        return null;
    }

    @Override
    protected CompletableFuture<?> m_253240_(CachedOutput p_253674_, HolderLookup.Provider p_335995_, AdvancementHolder p_297687_) {
        // NOOP - We don't replace any of the advancement things yet...
        return CompletableFuture.allOf();
    }

    @Nullable
    private Recipe<?> enhance(ResourceLocation id, ShapedRecipe vanilla) {
        ShapedRecipePattern pattern = getField(ShapedRecipe.class, vanilla, 2);
        var data = pattern.f_302791_().orElseThrow(() -> new IllegalStateException("Weird shaped recipe, data is missing? " + id + " " + vanilla));
        Map<Character, Ingredient> ingredients = data.f_302495_();
        boolean modified = false;
        for (Character x : ingredients.keySet()) {
            Ingredient ing = enhance(id, ingredients.get(x));
            if (ing != null) {
                ingredients.put(x, ing);
                modified = true;
            }
        }
        return modified ? vanilla : null;
    }

    @Nullable
    private Ingredient enhance(ResourceLocation name, Ingredient vanilla) {
        if (excludes.contains(name))
            return null;

        boolean modified = false;
        List<Value> items = new ArrayList<>();
        Value[] vanillaItems = getField(Ingredient.class, vanilla, 3); //This will probably crash between versions, if null fix index
        for (Value entry : vanillaItems) {
            if (entry instanceof ItemValue) {
                ItemStack stack = entry.m_6223_().stream().findFirst().orElse(ItemStack.f_41583_);
                TagKey<Item> replacement = replacements.get(stack.m_41720_());
                if (replacement != null) {
                    items.add(new TagValue(replacement));
                    modified = true;
                } else
                    items.add(entry);
            } else
                items.add(entry);
        }
        return modified ? Ingredient.m_43938_(items.stream()) : null;
    }

    @SuppressWarnings("unchecked")
    private <T, R> R getField(Class<T> clz, T inst, int index) {
        Field fld = clz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            return (R) fld.get(inst);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
