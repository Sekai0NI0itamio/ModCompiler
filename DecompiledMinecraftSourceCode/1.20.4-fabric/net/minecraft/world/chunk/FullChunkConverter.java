/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.world.chunk;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.chunk.Chunk;

@FunctionalInterface
public interface FullChunkConverter {
    public CompletableFuture<Chunk> apply(Chunk var1);
}

