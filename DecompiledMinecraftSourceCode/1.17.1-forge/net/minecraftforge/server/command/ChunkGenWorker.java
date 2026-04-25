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

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.BaseComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.common.WorldWorkerManager.IWorker;

public class ChunkGenWorker implements IWorker
{
    private final CommandSourceStack listener;
    protected final BlockPos start;
    protected final int total;
    private final ServerLevel dim;
    private final Queue<BlockPos> queue;
    private final int notificationFrequency;
    private int lastNotification = 0;
    private long lastNotifcationTime = 0;
    private int genned = 0;
    private Boolean keepingLoaded;

    public ChunkGenWorker(CommandSourceStack listener, BlockPos start, int total, ServerLevel dim, int interval)
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
                ret.add(start.m_142082_(radius, 0, q));

            for (int q = radius - 1; q >= -radius && ret.size() < total; q--)
                ret.add(start.m_142082_(q, 0, radius));

            for (int q = radius - 1; q >= -radius && ret.size() < total; q--)
                ret.add(start.m_142082_(-radius, 0, q));

            for (int q = -radius + 1; q <= radius && ret.size() < total; q++)
                ret.add(start.m_142082_(q, 0, -radius));

            radius++;
        }
        return ret;
    }

    public BaseComponent getStartMessage(CommandSourceStack sender)
    {
        return new TranslatableComponent("commands.forge.gen.start", total, start.m_123341_(), start.m_123343_(), dim);
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
                listener.m_81354_(new TranslatableComponent("commands.forge.gen.progress", total - queue.size(), total), true);
                lastNotification = 0;
                lastNotifcationTime = System.currentTimeMillis();
            }

            int x = next.m_123341_();
            int z = next.m_123343_();

            if (!dim.m_7232_(x, z)) { //Chunk is unloaded
                ChunkAccess chunk = dim.m_6522_(x, z, ChunkStatus.f_62314_, true);
                if (!chunk.m_6415_().m_62427_(ChunkStatus.f_62326_)) {
                    chunk = dim.m_46819_(x, z, ChunkStatus.f_62326_);
                    genned++; //There isn't a way to check if the chunk is actually created just if it was loaded
                }
            }
        }

        if (queue.size() == 0)
        {
            listener.m_81354_(new TranslatableComponent("commands.forge.gen.complete", genned, total, dim.m_46472_().m_135782_()), true);
            /* TODO: Readd if/when we introduce world unloading, or get Mojang to do it.
            if (keepingLoaded != null && !keepingLoaded)
                DimensionManager.keepLoaded(dim, false);
            */
            return false;
        }
        return true;
    }
}
