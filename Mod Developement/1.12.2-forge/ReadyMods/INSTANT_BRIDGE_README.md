# Instant Bridge - Testing Instructions

## Overview
Instant Bridge automatically places blocks beneath you while you're sneaking and walking. Perfect for bridging across gaps, building platforms, or traversing terrain quickly.

## Features
- Automatically places blocks beneath you while sneaking
- Only activates when moving (configurable)
- Configurable placement delay (default: 5 ticks = 0.25 seconds)
- Uses any block from your inventory
- Skips dangerous blocks (TNT, sand, gravel, bedrock)
- Works in survival and creative mode
- Consumes blocks from inventory (except in creative)

## Configuration
Config file: `config/instantbridge.cfg`

```
general {
    # Enable instant bridge feature
    B:enableInstantBridge=true
    
    # Require player to be sneaking to place blocks
    B:requireSneaking=true
    
    # Only place blocks when player is moving
    B:placeOnlyWhenMoving=true
    
    # Delay between block placements in ticks (1-20, default: 5)
    I:placementDelay=5
}
```

## Testing Steps

### 1. Install the Mod
- Copy `Instant-Bridge-1.0.0.jar` to mods folder
- Launch Minecraft 1.12.2 with Forge

### 2. Test Basic Bridging
1. Create a new world
2. Give yourself some blocks: `/give @p minecraft:stone 64`
3. Find or create a gap/cliff
4. Hold Shift (sneak) and walk forward
5. Blocks should automatically place beneath you
6. You should be able to walk across gaps easily

### 3. Test Block Consumption
1. Check your inventory - you should have fewer blocks
2. Each block placed should consume one from inventory
3. When you run out of blocks, bridging should stop
4. Switch to creative mode - blocks should not be consumed

### 4. Test Movement Requirement
1. Stand still while sneaking
2. No blocks should be placed (placeOnlyWhenMoving=true)
3. Start walking - blocks should place
4. This prevents accidental block placement

### 5. Test Different Block Types
Test with various blocks:
- Stone, cobblestone, dirt (should work)
- Wood planks, bricks (should work)
- TNT (should be skipped - too dangerous)
- Sand/Gravel (should be skipped - falls)
- Glass, wool, concrete (should work)

### 6. Test Placement Delay
1. Default delay is 5 ticks (0.25 seconds)
2. Walk forward while sneaking
3. Blocks should place every 5 ticks
4. Edit config: `placementDelay=1` (very fast)
5. Test again - blocks should place almost instantly
6. Edit config: `placementDelay=20` (slow)
7. Test again - blocks should place every second

### 7. Test Without Sneaking
1. Edit config: `requireSneaking=false`
2. Launch Minecraft
3. Walk forward without sneaking
4. Blocks should place automatically
5. WARNING: This can be annoying, use with caution!

### 8. Test Disable Feature
1. Edit config: `enableInstantBridge=false`
2. Launch Minecraft
3. Sneak and walk - no blocks should place
4. This confirms the config works

### 9. Test in Different Scenarios
- Bridge across lava
- Bridge across water
- Bridge in the Nether
- Bridge in the End
- Bridge while sprinting (sprint + sneak)
- Bridge while jumping

### 10. Test Edge Cases
1. Walk off a cliff while sneaking - should place blocks
2. Walk into a wall while sneaking - should not place (already solid)
3. Walk on existing blocks - should not place (already solid)
4. Empty inventory - should not place (no blocks)

## Expected Behavior
- ✅ Places blocks beneath player when sneaking and moving
- ✅ Only places when moving (prevents accidental placement)
- ✅ Consumes blocks from inventory (survival mode)
- ✅ Doesn't consume blocks in creative mode
- ✅ Skips dangerous blocks (TNT, sand, gravel)
- ✅ Configurable placement delay
- ✅ Can disable sneaking requirement
- ✅ Works in all dimensions
- ✅ No crashes or duplication glitches

## Use Cases
- **Bridging**: Cross gaps and chasms quickly
- **Building**: Create platforms and walkways efficiently
- **Exploration**: Traverse terrain without stopping to place blocks
- **Nether Travel**: Bridge across lava lakes safely
- **Skyblock**: Build bridges between islands

## Balance Considerations
- Requires blocks in inventory (not infinite)
- Placement delay prevents instant bridging
- Sneaking requirement prevents accidental use
- Movement requirement prevents tower building
- Skips dangerous blocks for safety

## Known Limitations
- Only places blocks directly beneath player (not diagonal)
- Doesn't work for tower building (requires movement)
- Skips falling blocks (sand, gravel) to prevent issues
- Uses first available block in inventory (not configurable)

## Performance Notes
- Minimal performance impact
- Only checks every tick when conditions are met
- No lag with large inventories
- Works smoothly even with low placement delay

## Files
- Mod JAR: `build/libs/Instant-Bridge-1.0.0.jar`
- Source: `src/main/java/asd/itamio/instantbridge/`
- Config: `config/instantbridge.cfg` (generated on first run)

## Version
- Mod Version: 1.0.0
- Minecraft Version: 1.12.2
- Forge Version: 14.23.5.2860
