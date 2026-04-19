package asd.itamio.cropgrowth;

import net.minecraftforge.common.config.Configuration;

public class CropGrowthConfig {
    private final Configuration config;
    
    public int radius;
    public int growthSpeedMultiplier;
    public int sleepGrowthBonus;
    public boolean enableWhilePlayerNearby;
    public boolean enableWhileSleeping;
    public boolean enableWeatherEffects;
    public int rainGrowthBonus;
    public int thunderGrowthBonus;
    public float snowGrowthPenalty;
    
    public CropGrowthConfig(Configuration configuration) {
        this.config = configuration;
        load();
    }
    
    public void load() {
        config.load();
        
        // Radius around player to accelerate growth
        radius = config.getInt(
            "radius",
            Configuration.CATEGORY_GENERAL,
            16,
            4,
            64,
            "Radius around player where crops grow faster (in blocks). Default: 16"
        );
        
        // Growth speed multiplier (how many extra growth ticks per check)
        growthSpeedMultiplier = config.getInt(
            "growthSpeedMultiplier",
            Configuration.CATEGORY_GENERAL,
            3,
            1,
            10,
            "How many times faster crops grow (1 = normal speed, 3 = 3x faster). Default: 3"
        );
        
        // Bonus growth ticks when player sleeps
        sleepGrowthBonus = config.getInt(
            "sleepGrowthBonus",
            Configuration.CATEGORY_GENERAL,
            50,
            0,
            200,
            "Extra growth ticks applied to crops when player wakes up from sleep. Default: 50"
        );
        
        // Enable growth acceleration when player is nearby
        enableWhilePlayerNearby = config.getBoolean(
            "enableWhilePlayerNearby",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable faster crop growth when player is nearby. Default: true"
        );
        
        // Enable bonus growth when player sleeps
        enableWhileSleeping = config.getBoolean(
            "enableWhileSleeping",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable bonus crop growth when player wakes up from sleep. Default: true"
        );
        
        // Enable weather-based growth effects
        enableWeatherEffects = config.getBoolean(
            "enableWeatherEffects",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enable weather-based growth modifiers (rain/thunder boost, snow penalty). Default: true"
        );
        
        // Rain growth bonus (extra ticks per check)
        rainGrowthBonus = config.getInt(
            "rainGrowthBonus",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            10,
            "Extra growth ticks per check when it's raining. Default: 2"
        );
        
        // Thunder growth bonus (extra ticks per check)
        thunderGrowthBonus = config.getInt(
            "thunderGrowthBonus",
            Configuration.CATEGORY_GENERAL,
            4,
            0,
            10,
            "Extra growth ticks per check when it's thundering. Default: 4"
        );
        
        // Snow growth penalty (multiplier, 0.5 = half speed)
        snowGrowthPenalty = config.getFloat(
            "snowGrowthPenalty",
            Configuration.CATEGORY_GENERAL,
            0.5f,
            0.0f,
            1.0f,
            "Growth speed multiplier when it's snowing (0.5 = half speed, 0 = no growth). Default: 0.5"
        );
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public void reload() {
        load();
    }
}
