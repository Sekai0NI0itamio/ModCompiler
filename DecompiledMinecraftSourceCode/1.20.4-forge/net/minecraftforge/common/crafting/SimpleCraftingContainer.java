/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class SimpleCraftingContainer implements CraftingContainer {
    private final int width;
    private final int height;
    private final NonNullList<ItemStack> items;

    public static Builder builder() {
        return new Builder();
    }

    public SimpleCraftingContainer(int width, int height) {
        this(width, height, NonNullList.m_122780_(width * height, ItemStack.f_41583_));
    }

    public SimpleCraftingContainer(int width, int height, NonNullList<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = items;
        if (items.size() != (width * height))
            throw new IllegalArgumentException("Invalid item list, must be same size inventory width * height, received " + items.size() + " expected " + (width * height));
    }

    @Override
    public int m_6643_() {
        return this.items.size();
    }

    @Override
    public boolean m_7983_() {
        return this.items.stream().noneMatch(p -> !p.m_41619_());
    }

    @Override
    public ItemStack m_8020_(int slot) {
        return slot >= this.items.size() ? ItemStack.f_41583_ : this.items.get(slot);
    }

    @Override
    public ItemStack m_7407_(int slot, int count) {
        return ContainerHelper.m_18969_(this.items, slot, count);
    }

    @Override
    public ItemStack m_8016_(int slot) {
        return ContainerHelper.m_18966_(this.items, slot);
    }

    @Override
    public void m_6836_(int slot, ItemStack stack) {
        this.items.set(slot, stack);
    }

    @Override
    public void m_6596_() {
    }

    @Override
    public boolean m_6542_(Player player) {
        return true;
    }

    @Override
    public void m_6211_() {
        this.items.clear();
    }

    @Override
    public void m_5809_(StackedContents stacked) {
        this.items.forEach(stacked::m_36466_);
    }

    @Override
    public int m_39347_() {
        return width;
    }

    @Override
    public int m_39346_() {
        return height;
    }

    @Override
    public List<ItemStack> m_58617_() {
        return List.copyOf(this.items);
    }

    public static class Builder {
        private final List<String> rows = new ArrayList<>();
        private final Char2ObjectMap<ItemStack> keys = new Char2ObjectOpenHashMap<>();

        private Builder() {
            this.define(' ', ItemStack.f_41583_);
        }

        public Builder pattern(String row) {
            if (!this.rows.isEmpty() && row.length() != this.rows.get(0).length())
                throw new IllegalArgumentException("Pattern must be the same width on every line");
            this.rows.add(row);
            return this;
        }

        public Builder pattern(String... rows) {
            for (var row : rows)
                pattern(row);
            return this;
        }

        public Builder define(char key, ItemLike item) {
            return define(key, new ItemStack(item));
        }

        public Builder define(char key, ItemStack stack) {
            if (this.keys.containsKey(key))
                throw new IllegalArgumentException("key '" + key + "' is already defined.");
            this.keys.put(key, stack);
            return this;
        }

        public SimpleCraftingContainer build() {
            var unseen = new CharArraySet(this.keys.keySet());
            unseen.remove(' ');

            int height = this.rows.size();
            if (height == 0)
                throw new IllegalStateException("Invalid builder, empty inventory");
            int width = this.rows.get(0).length();
            var items = NonNullList.m_122780_(width * height, ItemStack.f_41583_);

            int idx = 0;
            for (var row : this.rows) {
                for (int x = 0; x < width; x++) {
                    char key = row.charAt(x);
                    var stack = this.keys.get(key);
                    if (stack == null)
                        throw new IllegalStateException("Invalid builder pattern, missing value for key '" + key + "'");
                    unseen.remove(key);
                    items.set(idx++, stack.m_41777_());
                }
            }

            if (!unseen.isEmpty())
                throw new IllegalStateException("Invalid builder, missing usage of defined keys: " + unseen);

            return new SimpleCraftingContainer(width, height, items);
        }
    }
}
