package com.itamio.nature_is_alive;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class SkeletonDeathTracker {

    private static final Long2LongOpenHashMap deathLocations = new Long2LongOpenHashMap();
    private static final long DEATH_TTL = 24000;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton)) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;

        BlockPos pos = event.getEntity().blockPosition();
        deathLocations.put(pos.asLong(), event.getEntity().level().getGameTime());
    }

    public static List<BlockPos> getDeathLocationsNear(ServerLevel level, BlockPos center, int radius) {
        long now = level.getGameTime();
        List<BlockPos> result = new ArrayList<>();

        deathLocations.long2LongEntrySet().forEach(entry -> {
            long age = now - entry.getLongValue();
            if (age > DEATH_TTL) return;
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (pos.distManhattan(center) <= radius) {
                result.add(pos);
            }
        });

        return result;
    }

    public static void cleanup(long currentTick) {
        long cutoff = currentTick - DEATH_TTL;
        deathLocations.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < cutoff);
    }
}
