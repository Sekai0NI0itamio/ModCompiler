/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.DimensionType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraftforge.common.WorldWorkerManager.IWorker;

public class ChunkGenWorker implements IWorker
{
    private final CommandSource listener;
    protected final BlockPos start;
    protected final int total;
    private final ServerWorld dim;
    private final Queue<BlockPos> queue;
    private final int notificationFrequency;
    private int lastNotification = 0;
    private long lastNotifcationTime = 0;
    private int genned = 0;
    private Boolean keepingLoaded;

    public ChunkGenWorker(CommandSource listener, BlockPos start, int total, ServerWorld dim, int interval)
    {
        this.listener = listener;
        this.start = start;
        this.total = total;
        this.dim  = dim;
        this.queue = buildQueue();
        this.notificationFrequency = interval != -1 ? interval : Math.max(total / 20, 100); //Every 5% or every 100, whichever is more.
        this.lastNotifcationTime = System.currentTimeMillis(); //We also notify at least once every 60 seconds, to show we haven't froze.
    }

    protected Queue<BlockPos> buildQueue()
    {
        Queue<BlockPos> ret = new ArrayDeque<BlockPos>();
        ret.add(start);

        //This *should* spiral outwards, starting on right side, down, left, up, right, but hey we'll see!
        int radius = 1;
        while (ret.size() < total)
        {
            for (int q = -radius + 1; q <= radius && ret.size() < total; q++)
                ret.add(start.func_177982_a(radius, 0, q));

            for (int q = radius - 1; q >= -radius && ret.size() < total; q--)
                ret.add(start.func_177982_a(q, 0, radius));

            for (int q = radius - 1; q >= -radius && ret.size() < total; q--)
                ret.add(start.func_177982_a(-radius, 0, q));

            for (int q = -radius + 1; q <= radius && ret.size() < total; q++)
                ret.add(start.func_177982_a(q, 0, -radius));

            radius++;
        }
        return ret;
    }

    public TextComponent getStartMessage(CommandSource sender)
    {
        return new TranslationTextComponent("commands.forge.gen.start", total, start.func_177958_n(), start.func_177952_p(), dim);
    }

    @Override
    public boolean hasWork()
    {
        return queue.size() > 0;
    }

    @Override
    public boolean doWork()
    {
        /* TODO: Check how many things are pending save, and slow down world gen if to many
        AnvilChunkLoader loader = dim.getChunkProvider().chunkLoader instanceof AnvilChunkLoader ? (AnvilChunkLoader)world.getChunkProvider().chunkLoader : null;
        if (loader != null && loader.getPendingSaveCount() > 100)
        {

            if (lastNotifcationTime < System.currentTimeMillis() - 10*1000)
            {
                listener.sendFeedback(new TranslationTextComponent("commands.forge.gen.progress", total - queue.size(), total), true);
                lastNotifcationTime = System.currentTimeMillis();
            }
            return false;
        }
        */

        BlockPos next = queue.poll();

        if (next != null)
        {
            // While we work we don't want to cause world load spam so pause unloading the world.
            /* TODO: Readd if/when we introduce world unloading, or get Mojang to do it.
            if (keepingLoaded == null)
                keepingLoaded = DimensionManager.keepLoaded(dim, true);
            */

            if (++lastNotification >= notificationFrequency || lastNotifcationTime < System.currentTimeMillis() - 60*1000)
            {
                listener.func_197030_a(new TranslationTextComponent("commands.forge.gen.progress", total - queue.size(), total), true);
                lastNotification = 0;
                lastNotifcationTime = System.currentTimeMillis();
            }

            int x = next.func_177958_n();
            int z = next.func_177952_p();

            if (!dim.func_217354_b(x, z)) { //Chunk is unloaded
                IChunk chunk = dim.func_217353_a(x, z, ChunkStatus.field_223226_a_, true);
                if (!chunk.func_201589_g().func_209003_a(ChunkStatus.field_222617_m)) {
                    chunk = dim.func_217348_a(x, z, ChunkStatus.field_222617_m);
                    genned++; //There isn't a way to check if the chunk is actually created just if it was loaded
                }
            }
        }

        if (queue.size() == 0)
        {
            listener.func_197030_a(new TranslationTextComponent("commands.forge.gen.complete", genned, total, dim.func_234923_W_().func_240901_a_()), true);
            /* TODO: Readd if/when we introduce world unloading, or get Mojang to do it.
            if (keepingLoaded != null && !keepingLoaded)
                DimensionManager.keepLoaded(dim, false);
            */
            return false;
        }
        return true;
    }
}
