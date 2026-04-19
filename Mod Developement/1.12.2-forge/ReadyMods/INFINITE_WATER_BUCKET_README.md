# Infinite Water Bucket - Testing Instructions

## Overview
Infinite Water Bucket makes water buckets never empty when placed. Optionally supports infinite lava and milk buckets too.

## Features
- Water buckets never empty (enabled by default)
- Optional infinite lava buckets (disabled by default - can be overpowered)
- Optional infinite milk buckets (disabled by default)
- Fully configurable
- Works in survival mode

## Configuration
Config file: `config/infinitebucket.cfg`

```
general {
    # Enable infinite water buckets (never empties)
    B:enableInfiniteWater=true
    
    # Enable infinite lava buckets (never empties)
    # WARNING: Can be overpowered
    B:enableInfiniteLava=false
    
    # Enable infinite milk buckets (never empties)
    B:enableInfiniteMilk=false
}
```

## Testing Steps

### 1. Install the Mod
- Copy `Infinite-Water-Bucket-1.0.0.jar` to mods folder
- Launch Minecraft 1.12.2 with Forge

### 2. Test Infinite Water Bucket
1. Create a new world
2. Give yourself a water bucket: `/give @p minecraft:water_bucket`
3. Place the water somewhere
4. Check your inventory - you should still have the water bucket
5. Place water again multiple times - bucket should never empty
6. Try filling a large area with water - only need 1 bucket

### 3. Test Water Bucket Pickup
1. Right-click on a water source block with an empty bucket
2. You should get a water bucket
3. Place the water - bucket should remain filled
4. This confirms the infinite feature works

### 4. Test in Different Scenarios
1. Place water on the ground
2. Place water against a wall
3. Place water in the Nether (it should evaporate but bucket stays filled)
4. Place water underwater
5. All scenarios should keep the water bucket filled

### 5. Test Lava Bucket (Optional)
1. Exit Minecraft
2. Edit `config/infinitebucket.cfg`
3. Set `enableInfiniteLava=true`
4. Launch Minecraft
5. Give yourself a lava bucket: `/give @p minecraft:lava_bucket`
6. Place lava - bucket should stay filled
7. WARNING: This can be very overpowered!

### 6. Test Milk Bucket (Optional)
1. Edit config: `enableInfiniteMilk=true`
2. Launch Minecraft
3. Give yourself a milk bucket: `/give @p minecraft:milk_bucket`
4. Drink the milk (removes potion effects)
5. Check inventory - you should still have the milk bucket
6. Drink again - unlimited milk!

### 7. Test Disable Feature
1. Edit config: `enableInfiniteWater=false`
2. Launch Minecraft
3. Place water with a water bucket
4. Bucket should turn into an empty bucket (normal behavior)
5. This confirms the config works

### 8. Test in Survival
1. Switch to survival mode
2. Get a water bucket normally (fill empty bucket from water source)
3. Place water - should stay filled
4. Confirms it works in survival, not just creative

## Expected Behavior
- ✅ Water bucket never empties when placing water
- ✅ Bucket stays in the same inventory slot
- ✅ Works in survival and creative mode
- ✅ Works in all dimensions (Overworld, Nether, End)
- ✅ Lava bucket infinite when enabled in config
- ✅ Milk bucket infinite when enabled in config
- ✅ Can disable features via config
- ✅ No crashes or duplication glitches

## Use Cases
- **Building**: Fill large pools, moats, or water features with one bucket
- **Farming**: Create infinite water sources for crops
- **Redstone**: Build water-based contraptions without bucket management
- **Nether Travel**: Keep water bucket for emergencies (though it evaporates)
- **Convenience**: Never run out of water buckets

## Balance Considerations
- Water is already renewable in vanilla (infinite water sources)
- This mod just removes the tedium of refilling buckets
- Lava is disabled by default because it's not renewable in vanilla
- Milk is disabled by default to preserve some challenge

## Known Limitations
- Only works with vanilla buckets (water, lava, milk)
- Modded buckets may not be supported
- The bucket briefly turns into an empty bucket before being refilled (2 tick delay)

## Files
- Mod JAR: `build/libs/Infinite-Water-Bucket-1.0.0.jar`
- Source: `src/main/java/asd/itamio/infinitebucket/`
- Config: `config/infinitebucket.cfg` (generated on first run)

## Version
- Mod Version: 1.0.0
- Minecraft Version: 1.12.2
- Forge Version: 14.23.5.2860
