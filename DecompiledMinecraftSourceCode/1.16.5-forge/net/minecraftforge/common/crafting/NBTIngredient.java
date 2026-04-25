/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;

public class NBTIngredient extends Ingredient
{
    private final ItemStack stack;
    protected NBTIngredient(ItemStack stack)
    {
        super(Stream.of(new Ingredient.SingleItemList(stack)));
        this.stack = stack;
    }

    @Override
    public boolean test(@Nullable ItemStack input)
    {
        if (input == null)
            return false;
        //Can't use areItemStacksEqualUsingNBTShareTag because it compares stack size as well
        return this.stack.func_77973_b() == input.func_77973_b() && this.stack.func_77952_i() == input.func_77952_i() && this.stack.areShareTagsEqual(input);
    }

    @Override
    public boolean isSimple()
    {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer()
    {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement func_200304_c()
    {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        json.addProperty("item", stack.func_77973_b().getRegistryName().toString());
        json.addProperty("count", stack.func_190916_E());
        if (stack.func_77942_o())
            json.addProperty("nbt", stack.func_77978_p().toString());
        return json;
    }

    public static class Serializer implements IIngredientSerializer<NBTIngredient>
    {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public NBTIngredient parse(PacketBuffer buffer) {
            return new NBTIngredient(buffer.func_150791_c());
        }

        @Override
        public NBTIngredient parse(JsonObject json) {
            return new NBTIngredient(CraftingHelper.getItemStack(json, true));
        }

        @Override
        public void write(PacketBuffer buffer, NBTIngredient ingredient) {
            buffer.func_150788_a(ingredient.stack);
        }
    }
}
