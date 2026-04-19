package io.itamio.aipoweredcompanionship;

import net.minecraft.entity.Entity;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class CompanionWorldAccess {

    @SuppressWarnings("unchecked")
    static boolean register(WorldServer world, CompanionEntity entity, Logger logger) {
        if (world == null || entity == null) return false;
        try {
            int cx = MathHelper.floor(entity.posX) >> 4;
            int cz = MathHelper.floor(entity.posZ) >> 4;
            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
            chunk.addEntity(entity);
            if (!world.loadedEntityList.contains(entity)) {
                world.loadedEntityList.add(entity);
            }
            // Add to internal ID and UUID maps via reflection
            addEntityById(world, entity);
            addEntityByUuid(world, entity);
            entity.onAddedToWorld();
            // Remove from vanilla EntityTracker to prevent crashes
            removeFromTracker(world, entity);
            return true;
        } catch (Exception ex) {
            logger.error("Failed to register companion: {}", ex.getMessage());
            cleanup(world, entity);
            return false;
        }
    }

    static void unregister(WorldServer world, CompanionEntity entity, Logger logger) {
        if (world == null || entity == null) return;
        try {
            int cx = MathHelper.floor(entity.posX) >> 4;
            int cz = MathHelper.floor(entity.posZ) >> 4;
            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
            chunk.removeEntity(entity);
            world.loadedEntityList.remove(entity);
            world.playerEntities.remove(entity);
            removeEntityById(world, entity);
            removeEntityByUuid(world, entity);
            removeFromTracker(world, entity);
        } catch (Exception ex) {
            logger.error("Failed to unregister companion: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void addEntityById(WorldServer world, Entity entity) {
        try {
            for (Field f : world.getClass().getSuperclass().getDeclaredFields()) {
                if (IntHashMap.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    IntHashMap<Entity> map = (IntHashMap<Entity>) f.get(world);
                    map.addKey(entity.getEntityId(), entity);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void removeEntityById(WorldServer world, Entity entity) {
        try {
            for (Field f : world.getClass().getSuperclass().getDeclaredFields()) {
                if (IntHashMap.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    IntHashMap<Entity> map = (IntHashMap<Entity>) f.get(world);
                    map.removeObject(entity.getEntityId());
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void addEntityByUuid(WorldServer world, Entity entity) {
        try {
            for (Field f : world.getClass().getSuperclass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Map<UUID, Entity> map = (Map<UUID, Entity>) f.get(world);
                    if (map != null) {
                        map.put(entity.getUniqueID(), entity);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void removeEntityByUuid(WorldServer world, Entity entity) {
        try {
            for (Field f : world.getClass().getSuperclass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Map<UUID, Entity> map = (Map<UUID, Entity>) f.get(world);
                    if (map != null) {
                        map.remove(entity.getUniqueID());
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void removeFromTracker(WorldServer world, Entity entity) {
        try {
            Object tracker = world.getEntityTracker();
            for (Method method : tracker.getClass().getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && Entity.class.isAssignableFrom(params[0])) {
                    method.setAccessible(true);
                    method.invoke(tracker, entity);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void cleanup(WorldServer world, CompanionEntity entity) {
        try {
            world.loadedEntityList.remove(entity);
            world.playerEntities.remove(entity);
            removeEntityById(world, entity);
            removeEntityByUuid(world, entity);
        } catch (Exception ignored) {}
    }
}
