package com.itamio.nature_is_alive;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class StepTracker {

    private static final Long2IntOpenHashMap stepCounts = new Long2IntOpenHashMap();
    private static final Long2LongOpenHashMap lastWalkTick = new Long2LongOpenHashMap();
    private static long currentTick = 0;
    private static final long STALE_THRESHOLD = 48000;

    public static void onPlayerStep(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.DIRT_PATH)) return;

        long key = pos.asLong();
        stepCounts.addTo(key, 1);
        lastWalkTick.put(key, currentTick);

        if (state.is(Blocks.GRASS_BLOCK) && stepCounts.get(key) >= NIAConfig.getPathFormationSteps()) {
            level.setBlockAndUpdate(pos, Blocks.DIRT_PATH.defaultBlockState());
            stepCounts.remove(key);
        }
    }

    public static void setCurrentTick(long tick) {
        currentTick = tick;
    }

    public static long getLastWalkTick(BlockPos pos) {
        return lastWalkTick.getOrDefault(pos.asLong(), 0L);
    }

    public static long getCurrentTick() {
        return currentTick;
    }

    public static void cleanup() {
        if (currentTick < STALE_THRESHOLD) return;

        long cutoff = currentTick - STALE_THRESHOLD;
        stepCounts.long2IntEntrySet().removeIf(entry -> lastWalkTick.getOrDefault(entry.getLongKey(), 0L) < cutoff);
        lastWalkTick.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < cutoff);
    }
}
