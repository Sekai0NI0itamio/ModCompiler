package io.itamio.aipoweredcompanionship;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

public final class CompanionSavedData extends WorldSavedData {
    private static final String DATA_NAME = "aipoweredcompanionship";

    public CompanionSavedData() {
        super(DATA_NAME);
    }

    public CompanionSavedData(String name) {
        super(name);
    }

    public static CompanionSavedData get(World world) {
        CompanionSavedData data = (CompanionSavedData) world.loadData(CompanionSavedData.class, DATA_NAME);
        if (data == null) {
            data = new CompanionSavedData();
            world.setData(DATA_NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {}

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return nbt;
    }
}
