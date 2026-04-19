# Auto Tool Swap

Never waste time manually switching tools while mining! This mod automatically selects the best tool from your inventory when you start breaking a block.

## Features

- ✅ **Smart Tool Detection** - Automatically uses the right tool type for each block
  - Shovels for dirt, grass, sand, gravel, clay, farmland, soul sand, snow
  - Axes for logs, planks, fences, doors, chests, crafting tables
  - Pickaxes for stone, ores, and everything else
- ✅ **Enchantment Preferences** - Prioritizes tools with better enchantments
  - Fortune (configurable priority)
  - Silk Touch (configurable priority)
  - Efficiency (higher levels preferred)
  - Unbreaking (higher levels preferred)
- ✅ **Switch Back Feature** - Returns to your previous item after mining (2 second delay)
- ✅ **Flexible Search** - Choose between hotbar-only or full inventory search
- ✅ **Fully Configurable** - Customize all behavior via config file
- ✅ **Client-Side Only** - Runs on client for perfect visual synchronization

## How It Works

1. Left-click on any block to start mining
2. The mod detects the block type and finds the best matching tool
3. Your hotbar automatically switches to that tool
4. After you finish mining (2 seconds of inactivity), it switches back to your original item

## Configuration

The mod creates a config file at `.minecraft/config/autotoolswap.cfg` with these options:

```
enableAutoSwap (default: true)
  Enable or disable automatic tool swapping

hotbarOnly (default: true)
  Only search hotbar slots (0-8) for tools
  Set to false to search entire inventory

switchBack (default: true)
  Switch back to previous item after mining
  
preferFortune (default: true)
  Prefer tools with Fortune enchantment

preferSilkTouch (default: false)
  Prefer tools with Silk Touch enchantment
```

## Tool Selection Logic

The mod uses intelligent tool selection:

1. **Block Type Detection** - Checks if block needs shovel, axe, or pickaxe
2. **Tool Search** - Finds all matching tools in hotbar/inventory
3. **Speed Comparison** - Compares mining speed for the specific block
4. **Enchantment Scoring** - Adds bonus points for preferred enchantments
5. **Best Tool Selection** - Switches to the highest-scoring tool

## Enchantment Priority

When multiple tools have the same mining speed:
- Fortune/Silk Touch: +100 points per level (if enabled in config)
- Efficiency: +10 points per level
- Unbreaking: +5 points per level

## Performance

- Lightweight and efficient
- Client-side only (no server load)
- No performance impact on mining speed
- Instant tool switching with no delay

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. (Optional) Edit config file to customize behavior

## Compatibility

- **Client-side**: Required (handles tool switching)
- **Server-side**: Not needed (client-only mod)
- **Mod Compatibility**: Works with all tool mods and custom blocks

## Use Cases

Perfect for:
- Mining expeditions (automatically use Fortune pickaxe on ores)
- Building projects (quick switching between tools)
- Tree farming (auto-switch to axe for logs)
- Terraforming (auto-switch to shovel for dirt/sand)
- Efficiency-focused gameplay

## Known Limitations

- Only works on client side (other players won't see your tool switch)
- Requires tools to be in hotbar (unless hotbarOnly is disabled)
- Switch-back timer is fixed at 2 seconds

## Credits

**Author**: Itamio  
**Package**: asd.itamio.autotoolswap  
**Version**: 1.0.0  
**License**: MIT
