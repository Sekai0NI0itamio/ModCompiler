package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                CompoundNBT tag = CompressedStreamTools.read(playerFile);
                if (tag != null && tag.contains("hearts")) {
                    int h = tag.getInt("hearts");
                    cache.put(uuid, h);
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("hearts", hearts);
        try {
            CompressedStreamTools.write(tag, playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
