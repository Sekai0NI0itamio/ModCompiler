/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.server.ServerWorld;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.Collection;

/** @deprecated For removal in 1.17, superseded by {@code /execute in <dim> run tp <targets>} */
@Deprecated
public class CommandSetDimension
{
    private static final SimpleCommandExceptionType NO_ENTITIES = new SimpleCommandExceptionType(new TranslationTextComponent("commands.forge.setdim.invalid.entity"));
    private static final DynamicCommandExceptionType INVALID_DIMENSION = new DynamicCommandExceptionType(dim -> new TranslationTextComponent("commands.forge.setdim.invalid.dim", dim));
    static ArgumentBuilder<CommandSource, ?> register()
    {
        return Commands.func_197057_a("setdimension")
            .requires(cs->cs.func_197034_c(2)) //permission
            .then(Commands.func_197056_a("targets", EntityArgument.func_197093_b())
                .then(Commands.func_197056_a("dim", DimensionArgument.func_212595_a())
                    .then(Commands.func_197056_a("pos", BlockPosArgument.func_197276_a())
                        .executes(ctx -> execute(ctx, EntityArgument.func_197087_c(ctx, "targets"), DimensionArgument.func_212592_a(ctx, "dim"), BlockPosArgument.func_197274_b(ctx, "pos")))
                    )
                    .executes(ctx -> execute(ctx, EntityArgument.func_197087_c(ctx, "targets"), DimensionArgument.func_212592_a(ctx, "dim"), new BlockPos(ctx.getSource().func_197036_d())))
                )
            );
    }

    private static int execute(CommandContext<CommandSource> ctx, Collection<? extends Entity> entities, ServerWorld dim, BlockPos pos) throws CommandSyntaxException
    {
        entities.removeIf(e -> !canEntityTeleport(e));
        if (entities.isEmpty())
            throw NO_ENTITIES.create();

        String cmdTarget = "@s";
        String posTarget = "~ ~ ~";
        for (ParsedCommandNode<CommandSource> parsed : ctx.getNodes())
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
        final String dimName = dim.func_234923_W_().func_240901_a_().toString();
        final String finalCmdTarget = cmdTarget;
        final String finalPosTarget = posTarget;
        ITextComponent suggestion = new TranslationTextComponent("/execute in %s run tp %s %s", dimName, cmdTarget, finalPosTarget)
                .func_240700_a_((style) -> style.func_240712_a_(TextFormatting.GREEN).func_240715_a_(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/execute in " + dimName + " run tp " + finalCmdTarget + " " + finalPosTarget)));
        ctx.getSource().func_197030_a(new TranslationTextComponent("commands.forge.setdim.deprecated", suggestion), true);

        return 0;
    }

    private static boolean canEntityTeleport(Entity entity)
    {
        // use vanilla portal logic from BlockPortal#onEntityCollision
        return !entity.func_184218_aH() && !entity.func_184207_aI() && entity.func_184222_aU();
    }
}
