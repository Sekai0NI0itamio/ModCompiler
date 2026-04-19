# Vein Miner - Performance Optimized

**Version:** 1.0.0  
**Minecraft Version:** 1.12.2  
**Author:** Itamio

## Overview

Vein Miner is a highly optimized mining mod that allows you to mine entire ore veins and connected blocks at once. This implementation focuses on performance and addresses common user complaints from other vein miner mods.

## Key Features

### Performance Optimizations
- **No Particles**: Block break particles are disabled by default to prevent lag
- **Single Sound**: Only one break sound plays instead of hundreds
- **Centralized Drops**: All items drop at one location with automatic stacking
- **Efficient Algorithm**: Uses breadth-first search with configurable limits
- **Smart Stacking**: Combines identical items before spawning entities

### User-Requested Features
- **Sneak Activation**: Hold sneak while mining to activate (configurable)
- **Toggle Keybind**: Press 'V' to enable/disable vein mining on the fly
- **Durability Consumption**: Properly consumes tool durability for each block
- **Hunger System**: Balanced hunger/exhaustion consumption
- **Correct Tool Check**: Only works with appropriate tools (pickaxe for ores, axe for logs, etc.)
- **Block Limit**: Configurable maximum blocks per vein (default: 64)
- **Cooldown System**: Optional cooldown between uses
- **Tool Breaking Protection**: Stops mining if tool is about to break

### Supported Blocks (Configurable)
- **Ores**: Coal, Iron, Gold, Diamond, Emerald, Lapis, Redstone, Quartz
- **Logs**: All wood types (respects wood type - won't mix oak and birch)
- **Stone**: Stone and Cobblestone (disabled by default)
- **Terrain**: Dirt, Gravel, Sand, Clay (disabled by default)
- **Nether**: Netherrack, Glowstone
- **End**: End Stone

## How to Use

### Basic Usage
1. Hold sneak (default: Shift)
2. Mine any supported block with the correct tool
3. The entire connected vein mines instantly
4. All items drop at the first block location

### Toggle On/Off
- Press 'V' (configurable) to toggle vein mining
- Chat message confirms current state
- Useful for when you want to mine single blocks

### Requirements
- Must use correct tool type (pickaxe for ores, axe for logs, shovel for dirt/gravel)
- Must be in survival mode (doesn't work in creative)
- Must have sneak enabled (if configured)

## Configuration

Config file: `config/veinminer.cfg`

### General Settings
- `Enable Vein Miner` (default: true) - Master on/off switch
- `Require Sneak` (default: true) - Must sneak to activate
- `Max Blocks` (default: 64, range: 1-1000) - Maximum blocks per vein

### Balance Settings
- `Consume Durability` (default: true) - Use tool durability per block
- `Consume Hunger` (default: true) - Apply hunger exhaustion
- `Hunger Multiplier` (default: 1.0, range: 0.0-10.0) - Adjust hunger cost
- `Limit To Correct Tool` (default: true) - Require appropriate tool
- `Cooldown Ticks` (default: 0, range: 0-200) - Cooldown between uses (20 ticks = 1 second)

### Performance Settings
- `Drop At One Location` (default: true) - Centralize item drops
- `Disable Particles` (default: true) - Remove block break particles
- `Disable Sound` (default: false) - Mute individual break sounds

### Block Type Settings
Each block type can be enabled/disabled individually:
- `Mine Ores` (default: true)
- `Mine Logs` (default: true)
- `Mine Stone` (default: false)
- `Mine Dirt` (default: false)
- `Mine Gravel` (default: false)
- `Mine Sand` (default: false)
- `Mine Clay` (default: false)
- `Mine Netherrack` (default: false)
- `Mine End Stone` (default: false)
- `Mine Glowstone` (default: true)

## Solutions to Common Complaints

### "Vein miner causes lag"
✅ **Solved**: Particles disabled, single sound, optimized algorithm, block limits

### "Too overpowered"
✅ **Solved**: Configurable durability/hunger consumption, block limits, cooldown system, correct tool requirement

### "Can't turn it off when I want single blocks"
✅ **Solved**: Toggle keybind (V) with chat confirmation

### "Drops items everywhere"
✅ **Solved**: All items drop at one location with automatic stacking

### "Doesn't work with modded tools"
✅ **Solved**: Uses Forge tool class system, works with any tool that declares proper tool class

### "Mines too many blocks and breaks my tool"
✅ **Solved**: Configurable block limit (default 64), stops if tool about to break

### "Uses too much hunger"
✅ **Solved**: Configurable hunger multiplier, can be reduced or disabled

## Technical Details

### Algorithm
- Uses breadth-first search (BFS) for connected block detection
- Checks all 26 surrounding blocks (including diagonals)
- Respects block metadata (e.g., different wood types)
- Stops at configured block limit
- O(n) time complexity where n = blocks in vein

### Performance
- Single world update per vein
- Batch item spawning with pre-stacking
- No particle packets sent
- Minimal sound packets
- Efficient memory usage with HashSet

### Compatibility
- Works with Fortune enchantment
- Works with Silk Touch
- Compatible with modded ores (if configured)
- Server-side processing prevents cheating
- Client-side toggle for convenience

## Installation

1. Download the mod jar file
2. Place in your `mods` folder
3. Launch Minecraft 1.12.2 with Forge
4. Configure settings in `config/veinminer.cfg` if desired

## Recommended Settings

### For Survival Balance
```
Require Sneak: true
Max Blocks: 64
Consume Durability: true
Consume Hunger: true
Hunger Multiplier: 1.0
Cooldown Ticks: 0
```

### For Casual Play
```
Require Sneak: true
Max Blocks: 100
Consume Durability: true
Consume Hunger: true
Hunger Multiplier: 0.5
Cooldown Ticks: 0
```

### For Modpacks (Balanced)
```
Require Sneak: true
Max Blocks: 50
Consume Durability: true
Consume Hunger: true
Hunger Multiplier: 1.5
Cooldown Ticks: 20 (1 second)
```

## Known Limitations

- Does not work in creative mode (intentional)
- Maximum 1000 blocks per vein (configurable limit)
- Requires Forge (not compatible with Fabric)

## Future Enhancements

Potential features for future versions:
- Per-tool block limits
- Experience cost option
- Whitelist/blacklist for modded blocks
- Particle effects toggle per player
- Network sync for toggle state

## Credits

Created by Itamio  
Package: `asd.itamio.veinminer`

## License

All rights reserved.

---

**Note**: This mod is designed to be performance-friendly and balanced. All settings are configurable to match your playstyle or server requirements.
