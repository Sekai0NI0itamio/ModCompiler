package com.bothelpers.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class NamedLocationsData extends WorldSavedData {
    public static final String DATA_NAME = "BotHelpersNamedLocations";
    
    public static class NamedLocation {
        public String name;
        public BlockPos pos;
        public String blockType;
        public NamedLocation(String name, BlockPos pos, String blockType) {
            this.name = name;
            this.pos = pos;
            this.blockType = blockType;
        }
    }
    
    public final List<NamedLocation> locations = new ArrayList<>();

    public NamedLocationsData(String name) {
        super(name);
    }
    
    public NamedLocationsData() {
        super(DATA_NAME);
    }
    
    public static NamedLocationsData get(World world) {
        if (world.getMapStorage() == null) return null;
        NamedLocationsData instance = (NamedLocationsData) world.getMapStorage().getOrLoadData(NamedLocationsData.class, DATA_NAME);
        if (instance == null) {
            instance = new NamedLocationsData();
            world.getMapStorage().setData(DATA_NAME, instance);
        }
        return instance;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        locations.clear();
        NBTTagList list = nbt.getTagList("Locations", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound cmp = list.getCompoundTagAt(i);
            locations.add(new NamedLocation(
                cmp.getString("Name"),
                BlockPos.fromLong(cmp.getLong("Pos")),
                cmp.getString("BlockType")
            ));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (NamedLocation loc : locations) {
            NBTTagCompound cmp = new NBTTagCompound();
            cmp.setString("Name", loc.name);
            cmp.setLong("Pos", loc.pos.toLong());
            cmp.setString("BlockType", loc.blockType);
            list.appendTag(cmp);
        }
        compound.setTag("Locations", list);
        return compound;
    }
    
    public void addLocation(String name, BlockPos pos, String blockType) {
        locations.removeIf(l -> l.pos.equals(pos));
        locations.add(new NamedLocation(name, pos, blockType));
        this.markDirty();
    }
    
    public void removeLocation(String name) {
        if (locations.removeIf(l -> l.name.equals(name))) {
            this.markDirty();
        }
    }
}
