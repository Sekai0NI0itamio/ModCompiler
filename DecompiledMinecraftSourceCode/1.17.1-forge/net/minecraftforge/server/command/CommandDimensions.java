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

package net.minecraftforge.server.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.builder.ArgumentBuilder;

public class CommandDimensions
{
    static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.m_82127_("dimensions")
            .requires(cs->cs.m_6761_(0)) //permission
            .executes(ctx -> {
                ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.dimensions.list"), true);
                final Registry<DimensionType> reg = ctx.getSource().m_5894_().m_175515_(Registry.f_122818_);

                Map<ResourceLocation, List<ResourceLocation>> types = new HashMap<>();
                for (ServerLevel dim : ctx.getSource().m_81377_().m_129785_()) {
                    types.computeIfAbsent(reg.m_7981_(dim.m_6042_()), k -> new ArrayList<>()).add(dim.m_46472_().m_135782_());
                }

                types.keySet().stream().sorted().forEach(key -> {
                    ctx.getSource().m_81354_(new TextComponent(key + ": " + types.get(key).stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(", "))), false);
                });
                return 0;
            });
    }
}
