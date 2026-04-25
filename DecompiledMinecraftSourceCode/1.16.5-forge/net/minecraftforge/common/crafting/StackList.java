/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting;

import java.util.Collection;
import java.util.Collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient.IItemList;

public class StackList implements IItemList
{
    private Collection<ItemStack> items;
    public StackList(Collection<ItemStack> items)
    {
        this.items = Collections.unmodifiableCollection(items);
    }

    @Override
    public Collection<ItemStack> func_199799_a()
    {
        return items;
    }

    @Override
    public JsonObject func_200303_b()
    {
        if (items.size() == 1)
            return toJson(items.iterator().next());

        JsonObject ret = new JsonObject();
        JsonArray array = new JsonArray();
        items.forEach(stack -> array.add(toJson(stack)));
        ret.add("items", array);
        return ret;
    }

    private JsonObject toJson(ItemStack stack)
    {
        JsonObject ret = new JsonObject();
        ret.addProperty("item", stack.func_77973_b().getRegistryName().toString());
        if (stack.func_190916_E() != 1)
            ret.addProperty("count", stack.func_190916_E());
        if (stack.func_77978_p() != null)
            ret.addProperty("nbt", stack.func_77978_p().toString()); //TODO: Better serialization?
        return ret;
    }

}
