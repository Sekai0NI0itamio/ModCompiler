package com.itamio.snowaccumulation.fabric;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SnowAccumulationHandler {
    private static final Random RANDOM = new Random();
    private static final int CONFIG_CHECK_INTERVAL = 200;

    private static int accumulationTickCounter = 0;
    private static int configCheckCounter = 0;

    private SnowAccumulationHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }

        configCheckCounter++;
        if (configCheckCounter >= CONFIG_CHECK_INTERVAL) {
            configCheckCounter = 0;
            SnowAccumulationConfig.reloadIfChanged();
        }

        accumulationTickCounter++;
        if (accumulationTickCounter < SnowAccumulationConfig.getAccumulationSpeed()) {
            return;
        }
        accumulationTickCounter = 0;

        if (!world.isRaining()) {
            return;
        }

        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }

        ServerPlayerEntity player = players.get(RANDOM.nextInt(players.size()));
        BlockPos playerPos = player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int chunkRadius = SnowAccumulationConfig.getChunkRadius();
        int chunksPerTick = SnowAccumulationConfig.getChunksPerTick();

        for (int chunkIndex = 0; chunkIndex < chunksPerTick; chunkIndex++) {
            int chunkX = playerChunkX + RANDOM.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int chunkZ = playerChunkZ + RANDOM.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            for (int blockIndex = 0; blockIndex < SnowAccumulationConfig.getBlocksPerChunk(); blockIndex++) {
                int x = (chunkX << 4) + RANDOM.nextInt(16);
                int z = (chunkZ << 4) + RANDOM.nextInt(16);
                processColumn(world, x, z);
            }
        }
    }

    private static void processColumn(ServerWorld world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        if (topY <= 0) {
            return;
        }

        BlockPos surfacePos = new BlockPos(x, topY - 1, z);
        BlockState surfaceState = world.getBlockState(surfacePos);

        if (isSnow(surfaceState)) {
            BlockPos topSnowPos = findTopSnowBlock(world, surfacePos);
            BlockPos precipitationPos = topSnowPos.up();
            if (shouldAccumulateAt(world, precipitationPos)) {
                growSnowColumn(world, topSnowPos);
            }
            return;
        }

        BlockPos placementPos = surfacePos.up();
        if (!shouldAccumulateAt(world, placementPos)) {
            return;
        }
        if (!world.getBlockState(placementPos).isAir()) {
            return;
        }
        if (!Blocks.SNOW.getDefaultState().canPlaceAt(world, placementPos)) {
            return;
        }
        if (SnowAccumulationConfig.getMaxSnowHeight() < 1) {
            return;
        }

        world.setBlockState(placementPos, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1), 3);
    }

    private static boolean shouldAccumulateAt(ServerWorld world, BlockPos pos) {
        if (!world.isRaining()) {
            return false;
        }
        if (!world.isSkyVisible(pos)) {
            return false;
        }
        return getBiomeTemperature(world, pos) <= 0.15F;
    }

    private static void growSnowColumn(ServerWorld world, BlockPos topSnowPos) {
        int currentTotalLayers = countSnowLayers(world, topSnowPos);
        if (currentTotalLayers >= SnowAccumulationConfig.getMaxSnowHeight()) {
            return;
        }

        BlockState topState = world.getBlockState(topSnowPos);
        int layers = topState.get(SnowBlock.LAYERS);
        if (layers < 8) {
            world.setBlockState(topSnowPos, topState.with(SnowBlock.LAYERS, layers + 1), 3);
            return;
        }

        BlockPos newSnowPos = topSnowPos.up();
        if (!world.getBlockState(newSnowPos).isAir()) {
            return;
        }
        if (!Blocks.SNOW.getDefaultState().canPlaceAt(world, newSnowPos)) {
            return;
        }

        world.setBlockState(newSnowPos, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1), 3);
    }

    private static BlockPos findTopSnowBlock(ServerWorld world, BlockPos startPos) {
        BlockPos current = startPos;
        while (isSnow(world.getBlockState(current.up()))) {
            current = current.up();
        }
        return current;
    }

    private static int countSnowLayers(ServerWorld world, BlockPos topSnowPos) {
        int totalLayers = 0;
        BlockPos current = topSnowPos;
        while (current.getY() > 0) {
            BlockState state = world.getBlockState(current);
            if (!isSnow(state)) {
                break;
            }
            totalLayers += state.get(SnowBlock.LAYERS);
            current = current.down();
        }
        return totalLayers;
    }

    private static boolean isSnow(BlockState state) {
        return state.getBlock() == Blocks.SNOW;
    }

    private static float getBiomeTemperature(ServerWorld world, BlockPos pos) {
        try {
            Object biomeReference = world.getBiome(pos);
            Object biome = unwrapBiome(biomeReference);
            if (biome == null) {
                return 1.0F;
            }

            Float positional = invokeTemperatureMethod(biome, pos);
            if (positional != null) {
                return positional.floatValue();
            }

            Float basic = invokeTemperatureMethod(biome, null);
            if (basic != null) {
                return basic.floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 1.0F;
    }

    private static Object unwrapBiome(Object biomeReference) throws ReflectiveOperationException {
        if (biomeReference == null) {
            return null;
        }
        try {
            Method valueMethod = biomeReference.getClass().getMethod("value");
            return valueMethod.invoke(biomeReference);
        } catch (NoSuchMethodException ignored) {
            return biomeReference;
        }
    }

    private static Float invokeTemperatureMethod(Object biome, BlockPos pos) throws ReflectiveOperationException {
        Method[] methods = biome.getClass().getMethods();
        for (int index = 0; index < methods.length; index++) {
            Method method = methods[index];
            if (!"getTemperature".equals(method.getName())) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType != Float.TYPE && returnType != Float.class) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && pos != null && parameterTypes[0].isAssignableFrom(pos.getClass())) {
                Object result = method.invoke(biome, pos);
                return result instanceof Float ? (Float) result : Float.valueOf(((Number) result).floatValue());
            }
            if (parameterTypes.length == 0) {
                Object result = method.invoke(biome);
                return result instanceof Float ? (Float) result : Float.valueOf(((Number) result).floatValue());
            }
        }
        return null;
    }
}
