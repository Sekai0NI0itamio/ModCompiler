/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.world.chunk;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.BelowZeroRetrogen;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.FullChunkConverter;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.Blender;

public class ChunkGenerating {
    private static boolean isLightOn(Chunk chunk) {
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT) && chunk.isLightOn();
    }

    static CompletableFuture<Chunk> noop(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> noop(ChunkGenerationContext context, ChunkStatus status, FullChunkConverter fullChunkConverter, Chunk chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> generateStructures(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        if (serverWorld.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()) {
            context.generator().setStructureStarts(serverWorld.getRegistryManager(), serverWorld.getChunkManager().getStructurePlacementCalculator(), serverWorld.getStructureAccessor(), chunk, context.structureManager());
        }
        serverWorld.cacheStructures(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> loadStructures(ChunkGenerationContext context, ChunkStatus status, FullChunkConverter fullChunkConverter, Chunk chunk) {
        context.world().cacheStructures(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> generateStructureReferences(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, -1);
        context.generator().addStructureReferences(chunkRegion, serverWorld.getStructureAccessor().forRegion(chunkRegion), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> populateBiomes(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, -1);
        return context.generator().populateBiomes(executor, serverWorld.getChunkManager().getNoiseConfig(), Blender.getBlender(chunkRegion), serverWorld.getStructureAccessor().forRegion(chunkRegion), chunk);
    }

    static CompletableFuture<Chunk> populateNoise(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, 0);
        return context.generator().populateNoise(executor, Blender.getBlender(chunkRegion), serverWorld.getChunkManager().getNoiseConfig(), serverWorld.getStructureAccessor().forRegion(chunkRegion), chunk).thenApply(populated -> {
            ProtoChunk protoChunk;
            BelowZeroRetrogen belowZeroRetrogen;
            if (populated instanceof ProtoChunk && (belowZeroRetrogen = (protoChunk = (ProtoChunk)populated).getBelowZeroRetrogen()) != null) {
                BelowZeroRetrogen.replaceOldBedrock(protoChunk);
                if (belowZeroRetrogen.hasMissingBedrock()) {
                    belowZeroRetrogen.fillColumnsWithAirIfMissingBedrock(protoChunk);
                }
            }
            return populated;
        });
    }

    static CompletableFuture<Chunk> buildSurface(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, 0);
        context.generator().buildSurface(chunkRegion, serverWorld.getStructureAccessor().forRegion(chunkRegion), serverWorld.getChunkManager().getNoiseConfig(), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> carve(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, 0);
        if (chunk instanceof ProtoChunk) {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            Blender.createCarvingMasks(chunkRegion, protoChunk);
        }
        context.generator().carve(chunkRegion, serverWorld.getSeed(), serverWorld.getChunkManager().getNoiseConfig(), serverWorld.getBiomeAccess(), serverWorld.getStructureAccessor().forRegion(chunkRegion), chunk, GenerationStep.Carver.AIR);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> generateFeatures(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        ServerWorld serverWorld = context.world();
        Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR, Heightmap.Type.WORLD_SURFACE));
        ChunkRegion chunkRegion = new ChunkRegion(serverWorld, chunks, status, 1);
        context.generator().generateFeatures(chunkRegion, chunk, serverWorld.getStructureAccessor().forRegion(chunkRegion));
        Blender.tickLeavesAndFluids(chunkRegion, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> initializeLight(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        return ChunkGenerating.initializeLight(context.lightingProvider(), chunk);
    }

    static CompletableFuture<Chunk> initializeLight(ChunkGenerationContext context, ChunkStatus status, FullChunkConverter fullChunkConverter, Chunk chunk) {
        return ChunkGenerating.initializeLight(context.lightingProvider(), chunk);
    }

    private static CompletableFuture<Chunk> initializeLight(ServerLightingProvider lightingProvider, Chunk chunk) {
        chunk.refreshSurfaceY();
        ((ProtoChunk)chunk).setLightingProvider(lightingProvider);
        boolean bl = ChunkGenerating.isLightOn(chunk);
        return lightingProvider.initializeLight(chunk, bl);
    }

    static CompletableFuture<Chunk> light(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        return ChunkGenerating.light(context.lightingProvider(), chunk);
    }

    static CompletableFuture<Chunk> light(ChunkGenerationContext context, ChunkStatus status, FullChunkConverter fullChunkConverter, Chunk chunk) {
        return ChunkGenerating.light(context.lightingProvider(), chunk);
    }

    private static CompletableFuture<Chunk> light(ServerLightingProvider lightingProvider, Chunk chunk) {
        boolean bl = ChunkGenerating.isLightOn(chunk);
        return lightingProvider.light(chunk, bl);
    }

    static CompletableFuture<Chunk> generateEntities(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        if (!chunk.hasBelowZeroRetrogen()) {
            context.generator().populateEntities(new ChunkRegion(context.world(), chunks, status, -1));
        }
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<Chunk> convertToFull(ChunkGenerationContext context, ChunkStatus status, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks, Chunk chunk) {
        return fullChunkConverter.apply(chunk);
    }

    static CompletableFuture<Chunk> convertToFull(ChunkGenerationContext context, ChunkStatus status, FullChunkConverter fullChunkConverter, Chunk chunk) {
        return fullChunkConverter.apply(chunk);
    }
}

