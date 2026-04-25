/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting;

import java.util.stream.Stream;

import com.google.gson.JsonObject;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;

public class VanillaIngredientSerializer implements IIngredientSerializer<Ingredient>
{
    public static final VanillaIngredientSerializer INSTANCE  = new VanillaIngredientSerializer();

    public Ingredient parse(PacketBuffer buffer)
    {
        return Ingredient.func_209357_a(Stream.generate(() -> new Ingredient.SingleItemList(buffer.func_150791_c())).limit(buffer.func_150792_a()));
    }

    public Ingredient parse(JsonObject json)
    {
       return Ingredient.func_209357_a(Stream.of(Ingredient.func_199803_a(json)));
    }

    public void write(PacketBuffer buffer, Ingredient ingredient)
    {
        ItemStack[] items = ingredient.func_193365_a();
        buffer.func_150787_b(items.length);

        for (ItemStack stack : items)
            buffer.func_150788_a(stack);
    }
}
