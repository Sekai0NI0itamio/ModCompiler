package com.itamio.nature_is_alive;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class StepEventHandler {

    private int tickCounter = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player.level() instanceof ServerLevel level)) return;
        if (!event.player.onGround()) return;

        if (event.player.horizontalCollision || event.player.getDeltaMovement().horizontalDistanceSqr() < 0.001) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        BlockPos pos = event.player.blockPosition();
        StepTracker.onPlayerStep(level, pos);
    }
}
