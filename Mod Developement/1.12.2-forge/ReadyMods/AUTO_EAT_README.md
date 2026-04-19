# Auto Eat Mod - Testing Instructions

## Overview
Auto Eat automatically eats food from your inventory when your hunger drops below a configurable threshold. It intelligently selects the best food based on saturation values and respects a configurable blacklist.

## Features
- Automatically eats food when hunger drops below threshold (default: 14/20)
- Selects best food by saturation value (most efficient food first)
- Configurable food blacklist (avoid unwanted foods)
- Minimal performance impact (checks once per second)
- Works with any food item in inventory

## Default Blacklist
The following foods are blacklisted by default:
- Spider Eye (`minecraft:spider_eye`)
- Rotten Flesh (`minecraft:rotten_flesh`)
- Poisonous Potato (`minecraft:poisonous_potato`)
- Golden Apple (`minecraft:golden_apple`) - both normal and enchanted
- Chorus Fruit (`minecraft:chorus_fruit`)

## Configuration
Config file: `config/autoeat.cfg`

```
general {
    # Hunger level at which to auto-eat (0-20, where 20 is full). Default: 14
    I:hungerThreshold=14
    
    # Foods that will never be auto-eaten. Format: modid:itemname
    S:blacklistedFoods <
        minecraft:spider_eye
        minecraft:rotten_flesh
        minecraft:poisonous_potato
        minecraft:golden_apple
        minecraft:chorus_fruit
    >
}
```

## Testing Steps

### 1. Install the Mod
- Copy `Auto-Eat-1.0.0.jar` to your Minecraft 1.12.2 mods folder
- Launch Minecraft 1.12.2 with Forge

### 2. Test Basic Auto-Eating
1. Create a new world or join an existing one
2. Give yourself some food: `/give @p minecraft:bread 64`
3. Drain your hunger by jumping repeatedly or using `/effect @p minecraft:hunger 30 10`
4. Wait for hunger to drop below 14 (7 drumsticks)
5. The mod should automatically eat bread from your inventory
6. You should hear the eating sound effect

### 3. Test Food Selection (Best Food First)
1. Give yourself multiple food types:
   - `/give @p minecraft:bread 10` (saturation: 6.0)
   - `/give @p minecraft:cooked_beef 10` (saturation: 12.8)
   - `/give @p minecraft:apple 10` (saturation: 2.4)
2. Drain your hunger again
3. The mod should eat cooked beef first (highest saturation)
4. Once beef is gone, it should eat bread
5. Finally, it should eat apples

### 4. Test Blacklist
1. Give yourself blacklisted food:
   - `/give @p minecraft:spider_eye 10`
   - `/give @p minecraft:rotten_flesh 10`
   - `/give @p minecraft:bread 5`
2. Drain your hunger
3. The mod should ONLY eat bread, never spider eyes or rotten flesh

### 5. Test Golden Apple Blacklist
1. Give yourself golden apples:
   - `/give @p minecraft:golden_apple 5 0` (normal)
   - `/give @p minecraft:golden_apple 5 1` (enchanted)
   - `/give @p minecraft:bread 5`
2. Drain your hunger
3. The mod should eat bread and ignore both types of golden apples

### 6. Test Configuration Changes
1. Exit Minecraft
2. Edit `config/autoeat.cfg`
3. Change `hungerThreshold` to `18` (eat when hunger drops below 18)
4. Remove `minecraft:spider_eye` from blacklist
5. Launch Minecraft again
6. Drain hunger to 17 - mod should eat immediately
7. Give yourself spider eyes - mod should now eat them

### 7. Test with Empty Inventory
1. Clear your inventory: `/clear @p`
2. Drain your hunger
3. Nothing should happen (no crashes)
4. Give yourself food - mod should eat it

## Expected Behavior
- ✅ Automatically eats when hunger < threshold
- ✅ Selects best food by saturation
- ✅ Respects blacklist configuration
- ✅ Plays eating sound
- ✅ Works with any food item
- ✅ No performance issues
- ✅ No crashes with empty inventory

## Known Limitations
- Does not consider potion effects from food (e.g., won't avoid suspicious stew)
- Does not preserve special foods for later (e.g., won't save golden apples for combat)
- Checks every second, so there's a small delay before eating

## Files
- Mod JAR: `build/libs/Auto-Eat-1.0.0.jar`
- Source: `src/main/java/asd/itamio/autoeat/`
- Config: `config/autoeat.cfg` (generated on first run)

## Version
- Mod Version: 1.0.0
- Minecraft Version: 1.12.2
- Forge Version: 14.23.5.2860
