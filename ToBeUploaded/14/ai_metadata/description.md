# Crop Growth Accelerator

Transform your farming experience with intelligent crop growth acceleration! This mod speeds up crop growth based on player proximity, sleep cycles, and weather conditions.

## Features

### Player Proximity Growth
- Crops grow 3x faster when you're within 16 blocks (configurable)
- Checks 10 random blocks per second for minimal performance impact
- Works automatically in the background

### Sleep Bonus
- Wake up to a thriving farm! Crops get 50 bonus growth ticks when you sleep
- Rewards you for sleeping through the night
- Configurable bonus amount (0-200 ticks)

### Weather-Based Growth
- **Rain**: Crops grow faster with +2 extra growth ticks per check
- **Thunder**: Even faster growth with +4 extra growth ticks per check
- **Snow**: Crops grow slower at 0.5x speed in cold biomes
- Realistic farming mechanics that reward good weather

### Universal Crop Support
Works with all vanilla crops and plants:
- **Food Crops**: Wheat, Carrots, Potatoes, Beetroot
- **Fruit Stems**: Pumpkins, Melons
- **Special Crops**: Nether Wart, Cocoa Beans
- **Plants**: Saplings (all types), Mushrooms, Cactus, Sugar Cane

## Configuration

Fully customizable via `config/cropgrowth.cfg`:

```
general {
    # Radius around player (4-64 blocks)
    I:radius=16
    
    # Growth speed multiplier (1-10x)
    I:growthSpeedMultiplier=3
    
    # Sleep bonus growth ticks (0-200)
    I:sleepGrowthBonus=50
    
    # Enable/disable features
    B:enableWhilePlayerNearby=true
    B:enableWhileSleeping=true
    B:enableWeatherEffects=true
    
    # Weather modifiers
    I:rainGrowthBonus=2
    I:thunderGrowthBonus=4
    F:snowGrowthPenalty=0.5
}
```

## How It Works

### Proximity Growth
Every second, the mod checks 10 random blocks within your configured radius. If they're growable crops, it applies growth ticks based on your multiplier and current weather.

**Example**: With default settings (3x multiplier) in rain (+2 bonus):
- Normal speed: 1 growth tick
- With mod: 5 growth ticks (3x base + 2 rain bonus)

### Sleep Bonus
When you wake up from sleeping, all crops within your radius receive bonus growth ticks. This simulates time passing while you sleep.

**Example**: With 50 bonus ticks, crops advance significantly overnight, similar to real farming where plants grow while you rest.

### Weather Effects
The mod detects current weather and biome temperature:
- **Rain in warm biomes**: Bonus growth (plants love water!)
- **Thunder**: Maximum growth (intense rain + nutrients)
- **Snow in cold biomes**: Penalty (cold slows growth)

## Performance

- Checks only 10 random blocks per second (not all blocks in radius)
- Sleep bonus scans entire radius but only happens once per sleep
- Negligible FPS impact even with large farms
- Optimized for multiplayer servers

## Use Cases

### Efficient Farming
Speed up your food production without waiting hours for crops to grow. Perfect for survival gameplay where you need resources quickly.

### AFK Farming
Leave your character near crops while you're AFK. Come back to fully grown farms ready for harvest.

### Modpack Integration
Adjust growth rates to balance with other mods. Increase multiplier for harder packs, decrease for easier ones.

### Realistic Weather
Add depth to farming by making weather matter. Players will appreciate rain and avoid farming in snowy biomes.

## Compatibility

- **Client-side**: Optional (visual only)
- **Server-side**: Required (handles growth logic)
- Works with vanilla crops and most modded crops that extend vanilla classes
- No known mod conflicts

## Balancing Tips

**Too Fast?**
- Reduce `growthSpeedMultiplier` to 2 or 1
- Decrease `radius` to 8 blocks
- Lower weather bonuses

**Too Slow?**
- Increase `growthSpeedMultiplier` to 5-10
- Increase `radius` to 32-64 blocks
- Raise weather bonuses

**Disable Features**
- Set `enableWhilePlayerNearby=false` to only use sleep bonus
- Set `enableWhileSleeping=false` to only use proximity growth
- Set `enableWeatherEffects=false` to ignore weather

## FAQ

**Q: Does this work on servers?**  
A: Yes! The mod must be installed on the server. Clients don't need it but can install it for config access.

**Q: Will it work with modded crops?**  
A: Most modded crops that extend vanilla crop classes will work. Some custom crops may not be compatible.

**Q: Does it affect mob spawners or other growth?**  
A: No, only crops and plants listed in the features section.

**Q: Can I disable weather effects?**  
A: Yes, set `enableWeatherEffects=false` in the config.

**Q: Does it work in the Nether?**  
A: Yes, it works with Nether Wart. Weather effects don't apply in the Nether.

## Support

If you encounter issues or have suggestions, please report them on the issue tracker.
