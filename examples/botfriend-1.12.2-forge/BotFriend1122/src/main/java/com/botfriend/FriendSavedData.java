package com.botfriend;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class FriendSavedData extends WorldSavedData {
    public static final String DATA_NAME = "botfriend";

    private final Map<String, FriendRecord> byName = new LinkedHashMap<>();

    public FriendSavedData() {
        super(DATA_NAME);
    }

    public FriendSavedData(String name) {
        super(name);
    }

    public static FriendSavedData get(WorldServer world) {
        MapStorage storage = world.getMapStorage();
        FriendSavedData data = (FriendSavedData) storage.getOrLoadData(FriendSavedData.class, DATA_NAME);
        if (data == null) {
            data = new FriendSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        byName.clear();
        NBTTagList list = nbt.getTagList("friends", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            FriendRecord record = FriendRecord.fromTag(list.getCompoundTagAt(i));
            byName.put(record.getFriendName().toLowerCase(), record);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (FriendRecord record : byName.values()) {
            list.appendTag(record.toTag());
        }
        compound.setTag("friends", list);
        return compound;
    }

    public Collection<FriendRecord> getAll() {
        return byName.values();
    }

    public FriendRecord get(String friendName) {
        if (friendName == null) {
            return null;
        }
        return byName.get(friendName.toLowerCase());
    }

    public boolean contains(String friendName) {
        return get(friendName) != null;
    }

    public void put(FriendRecord record) {
        byName.put(record.getFriendName().toLowerCase(), record);
        markDirty();
    }

    public FriendRecord remove(String friendName) {
        FriendRecord removed = byName.remove(friendName.toLowerCase());
        if (removed != null) {
            markDirty();
        }
        return removed;
    }
}
