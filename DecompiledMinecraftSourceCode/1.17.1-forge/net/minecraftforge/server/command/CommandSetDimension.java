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
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerLevel;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;

import java.util.Collection;

/** @deprecated For removal in 1.18, superseded by {@code /execute in <dim> run tp <targets>} */
@Deprecated(forRemoval = true, since = "1.17.1")
public class CommandSetDimension
{
    private static final SimpleCommandExceptionType NO_ENTITIES = new SimpleCommandExceptionType(new TranslatableComponent("commands.forge.setdim.invalid.entity"));
    static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.m_82127_("setdimension")
            .requires(cs->cs.m_6761_(2)) //permission
            .then(Commands.m_82129_("targets", EntityArgument.m_91460_())
                .then(Commands.m_82129_("dim", DimensionArgument.m_88805_())
                    .then(Commands.m_82129_("pos", BlockPosArgument.m_118239_())
                        .executes(ctx -> execute(ctx, EntityArgument.m_91467_(ctx, "targets"), DimensionArgument.m_88808_(ctx, "dim"), BlockPosArgument.m_174395_(ctx, "pos")))
                    )
                    .executes(ctx -> execute(ctx, EntityArgument.m_91467_(ctx, "targets"), DimensionArgument.m_88808_(ctx, "dim"), new BlockPos(ctx.getSource().m_81371_())))
                )
            );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, ServerLevel dim, BlockPos pos) throws CommandSyntaxException
    {
        entities.removeIf(e -> !canEntityTeleport(e));
        if (entities.isEmpty())
            throw NO_ENTITIES.create();

        String cmdTarget = "@s";
        String posTarget = "~ ~ ~";
        for (ParsedCommandNode<CommandSourceStack> parsed : ctx.getNodes())
        {
            if (parsed.getNode() instanceof ArgumentCommandNode)
            {
                if ("targets".equals(parsed.getNode().getName()))
                {
                    cmdTarget = parsed.getRange().get(ctx.getInput());
                }
                else if ("pos".equals(parsed.getNode().getName()))
                {
                    posTarget = parsed.getRange().get(ctx.getInput());
                }
            }
        }
        final String dimName = dim.m_46472_().m_135782_().toString();
        final String finalCmdTarget = cmdTarget;
        final String finalPosTarget = posTarget;
        Component suggestion = new TranslatableComponent("/execute in %s run tp %s %s", dimName, cmdTarget, finalPosTarget)
                .m_130938_((style) -> style.m_131140_(ChatFormatting.GREEN).m_131142_(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/execute in " + dimName + " run tp " + finalCmdTarget + " " + finalPosTarget)));
        ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.setdim.deprecated", suggestion), true);

        return 0;
    }

    private static boolean canEntityTeleport(Entity entity)
    {
        // use vanilla portal logic from BlockPortal#onEntityCollision
        return !entity.m_20159_() && !entity.m_20160_() && entity.m_6072_();
    }
}
