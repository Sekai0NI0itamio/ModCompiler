package com.itamio.servercore.fabric;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.MagmaBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.Heightmap;

public final class RandomTeleportService {
    public static final int SQUARE_RADIUS = 10_000;
    private static final RandomTeleportService INSTANCE = new RandomTeleportService();

    private final Random random = new Random();

    private RandomTeleportService() {
    }

    public static RandomTeleportService getInstance() {
        return INSTANCE;
    }

    public RtpResult teleport(ServerPlayerEntity player, String dimensionKey) {
        if (player == null || player.getServer() == null) {
            return RtpResult.failure("Server unavailable.");
        }
        MinecraftServer server = player.getServer();
        ServerWorld world = TeleportUtil.resolveWorld(server, dimensionKey);
        if (world == null) {
            return RtpResult.failure("Target dimension is not loaded.");
        }
        int attempts = 30;
        for (int i = 0; i < attempts; i++) {
            BlockPos candidate = generateCandidate(world);
            if (candidate == null) {
                continue;
            }
            BlockPos safe = findSafePosition(world, candidate.getX(), candidate.getZ());
            if (safe != null) {
                TeleportUtil.teleport(
                        player,
                        world,
                        safe.getX() + 0.5,
                        safe.getY(),
                        safe.getZ() + 0.5,
                        RotationUtil.getYaw(player),
                        RotationUtil.getPitch(player)
                );
                return RtpResult.success(safe, "Teleported to random location in " + dimensionName(world) + ".");
            }
        }
        return RtpResult.failure("Could not find a safe location after " + attempts + " attempts.");
    }

    private BlockPos generateCandidate(ServerWorld world) {
        WorldBorder border = world.getWorldBorder();
        int minX = -SQUARE_RADIUS;
        int maxX = SQUARE_RADIUS;
        int minZ = -SQUARE_RADIUS;
        int maxZ = SQUARE_RADIUS;

        int borderMinX = (int) Math.ceil(border.getBoundWest());
        int borderMaxX = (int) Math.floor(border.getBoundEast());
        int borderMinZ = (int) Math.ceil(border.getBoundNorth());
        int borderMaxZ = (int) Math.floor(border.getBoundSouth());

        if (borderMinX > minX) minX = borderMinX;
        if (borderMaxX < maxX) maxX = borderMaxX;
        if (borderMinZ > minZ) minZ = borderMinZ;
        if (borderMaxZ < maxZ) maxZ = borderMaxZ;

        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        int x = randomBetween(minX, maxX);
        int z = randomBetween(minZ, maxZ);
        return new BlockPos(x, world.getSeaLevel(), z);
    }

    private int randomBetween(int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private BlockPos findSafePosition(ServerWorld world, int x, int z) {
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int maxY = Math.min(top, world.getHeight() - 2);
        int minY = 2;
        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (isSafeStandPosition(world, feet)) {
                return feet;
            }
        }
        return null;
    }

    private boolean isSafeStandPosition(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos ground = feet.down();
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        BlockState groundState = world.getBlockState(ground);
        return isPassable(feetState)
                && isPassable(headState)
                && isSolidGround(groundState);
    }

    private boolean isPassable(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir();
    }

    private boolean isSolidGround(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isAir()) {
            return false;
        }
        return !isDangerous(state.getBlock());
    }

    private boolean isDangerous(Block block) {
        return block == Blocks.LAVA
                || block == Blocks.FIRE
                || block instanceof FireBlock
                || block instanceof CactusBlock
                || block instanceof MagmaBlock;
    }

    private String dimensionName(ServerWorld world) {
        if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return "the Overworld";
        }
        if (world.getRegistryKey().equals(World.NETHER)) {
            return "the Nether";
        }
        if (world.getRegistryKey().equals(World.END)) {
            return "the End";
        }
        return world.getRegistryKey().getValue().toString();
    }

    public static final class RtpResult {
        private final boolean success;
        private final BlockPos location;
        private final String message;

        private RtpResult(boolean success, BlockPos location, String message) {
            this.success = success;
            this.location = location;
            this.message = message;
        }

        public static RtpResult success(BlockPos location, String message) {
            return new RtpResult(true, location, message);
        }

        public static RtpResult failure(String message) {
            return new RtpResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public BlockPos getLocation() {
            return location;
        }

        public String getMessage() {
            return message;
        }
    }
}
