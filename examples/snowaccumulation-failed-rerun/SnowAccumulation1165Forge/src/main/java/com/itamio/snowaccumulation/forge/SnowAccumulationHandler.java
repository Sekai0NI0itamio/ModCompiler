package com.itamio.snowaccumulation.forge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

public final class SnowAccumulationHandler {
    private static final Random RANDOM = new Random();
    private static final int CONFIG_CHECK_INTERVAL = 200;

    private static int accumulationTickCounter = 0;
    private static int configCheckCounter = 0;

    private SnowAccumulationHandler() {
    }

    public static void onServerTick(Object serverObject) {
        if (!(serverObject instanceof MinecraftServer)) {
            return;
        }

        ServerWorld world = resolveOverworld((MinecraftServer) serverObject);
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

        List<?> players = world.players();
        if (players.isEmpty()) {
            return;
        }

        Object player = players.get(RANDOM.nextInt(players.size()));
        BlockPos playerPos = extractPlayerPos(player);
        if (playerPos == null) {
            return;
        }

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int chunkRadius = SnowAccumulationConfig.getChunkRadius();
        int chunksPerTick = SnowAccumulationConfig.getChunksPerTick();

        for (int chunkIndex = 0; chunkIndex < chunksPerTick; chunkIndex++) {
            int chunkX = playerChunkX + RANDOM.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int chunkZ = playerChunkZ + RANDOM.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            if (!world.hasChunk(chunkX, chunkZ)) {
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
        int topY = world.getHeight(Heightmap.Type.MOTION_BLOCKING, x, z);
        if (topY <= 0) {
            return;
        }

        BlockPos surfacePos = new BlockPos(x, topY - 1, z);
        BlockState surfaceState = world.getBlockState(surfacePos);

        if (isSnow(surfaceState)) {
            BlockPos topSnowPos = findTopSnowBlock(world, surfacePos);
            BlockPos precipitationPos = topSnowPos.above();
            if (shouldAccumulateAt(world, precipitationPos)) {
                growSnowColumn(world, topSnowPos);
            }
            return;
        }

        BlockPos placementPos = surfacePos.above();
        if (!shouldAccumulateAt(world, placementPos)) {
            return;
        }
        if (!world.getBlockState(placementPos).isAir()) {
            return;
        }
        if (!Blocks.SNOW.defaultBlockState().canSurvive(world, placementPos)) {
            return;
        }
        if (SnowAccumulationConfig.getMaxSnowHeight() < 1) {
            return;
        }

        world.setBlockAndUpdate(placementPos, Blocks.SNOW.defaultBlockState().setValue(SnowBlock.LAYERS, 1));
    }

    private static boolean shouldAccumulateAt(ServerWorld world, BlockPos pos) {
        if (!world.isRaining()) {
            return false;
        }
        if (!world.canSeeSky(pos)) {
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
        int layers = topState.getValue(SnowBlock.LAYERS);
        if (layers < 8) {
            world.setBlockAndUpdate(topSnowPos, topState.setValue(SnowBlock.LAYERS, layers + 1));
            return;
        }

        BlockPos newSnowPos = topSnowPos.above();
        if (!world.getBlockState(newSnowPos).isAir()) {
            return;
        }
        if (!Blocks.SNOW.defaultBlockState().canSurvive(world, newSnowPos)) {
            return;
        }

        world.setBlockAndUpdate(newSnowPos, Blocks.SNOW.defaultBlockState().setValue(SnowBlock.LAYERS, 1));
    }

    private static BlockPos findTopSnowBlock(ServerWorld world, BlockPos startPos) {
        BlockPos current = startPos;
        while (isSnow(world.getBlockState(current.above()))) {
            current = current.above();
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
            totalLayers += state.getValue(SnowBlock.LAYERS);
            current = current.below();
        }
        return totalLayers;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW);
    }

    private static ServerWorld resolveOverworld(MinecraftServer server) {
        try {
            Method method = server.getClass().getMethod("overworld");
            Object world = method.invoke(server);
            if (world instanceof ServerWorld) {
                return (ServerWorld) world;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        for (Method method : server.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && ServerWorld.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Object world = method.invoke(server);
                    if (world instanceof ServerWorld) {
                        return (ServerWorld) world;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }

        return null;
    }

    private static BlockPos extractPlayerPos(Object player) {
        try {
            Method method = player.getClass().getMethod("blockPosition");
            Object pos = method.invoke(player);
            if (pos instanceof BlockPos) {
                return (BlockPos) pos;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = player.getClass().getMethod("getBlockPos");
            Object pos = method.invoke(player);
            if (pos instanceof BlockPos) {
                return (BlockPos) pos;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static float getBiomeTemperature(ServerWorld world, BlockPos pos) {
        try {
            Object biomeReference = invokeWorldBiome(world, pos);
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

    private static Object invokeWorldBiome(ServerWorld world, BlockPos pos) throws ReflectiveOperationException {
        Method[] methods = world.getClass().getMethods();
        for (int index = 0; index < methods.length; index++) {
            Method method = methods[index];
            if (!"getBiome".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(pos.getClass())) {
                return method.invoke(world, pos);
            }
        }
        return null;
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
