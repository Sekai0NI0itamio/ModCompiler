# Auto Torch Placer

Stop breaking your mining flow to place torches every few seconds! This mod automatically places torches from your inventory when the light level around you drops too low. Perfect for cave exploration, mining, and building in dark areas.

## Features

- ✅ **Automatic Torch Placement** - Places torches when average light level drops below 7
- ✅ **Movement-Based Activation** - Checks every 0.1 seconds while moving, less frequently when stationary
- ✅ **Smart Light Detection** - Calculates average light level in 3x3x3 area around player
- ✅ **Wall Priority** - Prioritizes placing torches on walls over ground
- ✅ **Three Placement Modes** - Auto (walls + ground), wall-only, or ground-only
- ✅ **Minimum Distance** - Prevents torch spam with configurable minimum distance (default 8 blocks)
- ✅ **Toggle Keybind** - Press K to turn on/off with visual feedback
- ✅ **Inventory Search** - Finds torches anywhere in your inventory (not just hotbar)
- ✅ **Dimension Control** - Disable in Nether, enable in End, customize for other dimensions
- ✅ **Fully Configurable** - Customize all behavior via config file

## How It Works

1. Walk through a dark cave with torches in your inventory
2. When the average light level around you drops below 7, a torch is automatically placed
3. Torches are placed on nearby walls first, then on the ground if no walls are available
4. The mod checks more frequently when you're moving (every 0.1 seconds)
5. Press K to toggle the mod on/off at any time

## Placement Logic

The mod uses intelligent placement logic:

1. **Light Level Check** - Calculates average light in 3x3x3 area around player
2. **Wall Search** - Looks for solid walls at player height and one block down
3. **Ground Search** - If no walls found, looks for solid ground nearby
4. **Distance Check** - Ensures new torch is at least 8 blocks from last placed torch
5. **Cooldown** - Prevents spam with 2-tick cooldown between placements

## Configuration

The mod creates a config file at `.minecraft/config/autotorch.cfg` with these options:

```
enableAutoTorch (default: true)
  Enable or disable automatic torch placement

lightThreshold (default: 7, range: 0-15)
  Light level threshold for torch placement
  Torches place when average light is below this value

placementCooldown (default: 2 ticks = 0.1 second)
  Cooldown between torch placements when moving

placementMode (default: "auto")
  Torch placement mode:
  - "auto": Places on walls and ground (walls prioritized)
  - "wall": Only places on walls
  - "ground": Only places on ground

minTorchDistance (default: 8 blocks)
  Minimum distance between placed torches

enableInNether (default: false)
  Enable torch placement in the Nether

enableInEnd (default: true)
  Enable torch placement in the End

playSound (default: true)
  Play sound when torch is placed

showMessage (default: false)
  Show on-screen message when torch is placed
```

## Key Binding

- **Default Key**: K
- **Customizable**: Minecraft Options → Controls → Key Binds → Gameplay → "Toggle Auto Torch"
- **Visual Feedback**: Shows "Auto Torch Placer: ON" (green) or "OFF" (red) when toggled

## Use Cases

**Cave Exploration:**
- Walk through caves and torches place automatically
- Never stop to place torches manually
- Maintain constant lighting as you explore

**Mining:**
- Focus on mining, not lighting
- Torches place as you dig deeper
- Prevents mob spawning automatically

**Building in Dark Areas:**
- Work on builds without stopping for lighting
- Torches place as you move around
- Perfect for underground builds

**Speedrunning:**
- Save time by not manually placing torches
- Maintain optimal lighting automatically
- Focus on navigation and resource gathering

## Performance

- **Lightweight** - Only checks when moving or every 0.5 seconds when stationary
- **Efficient** - Smart cooldown system prevents spam
- **No Lag** - Minimal performance impact
- **Multiplayer Ready** - Works seamlessly on servers

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. (Optional) Edit config file to customize behavior
5. Put torches in your inventory and start exploring!

## Compatibility

- **Client-side**: Required (handles key input and movement detection)
- **Server-side**: Required (performs torch placement)
- **Multiplayer**: Fully supported with client-server sync
- **Mod Compatibility**: Works with all torch mods and lighting mods

## Tips

- Keep a stack of torches in your inventory for long mining trips
- Use wall-only mode in caves for cleaner placement
- Increase minTorchDistance if you want fewer torches
- Disable in Nether to avoid wasting torches
- Toggle off when building to place torches manually

## Known Limitations

- Only works with vanilla torches (not modded torches)
- Requires solid blocks for placement (can't place on glass, leaves, etc.)
- Minimum distance is measured from last placed torch, not all torches
- Toggle state resets to config default on game restart

## Comparison to Manual Placement

**Manual Placement:**
- Stop every few seconds
- Break mining flow
- Easy to forget and get ambushed by mobs
- Tedious and repetitive

**Auto Torch Placer:**
- Never stop moving
- Maintain constant flow
- Always properly lit
- Effortless and automatic

## Credits

**Author**: Itamio  
**Package**: asd.itamio.autotorch  
**Version**: 1.0.0  
**License**: MIT  
**Inspired by**: Every miner who's tired of placing torches manually
