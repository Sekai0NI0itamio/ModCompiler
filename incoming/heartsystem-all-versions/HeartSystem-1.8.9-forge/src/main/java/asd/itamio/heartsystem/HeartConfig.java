package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class HeartConfig {
    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private final Configuration config;
    private int startHearts, maxHearts, minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        config.load();
        startHearts = config.getInt("startHearts", CAT, 10, 1, 100, "Hearts a new player starts with.");
        maxHearts   = config.getInt("maxHearts",   CAT, 20, 1, 100, "Maximum hearts a player can have.");
        minHearts   = config.getInt("minHearts",   CAT,  0, 0,  99, "Minimum hearts before permadeath.");
        if (config.hasChanged()) config.save();
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
