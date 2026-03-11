package com.itamio.servercore.fabric;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ServerCoreData extends SavedData {
    public static final String DATA_NAME = "servercore";

    private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
    private final Set<UUID> seenPlayers = new LinkedHashSet<>();

    public static ServerCoreData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ServerCoreData::load, ServerCoreData::new, DATA_NAME);
    }

    public static ServerCoreData load(CompoundTag tag) {
        ServerCoreData data = new ServerCoreData();
        data.read(tag);
        return data;
    }

    private void read(CompoundTag tag) {
        homesByPlayer.clear();
        seenPlayers.clear();

        ListTag players = tag.getList("players", 10);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            UUID uuid = parseUuid(playerTag.getString("uuid"));
            if (uuid == null) {
                continue;
            }
            ListTag homesList = playerTag.getList("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();
            for (int j = 0; j < homesList.size(); j++) {
                CompoundTag homeTag = homesList.getCompound(j);
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

        ListTag seenList = tag.getList("seen", 8);
        for (int i = 0; i < seenList.size(); i++) {
            String uuidText = seenList.getString(i);
            UUID uuid = parseUuid(uuidText);
            if (uuid != null) {
                seenPlayers.add(uuid);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, HomeRecord>> entry : homesByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("uuid", entry.getKey().toString());
            ListTag homesList = new ListTag();
            for (HomeRecord record : entry.getValue().values()) {
                CompoundTag homeTag = new CompoundTag();
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

        ListTag seenList = new ListTag();
        for (UUID uuid : seenPlayers) {
            seenList.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString()));
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
        setDirty();
    }

    public HomeRecord removeHome(UUID playerUuid, String key) {
        HomeRecord removed = getHomes(playerUuid).remove(key);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public boolean hasSeen(UUID uuid) {
        return seenPlayers.contains(uuid);
    }

    public void markSeen(UUID uuid) {
        if (seenPlayers.add(uuid)) {
            setDirty();
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
