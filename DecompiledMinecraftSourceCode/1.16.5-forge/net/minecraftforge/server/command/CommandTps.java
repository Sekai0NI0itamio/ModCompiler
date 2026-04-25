/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import java.text.DecimalFormat;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.server.ServerWorld;

class CommandTps
{
    private static final DecimalFormat TIME_FORMATTER = new DecimalFormat("########0.000");
    private static final long[] UNLOADED = new long[] {0};

    static ArgumentBuilder<CommandSource, ?> register()
    {
        return Commands.func_197057_a("tps")
            .requires(cs->cs.func_197034_c(0)) //permission
            .then(Commands.func_197056_a("dim", DimensionArgument.func_212595_a())
                .executes(ctx -> sendTime(ctx.getSource(), DimensionArgument.func_212592_a(ctx, "dim")))
            )
            .executes(ctx -> {
                for (ServerWorld dim : ctx.getSource().func_197028_i().func_212370_w())
                    sendTime(ctx.getSource(), dim);

                @SuppressWarnings("resource")
                double meanTickTime = mean(ctx.getSource().func_197028_i().field_71311_j) * 1.0E-6D;
                double meanTPS = Math.min(1000.0/meanTickTime, 20);
                ctx.getSource().func_197030_a(new TranslationTextComponent("commands.forge.tps.summary.all", TIME_FORMATTER.format(meanTickTime), TIME_FORMATTER.format(meanTPS)), false);

                return 0;
            }
        );
    }

    private static int sendTime(CommandSource cs, ServerWorld dim) throws CommandSyntaxException
    {
        long[] times = cs.func_197028_i().getTickTime(dim.func_234923_W_());

        if (times == null) // Null means the world is unloaded. Not invalid. That's taken car of by DimensionArgument itself.
            times = UNLOADED;

        final Registry<DimensionType> reg = cs.func_241861_q().func_243612_b(Registry.field_239698_ad_);
        double worldTickTime = mean(times) * 1.0E-6D;
        double worldTPS = Math.min(1000.0 / worldTickTime, 20);
        cs.func_197030_a(new TranslationTextComponent("commands.forge.tps.summary.named", dim.func_234923_W_().func_240901_a_().toString(), reg.func_177774_c(dim.func_230315_m_()), TIME_FORMATTER.format(worldTickTime), TIME_FORMATTER.format(worldTPS)), false);

        return 1;
    }

    private static long mean(long[] values)
    {
        long sum = 0L;
        for (long v : values)
            sum += v;
        return sum / values.length;
    }
}
