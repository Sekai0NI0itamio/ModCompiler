package com.bothelpers.script;

import com.bothelpers.entity.EntityBotHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BotNaturalSpawner {
    private static final int MOB_COUNT_DIV = (int) Math.pow(17.0D, 2.0D);
    private final Set<ChunkPos> eligibleChunksForSpawning = new HashSet<>();

    public int findChunksForSpawning(WorldServer world, List<EntityBotHelper> bots, boolean spawnHostileMobs, boolean spawnPeacefulMobs, boolean spawnOnSetTickRate) {
        if ((!spawnHostileMobs && !spawnPeacefulMobs) || bots.isEmpty()) {
            return 0;
        }

        this.eligibleChunksForSpawning.clear();
        int eligibleChunkCount = 0;

        for (EntityBotHelper bot : bots) {
            if (bot == null || bot.isDead) {
                continue;
            }

            int chunkX = MathHelper.floor(bot.posX / 16.0D);
            int chunkZ = MathHelper.floor(bot.posZ / 16.0D);

            for (int offsetX = -8; offsetX <= 8; ++offsetX) {
                for (int offsetZ = -8; offsetZ <= 8; ++offsetZ) {
                    boolean border = offsetX == -8 || offsetX == 8 || offsetZ == -8 || offsetZ == 8;
                    ChunkPos chunkPos = new ChunkPos(offsetX + chunkX, offsetZ + chunkZ);

                    if (!this.eligibleChunksForSpawning.contains(chunkPos)) {
                        ++eligibleChunkCount;

                        if (!border && world.getWorldBorder().contains(chunkPos)) {
                            net.minecraft.server.management.PlayerChunkMapEntry entry = world.getPlayerChunkMap().getEntry(chunkPos.x, chunkPos.z);
                            if (entry != null && entry.isSentToPlayers()) {
                                this.eligibleChunksForSpawning.add(chunkPos);
                            }
                        }
                    }
                }
            }
        }

        int spawnedCount = 0;
        BlockPos spawnPoint = world.getSpawnPoint();

        for (EnumCreatureType creatureType : EnumCreatureType.values()) {
            if ((!creatureType.getPeacefulCreature() || spawnPeacefulMobs)
                && (creatureType.getPeacefulCreature() || spawnHostileMobs)
                && (!creatureType.getAnimal() || spawnOnSetTickRate)) {
                int currentCount = world.countEntities(creatureType, true);
                int maxAllowed = creatureType.getMaxNumberOfCreature() * eligibleChunkCount / MOB_COUNT_DIV;

                if (currentCount <= maxAllowed) {
                    List<ChunkPos> shuffled = new ArrayList<>(this.eligibleChunksForSpawning);
                    Collections.shuffle(shuffled, world.rand);
                    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

                    chunkLoop:
                    for (ChunkPos chunkPos : shuffled) {
                        BlockPos randomPos = getRandomChunkPosition(world, chunkPos.x, chunkPos.z);
                        int baseX = randomPos.getX();
                        int baseY = randomPos.getY();
                        int baseZ = randomPos.getZ();
                        IBlockState state = world.getBlockState(randomPos);

                        if (!state.isNormalCube()) {
                            int packCount = 0;

                            for (int packAttempt = 0; packAttempt < 3; ++packAttempt) {
                                int x = baseX;
                                int y = baseY;
                                int z = baseZ;
                                Biome.SpawnListEntry spawnEntry = null;
                                IEntityLivingData livingData = null;
                                int tries = MathHelper.ceil(Math.random() * 4.0D);

                                for (int tryIndex = 0; tryIndex < tries; ++tryIndex) {
                                    x += world.rand.nextInt(6) - world.rand.nextInt(6);
                                    y += world.rand.nextInt(1) - world.rand.nextInt(1);
                                    z += world.rand.nextInt(6) - world.rand.nextInt(6);
                                    mutablePos.setPos(x, y, z);
                                    float fx = (float) x + 0.5F;
                                    float fz = (float) z + 0.5F;

                                    if (!isAnyHumanOrBotWithinRange(world, bots, fx, y, fz, 24.0D)
                                        && spawnPoint.distanceSq(fx, y, fz) >= 576.0D) {
                                        if (spawnEntry == null) {
                                            spawnEntry = world.getSpawnListEntryForTypeAt(creatureType, mutablePos);
                                            if (spawnEntry == null) {
                                                break;
                                            }
                                        }

                                        if (world.canCreatureTypeSpawnHere(creatureType, spawnEntry, mutablePos)
                                            && WorldEntitySpawner.canCreatureTypeSpawnAtLocation(EntitySpawnPlacementRegistry.getPlacementForEntity(spawnEntry.entityClass), world, mutablePos)) {
                                            EntityLiving entityLiving;

                                            try {
                                                entityLiving = spawnEntry.newInstance(world);
                                            } catch (Exception exception) {
                                                exception.printStackTrace();
                                                return spawnedCount;
                                            }

                                            entityLiving.setLocationAndAngles(fx, y, fz, world.rand.nextFloat() * 360.0F, 0.0F);

                                            net.minecraftforge.fml.common.eventhandler.Event.Result canSpawn = net.minecraftforge.event.ForgeEventFactory.canEntitySpawn(entityLiving, world, fx, y, fz, false);
                                            if (canSpawn == net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW
                                                || (canSpawn == net.minecraftforge.fml.common.eventhandler.Event.Result.DEFAULT
                                                && entityLiving.getCanSpawnHere()
                                                && entityLiving.isNotColliding())) {
                                                if (!net.minecraftforge.event.ForgeEventFactory.doSpecialSpawn(entityLiving, world, fx, y, fz)) {
                                                    livingData = entityLiving.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entityLiving)), livingData);
                                                }

                                                if (entityLiving.isNotColliding()) {
                                                    ++packCount;
                                                    world.spawnEntity(entityLiving);
                                                } else {
                                                    entityLiving.setDead();
                                                }

                                                if (packCount >= net.minecraftforge.event.ForgeEventFactory.getMaxSpawnPackSize(entityLiving)) {
                                                    continue chunkLoop;
                                                }
                                            }

                                            spawnedCount += packCount;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return spawnedCount;
    }

    private static BlockPos getRandomChunkPosition(WorldServer world, int chunkX, int chunkZ) {
        Chunk chunk = world.getChunk(chunkX, chunkZ);
        int x = chunkX * 16 + world.rand.nextInt(16);
        int z = chunkZ * 16 + world.rand.nextInt(16);
        int maxHeight = MathHelper.roundUp(chunk.getHeight(new BlockPos(x, 0, z)) + 1, 16);
        int y = world.rand.nextInt(maxHeight > 0 ? maxHeight : chunk.getTopFilledSegment() + 16 - 1);
        return new BlockPos(x, y, z);
    }

    private static boolean isAnyHumanOrBotWithinRange(WorldServer world, List<EntityBotHelper> bots, double x, double y, double z, double distance) {
        if (world.isAnyPlayerWithinRangeAt(x, y, z, distance)) {
            return true;
        }

        double maxDistanceSq = distance * distance;
        for (EntityBotHelper bot : bots) {
            if (bot != null && !bot.isDead && bot.getDistanceSq(x, y, z) < maxDistanceSq) {
                return true;
            }
        }

        return false;
    }
}
