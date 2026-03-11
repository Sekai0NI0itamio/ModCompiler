package com.itamio.servercore.forge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

public final class ServerCoreData extends WorldSavedData {
    public static final String DATA_NAME = "servercore";

    private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
    private final Set<UUID> seenPlayers = new LinkedHashSet<>();

    public ServerCoreData() {
        super(DATA_NAME);
    }

    public ServerCoreData(String name) {
        super(name);
    }

    public static ServerCoreData get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
        DimensionSavedDataManager storage = overworld.getSavedData();
        return storage.getOrCreate(ServerCoreData::new, DATA_NAME);
    }

    @Override
    public void read(CompoundNBT nbt) {
        homesByPlayer.clear();
        seenPlayers.clear();

        ListNBT players = nbt.getList("players", 10);
        for (int i = 0; i < players.size(); i++) {
            CompoundNBT playerTag = players.getCompound(i);
            UUID uuid = parseUuid(playerTag.getString("uuid"));
            if (uuid == null) {
                continue;
            }
            ListNBT homesList = playerTag.getList("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();
            for (int j = 0; j < homesList.size(); j++) {
                CompoundNBT homeTag = homesList.getCompound(j);
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

        ListNBT seenList = nbt.getList("seen", 8);
        for (int i = 0; i < seenList.size(); i++) {
            UUID uuid = parseUuid(seenList.getString(i));
            if (uuid != null) {
                seenPlayers.add(uuid);
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        ListNBT players = new ListNBT();
        for (Map.Entry<UUID, Map<String, HomeRecord>> entry : homesByPlayer.entrySet()) {
            CompoundNBT playerTag = new CompoundNBT();
            playerTag.putString("uuid", entry.getKey().toString());
            ListNBT homesList = new ListNBT();
            for (HomeRecord record : entry.getValue().values()) {
                CompoundNBT homeTag = new CompoundNBT();
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
        nbt.put("players", players);

        ListNBT seenList = new ListNBT();
        for (UUID uuid : seenPlayers) {
            seenList.add(StringNBT.valueOf(uuid.toString()));
        }
        nbt.put("seen", seenList);
        return nbt;
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
