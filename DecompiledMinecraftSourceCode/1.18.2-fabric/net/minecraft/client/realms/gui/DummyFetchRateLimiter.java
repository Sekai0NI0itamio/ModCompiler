/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.realms.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.realms.gui.FetchRateLimiter;

/**
 * A fetch rate limiter that does nothing.
 */
@Environment(value=EnvType.CLIENT)
public class DummyFetchRateLimiter
implements FetchRateLimiter {
    @Override
    public void onRun() {
    }

    @Override
    public long getRemainingPeriod() {
        return 0L;
    }
}

