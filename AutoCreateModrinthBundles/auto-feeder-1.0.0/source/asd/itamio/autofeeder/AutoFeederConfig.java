package asd.itamio.autofeeder;

import net.minecraftforge.common.config.Configuration;

public class AutoFeederConfig {
    private final Configuration config;
    
    public boolean enableAutoFeeder;
    public int searchRange;
    public int feedingInterval;
    public boolean feedCows;
    public boolean feedPigs;
    public boolean feedSheep;
    public boolean feedChickens;
    public boolean feedHorses;
    public boolean feedRabbits;
    public boolean playSound;
    public boolean showParticles;
    
    public AutoFeederConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        enableAutoFeeder = config.getBoolean(
            "enableAutoFeeder",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable automatic animal feeding. Default: true"
        );
        
        searchRange = config.getInt(
            "searchRange",
            Configuration.CATEGORY_GENERAL,
            8,
            1,
            32,
            "Range in blocks to search for animals and containers. Default: 8"
        );
        
        feedingInterval = config.getInt(
            "feedingInterval",
            Configuration.CATEGORY_GENERAL,
            100,
            20,
            1200,
            "Ticks between feeding attempts (20 ticks = 1 second). Default: 100 (5 seconds)"
        );
        
        feedCows = config.getBoolean(
            "feedCows",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed cows. Default: true"
        );
        
        feedPigs = config.getBoolean(
            "feedPigs",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed pigs. Default: true"
        );
        
        feedSheep = config.getBoolean(
            "feedSheep",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed sheep. Default: true"
        );
        
        feedChickens = config.getBoolean(
            "feedChickens",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed chickens. Default: true"
        );
        
        feedHorses = config.getBoolean(
            "feedHorses",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed horses. Default: true"
        );
        
        feedRabbits = config.getBoolean(
            "feedRabbits",
            Configuration.CATEGORY_GENERAL,
            true,
            "Automatically feed rabbits. Default: true"
        );
        
        playSound = config.getBoolean(
            "playSound",
            Configuration.CATEGORY_GENERAL,
            true,
            "Play sound when animals are fed. Default: true"
        );
        
        showParticles = config.getBoolean(
            "showParticles",
            Configuration.CATEGORY_GENERAL,
            true,
            "Show heart particles when animals are fed. Default: true"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
