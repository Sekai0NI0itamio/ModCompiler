package com.itamio.servercore.fabric;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
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
        try {
            return getWithType(manager);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = manager.getClass().getMethod("getOrCreate", Function.class, Supplier.class, String.class);
            return (ServerCoreData) method.invoke(
                    manager,
                    (Function<NbtCompound, ServerCoreData>) ServerCoreData::fromNbt,
                    (Supplier<ServerCoreData>) ServerCoreData::new,
                    DATA_NAME
            );
        } catch (ReflectiveOperationException ignored) {
            return new ServerCoreData();
        }
    }

    private static ServerCoreData getWithType(PersistentStateManager manager) throws ReflectiveOperationException {
        Object type = createPersistentStateType();
        Method method = manager.getClass().getMethod("getOrCreate", type.getClass(), String.class);
        return (ServerCoreData) method.invoke(manager, type, DATA_NAME);
    }

    private static Object createPersistentStateType() throws ReflectiveOperationException {
        Class<?> typeClass = Class.forName("net.minecraft.world.PersistentState$Type");
        Object dataFixTypes = getDataFixTypes();
        Function<NbtCompound, ServerCoreData> loader = ServerCoreData::fromNbt;
        Supplier<ServerCoreData> supplier = ServerCoreData::new;
        for (Constructor<?> ctor : typeClass.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && isFunction(params[0]) && isSupplier(params[1])) {
                return ctor.newInstance(loader, supplier);
            }
            if (params.length == 2 && isSupplier(params[0]) && isFunction(params[1])) {
                return ctor.newInstance(supplier, loader);
            }
            if (params.length == 3 && dataFixTypes != null && params[2].isInstance(dataFixTypes)) {
                if (isFunction(params[0]) && isSupplier(params[1])) {
                    return ctor.newInstance(loader, supplier, dataFixTypes);
                }
                if (isSupplier(params[0]) && isFunction(params[1])) {
                    return ctor.newInstance(supplier, loader, dataFixTypes);
                }
            }
        }
        try {
            Method method = typeClass.getMethod("create", Function.class, Supplier.class, dataFixTypes == null ? Object.class : dataFixTypes.getClass());
            return method.invoke(null, loader, supplier, dataFixTypes);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = typeClass.getMethod("of", Function.class, Supplier.class, dataFixTypes == null ? Object.class : dataFixTypes.getClass());
            return method.invoke(null, loader, supplier, dataFixTypes);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = typeClass.getMethod("create", Function.class, Supplier.class);
            return method.invoke(null, loader, supplier);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = typeClass.getMethod("of", Function.class, Supplier.class);
            return method.invoke(null, loader, supplier);
        } catch (ReflectiveOperationException ignored) {
        }
        throw new ReflectiveOperationException("No compatible PersistentState.Type constructor");
    }

    private static Object getDataFixTypes() {
        try {
            Class<?> dataFixClass = Class.forName("net.minecraft.util.datafix.DataFixTypes");
            return dataFixClass.getField("SAVED_DATA").get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isFunction(Class<?> type) {
        return Function.class.isAssignableFrom(type);
    }

    private static boolean isSupplier(Class<?> type) {
        return Supplier.class.isAssignableFrom(type);
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
