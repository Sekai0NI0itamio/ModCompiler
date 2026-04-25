/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting.ingredients;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Ingredient that matches if any of the child ingredients match */
public class CompoundIngredient extends AbstractIngredient {
    public static Ingredient of(Ingredient... children) {
        if (children.length == 0)
            throw new IllegalArgumentException("Cannot create a compound ingredient with no children, use Ingredient.of() to create an empty ingredient");

        if (children.length == 1)
            return children[0];

        return new CompoundIngredient(Arrays.asList(children));
    }

    private List<Ingredient> children;
    private ItemStack[] stacks;
    private IntList itemIds;
    private final boolean isSimple;

    private CompoundIngredient(List<Ingredient> children) {
        this.children = Collections.unmodifiableList(children);
        this.isSimple = children.stream().allMatch(Ingredient::isSimple);
    }

    @Override
    @NotNull
    public ItemStack[] m_43908_() {
        if (stacks == null) {
            List<ItemStack> tmp = Lists.newArrayList();
            for (Ingredient child : children)
                Collections.addAll(tmp, child.m_43908_());
            stacks = tmp.toArray(new ItemStack[tmp.size()]);

        }
        return stacks;
    }

    @Override
    @NotNull
    public IntList m_43931_() {
        boolean childrenNeedInvalidation = false;
        for (Ingredient child : children)
            childrenNeedInvalidation |= child.checkInvalidation();
        if (childrenNeedInvalidation || this.itemIds == null || checkInvalidation()) {
            this.markValid();
            this.itemIds = new IntArrayList();
            for (Ingredient child : children)
                this.itemIds.addAll(child.m_43931_());
            this.itemIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.itemIds;
    }

    @Override
    public boolean test(@Nullable ItemStack target) {
        if (target == null)
            return false;

        return children.stream().anyMatch(c -> c.test(target));
    }

    @Override
    public boolean isSimple() {
        return isSimple;
    }

    @Override
    public boolean m_43947_() {
        return children.stream().allMatch(Ingredient::m_43947_);
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }

    public static final MapCodec<CompoundIngredient> CODEC = RecordCodecBuilder.mapCodec(builder ->
        builder.group(
            Ingredient.f_290991_.listOf().fieldOf("children").forGetter(i -> i.children)
        ).apply(builder, CompoundIngredient::new)
    );

    public static final IIngredientSerializer<CompoundIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public MapCodec<CompoundIngredient> codec() {
            return CODEC;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer, CompoundIngredient value) {
            buffer.m_236828_(value.children, (buf, child) -> Ingredient.f_317040_.m_318638_(buffer, child));
        }

        @Override
        public CompoundIngredient read(RegistryFriendlyByteBuf buffer) {
            var children = buffer.m_236838_(ArrayList::new, buf -> Ingredient.f_317040_.m_318688_(buffer));
            return new CompoundIngredient(children);
        }
    };
}
