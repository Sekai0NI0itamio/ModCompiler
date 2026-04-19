package com.botfriend;

import com.botfriend.FriendBrainService.BrainResult;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketSpawnPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.apache.logging.log4j.Logger;

public final class FriendManager {
    private final FriendBrainService brainService;
    private final FriendActionExecutor actionExecutor;
    private final Logger logger;
    private final Map<String, BotFriendPlayer> activeFriends = new HashMap<>();
    private final Map<String, Set<UUID>> visibleViewers = new HashMap<>();
    private MinecraftServer server;
    private long tickCounter;

    public FriendManager(FriendBrainService brainService, Logger logger) {
        this.brainService = brainService;
        this.actionExecutor = new FriendActionExecutor(logger);
        this.logger = logger;
    }

    public void attachServer(MinecraftServer server) {
        this.server = server;
    }

    public void detachServer() {
        for (BotFriendPlayer friend : new ArrayList<>(activeFriends.values())) {
            despawn(friend, false);
        }
        activeFriends.clear();
        visibleViewers.clear();
        server = null;
    }

    public void restoreAll() {
        if (server == null || !BotFriendConfig.data.chat.restoreFriendsOnStart) {
            return;
        }
        FriendSavedData data = getSavedData();
        for (FriendRecord record : data.getAll()) {
            spawnFromRecord(record, false);
        }
    }

    public String createFriend(EntityPlayerMP owner, String requestedName) {
        if (server == null) {
            return "BotFriend server runtime is not ready.";
        }
        String name = normalizeName(requestedName);
        if (name == null) {
            return "Friend names must be 3-16 letters, numbers, or underscores.";
        }
        FriendSavedData data = getSavedData();
        if (data.contains(name)) {
            return "A BotFriend named " + name + " already exists in this world.";
        }

        UUID friendUuid = UUID.nameUUIDFromBytes(("botfriend:" + name.toLowerCase(Locale.ROOT)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FriendRecord record = new FriendRecord(owner.getUniqueID(), owner.getName(), friendUuid, name);
        Vec3d spawn = computeSpawnInFront(owner);
        record.setX(spawn.x);
        record.setY(spawn.y);
        record.setZ(spawn.z);
        record.setYaw(owner.rotationYaw + 180.0F);
        record.setPitch(0.0F);
        record.setDimension(owner.dimension);
        record.setActiveMode("follow");
        data.put(record);

        BotFriendPlayer friend = spawnFromRecord(record, true);
        if (friend == null) {
            data.remove(name);
            return "Failed to spawn " + name + ".";
        }
        String greeting = BotFriendConfig.profile.greetingTemplate
            .replace("%owner%", owner.getName())
            .replace("%friend%", name);
        friend.say(greeting);
        return "Spawned BotFriend " + name + ".";
    }

    public String removeFriend(EntityPlayerMP owner, String friendName) {
        BotFriendPlayer friend = activeFriends.get(friendName.toLowerCase(Locale.ROOT));
        FriendSavedData data = getSavedData();
        FriendRecord record = data.get(friendName);
        if (record == null) {
            return "No BotFriend named " + friendName + " exists.";
        }
        if (!record.getOwnerId().equals(owner.getUniqueID())) {
            return friendName + " does not belong to you.";
        }
        if (friend != null) {
            despawn(friend, true);
        }
        data.remove(friendName);
        return "Removed BotFriend " + record.getFriendName() + ".";
    }

    public List<String> listOwnedFriendNames(UUID ownerId) {
        if (ownerId == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (FriendRecord record : getSavedData().getAll()) {
            if (ownerId.equals(record.getOwnerId())) {
                result.add(record.getFriendName());
            }
        }
        return result;
    }

    public String sendInstruction(EntityPlayerMP owner, String friendName, String message, boolean direct) {
        BotFriendPlayer friend = getOwnedFriend(owner, friendName);
        if (friend == null) {
            return "You do not own a BotFriend named " + friendName + ".";
        }
        return brainService.enqueue(friend, owner.getName(), message, direct);
    }

    public String stopFriend(EntityPlayerMP owner, String friendName) {
        BotFriendPlayer friend = getOwnedFriend(owner, friendName);
        if (friend == null) {
            return "You do not own a BotFriend named " + friendName + ".";
        }
        friend.stopActions();
        return friend.getFriendName() + " stopped.";
    }

    public void handleAddressedChat(EntityPlayerMP speaker, String message) {
        if (speaker == null || message == null) {
            return;
        }
        String trimmed = message.trim();
        String prefix = BotFriendConfig.data.chat.publicPrefix == null ? "@" : BotFriendConfig.data.chat.publicPrefix;
        if (!trimmed.startsWith(prefix) || trimmed.length() <= prefix.length()) {
            return;
        }
        int space = trimmed.indexOf(' ');
        if (space <= prefix.length()) {
            return;
        }
        String friendName = trimmed.substring(prefix.length(), space).trim();
        String instruction = trimmed.substring(space + 1).trim();
        if (instruction.isEmpty()) {
            return;
        }
        BotFriendPlayer friend = getOwnedFriend(speaker, friendName);
        if (friend == null) {
            return;
        }
        brainService.enqueue(friend, speaker.getName(), instruction, false);
    }

    public void tick() {
        tickCounter++;
        BrainResult result;
        while ((result = brainService.pollCompleted()) != null) {
            BotFriendPlayer friend = activeFriends.get(result.friendName == null ? "" : result.friendName.toLowerCase(Locale.ROOT));
            if (friend == null) {
                continue;
            }
            if (result.error != null && !result.error.trim().isEmpty()) {
                friend.tellOwner(result.error);
                continue;
            }
            actionExecutor.apply(friend, result.plan);
        }

        for (BotFriendPlayer friend : new ArrayList<>(activeFriends.values())) {
            if (friend.isDead || friend.world == null) {
                continue;
            }
            friend.tickFriend();
            syncVisibility(friend);
            if (tickCounter % 40L == 0L && BotFriendConfig.profile.enableSelfPrompt && "guard".equalsIgnoreCase(friend.getRecord().getActiveMode())) {
                brainService.queueSelfPrompt(friend);
            }
            if (tickCounter % 20L == 0L) {
                getSavedData().put(friend.getRecord());
            }
        }
    }

    public void onPlayerLogin(EntityPlayerMP player) {
        for (BotFriendPlayer friend : activeFriends.values()) {
            syncVisibilityForViewer(friend, player);
        }
    }

    public void onPlayerLogout(EntityPlayerMP player) {
        for (Set<UUID> viewers : visibleViewers.values()) {
            viewers.remove(player.getUniqueID());
        }
    }

    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof BotFriendPlayer)) {
            return;
        }
        BotFriendPlayer friend = (BotFriendPlayer) event.getEntityLiving();
        Entity source = event.getSource().getTrueSource();
        if (source instanceof EntityLivingBase) {
            friend.attackNearest(source.getName());
        }
    }

    public EntityPlayerMP getOwner(UUID ownerId) {
        if (server == null || ownerId == null) {
            return null;
        }
        return server.getPlayerList().getPlayerByUUID(ownerId);
    }

    public Vec3d computeSpawnInFront(EntityPlayerMP owner) {
        Vec3d look = owner.getLookVec();
        double dx = look.x;
        double dz = look.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001D) {
            dx = 0.0D;
            dz = 1.0D;
            horizontal = 1.0D;
        }
        dx /= horizontal;
        dz /= horizontal;
        double x = owner.posX + dx * 2.0D;
        double z = owner.posZ + dz * 2.0D;
        double y = owner.posY;
        return new Vec3d(x, y, z);
    }

    public void broadcastFriendChat(BotFriendPlayer friend, String message) {
        String prefix = "<" + friend.getFriendName() + "> ";
        TextComponentString component = new TextComponentString(prefix + message);
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension == friend.dimension) {
                player.sendMessage(component);
            }
        }
    }

    public Entity findEntityByUuid(BotFriendPlayer friend, UUID uuid) {
        if (uuid == null || friend == null || friend.world == null) {
            return null;
        }
        for (Entity entity : friend.world.loadedEntityList) {
            if (uuid.equals(entity.getUniqueID())) {
                return entity;
            }
        }
        return null;
    }

    private BotFriendPlayer getOwnedFriend(EntityPlayerMP owner, String friendName) {
        if (owner == null || friendName == null) {
            return null;
        }
        BotFriendPlayer friend = activeFriends.get(friendName.toLowerCase(Locale.ROOT));
        if (friend == null) {
            return null;
        }
        return owner.getUniqueID().equals(friend.getRecord().getOwnerId()) ? friend : null;
    }

    private BotFriendPlayer spawnFromRecord(FriendRecord record, boolean greetOwner) {
        if (server == null || record == null) {
            return null;
        }
        WorldServer world = server.getWorld(record.getDimension());
        if (world == null) {
            world = server.getWorld(0);
            record.setDimension(world.provider.getDimension());
        }
        GameProfile profile = new GameProfile(record.getFriendId(), record.getFriendName());
        BotFriendPlayer friend = new BotFriendPlayer(world, profile, this, record);
        if (greetOwner) {
            EntityPlayerMP owner = getOwner(record.getOwnerId());
            if (owner != null) {
                friend.initializeSpawn(computeSpawnInFront(owner), owner);
            } else {
                friend.restorePosition();
            }
        } else {
            friend.restorePosition();
        }
        activeFriends.put(record.getFriendName().toLowerCase(Locale.ROOT), friend);
        visibleViewers.put(record.getFriendName().toLowerCase(Locale.ROOT), new HashSet<UUID>());
        try {
            world.spawnEntity(friend);
        } catch (Exception ex) {
            logger.error("Failed to spawn BotFriend {}: {}", record.getFriendName(), ex.getMessage());
            activeFriends.remove(record.getFriendName().toLowerCase(Locale.ROOT));
            visibleViewers.remove(record.getFriendName().toLowerCase(Locale.ROOT));
            return null;
        }
        friend.snapshot();
        getSavedData().put(record);
        return friend;
    }

    private void despawn(BotFriendPlayer friend, boolean removePackets) {
        if (friend == null) {
            return;
        }
        if (removePackets) {
            destroyForAllViewers(friend);
        }
        activeFriends.remove(friend.getFriendName().toLowerCase(Locale.ROOT));
        visibleViewers.remove(friend.getFriendName().toLowerCase(Locale.ROOT));
        friend.setDead();
        if (friend.world != null) {
            friend.world.removeEntity(friend);
        }
    }

    private void syncVisibility(BotFriendPlayer friend) {
        if (server == null || friend == null) {
            return;
        }
        for (EntityPlayerMP viewer : server.getPlayerList().getPlayers()) {
            syncVisibilityForViewer(friend, viewer);
        }
    }

    private void syncVisibilityForViewer(BotFriendPlayer friend, EntityPlayerMP viewer) {
        if (viewer == null || friend == null || viewer.getUniqueID().equals(friend.getRecord().getFriendId())) {
            return;
        }
        Set<UUID> viewers = visibleViewers.get(friend.getFriendName().toLowerCase(Locale.ROOT));
        if (viewers == null) {
            return;
        }
        boolean shouldBeVisible = friend.shouldStayVisibleTo(viewer);
        boolean isVisible = viewers.contains(viewer.getUniqueID());
        if (shouldBeVisible && !isVisible) {
            sendSpawnPackets(viewer, friend);
            viewers.add(viewer.getUniqueID());
            return;
        }
        if (!shouldBeVisible && isVisible) {
            sendDestroyPackets(viewer, friend);
            viewers.remove(viewer.getUniqueID());
            return;
        }
        if (shouldBeVisible) {
            sendUpdatePackets(viewer, friend);
        }
    }

    private void sendSpawnPackets(EntityPlayerMP viewer, BotFriendPlayer friend) {
        viewer.connection.sendPacket(new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, friend));
        viewer.connection.sendPacket(new SPacketSpawnPlayer(friend));
        viewer.connection.sendPacket(new SPacketEntityMetadata(friend.getEntityId(), friend.getDataManager(), true));
        sendEquipmentPackets(viewer, friend);
        sendUpdatePackets(viewer, friend);
    }

    private void sendUpdatePackets(EntityPlayerMP viewer, BotFriendPlayer friend) {
        viewer.connection.sendPacket(new SPacketEntityTeleport(friend));
        byte headYaw = (byte) MathHelper.floor(friend.rotationYawHead * 256.0F / 360.0F);
        viewer.connection.sendPacket(new SPacketEntityHeadLook(friend, headYaw));
        viewer.connection.sendPacket(new SPacketEntityMetadata(friend.getEntityId(), friend.getDataManager(), false));
        if (tickCounter % 10L == 0L) {
            sendEquipmentPackets(viewer, friend);
        }
    }

    private void sendEquipmentPackets(EntityPlayerMP viewer, BotFriendPlayer friend) {
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.MAINHAND, friend.getHeldItemMainhand().copy()));
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.OFFHAND, friend.getHeldItemOffhand().copy()));
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.HEAD, friend.getItemStackFromSlot(EntityEquipmentSlot.HEAD).copy()));
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.CHEST, friend.getItemStackFromSlot(EntityEquipmentSlot.CHEST).copy()));
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.LEGS, friend.getItemStackFromSlot(EntityEquipmentSlot.LEGS).copy()));
        viewer.connection.sendPacket(new SPacketEntityEquipment(friend.getEntityId(), EntityEquipmentSlot.FEET, friend.getItemStackFromSlot(EntityEquipmentSlot.FEET).copy()));
    }

    private void sendDestroyPackets(EntityPlayerMP viewer, BotFriendPlayer friend) {
        viewer.connection.sendPacket(new SPacketDestroyEntities(friend.getEntityId()));
        viewer.connection.sendPacket(new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, friend));
    }

    private void destroyForAllViewers(BotFriendPlayer friend) {
        if (server == null || friend == null) {
            return;
        }
        for (EntityPlayerMP viewer : server.getPlayerList().getPlayers()) {
            sendDestroyPackets(viewer, friend);
        }
    }

    private FriendSavedData getSavedData() {
        WorldServer world = server.getWorld(0);
        return FriendSavedData.get(world);
    }

    private static String normalizeName(String requestedName) {
        if (requestedName == null) {
            return null;
        }
        String trimmed = requestedName.trim();
        if (!trimmed.matches("^[A-Za-z0-9_]{3,16}$")) {
            return null;
        }
        return trimmed;
    }
}
