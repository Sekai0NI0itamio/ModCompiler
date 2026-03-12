package com.itamio.servercore.forge;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.border.WorldBorder;

public final class RandomTeleportService {
    public static final int SQUARE_RADIUS = 10_000;
    private static final RandomTeleportService INSTANCE = new RandomTeleportService();

    private final Random random = new Random();

    private RandomTeleportService() {
    }

    public static RandomTeleportService getInstance() {
        return INSTANCE;
    }

    public RtpResult teleport(ServerPlayer player, String dimensionKey) {
        MinecraftServer server = ServerCoreAccess.getServer(player);
        if (player == null || server == null) {
            return RtpResult.failure("Server unavailable.");
        }
        ServerLevel level = TeleportUtil.resolveLevel(server, dimensionKey);
        if (level == null) {
            return RtpResult.failure("Target dimension is not loaded.");
        }

        int attempts = 30;
        for (int i = 0; i < attempts; i++) {
            BlockPos candidate = generateCandidate(level);
            if (candidate == null) {
                continue;
            }
            BlockPos safe = findSafePosition(level, candidate.getX(), candidate.getZ());
            if (safe != null) {
                TeleportUtil.teleport(player, level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
                return RtpResult.success(safe, "Teleported to random location in " + dimensionName(level) + ".");
            }
        }

        return RtpResult.failure("Could not find a safe location after " + attempts + " attempts.");
    }

    private BlockPos generateCandidate(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        int minX = -SQUARE_RADIUS;
        int maxX = SQUARE_RADIUS;
        int minZ = -SQUARE_RADIUS;
        int maxZ = SQUARE_RADIUS;

        int borderMinX = (int) Math.ceil(border.getMinX());
        int borderMaxX = (int) Math.floor(border.getMaxX());
        int borderMinZ = (int) Math.ceil(border.getMinZ());
        int borderMaxZ = (int) Math.floor(border.getMaxZ());

        if (borderMinX > minX) minX = borderMinX;
        if (borderMaxX < maxX) maxX = borderMaxX;
        if (borderMinZ > minZ) minZ = borderMinZ;
        if (borderMaxZ < maxZ) maxZ = borderMaxZ;

        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        int x = randomBetween(minX, maxX);
        int z = randomBetween(minZ, maxZ);
        return new BlockPos(x, level.getSeaLevel(), z);
    }

    private int randomBetween(int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private BlockPos findSafePosition(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int maxY = Math.min(top, ServerCoreAccess.getMaxBuildHeight(level) - 2);
        int minY = ServerCoreAccess.getMinBuildHeight(level) + 1;

        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (isSafeStandPosition(level, feet)) {
                return feet;
            }
        }
        return null;
    }

    private boolean isSafeStandPosition(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos ground = feet.below();
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState groundState = level.getBlockState(ground);
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

    private String dimensionName(ServerLevel level) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            return "the Overworld";
        }
        if (level.dimension().equals(Level.NETHER)) {
            return "the Nether";
        }
        if (level.dimension().equals(Level.END)) {
            return "the End";
        }
        return level.dimension().location().toString();
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
