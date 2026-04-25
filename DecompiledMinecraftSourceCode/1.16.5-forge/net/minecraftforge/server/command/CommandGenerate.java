/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.WorldWorkerManager;

class CommandGenerate
{
    static ArgumentBuilder<CommandSource, ?> register()
    {
        return Commands.func_197057_a("generate")
            .requires(cs->cs.func_197034_c(4)) //permission
            .then(Commands.func_197056_a("pos", BlockPosArgument.func_197276_a())
                .then(Commands.func_197056_a("count", IntegerArgumentType.integer(1))
                    .then(Commands.func_197056_a("dim", DimensionArgument.func_212595_a())
                        .then(Commands.func_197056_a("interval", IntegerArgumentType.integer())
                            .executes(ctx -> execute(ctx.getSource(), BlockPosArgument.func_197274_b(ctx, "pos"), getInt(ctx, "count"), DimensionArgument.func_212592_a(ctx, "dim"), getInt(ctx, "interval")))
                        )
                        .executes(ctx -> execute(ctx.getSource(), BlockPosArgument.func_197274_b(ctx, "pos"), getInt(ctx, "count"), DimensionArgument.func_212592_a(ctx, "dim"), -1))
                    )
                    .executes(ctx -> execute(ctx.getSource(), BlockPosArgument.func_197274_b(ctx, "pos"), getInt(ctx, "count"), ctx.getSource().func_197023_e(), -1))
                )
            );
    }

    private static int getInt(CommandContext<CommandSource> ctx, String name)
    {
        return IntegerArgumentType.getInteger(ctx, name);
    }

    private static int execute(CommandSource source, BlockPos pos, int count, ServerWorld dim, int interval) throws CommandException
    {
        BlockPos chunkpos = new BlockPos(pos.func_177958_n() >> 4, 0, pos.func_177952_p() >> 4);

        ChunkGenWorker worker = new ChunkGenWorker(source, chunkpos, count, dim, interval);
        source.func_197030_a(worker.getStartMessage(source), true);
        WorldWorkerManager.addWorker(worker);

        return 0;
    }
}
