/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.FriendlyByteBuf;

/** Ingredient that matches the given stack, performing an exact NBT match. Use {@link PartialNBTIngredient} if you need partial match. */
public class NBTIngredient extends AbstractIngredient
{
    private final ItemStack stack;
    protected NBTIngredient(ItemStack stack)
    {
        super(Stream.of(new Ingredient.ItemValue(stack)));
        this.stack = stack;
    }

    /** Creates a new ingredient matching the given stack and tag */
    public static NBTIngredient of(ItemStack stack)
    {
        return new NBTIngredient(stack);
    }

    @Override
    public boolean test(@Nullable ItemStack input)
    {
        if (input == null)
            return false;
        //Can't use areItemStacksEqualUsingNBTShareTag because it compares stack size as well
        return this.stack.m_41720_() == input.m_41720_() && this.stack.m_41773_() == input.m_41773_() && this.stack.areShareTagsEqual(input);
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
    public JsonElement m_43942_()
    {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        json.addProperty("item", stack.m_41720_().getRegistryName().toString());
        json.addProperty("count", stack.m_41613_());
        if (stack.m_41782_())
            json.addProperty("nbt", stack.m_41783_().toString());
        return json;
    }

    public static class Serializer implements IIngredientSerializer<NBTIngredient>
    {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public NBTIngredient parse(FriendlyByteBuf buffer) {
            return new NBTIngredient(buffer.m_130267_());
        }

        @Override
        public NBTIngredient parse(JsonObject json) {
            return new NBTIngredient(CraftingHelper.getItemStack(json, true));
        }

        @Override
        public void write(FriendlyByteBuf buffer, NBTIngredient ingredient) {
            buffer.m_130055_(ingredient.stack);
        }
    }
}
