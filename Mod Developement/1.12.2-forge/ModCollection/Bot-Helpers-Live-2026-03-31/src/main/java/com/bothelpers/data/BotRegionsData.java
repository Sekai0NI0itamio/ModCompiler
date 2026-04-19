package com.bothelpers.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class BotRegionsData extends WorldSavedData {
    public static final String DATA_NAME = "BotHelpersRegions";

    public static class Region {
        public String name;
        public BlockPos signPos;
        public BlockPos firstPos;
        public BlockPos secondPos;

        public Region(String name, BlockPos signPos, BlockPos firstPos, BlockPos secondPos) {
            this.name = name;
            this.signPos = signPos;
            this.firstPos = firstPos;
            this.secondPos = secondPos;
        }

        public BlockPos getMinPos() {
            return new BlockPos(
                Math.min(firstPos.getX(), secondPos.getX()),
                Math.min(firstPos.getY(), secondPos.getY()),
                Math.min(firstPos.getZ(), secondPos.getZ())
            );
        }

        public BlockPos getMaxPos() {
            return new BlockPos(
                Math.max(firstPos.getX(), secondPos.getX()),
                Math.max(firstPos.getY(), secondPos.getY()),
                Math.max(firstPos.getZ(), secondPos.getZ())
            );
        }

        public boolean contains(BlockPos pos) {
            BlockPos min = getMinPos();
            BlockPos max = getMaxPos();
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    public final List<Region> regions = new ArrayList<>();

    public BotRegionsData(String name) {
        super(name);
    }

    public BotRegionsData() {
        super(DATA_NAME);
    }

    public static BotRegionsData get(World world) {
        if (world == null || world.getMapStorage() == null) {
            return null;
        }

        BotRegionsData instance = (BotRegionsData) world.getMapStorage().getOrLoadData(BotRegionsData.class, DATA_NAME);
        if (instance == null) {
            instance = new BotRegionsData();
            world.getMapStorage().setData(DATA_NAME, instance);
        }
        return instance;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        regions.clear();
        NBTTagList list = nbt.getTagList("Regions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound cmp = list.getCompoundTagAt(i);
            regions.add(new Region(
                cmp.getString("Name"),
                BlockPos.fromLong(cmp.getLong("SignPos")),
                BlockPos.fromLong(cmp.getLong("FirstPos")),
                BlockPos.fromLong(cmp.getLong("SecondPos"))
            ));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Region region : regions) {
            NBTTagCompound cmp = new NBTTagCompound();
            cmp.setString("Name", region.name);
            cmp.setLong("SignPos", region.signPos.toLong());
            cmp.setLong("FirstPos", region.firstPos.toLong());
            cmp.setLong("SecondPos", region.secondPos.toLong());
            list.appendTag(cmp);
        }
        compound.setTag("Regions", list);
        return compound;
    }

    public Region getRegion(String name) {
        if (name == null) {
            return null;
        }

        for (Region region : regions) {
            if (region.name != null && region.name.equalsIgnoreCase(name.trim())) {
                return region;
            }
        }
        return null;
    }

    public Region getRegionBySign(BlockPos signPos) {
        if (signPos == null) {
            return null;
        }

        for (Region region : regions) {
            if (region.signPos != null && region.signPos.equals(signPos)) {
                return region;
            }
        }
        return null;
    }

    public void addOrUpdateRegion(String name, BlockPos signPos, BlockPos firstPos, BlockPos secondPos) {
        Region region = getRegionBySign(signPos);
        if (region == null) {
            region = getRegion(name);
        }

        if (region == null) {
            regions.add(new Region(name, signPos, firstPos, secondPos));
        } else {
            region.name = name;
            region.signPos = signPos;
            region.firstPos = firstPos;
            region.secondPos = secondPos;
        }

        this.markDirty();
    }

    public void removeRegionBySign(BlockPos signPos) {
        if (signPos == null) {
            return;
        }

        for (int i = regions.size() - 1; i >= 0; i--) {
            Region region = regions.get(i);
            if (region.signPos != null && region.signPos.equals(signPos)) {
                regions.remove(i);
                this.markDirty();
                return;
            }
        }
    }
}
