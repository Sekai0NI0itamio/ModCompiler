package io.itamio.aipoweredcompanionship;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CompanionManager {
    private final CompanionBrainService brainService;
    private final org.apache.logging.log4j.Logger logger;
    private final Map<String, CompanionEntity> companions = new ConcurrentHashMap<>();
    private final Map<String, Set<java.util.UUID>> visibleTo = new ConcurrentHashMap<>();
    private final Map<String, Set<java.util.UUID>> pendingSpawn = new ConcurrentHashMap<>();
    private MinecraftServer server;
    private long tickCounter;

    public CompanionManager(CompanionBrainService brainService, org.apache.logging.log4j.Logger logger) {
        this.brainService = brainService;
        this.logger = logger;
    }

    public void attachServer(MinecraftServer server) {
        this.server = server;
    }

    public void detachServer() {
        this.server = null;
    }

    public void tick() {
        if (server == null) return;
        tickCounter++;
        for (CompanionEntity c : new ArrayList<>(companions.values())) {
            if (c.isDead || c.world == null) {
                removeCompanion(c.getCompanionName());
                continue;
            }
            c.companionTick();
            syncVisibility(c);
        }
    }

    public boolean addCompanion(String name, EntityPlayerMP owner) {
        String key = name.toLowerCase();
        if (companions.containsKey(key)) return false;
        WorldServer world = server.getWorld(owner.dimension);
        Vec3d pos = computeSpawnNear(owner);
        CompanionEntity entity = new CompanionEntity(world, name, owner.getUniqueID(), this);
        entity.setPosition(pos.x, pos.y, pos.z);
        if (!CompanionWorldAccess.register(world, entity, logger)) return false;
        companions.put(key, entity);
        visibleTo.put(key, new HashSet<>());
        pendingSpawn.put(key, new HashSet<>());
        logger.info("[AIPC] Spawned companion '{}' for {}", name, owner.getName());
        return true;
    }

    public boolean removeCompanion(String name) {
        String key = name.toLowerCase();
        CompanionEntity entity = companions.remove(key);
        if (entity == null) return false;
        CompanionWorldAccess.unregister((WorldServer) entity.world, entity, logger);
        visibleTo.remove(key);
        pendingSpawn.remove(key);
        logger.info("[AIPC] Removed companion '{}'", name);
        return true;
    }

    public void handleMessage(EntityPlayerMP speaker, String message) {
        String prefix = CompanionConfig.data.chat.prefix;
        if (prefix == null) prefix = "@";
        String trimmed = message.trim();
        if (!trimmed.startsWith(prefix) || trimmed.length() <= prefix.length()) return;
        int space = trimmed.indexOf(' ', prefix.length());
        if (space <= prefix.length()) return;
        String name = trimmed.substring(prefix.length(), space).trim();
        String text = trimmed.substring(space + 1).trim();
        if (text.isEmpty()) return;
        CompanionEntity entity = getCompanion(name, speaker);
        if (entity == null) return;
        brainService.processMessage(entity, speaker.getName(), text);
    }

    public void handleCommand(EntityPlayerMP speaker, String name, String instruction) {
        CompanionEntity entity = getCompanion(name, speaker);
        if (entity == null) return;
        brainService.processMessage(entity, speaker.getName(), instruction);
    }

    private CompanionEntity getCompanion(String name, EntityPlayerMP owner) {
        CompanionEntity c = companions.get(name.toLowerCase());
        if (c == null) return null;
        if (!c.getRecord().getOwnerUuid().equals(owner.getUniqueID())) return null;
        return c;
    }

    public Collection<CompanionEntity> getCompanions() {
        return Collections.unmodifiableCollection(companions.values());
    }

    public CompanionEntity getCompanionByName(String name) {
        return companions.get(name.toLowerCase());
    }

    public void onPlayerLogin(EntityPlayerMP player) {
        for (CompanionEntity c : companions.values()) syncVisibilityFor(c, player);
    }

    public void onPlayerLogout(EntityPlayerMP player) {
        for (Set<java.util.UUID> v : visibleTo.values()) v.remove(player.getUniqueID());
        for (Set<java.util.UUID> v : pendingSpawn.values()) v.remove(player.getUniqueID());
    }

    public void onCompanionDeath(CompanionEntity entity) {
        removeCompanion(entity.getCompanionName());
    }

    private void syncVisibility(CompanionEntity entity) {
        if (server == null || entity.world == null) return;
        String key = entity.getCompanionName().toLowerCase();
        for (EntityPlayerMP viewer : server.getPlayerList().getPlayers()) {
            boolean shouldSee = viewer.dimension == entity.dimension
                && viewer.getDistanceSq(entity) <= 128.0 * 128.0;
            Set<java.util.UUID> visible = visibleTo.getOrDefault(key, Collections.emptySet());
            Set<java.util.UUID> pending = pendingSpawn.getOrDefault(key, Collections.emptySet());
            java.util.UUID id = viewer.getUniqueID();
            if (shouldSee && !visible.contains(id)) {
                if (!pending.contains(id)) {
                    pendingSpawn.computeIfAbsent(key, k -> new HashSet<>()).add(id);
                    sendPlayerListAdd(viewer, entity);
                } else {
                    pendingSpawn.get(key).remove(id);
                    visibleTo.computeIfAbsent(key, k -> new HashSet<>()).add(id);
                    sendSpawnPackets(viewer, entity);
                }
            } else if (!shouldSee && visible.contains(id)) {
                visibleTo.get(key).remove(id);
                sendDestroyPackets(viewer, entity);
            } else if (shouldSee && visible.contains(id)) {
                if (tickCounter % 2L == 0L) sendUpdatePackets(viewer, entity);
            }
        }
    }

    private void syncVisibilityFor(CompanionEntity entity, EntityPlayerMP viewer) {
        String key = entity.getCompanionName().toLowerCase();
        boolean shouldSee = viewer.dimension == entity.dimension
            && viewer.getDistanceSq(entity) <= 128.0 * 128.0;
        if (shouldSee) {
            sendPlayerListAdd(viewer, entity);
            visibleTo.computeIfAbsent(key, k -> new HashSet<>()).add(viewer.getUniqueID());
            sendSpawnPackets(viewer, entity);
        }
    }

    private void sendPlayerListAdd(EntityPlayerMP viewer, CompanionEntity entity) {
        if (viewer == null || viewer.connection == null) return;
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketPlayerListItem(
            net.minecraft.network.play.server.SPacketPlayerListItem.Action.ADD_PLAYER, entity));
    }

    private void sendSpawnPackets(EntityPlayerMP viewer, CompanionEntity entity) {
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketSpawnPlayer(entity));
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityMetadata(
            entity.getEntityId(), entity.getDataManager(), true));
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityTeleport(entity));
    }

    private void sendUpdatePackets(EntityPlayerMP viewer, CompanionEntity entity) {
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityTeleport(entity));
        byte headYaw = (byte) MathHelper.floor(entity.rotationYawHead * 256.0F / 360.0F);
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityHeadLook(entity, headYaw));
    }

    private void sendDestroyPackets(EntityPlayerMP viewer, CompanionEntity entity) {
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketDestroyEntities(entity.getEntityId()));
        viewer.connection.sendPacket(new net.minecraft.network.play.server.SPacketPlayerListItem(
            net.minecraft.network.play.server.SPacketPlayerListItem.Action.REMOVE_PLAYER, entity));
    }

    private Vec3d computeSpawnNear(EntityPlayerMP owner) {
        double angle = owner.rotationYaw * Math.PI / 180.0;
        return new Vec3d(owner.posX - Math.sin(angle) * 2.0, owner.posY, owner.posZ + Math.cos(angle) * 2.0);
    }
}
