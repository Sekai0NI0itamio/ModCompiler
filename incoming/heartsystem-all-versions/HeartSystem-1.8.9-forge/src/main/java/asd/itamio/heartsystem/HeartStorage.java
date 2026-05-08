package asd.itamio.heartsystem;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map cache = new HashMap();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.read(playerFile);
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, Integer.valueOf(h));
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load heart data for {}: {}", playerUUID, e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, Integer.valueOf(hearts));
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.write(data.toNBT(), playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save heart data for {}: {}", playerUUID, e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }

    public int getHearts(UUID uuid) {
        Integer h = (Integer) cache.get(uuid);
        return h != null ? h.intValue() : -1;
    }

    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, Integer.valueOf(hearts)); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
