/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting.ingredients;

import java.util.Arrays;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public interface IIngredientSerializer<T extends Ingredient> {
    MapCodec<? extends T> codec();

    void write(RegistryFriendlyByteBuf buffer, T value);
    T read(RegistryFriendlyByteBuf buffer);


    IIngredientSerializer<Ingredient> VANILLA = new IIngredientSerializer<>() {
        @Override
        public MapCodec<? extends Ingredient> codec() {
            return Ingredient.VANILLA_MAP_CODEC;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer, Ingredient value) {
            ItemStack.f_314960_.m_318638_(buffer, Arrays.asList(value.m_43908_()));
        }

        @Override
        public Ingredient read(RegistryFriendlyByteBuf buffer) {
            return Ingredient.m_43921_(ItemStack.f_314960_.m_318688_(buffer).stream());
        }
    };
}
