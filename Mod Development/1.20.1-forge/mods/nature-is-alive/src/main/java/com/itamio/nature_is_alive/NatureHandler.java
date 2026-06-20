package com.itamio.nature_is_alive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class NatureHandler {

    private static final Logger LOG = LoggerFactory.getLogger("NatureIsAlive");
    private static final int BLOCKS_PER_CHUNK = 3;
    private static final double TPS_PAUSE = 18.0;
    private static final double TPS_HIGH = 19.5;
    private static final double EMA_ALPHA = 0.05;
    private static final Map<ResourceKey<Biome>, Boolean> biomeGrassCache = new HashMap<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final TagKey<Block> VINE_SURFACE_BLOCKS = TagKey.create(Registries.BLOCK,
            new net.minecraft.resources.ResourceLocation("nature_is_alive", "vine_surface_blocks"));

    private static final Block[] VANILLA_FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID,
            Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
            Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
    };

    private static final Map<Block, Block> WALL_TO_STAIR = Map.ofEntries(
            Map.entry(Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_STAIRS),
            Map.entry(Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_STAIRS),
            Map.entry(Blocks.BRICK_WALL, Blocks.BRICK_STAIRS),
            Map.entry(Blocks.ANDESITE_WALL, Blocks.ANDESITE_STAIRS),
            Map.entry(Blocks.GRANITE_WALL, Blocks.GRANITE_STAIRS),
            Map.entry(Blocks.DIORITE_WALL, Blocks.DIORITE_STAIRS),
            Map.entry(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_STAIRS),
            Map.entry(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.COBBLESTONE_STAIRS),
            Map.entry(Blocks.RED_NETHER_BRICK_WALL, Blocks.RED_NETHER_BRICK_STAIRS),
            Map.entry(Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_STAIRS),
            Map.entry(Blocks.RED_SANDSTONE_WALL, Blocks.RED_SANDSTONE_STAIRS),
            Map.entry(Blocks.PRISMARINE_WALL, Blocks.PRISMARINE_STAIRS),
            Map.entry(Blocks.END_STONE_BRICK_WALL, Blocks.END_STONE_BRICK_STAIRS),
            Map.entry(Blocks.NETHER_BRICK_WALL, Blocks.NETHER_BRICK_STAIRS),
            Map.entry(Blocks.DEEPSLATE_BRICK_WALL, Blocks.DEEPSLATE_BRICK_STAIRS),
            Map.entry(Blocks.DEEPSLATE_TILE_WALL, Blocks.DEEPSLATE_TILE_STAIRS),
            Map.entry(Blocks.POLISHED_DEEPSLATE_WALL, Blocks.POLISHED_DEEPSLATE_STAIRS),
            Map.entry(Blocks.BLACKSTONE_WALL, Blocks.BLACKSTONE_STAIRS),
            Map.entry(Blocks.POLISHED_BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_STAIRS),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS)
    );

    private static final Map<Block, Block> STAIR_TO_SLAB = Map.ofEntries(
            Map.entry(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB),
            Map.entry(Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB),
            Map.entry(Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB),
            Map.entry(Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB),
            Map.entry(Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB),
            Map.entry(Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB),
            Map.entry(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB),
            Map.entry(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB),
            Map.entry(Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB),
            Map.entry(Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB),
            Map.entry(Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB),
            Map.entry(Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB),
            Map.entry(Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB),
            Map.entry(Blocks.END_STONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_SLAB),
            Map.entry(Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_BRICK_SLAB),
            Map.entry(Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB),
            Map.entry(Blocks.PURPUR_STAIRS, Blocks.PURPUR_SLAB),
            Map.entry(Blocks.OAK_STAIRS, Blocks.OAK_SLAB),
            Map.entry(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB),
            Map.entry(Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB),
            Map.entry(Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB),
            Map.entry(Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB),
            Map.entry(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB),
            Map.entry(Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB),
            Map.entry(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB),
            Map.entry(Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB),
            Map.entry(Blocks.STONE_STAIRS, Blocks.STONE_SLAB),
            Map.entry(Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB),
            Map.entry(Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB),
            Map.entry(Blocks.CUT_COPPER_STAIRS, Blocks.CUT_COPPER_SLAB),
            Map.entry(Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_SLAB),
            Map.entry(Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_SLAB),
            Map.entry(Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_SLAB)
    );

    private static final Map<Block, Block> BLOCK_TO_STAIR = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS),
            Map.entry(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS),
            Map.entry(Blocks.BRICKS, Blocks.BRICK_STAIRS),
            Map.entry(Blocks.ANDESITE, Blocks.ANDESITE_STAIRS),
            Map.entry(Blocks.GRANITE, Blocks.GRANITE_STAIRS),
            Map.entry(Blocks.DIORITE, Blocks.DIORITE_STAIRS),
            Map.entry(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS),
            Map.entry(Blocks.BLACKSTONE, Blocks.BLACKSTONE_STAIRS)
    );

    private static final Map<Block, Block> BLOCK_TO_SLAB = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB),
            Map.entry(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB),
            Map.entry(Blocks.BRICKS, Blocks.BRICK_SLAB),
            Map.entry(Blocks.ANDESITE, Blocks.ANDESITE_SLAB),
            Map.entry(Blocks.GRANITE, Blocks.GRANITE_SLAB),
            Map.entry(Blocks.DIORITE, Blocks.DIORITE_SLAB),
            Map.entry(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB),
            Map.entry(Blocks.BLACKSTONE, Blocks.BLACKSTONE_SLAB)
    );

    private List<Block> cachedFlowerBlocks = null;

    private int tickCounter = 0;
    private int nextInterval = NIAConfig.getTickIntervalMin();
    private final LinkedList<ChunkJob> jobQueue = new LinkedList<>();
    private List<ChunkJob> cachedFullQueue = null;
    private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
    private int writesThisTick = 0;

    private long lastTickNanos = 0;
    private double avgTickTimeMs = 25.0;

    private final Map<UUID, Long> lastPlayerChunkKeys = new HashMap<>();
    private final Set<Long> ineligibleChunks = new HashSet<>();
    private int eligibilityClearCounter = 0;
    private static final int ELIGIBILITY_CLEAR_INTERVAL = 10;
    private int trackerCleanupCounter = 0;
    private static final int TRACKER_CLEANUP_INTERVAL = 600;

    private static class ChunkJob {
        final ServerLevel level;
        final LevelChunk chunk;
        final BlockPos playerPos;
        ChunkJob(ServerLevel level, LevelChunk chunk, BlockPos playerPos) {
            this.level = level; this.chunk = chunk; this.playerPos = playerPos;
        }
        double distanceSq() {
            double dx = chunk.getPos().getMinBlockX() + 8 - playerPos.getX();
            double dz = chunk.getPos().getMinBlockZ() + 8 - playerPos.getZ();
            return dx * dx + dz * dz;
        }
    }

    private List<Block> getFlowerBlocks() {
        if (cachedFlowerBlocks == null) {
            var registry = ForgeRegistries.BLOCKS;
            List<Block> tagged = new ArrayList<>();
            for (var entry : registry.getEntries()) {
                var block = entry.getValue();
                var defaultState = block.defaultBlockState();
                if (defaultState.is(BlockTags.FLOWERS) && !(block instanceof TallFlowerBlock)) {
                    tagged.add(block);
                }
            }
            cachedFlowerBlocks = tagged.isEmpty() ? Arrays.asList(VANILLA_FLOWERS) : tagged;
        }
        return cachedFlowerBlocks;
    }

    private Block getSaplingForBiome(Holder<Biome> biome, RandomSource random) {
        if (biome.is(BiomeTags.IS_JUNGLE)) return Blocks.JUNGLE_SAPLING;
        if (biome.is(BiomeTags.IS_TAIGA)) return Blocks.SPRUCE_SAPLING;
        if (biomeIsSavanna(biome)) return Blocks.ACACIA_SAPLING;
        if (biomeIsDarkForest(biome)) return Blocks.DARK_OAK_SAPLING;
        if (biome.is(BiomeTags.IS_FOREST)) return random.nextBoolean() ? Blocks.OAK_SAPLING : Blocks.BIRCH_SAPLING;
        return random.nextBoolean() ? Blocks.OAK_SAPLING : Blocks.BIRCH_SAPLING;
    }

    private boolean biomeIsSavanna(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_SAVANNA)) return true;
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) return false;
        return key.location().getPath().contains("savanna");
    }

    private boolean biomeIsDarkForest(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) return false;
        String path = key.location().getPath();
        return path.contains("dark_forest") || path.contains("dark_oak");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.nanoTime();
        if (lastTickNanos > 0) {
            double tickMs = (now - lastTickNanos) / 1_000_000.0;
            avgTickTimeMs = avgTickTimeMs * (1.0 - EMA_ALPHA) + tickMs * EMA_ALPHA;
        }
        lastTickNanos = now;

        StepTracker.setCurrentTick(event.getServer().overworld().getGameTime());

        trackerCleanupCounter++;
        if (trackerCleanupCounter >= TRACKER_CLEANUP_INTERVAL) {
            StepTracker.cleanup();
            SkeletonDeathTracker.cleanup(StepTracker.getCurrentTick());
            trackerCleanupCounter = 0;
        }

        if (NIAConfig.checkAndClearDirty()) {
            tickCounter = 0;
            cachedFullQueue = null;
            ineligibleChunks.clear();
            int min = NIAConfig.getTickIntervalMin();
            int max = NIAConfig.getTickIntervalMax();
            nextInterval = min + event.getServer().overworld().random.nextInt(Math.max(1, max - min + 1));
        }

        double tps = avgTickTimeMs > 0 ? Math.min(20.0, 1000.0 / avgTickTimeMs) : 20.0;
        if (tps < TPS_PAUSE) return;

        int blockBudget = NIAConfig.getMaxBlocksPerTick();
        int writeBudget = NIAConfig.getMaxWritesPerTick();
        if (tps >= TPS_HIGH) {
            blockBudget = (int) (blockBudget * 2.0);
            writeBudget = (int) (writeBudget * 2.0);
        }

        if (jobQueue.isEmpty()) {
            tickCounter++;
            if (tickCounter < nextInterval) return;

            tickCounter = 0;
            int min = NIAConfig.getTickIntervalMin();
            int max = NIAConfig.getTickIntervalMax();
            nextInterval = min + event.getServer().overworld().random.nextInt(Math.max(1, max - min + 1));

            if (cachedFullQueue != null && !hasPlayerChunkChanged(event.getServer())) {
                jobQueue.addAll(cachedFullQueue);
            } else {
                buildJobQueue(event.getServer().getAllLevels());
                cachedFullQueue = new ArrayList<>(jobQueue);
            }
            if (jobQueue.isEmpty()) return;
        }

        writesThisTick = 0;
        int blocksProcessed = 0;

        while (!jobQueue.isEmpty() && blocksProcessed < blockBudget && writesThisTick < writeBudget) {
            ChunkJob job = jobQueue.removeFirst();
            processChunk(job.level, job.chunk, job.level.random);
            blocksProcessed += BLOCKS_PER_CHUNK;
        }
    }

    private boolean hasPlayerChunkChanged(MinecraftServer server) {
        boolean changed = false;
        Set<UUID> online = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).collect(Collectors.toSet());
        for (var player : server.getPlayerList().getPlayers()) {
            ChunkPos cp = player.chunkPosition();
            long key = ChunkPos.asLong(cp.x, cp.z);
            Long last = lastPlayerChunkKeys.put(player.getUUID(), key);
            if (last == null || last != key) changed = true;
        }
        lastPlayerChunkKeys.keySet().retainAll(online);
        if (!online.equals(lastPlayerChunkKeys.keySet())) changed = true;
        return changed;
    }

    private void buildJobQueue(Iterable<ServerLevel> levels) {
        jobQueue.clear();

        eligibilityClearCounter++;
        if (eligibilityClearCounter >= ELIGIBILITY_CLEAR_INTERVAL) {
            ineligibleChunks.clear();
            eligibilityClearCounter = 0;
        }

        int radius = NIAConfig.getTickRadius();
        for (ServerLevel level : levels) {
            if (level.players().isEmpty()) continue;
            List<ChunkJob> levelJobs = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (var player : level.players()) {
                BlockPos playerPos = player.blockPosition();
                LevelChunk center = level.getChunkAt(playerPos);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = center.getPos().x + dx;
                        int cz = center.getPos().z + dz;
                        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                        if (seen.contains(key) || !level.hasChunk(cx, cz)) continue;
                        if (ineligibleChunks.contains(key)) continue;
                        seen.add(key);
                        levelJobs.add(new ChunkJob(level, level.getChunk(cx, cz), playerPos));
                    }
                }
            }
            levelJobs.sort(Comparator.comparingDouble(ChunkJob::distanceSq));
            jobQueue.addAll(levelJobs);
        }
    }

    private void processChunk(ServerLevel level, LevelChunk chunk, RandomSource random) {
        int eligiblePositions = 0;
        for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
            int cx = chunk.getPos().getMinBlockX() + random.nextInt(16);
            int cz = chunk.getPos().getMinBlockZ() + random.nextInt(16);
            int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, cx & 15, cz & 15);
            if (surfaceY < level.getMinBuildHeight()) continue;

            mutable.set(cx, surfaceY, cz);
            BlockState state = level.getBlockState(mutable);
            Holder<Biome> biome = level.getBiome(mutable);

            mutable.setY(surfaceY + 1);
            boolean skyVisible = level.canSeeSky(mutable);
            BlockState aboveState = level.getBlockState(mutable);

            boolean noGrassBiome = biomeIsNoGrass(biome);
            BlockPos pos = new BlockPos(cx, surfaceY, cz);
            Block block = state.getBlock();

            boolean eligible = false;

            if (!noGrassBiome) {
                if (state.is(Blocks.GRASS_BLOCK)) {
                    eligible = true;
                    processGrassSpawn(level, pos, state, random, skyVisible, aboveState);
                    processFlowerSpawn(level, pos, state, random, skyVisible, aboveState);
                    processSaplingSpawn(level, pos, state, random, skyVisible, aboveState, biome);
                    processBoneMeal(level, pos, state, random, aboveState);
                    processSnowAccumulation(level, pos, state, random, biome, skyVisible, aboveState);
                } else if (state.is(Blocks.GRASS)) {
                    eligible = true;
                    processGrassGrowth(level, pos, state, random, aboveState);
                } else if (state.is(Blocks.DIRT)) {
                    eligible = true;
                    processGrassSpread(level, pos, state, random, skyVisible, aboveState);
                    processMudFormation(level, pos, state, random);
                    processSnowAccumulation(level, pos, state, random, biome, skyVisible, aboveState);
                } else if (state.is(Blocks.COBBLESTONE)) {
                    eligible = true;
                    processMossConversion(level, pos, state, random);
                    processDecay(level, pos, state, random);
                    processSnowAccumulation(level, pos, state, random, biome, skyVisible, aboveState);
                } else if (state.is(Blocks.STONE)) {
                    eligible = true;
                    processMaterialDegradation(level, pos, state, random);
                    processSnowAccumulation(level, pos, state, random, biome, skyVisible, aboveState);
                } else if (block instanceof SaplingBlock) {
                    eligible = true;
                    processSaplingGrowth(level, pos, state, random);
                } else if (isVineSurface(state)) {
                    eligible = true;
                    processVineGrowth(level, pos, state, random);
                } else if (state.is(Blocks.DIRT_PATH)) {
                    eligible = true;
                    processPathDegradation(level, pos, state, random);
                } else if (state.is(Blocks.SNOW_BLOCK)) {
                    eligible = true;
                    processSnowAccumulation(level, pos, state, random, biome, skyVisible, aboveState);
                } else if (state.is(Blocks.GRAVEL) || state.is(Blocks.SAND)) {
                    eligible = true;
                }
            }

            if (state.isAir()) {
                processCobwebSpawn(level, pos, state, random);
            } else if (state.is(Blocks.ICE)) {
                eligible = true;
                processIceCrack(level, pos, state, random, biome);
            } else if (block == Blocks.COPPER_BLOCK || block == Blocks.EXPOSED_COPPER || block == Blocks.WEATHERED_COPPER) {
                eligible = true;
                processCopperOxidation(level, pos, state, random, skyVisible);
            } else if (state.is(BlockTags.WALLS)) {
                eligible = true;
                processDecay(level, pos, state, random);
            } else if (block instanceof StairBlock) {
                eligible = true;
                processDecay(level, pos, state, random);
            } else if (block instanceof SlabBlock) {
                eligible = true;
                processDecay(level, pos, state, random);
            } else if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.STONE_BRICKS)
                    || state.is(Blocks.CRACKED_STONE_BRICKS)
                    || state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.POLISHED_GRANITE)
                    || state.is(Blocks.POLISHED_DIORITE) || state.is(Blocks.POLISHED_DEEPSLATE)) {
                eligible = true;
                processMaterialDegradation(level, pos, state, random);
            }

            processWaterErosion(level, pos, state, random);

            if (eligible || isEligibleBlock(state)) eligiblePositions++;
        }

        if (eligiblePositions == 0) {
            long chunkKey = (long) chunk.getPos().x << 32 | (chunk.getPos().z & 0xFFFFFFFFL);
            ineligibleChunks.add(chunkKey);
        }
    }

    private boolean isVineSurface(BlockState state) {
        if (state.is(VINE_SURFACE_BLOCKS)) return true;
        if (state.is(BlockTags.WALLS)) return true;
        return state.is(Blocks.STONE_BRICKS) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.STONE) || state.is(Blocks.BRICKS);
    }

    private boolean isEligibleBlock(BlockState state) {
        Block block = state.getBlock();
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.BRICKS) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.ICE)
                || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)
                || block instanceof SaplingBlock || block instanceof StairBlock
                || block instanceof SlabBlock || state.is(BlockTags.WALLS)
                || block == Blocks.COPPER_BLOCK || block == Blocks.EXPOSED_COPPER
                || block == Blocks.WEATHERED_COPPER;
    }

    private boolean trySetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        writesThisTick++;
        return true;
    }

    private void tryRemoveBlock(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        writesThisTick++;
    }

    private void processGrassSpawn(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                    boolean skyVisible, BlockState aboveState) {
        if (!skyVisible) return;
        if (shouldSkip(random, NIAConfig.getGrassSpawnChance())) return;
        if (!aboveState.isAir()) return;
        trySetBlock(level, pos.above(), Blocks.GRASS.defaultBlockState());
    }

    private void processGrassGrowth(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                     BlockState aboveState) {
        if (shouldSkip(random, NIAConfig.getGrassGrowthChance())) return;
        if (!aboveState.isAir()) return;
        BlockPos above = pos.above();
        trySetBlock(level, pos, Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        trySetBlock(level, above, Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    private void processFlowerSpawn(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                     boolean skyVisible, BlockState aboveState) {
        if (!skyVisible) return;
        if (shouldSkip(random, NIAConfig.getFlowerSpawnChance())) return;
        if (!aboveState.isAir()) return;
        List<Block> flowers = getFlowerBlocks();
        trySetBlock(level, pos.above(), flowers.get(random.nextInt(flowers.size())).defaultBlockState());
    }

    private void processGrassSpread(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                     boolean skyVisible, BlockState aboveState) {
        if (!skyVisible) return;
        if (shouldSkip(random, NIAConfig.getGrassSpreadChance())) return;
        if (!aboveState.isAir()) return;
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                mutable.set(px + dx, py, pz + dz);
                if (level.getBlockState(mutable).is(Blocks.GRASS_BLOCK)) {
                    trySetBlock(level, pos, Blocks.GRASS_BLOCK.defaultBlockState());
                    return;
                }
            }
        }
    }

    private void processMossConversion(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(Blocks.COBBLESTONE)) return;
        if (shouldSkip(random, NIAConfig.getMossConversionChance())) return;
        int waterDist = findWaterDistance(level, pos, NIAConfig.getMossConversionRadius());
        if (waterDist < 0) return;
        double chanceByDist = NIAConfig.getMossConversionChance() / (waterDist * waterDist);
        if (random.nextDouble() * 100.0 >= Math.min(chanceByDist * NIAConfig.getGlobalSpeedMultiplier(), 100.0)) return;
        trySetBlock(level, pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    }

    private void processSaplingSpawn(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                      boolean skyVisible, BlockState aboveState, Holder<Biome> biome) {
        if (!skyVisible) return;
        if (shouldSkip(random, NIAConfig.getSaplingSpawnChance())) return;
        if (!aboveState.isAir()) return;
        Block sapling = getSaplingForBiome(biome, random);
        trySetBlock(level, pos.above(), sapling.defaultBlockState());
    }

    private void processSaplingGrowth(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getSaplingGrowthChance())) return;
        if (state.hasProperty(BlockStateProperties.STAGE)) {
            trySetBlock(level, pos, state.setValue(BlockStateProperties.STAGE, 1));
        }
        ((SaplingBlock) state.getBlock()).advanceTree(level, pos, state, random);
    }

    private void processVineGrowth(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getVineGrowthChance())) return;

        if (!findNearbyGreen(level, pos, 2)) return;

        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (Direction dir : DIRECTIONS) {
            if (dir == Direction.DOWN) continue;
            mutable.set(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
            if (!level.getBlockState(mutable).isAir()) continue;
            if (dir == Direction.UP) continue;
            BlockState vineState = Blocks.VINE.defaultBlockState();
            if (dir.getAxis().isHorizontal()) {
                vineState = vineState.setValue(getVineProperty(dir), true);
            }
            BlockPos adjPos = mutable.immutable();
            if (Blocks.VINE.canSurvive(vineState, level, adjPos)) {
                trySetBlock(level, adjPos, vineState);
                return;
            }
        }
    }

    private void processCobwebSpawn(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getCobwebSpawnChance())) return;

        int light = level.getMaxLocalRawBrightness(pos);
        if (light > 7) return;

        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        boolean hasAdjacentSolid = false;
        for (Direction dir : DIRECTIONS) {
            mutable.set(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
            if (level.getBlockState(mutable).isSolidRender(level, mutable)) {
                hasAdjacentSolid = true;
                break;
            }
        }
        if (!hasAdjacentSolid) return;

        trySetBlock(level, pos, Blocks.COBWEB.defaultBlockState());
    }

    private void processWaterErosion(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(Blocks.DIRT_PATH) && !state.is(Blocks.DIRT) && !state.is(Blocks.GRAVEL) && !state.is(Blocks.SAND)) return;
        if (shouldSkip(random, NIAConfig.getWaterErosionChance())) return;

        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        boolean nearWater = false;
        for (Direction dir : DIRECTIONS) {
            mutable.set(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
            BlockState adj = level.getBlockState(mutable);
            if (adj.is(Blocks.WATER) || (adj.getBlock() instanceof LiquidBlock)) {
                nearWater = true;
                break;
            }
        }
        if (!nearWater) return;

        if (state.is(Blocks.DIRT_PATH)) {
            trySetBlock(level, pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if (state.is(Blocks.DIRT)) {
            trySetBlock(level, pos, Blocks.GRAVEL.defaultBlockState());
            return;
        }
        if (state.is(Blocks.GRAVEL)) {
            trySetBlock(level, pos, Blocks.SAND.defaultBlockState());
            return;
        }
        if (state.is(Blocks.SAND)) {
            mutable.set(px, py + 1, pz);
            BlockState aboveState = level.getBlockState(mutable);
            if (aboveState.isAir() || aboveState.is(Blocks.WATER)) {
                tryRemoveBlock(level, pos);
            }
        }
    }

    private void processSnowAccumulation(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                          Holder<Biome> biome, boolean skyVisible, BlockState aboveState) {
        if (shouldSkip(random, NIAConfig.getSnowAccumulationChance())) return;

        if (!biome.is(BiomeTags.IS_TAIGA) && !biomeIsSnowy(biome)) return;
        if (!skyVisible) return;

        BlockPos above = pos.above();
        if (aboveState.isAir()) {
            trySetBlock(level, above, Blocks.SNOW.defaultBlockState());
        } else if (aboveState.is(Blocks.SNOW) && aboveState.hasProperty(BlockStateProperties.LAYERS)) {
            int layers = aboveState.getValue(BlockStateProperties.LAYERS);
            if (layers < 8) {
                trySetBlock(level, above, aboveState.setValue(BlockStateProperties.LAYERS, layers + 1));
            }
        }
    }

    private void processMudFormation(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getMudFormationChance())) return;

        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (Direction dir : DIRECTIONS) {
            mutable.set(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
            if (level.getBlockState(mutable).is(Blocks.WATER)) {
                trySetBlock(level, pos, Blocks.MUD.defaultBlockState());
                return;
            }
        }
    }

    private void processPathDegradation(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getPathDegradationChance())) return;

        long lastWalk = StepTracker.getLastWalkTick(pos);
        long now = StepTracker.getCurrentTick();
        if (now - lastWalk < 24000) return;

        trySetBlock(level, pos, Blocks.DIRT.defaultBlockState());
    }

    private void processBoneMeal(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                  BlockState aboveState) {
        if (shouldSkip(random, NIAConfig.getBoneMealChance())) return;

        List<BlockPos> deaths = SkeletonDeathTracker.getDeathLocationsNear(level, pos, 5);
        if (deaths.isEmpty()) return;

        if (!aboveState.isAir()) return;

        BlockPos above = pos.above();
        if (random.nextBoolean()) {
            trySetBlock(level, above, Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            mutable.set(pos.getX(), pos.getY() + 2, pos.getZ());
            if (level.getBlockState(mutable).isAir()) {
                trySetBlock(level, mutable.immutable(), Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            }
        } else {
            List<Block> flowers = getFlowerBlocks();
            trySetBlock(level, above, flowers.get(random.nextInt(flowers.size())).defaultBlockState());
        }
    }

    private void processIceCrack(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                  Holder<Biome> biome) {
        if (shouldSkip(random, NIAConfig.getIceCrackChance())) return;

        float temp = biome.value().getBaseTemperature();
        if (temp < 0.15f) return;

        trySetBlock(level, pos, Blocks.WATER.defaultBlockState());
    }

    private void processCopperOxidation(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                         boolean skyVisible) {
        if (shouldSkip(random, NIAConfig.getCopperOxidationChance())) return;

        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        boolean nearWater = false;
        for (Direction dir : DIRECTIONS) {
            mutable.set(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
            BlockState adj = level.getBlockState(mutable);
            if (adj.is(Blocks.WATER) || adj.is(Blocks.WATER_CAULDRON)) {
                nearWater = true;
                break;
            }
        }

        boolean exposedToRain = skyVisible && level.isRainingAt(pos.above());
        if (!nearWater && !exposedToRain) return;

        if (state.is(Blocks.COPPER_BLOCK)) {
            trySetBlock(level, pos, Blocks.EXPOSED_COPPER.defaultBlockState());
        } else if (state.is(Blocks.EXPOSED_COPPER)) {
            trySetBlock(level, pos, Blocks.WEATHERED_COPPER.defaultBlockState());
        } else if (state.is(Blocks.WEATHERED_COPPER)) {
            trySetBlock(level, pos, Blocks.OXIDIZED_COPPER.defaultBlockState());
        }
    }

    private void processDecay(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (shouldSkip(random, NIAConfig.getDecayChance())) return;

        if (state.is(Blocks.COBBLESTONE)) {
            Block stair = BLOCK_TO_STAIR.get(state.getBlock());
            if (stair != null) {
                trySetBlock(level, pos, stair.defaultBlockState());
                return;
            }
        }
        if (state.is(BlockTags.WALLS)) {
            Block stair = WALL_TO_STAIR.get(state.getBlock());
            if (stair != null) { trySetBlock(level, pos, stair.defaultBlockState()); return; }
        }
        if (state.getBlock() instanceof StairBlock) {
            Block slab = STAIR_TO_SLAB.get(state.getBlock());
            if (slab != null) { trySetBlock(level, pos, slab.defaultBlockState()); return; }
        }
        if (state.getBlock() instanceof SlabBlock) {
            tryRemoveBlock(level, pos);
        }
    }

    private void processMaterialDegradation(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.is(Blocks.STONE)) {
            if (shouldSkip(random, NIAConfig.getCobbleDowngradeChance())) return;
            trySetBlock(level, pos, Blocks.COBBLESTONE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.DEEPSLATE)) {
            if (shouldSkip(random, NIAConfig.getCobbleDowngradeChance())) return;
            trySetBlock(level, pos, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.STONE_BRICKS)) {
            if (shouldSkip(random, NIAConfig.getCrackedDowngradeChance())) return;
            trySetBlock(level, pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
            return;
        }
        if (state.is(Blocks.CRACKED_STONE_BRICKS)) {
            if (shouldSkip(random, NIAConfig.getCobbleDowngradeChance())) return;
            trySetBlock(level, pos, Blocks.COBBLESTONE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.POLISHED_GRANITE)
                || state.is(Blocks.POLISHED_DIORITE) || state.is(Blocks.POLISHED_DEEPSLATE)) {
            if (shouldSkip(random, NIAConfig.getPolishedDowngradeChance())) return;
            if (state.is(Blocks.POLISHED_ANDESITE)) trySetBlock(level, pos, Blocks.ANDESITE.defaultBlockState());
            else if (state.is(Blocks.POLISHED_GRANITE)) trySetBlock(level, pos, Blocks.GRANITE.defaultBlockState());
            else if (state.is(Blocks.POLISHED_DIORITE)) trySetBlock(level, pos, Blocks.DIORITE.defaultBlockState());
            else trySetBlock(level, pos, Blocks.DEEPSLATE.defaultBlockState());
        }
    }

    private int findWaterDistance(ServerLevel level, BlockPos pos, int maxRadius) {
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (int dist = 1; dist <= maxRadius; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dy = -dist; dy <= dist; dy++) {
                    for (int dz = -dist; dz <= dist; dz++) {
                        if (Math.abs(dx) != dist && Math.abs(dy) != dist && Math.abs(dz) != dist) continue;
                        mutable.set(px + dx, py + dy, pz + dz);
                        if (level.getBlockState(mutable).is(Blocks.WATER)) return dist;
                    }
                }
            }
        }
        return -1;
    }

    private boolean findNearbyGreen(ServerLevel level, BlockPos pos, int radius) {
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (int dist = 1; dist <= radius; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dy = -dist; dy <= dist; dy++) {
                    for (int dz = -dist; dz <= dist; dz++) {
                        if (Math.abs(dx) != dist && Math.abs(dy) != dist && Math.abs(dz) != dist) continue;
                        mutable.set(px + dx, py + dy, pz + dz);
                        BlockState nearby = level.getBlockState(mutable);
                        if (nearby.is(Blocks.GRASS_BLOCK) || nearby.is(BlockTags.LEAVES) || nearby.is(Blocks.GRASS)
                                || nearby.is(Blocks.TALL_GRASS) || nearby.is(Blocks.VINE)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private net.minecraft.world.level.block.state.properties.BooleanProperty getVineProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            case WEST -> BlockStateProperties.WEST;
            default -> BlockStateProperties.UP;
        };
    }

    private boolean shouldSkip(RandomSource random, double chancePercent) {
        double effective = chancePercent * NIAConfig.getGlobalSpeedMultiplier();
        return random.nextDouble() * 100.0 >= Math.min(effective, 100.0);
    }

    private boolean biomeIsNoGrass(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) return false;
        Boolean cached = biomeGrassCache.get(key);
        if (cached != null) return cached;

        boolean result = computeBiomeIsNoGrass(biome, key);
        biomeGrassCache.put(key, result);
        return result;
    }

    private boolean computeBiomeIsNoGrass(Holder<Biome> biome, ResourceKey<Biome> key) {
        if (biome.is(BiomeTags.IS_OCEAN)) return true;
        if (biome.is(BiomeTags.IS_RIVER)) return true;
        if (biome.is(BiomeTags.IS_BEACH)) return true;
        if (biome.is(BiomeTags.IS_NETHER)) return true;
        if (biome.is(BiomeTags.IS_END)) return true;
        if (biome.is(BiomeTags.HAS_CLOSER_WATER_FOG)) return true;
        if (biome.is(BiomeTags.IS_BADLANDS)) return true;
        if (biome.is(BiomeTags.IS_MOUNTAIN)) return true;
        if (biome.is(BiomeTags.IS_HILL)) return true;
        if (biome.is(BiomeTags.IS_FOREST)) return false;
        if (biome.is(BiomeTags.IS_JUNGLE)) return false;
        if (biome.is(BiomeTags.IS_SAVANNA)) return false;
        if (biome.is(BiomeTags.IS_TAIGA)) return false;
        String path = key.location().getPath();
        return path.contains("desert") || path.contains("ice") || path.contains("frozen")
                || path.contains("snowy") || path.contains("mushroom") || path.contains("waste")
                || path.contains("basalt") || path.contains("crimson") || path.contains("warped")
                || path.contains("soul") || path.contains("nether") || path.contains("end")
                || path.contains("ocean") || path.contains("beach") || path.contains("shore")
                || path.contains("cold") || path.contains("grove");
    }

    private boolean biomeIsSnowy(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_TAIGA)) return true;
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) return false;
        String path = key.location().getPath();
        return path.contains("snowy") || path.contains("frozen") || path.contains("ice") || path.contains("cold");
    }
}
