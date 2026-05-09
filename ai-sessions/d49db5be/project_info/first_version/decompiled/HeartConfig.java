/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.config.Configuration
 */
package asd.itamio.heartsystem;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public class HeartConfig {
    private static final String CAT = "general";
    private final Configuration config;
    private int startHearts;
    private int maxHearts;
    private int minHearts;

    public HeartConfig(File configFile) {
        this.config = new Configuration(configFile);
        this.load();
    }

    private void load() {
        this.config.load();
        this.startHearts = this.config.getInt("startHearts", CAT, 10, 1, 100, "Number of hearts a new player starts with. (1 heart = 2 HP)");
        this.maxHearts = this.config.getInt("maxHearts", CAT, 20, 1, 100, "Maximum hearts a player can have. Kills cannot push hearts above this.");
        this.minHearts = this.config.getInt("minHearts", CAT, 0, 0, 99, "Minimum hearts before permadeath triggers. 0 means ban on reaching 0 hearts.");
        if (this.config.hasChanged()) {
            this.config.save();
        }
    }

    public int getStartHearts() {
        return this.startHearts;
    }

    public int getMaxHearts() {
        return this.maxHearts;
    }

    public int getMinHearts() {
        return this.minHearts;
    }
}

