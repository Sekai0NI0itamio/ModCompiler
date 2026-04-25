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

import java.text.DecimalFormat;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.level.ServerLevel;

class CommandTps
{
    private static final DecimalFormat TIME_FORMATTER = new DecimalFormat("########0.000");
    private static final long[] UNLOADED = new long[] {0};

    static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.m_82127_("tps")
            .requires(cs->cs.m_6761_(0)) //permission
            .then(Commands.m_82129_("dim", DimensionArgument.m_88805_())
                .executes(ctx -> sendTime(ctx.getSource(), DimensionArgument.m_88808_(ctx, "dim")))
            )
            .executes(ctx -> {
                for (ServerLevel dim : ctx.getSource().m_81377_().m_129785_())
                    sendTime(ctx.getSource(), dim);

                @SuppressWarnings("resource")
                double meanTickTime = mean(ctx.getSource().m_81377_().f_129748_) * 1.0E-6D;
                double meanTPS = Math.min(1000.0/meanTickTime, 20);
                ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.tps.summary.all", TIME_FORMATTER.format(meanTickTime), TIME_FORMATTER.format(meanTPS)), false);

                return 0;
            }
        );
    }

    private static int sendTime(CommandSourceStack cs, ServerLevel dim) throws CommandSyntaxException
    {
        long[] times = cs.m_81377_().getTickTime(dim.m_46472_());

        if (times == null) // Null means the world is unloaded. Not invalid. That's taken car of by DimensionArgument itself.
            times = UNLOADED;

        final Registry<DimensionType> reg = cs.m_5894_().m_175515_(Registry.f_122818_);
        double worldTickTime = mean(times) * 1.0E-6D;
        double worldTPS = Math.min(1000.0 / worldTickTime, 20);
        cs.m_81354_(new TranslatableComponent("commands.forge.tps.summary.named", dim.m_46472_().m_135782_().toString(), reg.m_7981_(dim.m_6042_()), TIME_FORMATTER.format(worldTickTime), TIME_FORMATTER.format(worldTPS)), false);

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
