package com.itamio.servercore.fabric;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

public final class ServerCoreData extends PersistentState {
    public static final String DATA_NAME = "servercore";

    private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
    private final Set<UUID> seenPlayers = new LinkedHashSet<>();

    public static ServerCoreData get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(ServerCoreData::fromNbt, ServerCoreData::new, DATA_NAME);
    }

    public static ServerCoreData fromNbt(NbtCompound tag) {
        ServerCoreData data = new ServerCoreData();
        data.read(tag);
        return data;
    }

    private void read(NbtCompound tag) {
        homesByPlayer.clear();
        seenPlayers.clear();

        NbtList players = tag.getList("players", 10);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound playerTag = players.getCompound(i);
            UUID uuid = parseUuid(playerTag.getString("uuid"));
            if (uuid == null) {
                continue;
            }
            NbtList homesList = playerTag.getList("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();
            for (int j = 0; j < homesList.size(); j++) {
                NbtCompound homeTag = homesList.getCompound(j);
                String key = homeTag.getString("key");
                String name = homeTag.getString("name");
                String dimension = homeTag.getString("dimension");
                if (key == null || key.isEmpty() || name == null || name.isEmpty()) {
                    continue;
                }
                HomeRecord record = new HomeRecord(
                        key,
                        name,
                        dimension,
                        homeTag.getDouble("x"),
                        homeTag.getDouble("y"),
                        homeTag.getDouble("z"),
                        homeTag.getFloat("yaw"),
                        homeTag.getFloat("pitch")
                );
                homes.put(key, record);
            }
            homesByPlayer.put(uuid, homes);
        }

        NbtList seenList = tag.getList("seen", 8);
        for (int i = 0; i < seenList.size(); i++) {
            UUID uuid = parseUuid(seenList.getString(i));
            if (uuid != null) {
                seenPlayers.add(uuid);
            }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList players = new NbtList();
        for (Map.Entry<UUID, Map<String, HomeRecord>> entry : homesByPlayer.entrySet()) {
            NbtCompound playerTag = new NbtCompound();
            playerTag.putString("uuid", entry.getKey().toString());
            NbtList homesList = new NbtList();
            for (HomeRecord record : entry.getValue().values()) {
                NbtCompound homeTag = new NbtCompound();
                homeTag.putString("key", record.getKey());
                homeTag.putString("name", record.getName());
                homeTag.putString("dimension", record.getDimension());
                homeTag.putDouble("x", record.getX());
                homeTag.putDouble("y", record.getY());
                homeTag.putDouble("z", record.getZ());
                homeTag.putFloat("yaw", record.getYaw());
                homeTag.putFloat("pitch", record.getPitch());
                homesList.add(homeTag);
            }
            playerTag.put("homes", homesList);
            players.add(playerTag);
        }
        tag.put("players", players);

        NbtList seenList = new NbtList();
        for (UUID uuid : seenPlayers) {
            seenList.add(NbtString.of(uuid.toString()));
        }
        tag.put("seen", seenList);
        return tag;
    }

    public Collection<HomeRecord> listHomes(UUID playerUuid) {
        return getHomes(playerUuid).values();
    }

    public HomeRecord getHome(UUID playerUuid, String key) {
        return getHomes(playerUuid).get(key);
    }

    public void putHome(UUID playerUuid, HomeRecord record) {
        getHomes(playerUuid).put(record.getKey(), record);
        markDirty();
    }

    public HomeRecord removeHome(UUID playerUuid, String key) {
        HomeRecord removed = getHomes(playerUuid).remove(key);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public boolean hasSeen(UUID uuid) {
        return seenPlayers.contains(uuid);
    }

    public void markSeen(UUID uuid) {
        if (seenPlayers.add(uuid)) {
            markDirty();
        }
    }

    private Map<String, HomeRecord> getHomes(UUID playerUuid) {
        Map<String, HomeRecord> homes = homesByPlayer.get(playerUuid);
        if (homes == null) {
            homes = new LinkedHashMap<>();
            homesByPlayer.put(playerUuid, homes);
        }
        return homes;
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
