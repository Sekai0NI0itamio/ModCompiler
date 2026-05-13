package asd.itamio.lifestealparrotmod;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    private final Map<UUID, Integer> cache = new HashMap<>();

    public static HeartStorage get() {
        return INSTANCE;
    }

    private HeartStorage() {
    }

    public void loadOrInit(ServerPlayer player, int defaultHearts) {
        UUID uuid = player.getUUID();
        File file = this.getFile(player);
        if (file.exists()) {
            try {
                CompoundTag tag = NbtIo.read(file.toPath());
                if (tag != null && tag.contains("hearts")) {
                    this.cache.put(uuid, tag.getInt("hearts"));
                    return;
                }
            } catch (IOException e) {
                LifestealparrotmodMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }

        this.cache.put(uuid, defaultHearts);
    }

    public void save(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int hearts = this.getHearts(uuid);
        if (hearts >= 0) {
            File file = this.getFile(player);
            CompoundTag tag = new CompoundTag();
            tag.putInt("hearts", hearts);

            try {
                NbtIo.write(tag, file.toPath());
            } catch (IOException e) {
                LifestealparrotmodMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
            }
        }
    }

    private File getFile(ServerPlayer player) {
        File worldDir = player.getServer().getWorldPath(LevelResource.PLAYERS_DIR).toFile();
        return new File(worldDir, player.getUUID() + ".heartsystem.dat");
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

    public void copyHearts(UUID from, UUID to) {
        Integer h = this.cache.get(from);
        if (h != null) {
            this.cache.put(to, h);
        }
    }

    public void remove(UUID uuid) {
        this.cache.remove(uuid);
    }
}