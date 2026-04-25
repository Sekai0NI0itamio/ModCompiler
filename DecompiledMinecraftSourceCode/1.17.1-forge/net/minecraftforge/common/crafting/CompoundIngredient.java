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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.FriendlyByteBuf;

public class CompoundIngredient extends Ingredient
{
    private List<Ingredient> children;
    private ItemStack[] stacks;
    private IntList itemIds;
    private final boolean isSimple;

    protected CompoundIngredient(List<Ingredient> children)
    {
        super(Stream.of());
        this.children = Collections.unmodifiableList(children);
        this.isSimple = children.stream().allMatch(Ingredient::isSimple);
    }

    @Override
    @Nonnull
    public ItemStack[] m_43908_()
    {
        if (stacks == null)
        {
            List<ItemStack> tmp = Lists.newArrayList();
            for (Ingredient child : children)
                Collections.addAll(tmp, child.m_43908_());
            stacks = tmp.toArray(new ItemStack[tmp.size()]);

        }
        return stacks;
    }

    @Override
    @Nonnull
    public IntList m_43931_()
    {
        //TODO: Add a child.isInvalid()?
        if (this.itemIds == null)
        {
            this.itemIds = new IntArrayList();
            for (Ingredient child : children)
                this.itemIds.addAll(child.m_43931_());
            this.itemIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.itemIds;
    }

    @Override
    public boolean test(@Nullable ItemStack target)
    {
        if (target == null)
            return false;

        return children.stream().anyMatch(c -> c.test(target));
    }

    @Override
    protected void invalidate()
    {
        this.itemIds = null;
        this.stacks = null;
        //Shouldn't need to invalidate children as this is only called form invalidateAll..
    }

    @Override
    public boolean isSimple()
    {
        return isSimple;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer()
    {
        return Serializer.INSTANCE;
    }

    @Nonnull
    public Collection<Ingredient> getChildren()
    {
        return this.children;
    }

    @Override
    public JsonElement m_43942_()
    {
       if (this.children.size() == 1)
       {
          return this.children.get(0).m_43942_();
       }
       else
       {
          JsonArray json = new JsonArray();
          this.children.stream().forEach(e -> json.add(e.m_43942_()));
          return json;
       }
    }

    @Override
    public boolean m_43947_()
    {
        return m_43908_().length == 0;
    }

    public static class Serializer implements IIngredientSerializer<CompoundIngredient>
    {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public CompoundIngredient parse(FriendlyByteBuf buffer)
        {
            return new CompoundIngredient(Stream.generate(() -> Ingredient.m_43940_(buffer)).limit(buffer.m_130242_()).collect(Collectors.toList()));
        }

        @Override
        public CompoundIngredient parse(JsonObject json)
        {
            throw new JsonSyntaxException("CompoundIngredient should not be directly referenced in json, just use an array of ingredients.");
        }

        @Override
        public void write(FriendlyByteBuf buffer, CompoundIngredient ingredient)
        {
            buffer.m_130130_(ingredient.children.size());
            ingredient.children.forEach(c -> c.m_43923_(buffer));
        }

    }
}
