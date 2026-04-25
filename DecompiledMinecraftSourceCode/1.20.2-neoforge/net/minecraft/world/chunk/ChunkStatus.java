/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.world.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.Finishable;
import net.minecraft.util.profiling.jfr.FlightProfiler;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerating;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.ChunkType;
import net.minecraft.world.chunk.FullChunkConverter;
import net.minecraft.world.chunk.ProtoChunk;
import org.jetbrains.annotations.Nullable;

public class ChunkStatus {
    public static final int field_35470 = 8;
    private static final EnumSet<Heightmap.Type> PRE_CARVER_HEIGHTMAPS = EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG);
    public static final EnumSet<Heightmap.Type> POST_CARVER_HEIGHTMAPS = EnumSet.of(Heightmap.Type.OCEAN_FLOOR, Heightmap.Type.WORLD_SURFACE, Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES);
    public static final ChunkStatus EMPTY = ChunkStatus.register("empty", null, -1, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::noop, ChunkGenerating::noop);
    public static final ChunkStatus STRUCTURE_STARTS = ChunkStatus.register("structure_starts", EMPTY, 0, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::generateStructures, ChunkGenerating::loadStructures);
    public static final ChunkStatus STRUCTURE_REFERENCES = ChunkStatus.register("structure_references", STRUCTURE_STARTS, 8, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::generateStructureReferences, ChunkGenerating::noop);
    public static final ChunkStatus BIOMES = ChunkStatus.register("biomes", STRUCTURE_REFERENCES, 8, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::populateBiomes, ChunkGenerating::noop);
    public static final ChunkStatus NOISE = ChunkStatus.register("noise", BIOMES, 8, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::populateNoise, ChunkGenerating::noop);
    public static final ChunkStatus SURFACE = ChunkStatus.register("surface", NOISE, 8, false, PRE_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::buildSurface, ChunkGenerating::noop);
    public static final ChunkStatus CARVERS = ChunkStatus.register("carvers", SURFACE, 8, false, POST_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::carve, ChunkGenerating::noop);
    public static final ChunkStatus FEATURES = ChunkStatus.register("features", CARVERS, 8, false, POST_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::generateFeatures, ChunkGenerating::noop);
    public static final ChunkStatus INITIALIZE_LIGHT = ChunkStatus.register("initialize_light", FEATURES, 0, false, POST_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::initializeLight, ChunkGenerating::initializeLight);
    public static final ChunkStatus LIGHT = ChunkStatus.register("light", INITIALIZE_LIGHT, 1, true, POST_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::light, ChunkGenerating::light);
    public static final ChunkStatus SPAWN = ChunkStatus.register("spawn", LIGHT, 1, false, POST_CARVER_HEIGHTMAPS, ChunkType.PROTOCHUNK, ChunkGenerating::generateEntities, ChunkGenerating::noop);
    public static final ChunkStatus FULL = ChunkStatus.register("full", SPAWN, 0, false, POST_CARVER_HEIGHTMAPS, ChunkType.LEVELCHUNK, ChunkGenerating::convertToFull, ChunkGenerating::convertToFull);
    private static final List<ChunkStatus> DISTANCE_TO_STATUS = ImmutableList.of(FULL, INITIALIZE_LIGHT, CARVERS, BIOMES, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, STRUCTURE_STARTS, new ChunkStatus[0]);
    private static final IntList STATUS_TO_DISTANCE = Util.make(new IntArrayList(ChunkStatus.createOrderedList().size()), statusToDistance -> {
        int i = 0;
        for (int j = ChunkStatus.createOrderedList().size() - 1; j >= 0; --j) {
            while (i + 1 < DISTANCE_TO_STATUS.size() && j <= DISTANCE_TO_STATUS.get(i + 1).getIndex()) {
                ++i;
            }
            statusToDistance.add(0, i);
        }
    });
    private final int index;
    private final ChunkStatus previous;
    private final GenerationTask generationTask;
    private final LoadTask loadTask;
    private final int taskMargin;
    private final boolean shouldAlwaysUpgrade;
    private final ChunkType chunkType;
    private final EnumSet<Heightmap.Type> heightMapTypes;

    private static ChunkStatus register(String id, @Nullable ChunkStatus previous, int taskMargin, boolean shouldAlwaysUpgrade, EnumSet<Heightmap.Type> heightMapTypes, ChunkType chunkType, GenerationTask generationTask, LoadTask loadTask) {
        return Registry.register(Registries.CHUNK_STATUS, id, new ChunkStatus(previous, taskMargin, shouldAlwaysUpgrade, heightMapTypes, chunkType, generationTask, loadTask));
    }

    public static List<ChunkStatus> createOrderedList() {
        ChunkStatus chunkStatus;
        ArrayList<ChunkStatus> list = Lists.newArrayList();
        for (chunkStatus = FULL; chunkStatus.getPrevious() != chunkStatus; chunkStatus = chunkStatus.getPrevious()) {
            list.add(chunkStatus);
        }
        list.add(chunkStatus);
        Collections.reverse(list);
        return list;
    }

    public static ChunkStatus byDistanceFromFull(int level) {
        if (level >= DISTANCE_TO_STATUS.size()) {
            return EMPTY;
        }
        if (level < 0) {
            return FULL;
        }
        return DISTANCE_TO_STATUS.get(level);
    }

    public static int getMaxDistanceFromFull() {
        return DISTANCE_TO_STATUS.size();
    }

    public static int getDistanceFromFull(ChunkStatus status) {
        return STATUS_TO_DISTANCE.getInt(status.getIndex());
    }

    ChunkStatus(@Nullable ChunkStatus previous, int taskMargin, boolean shouldAlwaysUpgrade, EnumSet<Heightmap.Type> heightMapTypes, ChunkType chunkType, GenerationTask generationTask, LoadTask loadTask) {
        this.previous = previous == null ? this : previous;
        this.generationTask = generationTask;
        this.loadTask = loadTask;
        this.taskMargin = taskMargin;
        this.shouldAlwaysUpgrade = shouldAlwaysUpgrade;
        this.chunkType = chunkType;
        this.heightMapTypes = heightMapTypes;
        this.index = previous == null ? 0 : previous.getIndex() + 1;
    }

    public int getIndex() {
        return this.index;
    }

    public ChunkStatus getPrevious() {
        return this.previous;
    }

    public CompletableFuture<Chunk> runGenerationTask(ChunkGenerationContext context, Executor executor, FullChunkConverter fullChunkConverter, List<Chunk> chunks) {
        Chunk chunk2 = chunks.get(chunks.size() / 2);
        Finishable finishable = FlightProfiler.INSTANCE.startChunkGenerationProfiling(chunk2.getPos(), context.world().getRegistryKey(), this.toString());
        return this.generationTask.doWork(context, this, executor, fullChunkConverter, chunks, chunk2).thenApply(chunk -> {
            ProtoChunk protoChunk;
            if (chunk instanceof ProtoChunk && !(protoChunk = (ProtoChunk)chunk).getStatus().isAtLeast(this)) {
                protoChunk.setStatus(this);
            }
            if (finishable != null) {
                finishable.finish();
            }
            return chunk;
        });
    }

    public CompletableFuture<Chunk> runLoadTask(ChunkGenerationContext context, FullChunkConverter fullChunkConverter, Chunk chunk) {
        return this.loadTask.doWork(context, this, fullChunkConverter, chunk);
    }

    public int getTaskMargin() {
        return this.taskMargin;
    }

    public boolean shouldAlwaysUpgrade() {
        return this.shouldAlwaysUpgrade;
    }

    public ChunkType getChunkType() {
        return this.chunkType;
    }

    public static ChunkStatus byId(String id) {
        return Registries.CHUNK_STATUS.get(Identifier.tryParse(id));
    }

    public EnumSet<Heightmap.Type> getHeightmapTypes() {
        return this.heightMapTypes;
    }

    public boolean isAtLeast(ChunkStatus chunkStatus) {
        return this.getIndex() >= chunkStatus.getIndex();
    }

    public String toString() {
        return Registries.CHUNK_STATUS.getId(this).toString();
    }

    @FunctionalInterface
    protected static interface GenerationTask {
        public CompletableFuture<Chunk> doWork(ChunkGenerationContext var1, ChunkStatus var2, Executor var3, FullChunkConverter var4, List<Chunk> var5, Chunk var6);
    }

    @FunctionalInterface
    protected static interface LoadTask {
        public CompletableFuture<Chunk> doWork(ChunkGenerationContext var1, ChunkStatus var2, FullChunkConverter var3, Chunk var4);
    }
}

