/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.crafting;

import java.util.Collection;
import java.util.Collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient.Value;

public class StackList implements Value
{
    private Collection<ItemStack> items;
    public StackList(Collection<ItemStack> items)
    {
        this.items = Collections.unmodifiableCollection(items);
    }

    @Override
    public Collection<ItemStack> m_6223_()
    {
        return items;
    }

    @Override
    public JsonObject m_6544_()
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
        ret.addProperty("item", stack.m_41720_().getRegistryName().toString());
        if (stack.m_41613_() != 1)
            ret.addProperty("count", stack.m_41613_());
        if (stack.m_41783_() != null)
            ret.addProperty("nbt", stack.m_41783_().toString()); //TODO: Better serialization?
        return ret;
    }

}
