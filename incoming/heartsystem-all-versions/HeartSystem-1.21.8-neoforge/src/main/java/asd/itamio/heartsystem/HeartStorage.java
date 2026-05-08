package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

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
                CompoundTag tag = NbtIo.read(playerFile);
                if (tag == null) return -1;
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, h);
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            NbtIo.write(data.toNBT(), playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
