/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.network.listener;

import net.minecraft.network.listener.PacketListener;

/**
 * The base interface for serverbound packet listeners.
 * 
 * @implNote Serverbound packet listeners log any uncaught exceptions
 * without crashing.
 */
public interface ServerPacketListener
extends PacketListener {
    @Override
    default public boolean shouldCrashOnException() {
        return false;
    }
}

