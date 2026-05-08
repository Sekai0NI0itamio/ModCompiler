package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

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

    public void loadOrInit(ServerPlayer player, int defaultHearts) {
        UUID uuid = player.getUUID();
        File file = getFile(player);
        if (file.exists()) {
            try {
                CompoundTag tag = NbtIo.read(file.toPath());
                if (tag != null && tag.contains("hearts")) {
                    cache.put(uuid, tag.getInt("hearts"));
                    return;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        cache.put(uuid, defaultHearts);
    }

    public void save(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int hearts = getHearts(uuid);
        if (hearts < 0) return;
        File file = getFile(player);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, file.toPath());
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    private File getFile(ServerPlayer player) {
        File worldDir = player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        return new File(worldDir, player.getStringUUID() + ".heartsystem.dat");
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void copyHearts(UUID from, UUID to) { Integer h = cache.get(from); if (h != null) cache.put(to, h); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
