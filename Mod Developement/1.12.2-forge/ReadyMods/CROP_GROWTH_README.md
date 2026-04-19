# Crop Growth Accelerator - Testing Instructions

## Overview
Crop Growth Accelerator speeds up crop growth when players are nearby or sleeping. Fully configurable with growth multipliers, radius, and sleep bonuses.

## Features
- Crops grow faster when player is nearby (default: 3x speed)
- Bonus growth when player wakes up from sleep (default: 50 ticks)
- Weather-based growth modifiers:
  - Rain: +2 extra growth ticks per check
  - Thunder: +4 extra growth ticks per check
  - Snow: 0.5x growth speed (half speed)
- Configurable radius (default: 16 blocks)
- Works with all vanilla crops and plants
- Minimal performance impact (checks 10 random blocks per second)

## Supported Crops/Plants
- Wheat, Carrots, Potatoes, Beetroot
- Pumpkin/Melon stems
- Nether Wart
- Cocoa Beans
- Saplings
- Mushrooms
- Cactus
- Sugar Cane

## Configuration
Config file: `config/cropgrowth.cfg`

```
general {
    # Radius around player where crops grow faster (4-64 blocks)
    I:radius=16
    
    # Growth speed multiplier (1-10x, where 1 = normal speed)
    I:growthSpeedMultiplier=3
    
    # Extra growth ticks when player wakes up (0-200)
    I:sleepGrowthBonus=50
    
    # Enable faster growth when player is nearby
    B:enableWhilePlayerNearby=true
    
    # Enable bonus growth when player sleeps
    B:enableWhileSleeping=true
    
    # Enable weather-based growth modifiers
    B:enableWeatherEffects=true
    
    # Extra growth ticks per check when raining (0-10)
    I:rainGrowthBonus=2
    
    # Extra growth ticks per check when thundering (0-10)
    I:thunderGrowthBonus=4
    
    # Growth speed multiplier when snowing (0.0-1.0, 0.5 = half speed)
    F:snowGrowthPenalty=0.5
}
```

## Testing Steps

### 1. Install the Mod
- Copy `Crop-Growth-Accelerator-1.0.0.jar` to mods folder
- Launch Minecraft 1.12.2 with Forge

### 2. Test Nearby Growth Acceleration
1. Create a new world
2. Plant some wheat: `/give @p minecraft:wheat_seeds 64`
3. Plant the seeds in a 5x5 area
4. Stand near the crops and wait
5. Crops should grow noticeably faster (3x speed by default)
6. Walk away (more than 16 blocks) - growth returns to normal
7. Walk back - growth accelerates again

### 3. Test Sleep Growth Bonus
1. Plant more crops
2. Wait until night
3. Sleep in a bed
4. When you wake up, crops near your bed should have grown significantly
5. Check crops far from bed - they should not have the bonus

### 4. Test Different Crop Types
Test with various crops to ensure all work:
- `/give @p minecraft:carrot 64`
- `/give @p minecraft:potato 64`
- `/give @p minecraft:beetroot_seeds 64`
- `/give @p minecraft:pumpkin_seeds 64`
- `/give @p minecraft:melon_seeds 64`
- `/give @p minecraft:nether_wart 64`
- `/give @p minecraft:sapling 0 64` (oak sapling)
- `/give @p minecraft:cactus 64`
- `/give @p minecraft:reeds 64` (sugar cane)

### 5. Test Configuration Changes
1. Exit Minecraft
2. Edit `config/cropgrowth.cfg`
3. Change `growthSpeedMultiplier` to `10` (very fast)
4. Change `radius` to `32` (larger area)
5. Change `sleepGrowthBonus` to `100` (more growth)
6. Launch Minecraft
7. Test again - crops should grow much faster

### 6. Test Disable Options
1. Edit config: `enableWhilePlayerNearby=false`
2. Launch Minecraft
3. Stand near crops - they should grow at normal speed
4. Sleep near crops - they should still get bonus (if enabled)

### 7. Performance Test
1. Plant a large farm (20x20 or bigger)
2. Stand in the middle
3. Check F3 debug screen for FPS
4. Should have minimal performance impact

### 8. Test Weather Effects
1. Plant crops in different biomes
2. Use `/weather rain` - crops should grow faster (3x + 2 bonus = 5x)
3. Use `/weather thunder` - crops should grow even faster (3x + 4 bonus = 7x)
4. Go to a cold biome (taiga, ice plains)
5. Use `/weather rain` - it will snow, crops should grow slower (half speed)
6. Use `/weather clear` - crops return to normal 3x speed

### 9. Test Weather Config
1. Edit config: `rainGrowthBonus=5`, `thunderGrowthBonus=10`
2. Launch Minecraft
3. Make it rain - crops should grow much faster
4. Edit config: `snowGrowthPenalty=0.0` (no growth in snow)
5. Test in cold biome - crops should not grow at all
6. Edit config: `enableWeatherEffects=false`
7. Test - weather should have no effect on growth

## Expected Behavior
- ✅ Crops grow 3x faster when player is within 16 blocks
- ✅ Crops get 50 bonus growth ticks when player wakes up
- ✅ Rain adds +2 growth ticks per check (5x total)
- ✅ Thunder adds +4 growth ticks per check (7x total)
- ✅ Snow reduces growth to 0.5x speed (half speed)
- ✅ Works with all vanilla crops and plants
- ✅ Configurable speed, radius, weather effects, and bonuses
- ✅ Can disable nearby, sleep, or weather growth independently
- ✅ No performance issues with large farms
- ✅ No crashes or errors

## Performance Notes
- Checks only 10 random blocks per second (not all blocks in radius)
- Sleep bonus scans entire radius but only happens once per sleep
- Larger radius = more blocks to check during sleep
- Higher multiplier = more growth ticks per check

## Use Cases
- **Fast Farming**: Grow crops quickly for food/resources
- **AFK Farming**: Leave crops growing while you're nearby
- **Sleep Bonus**: Reward for sleeping through the night
- **Modpack Balance**: Adjust growth speed to match pack difficulty

## Files
- Mod JAR: `build/libs/Crop-Growth-Accelerator-1.0.0.jar`
- Source: `src/main/java/asd/itamio/cropgrowth/`
- Config: `config/cropgrowth.cfg` (generated on first run)

## Version
- Mod Version: 1.0.0
- Minecraft Version: 1.12.2
- Forge Version: 14.23.5.2860
