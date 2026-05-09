/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompressedStreamTools
 *  net.minecraft.nbt.NBTTagCompound
 */
package asd.itamio.heartsystem;

import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartSystemMod;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    private final Map<UUID, Integer> cache = new HashMap<UUID, Integer>();

    public static HeartStorage get() {
        return INSTANCE;
    }

    private HeartStorage() {
    }

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.func_74797_a((File)playerFile);
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                this.cache.put(uuid, h);
                return h;
            }
            catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load heart data for {}: {}", (Object)playerUUID, (Object)e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        this.cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.func_74795_b((NBTTagCompound)data.toNBT(), (File)playerFile);
        }
        catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save heart data for {}: {}", (Object)playerUUID, (Object)e.getMessage());
        }
    }

    public boolean has(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

    public int getHearts(UUID uuid) {
        Integer h = this.cache.get(uuid);
        return h != null ? h : -1;
    }

    public void setHearts(UUID uuid, int hearts) {
        this.cache.put(uuid, hearts);
    }

    public void remove(UUID uuid) {
        this.cache.remove(uuid);
    }
}

