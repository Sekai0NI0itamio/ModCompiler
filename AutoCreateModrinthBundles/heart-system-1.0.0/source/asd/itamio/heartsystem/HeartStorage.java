package asd.itamio.heartsystem;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory cache of heart counts, backed by per-player NBT files.
 * Files are written to the player data directory with the suffix ".heartsystem.dat".
 */
public class HeartStorage {

    private static final HeartStorage INSTANCE = new HeartStorage();

    public static HeartStorage get() {
        return INSTANCE;
    }

    // UUID -> heart count (in-memory cache)
    private final Map<UUID, Integer> cache = new HashMap<UUID, Integer>();

    private HeartStorage() {}

    // -----------------------------------------------------------------------
    // Load / save via PlayerEvent file hooks
    // -----------------------------------------------------------------------

    /**
     * Load heart data from the player's NBT file.
     * Called from PlayerEvent.LoadFromFile.
     *
     * @param playerUUID  the player's UUID string
     * @param playerFile  the recommended file (from event.getPlayerFile("heartsystem"))
     * @return the loaded heart count, or -1 if the file does not exist (new player)
     */
    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.read(playerFile);
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, h);
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load heart data for {}: {}", playerUUID, e.getMessage());
            }
        }
        // New player — not yet in cache
        return -1;
    }

    /**
     * Save heart data to the player's NBT file.
     * Called from PlayerEvent.SaveToFile.
     */
    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.write(data.toNBT(), playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save heart data for {}: {}", playerUUID, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // In-memory access
    // -----------------------------------------------------------------------

    public boolean has(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public int getHearts(UUID uuid) {
        Integer h = cache.get(uuid);
        return h != null ? h : -1;
    }

    public void setHearts(UUID uuid, int hearts) {
        cache.put(uuid, hearts);
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }
}
