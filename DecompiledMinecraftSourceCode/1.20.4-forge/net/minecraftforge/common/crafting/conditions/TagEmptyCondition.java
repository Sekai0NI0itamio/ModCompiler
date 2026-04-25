/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting.conditions;

import java.util.Optional;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record TagEmptyCondition(TagKey<Item> tag) implements ICondition {
    public static final MapCodec<TagEmptyCondition> CODEC = RecordCodecBuilder.mapCodec(b -> b.group(
        ResourceLocation.f_135803_.xmap(loc -> TagKey.m_203882_(Registries.f_256913_, loc), TagKey::f_203868_).fieldOf("tag").forGetter(TagEmptyCondition::tag)
    ).apply(b, TagEmptyCondition::new));


    @Override
    public boolean test(ICondition.IContext context, DynamicOps<?> ops) {
        // Check the actual deserialization context if possible
        if (ops instanceof RegistryOps<?> reg) {
            Optional<HolderGetter<Item>> items = reg.m_255006_(Registries.f_256913_);
            if (items.isPresent()) {
                var optional = items.get().m_255050_(this.tag);
                return optional.isPresent();
            }
        }
        return context.getTag(tag).isEmpty();
    }

    @Override
    public String toString() {
        return "tag_empty(\"" + tag.f_203868_() + "\")";
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
