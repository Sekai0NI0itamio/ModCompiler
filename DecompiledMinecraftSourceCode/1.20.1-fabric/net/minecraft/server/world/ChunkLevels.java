/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.server.world;

import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.world.chunk.ChunkStatus;

public class ChunkLevels {
    private static final int FULL = 33;
    private static final int BLOCK_TICKING = 32;
    private static final int ENTITY_TICKING = 31;
    public static final int INACCESSIBLE = 33 + ChunkStatus.getMaxDistanceFromFull();

    public static ChunkStatus getStatus(int level) {
        if (level < 33) {
            return ChunkStatus.FULL;
        }
        return ChunkStatus.byDistanceFromFull(level - 33);
    }

    public static int getLevelFromStatus(ChunkStatus status) {
        return 33 + ChunkStatus.getDistanceFromFull(status);
    }

    public static ChunkLevelType getType(int level) {
        if (level <= 31) {
            return ChunkLevelType.ENTITY_TICKING;
        }
        if (level <= 32) {
            return ChunkLevelType.BLOCK_TICKING;
        }
        if (level <= 33) {
            return ChunkLevelType.FULL;
        }
        return ChunkLevelType.INACCESSIBLE;
    }

    public static int getLevelFromType(ChunkLevelType type) {
        return switch (type) {
            default -> throw new MatchException(null, null);
            case ChunkLevelType.INACCESSIBLE -> INACCESSIBLE;
            case ChunkLevelType.FULL -> 33;
            case ChunkLevelType.BLOCK_TICKING -> 32;
            case ChunkLevelType.ENTITY_TICKING -> 31;
        };
    }

    public static boolean shouldTickEntities(int level) {
        return level <= 31;
    }

    public static boolean shouldTickBlocks(int level) {
        return level <= 32;
    }

    public static boolean isAccessible(int level) {
        return level <= INACCESSIBLE;
    }
}

