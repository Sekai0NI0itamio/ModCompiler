package asd.itamio.autoeat;

import net.minecraftforge.common.config.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AutoEatConfig {
    private final Configuration config;
    
    public int hungerThreshold;
    public Set<String> blacklistedFoods;
    
    public AutoEatConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        // Hunger threshold (0-20, where 20 is full)
        hungerThreshold = config.getInt(
            "hungerThreshold",
            Configuration.CATEGORY_GENERAL,
            14,
            0,
            20,
            "Hunger level at which to auto-eat (0-20, where 20 is full). Default: 14"
        );
        
        // Blacklisted foods
        String[] defaultBlacklist = {
            "minecraft:spider_eye",
            "minecraft:rotten_flesh",
            "minecraft:poisonous_potato",
            "minecraft:golden_apple",
            "minecraft:chorus_fruit"
        };
        
        String[] blacklistArray = config.getStringList(
            "blacklistedFoods",
            Configuration.CATEGORY_GENERAL,
            defaultBlacklist,
            "Foods that will never be auto-eaten. Format: modid:itemname\n" +
            "Default blacklist includes: spider_eye, rotten_flesh, poisonous_potato, golden_apple (both types), chorus_fruit"
        );
        
        blacklistedFoods = new HashSet<>(Arrays.asList(blacklistArray));
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
