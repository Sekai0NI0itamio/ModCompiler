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

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.server.timings.ForgeTimings;
import net.minecraftforge.server.timings.TimeTracker;

class CommandTrack
{
    private static final DecimalFormat TIME_FORMAT = new DecimalFormat("#####0.00");

    static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.m_82127_("track")
            .then(StartTrackingCommand.register())
            .then(ResetTrackingCommand.register())
            .then(TrackResultsEntity.register())
            .then(TrackResultsBlockEntity.register())
            .then(StartTrackingCommand.register());
    }

    private static class StartTrackingCommand
    {
        static ArgumentBuilder<CommandSourceStack, ?> register()
        {
            return Commands.m_82127_("start")
                .requires(cs->cs.m_6761_(2)) //permission
                .then(Commands.m_82127_("te")
                    .then(Commands.m_82129_("duration", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int duration = IntegerArgumentType.getInteger(ctx, "duration");
                            TimeTracker.BLOCK_ENTITY_UPDATE.reset();
                            TimeTracker.BLOCK_ENTITY_UPDATE.enable(duration);
                            ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.tracking.be.enabled", duration), true);
                            return 0;
                        })
                    )
                )
                .then(Commands.m_82127_("entity")
                    .then(Commands.m_82129_("duration", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int duration = IntegerArgumentType.getInteger(ctx, "duration");
                            TimeTracker.ENTITY_UPDATE.reset();
                            TimeTracker.ENTITY_UPDATE.enable(duration);
                            ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.tracking.entity.enabled", duration), true);
                            return 0;
                        })
                    )
                );
        }
    }

    private static class ResetTrackingCommand
    {
        static ArgumentBuilder<CommandSourceStack, ?> register()
        {
            return Commands.m_82127_("reset")
                .requires(cs->cs.m_6761_(2)) //permission
                .then(Commands.m_82127_("te")
                    .executes(ctx -> {
                        TimeTracker.BLOCK_ENTITY_UPDATE.reset();
                        ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.tracking.be.reset"), true);
                        return 0;
                    })
                )
                .then(Commands.m_82127_("entity")
                    .executes(ctx -> {
                        TimeTracker.ENTITY_UPDATE.reset();
                        ctx.getSource().m_81354_(new TranslatableComponent("commands.forge.tracking.entity.reset"), true);
                        return 0;
                    })
                );
        }
    }

    private static class TrackResults
    {
        /**
         * Returns the time objects recorded by the time tracker sorted by average time
         *
         * @return A list of time objects
         */
        private static <T> List<ForgeTimings<T>> getSortedTimings(TimeTracker<T> tracker)
        {
            ArrayList<ForgeTimings<T>> list = new ArrayList<>();

            list.addAll(tracker.getTimingData());
            list.sort(Comparator.comparingDouble(ForgeTimings::getAverageTimings));
            Collections.reverse(list);

            return list;
        }

        private static <T> int execute(CommandSourceStack source, TimeTracker<T> tracker, Function<ForgeTimings<T>, Component> toString) throws CommandRuntimeException
        {
            List<ForgeTimings<T>> timingsList = getSortedTimings(tracker);
            if (timingsList.isEmpty())
            {
                source.m_81354_(new TranslatableComponent("commands.forge.tracking.no_data"), true);
            }
            else
            {
                timingsList.stream()
                        .filter(timings -> timings.getObject().get() != null)
                        .limit(10)
                        .forEach(timings -> source.m_81354_(toString.apply(timings), true));
            }
            return 0;
        }
    }

    private static class TrackResultsEntity
    {
        static ArgumentBuilder<CommandSourceStack, ?> register()
        {
            return Commands.m_82127_("entity").executes(ctx -> TrackResults.execute(ctx.getSource(), TimeTracker.ENTITY_UPDATE, data ->
                {
                    Entity entity = data.getObject().get();
                    if (entity == null)
                        return new TranslatableComponent("commands.forge.tracking.invalid");

                    BlockPos pos = entity.m_142538_();
                    double averageTimings = data.getAverageTimings();
                    String tickTime = (averageTimings > 1000 ? TIME_FORMAT.format(averageTimings / 1000) : TIME_FORMAT.format(averageTimings)) + (averageTimings < 1000 ? "\u03bcs" : "ms");

                    return new TranslatableComponent("commands.forge.tracking.timing_entry", entity.m_6095_().getRegistryName(), entity.f_19853_.m_46472_().m_135782_().toString(), pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), tickTime);
                })
            );
        }
    }

    private static class TrackResultsBlockEntity
    {
        static ArgumentBuilder<CommandSourceStack, ?> register()
        {
            return Commands.m_82127_("te").executes(ctx -> TrackResults.execute(ctx.getSource(), TimeTracker.BLOCK_ENTITY_UPDATE, data ->
                {
                    BlockEntity te = data.getObject().get();
                    if (te == null)
                        return new TranslatableComponent("commands.forge.tracking.invalid");

                    BlockPos pos = te.m_58899_();

                    double averageTimings = data.getAverageTimings();
                    String tickTime = (averageTimings > 1000 ? TIME_FORMAT.format(averageTimings / 1000) : TIME_FORMAT.format(averageTimings)) + (averageTimings < 1000 ? "\u03bcs" : "ms");
                    return new TranslatableComponent("commands.forge.tracking.timing_entry", te.m_58903_().getRegistryName(), te.m_58904_().m_46472_().m_135782_().toString(), pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), tickTime);
                })
            );
        }
    }
}
