/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.builder.ArgumentBuilder;

public class CommandDimensions
{
    static ArgumentBuilder<CommandSource, ?> register()
    {
        return Commands.func_197057_a("dimensions")
            .requires(cs->cs.func_197034_c(0)) //permission
            .executes(ctx -> {
                ctx.getSource().func_197030_a(new TranslationTextComponent("commands.forge.dimensions.list"), true);
                final Registry<DimensionType> reg = ctx.getSource().func_241861_q().func_243612_b(Registry.field_239698_ad_);

                Map<ResourceLocation, List<ResourceLocation>> types = new HashMap<>();
                for (ServerWorld dim : ctx.getSource().func_197028_i().func_212370_w()) {
                    types.computeIfAbsent(reg.func_177774_c(dim.func_230315_m_()), k -> new ArrayList<>()).add(dim.func_234923_W_().func_240901_a_());
                }

                types.keySet().stream().sorted().forEach(key -> {
                    ctx.getSource().func_197030_a(new StringTextComponent(key + ": " + types.get(key).stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(", "))), false);
                });
                return 0;
            });
    }
}
