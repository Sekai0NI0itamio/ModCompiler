package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

/**
 * Configuration for the Heart System mod.
 * All values are in "hearts" (1 heart = 2 HP).
 */
public class HeartConfig {

    private static final String CAT = Configuration.CATEGORY_GENERAL;

    private final Configuration config;

    private int startHearts;
    private int maxHearts;
    private int minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private void load() {
        config.load();

        startHearts = config.getInt(
            "startHearts", CAT, 10, 1, 100,
            "Number of hearts a new player starts with. (1 heart = 2 HP)"
        );
        maxHearts = config.getInt(
            "maxHearts", CAT, 20, 1, 100,
            "Maximum hearts a player can have. Kills cannot push hearts above this."
        );
        minHearts = config.getInt(
            "minHearts", CAT, 0, 0, 99,
            "Minimum hearts before permadeath triggers. 0 means ban on reaching 0 hearts."
        );

        if (config.hasChanged()) {
            config.save();
        }
    }

    public int getStartHearts()  { return startHearts; }
    public int getMaxHearts()    { return maxHearts; }
    public int getMinHearts()    { return minHearts; }
}
