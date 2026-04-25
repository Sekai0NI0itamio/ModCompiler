/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity.ai.brain.task;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;

public class WakeUpTask
extends Task<LivingEntity> {
    public WakeUpTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean shouldRun(ServerWorld world, LivingEntity entity) {
        return !entity.getBrain().hasActivity(Activity.REST) && entity.isSleeping();
    }

    @Override
    protected void run(ServerWorld world, LivingEntity entity, long time) {
        entity.wakeUp();
    }
}

