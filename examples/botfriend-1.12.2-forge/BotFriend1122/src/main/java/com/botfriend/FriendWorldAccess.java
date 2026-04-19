package com.botfriend;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Logger;

final class FriendWorldAccess {
    private static final Field WORLD_ENTITIES_BY_ID = findUniqueField(World.class, IntHashMap.class, "entitiesById");
    private static final Field WORLD_SERVER_ENTITIES_BY_UUID = findUniqueField(WorldServer.class, Map.class, "entitiesByUuid");

    private FriendWorldAccess() {
    }

    static boolean register(WorldServer world, BotFriendPlayer friend, Logger logger) {
        if (world == null || friend == null) {
            return false;
        }
        try {
            int chunkX = MathHelper.floor(friend.posX) >> 4;
            int chunkZ = MathHelper.floor(friend.posZ) >> 4;
            Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
            if (!world.playerEntities.contains(friend)) {
                world.playerEntities.add(friend);
                world.updateAllPlayersSleepingFlag();
            }
            chunk.addEntity(friend);
            if (!world.loadedEntityList.contains(friend)) {
                world.loadedEntityList.add(friend);
            }
            getEntitiesById(world).addKey(friend.getEntityId(), friend);
            getEntitiesByUuid(world).put(friend.getUniqueID(), friend);
            friend.onAddedToWorld();
            removeFromVanillaTracker(world, friend);
            return true;
        } catch (Exception ex) {
            logger.error("Failed to register BotFriend {} in world: {}", friend.getFriendName(), ex.getMessage());
            cleanupPartialRegistration(world, friend);
            return false;
        }
    }

    static void unregister(WorldServer world, BotFriendPlayer friend) {
        if (world == null || friend == null) {
            return;
        }
        world.removeEntityDangerously(friend);
    }

    @SuppressWarnings("unchecked")
    private static IntHashMap<net.minecraft.entity.Entity> getEntitiesById(World world) throws IllegalAccessException {
        return (IntHashMap<net.minecraft.entity.Entity>) WORLD_ENTITIES_BY_ID.get(world);
    }

    @SuppressWarnings("unchecked")
    private static Map<java.util.UUID, net.minecraft.entity.Entity> getEntitiesByUuid(WorldServer world) throws IllegalAccessException {
        return (Map<java.util.UUID, net.minecraft.entity.Entity>) WORLD_SERVER_ENTITIES_BY_UUID.get(world);
    }

    private static Field findUniqueField(Class<?> owner, Class<?> type, String preferredName) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type && preferredName.equals(field.getName())) {
                field.setAccessible(true);
                return field;
            }
        }
        Field match = null;
        for (Field field : owner.getDeclaredFields()) {
            if (!type.isAssignableFrom(field.getType())) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException("Expected one " + type.getName() + " field in " + owner.getName());
            }
            field.setAccessible(true);
            match = field;
        }
        if (match == null) {
            throw new IllegalStateException("Missing " + type.getName() + " field in " + owner.getName());
        }
        return match;
    }

    private static void removeFromVanillaTracker(WorldServer world, BotFriendPlayer friend) {
        try {
            EntityTracker tracker = world.getEntityTracker();
            for (Method method : EntityTracker.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0] == Entity.class) {
                    method.setAccessible(true);
                    method.invoke(tracker, friend);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanupPartialRegistration(WorldServer world, BotFriendPlayer friend) {
        try {
            if (friend.addedToChunk) {
                world.getChunkFromChunkCoords(friend.chunkCoordX, friend.chunkCoordZ).removeEntity(friend);
            }
        } catch (Exception ignored) {
        }
        world.playerEntities.remove(friend);
        world.updateAllPlayersSleepingFlag();
        world.loadedEntityList.remove(friend);
        try {
            getEntitiesById(world).removeObject(friend.getEntityId());
        } catch (Exception ignored) {
        }
        try {
            getEntitiesByUuid(world).remove(friend.getUniqueID());
        } catch (Exception ignored) {
        }
        friend.onRemovedFromWorld();
    }
}
