# Keep Inventory

Never lose your items on death again! This mod automatically enforces the `keepInventory` gamerule to always be `true`, preventing any manual changes.

## Features

- ✅ **Auto-Enable** - Sets `keepInventory=true` when any world loads
- ✅ **Constant Enforcement** - Checks every second to ensure it stays enabled
- ✅ **Auto-Correction** - If changed to false, automatically sets it back to true
- ✅ **All Dimensions** - Works in Overworld, Nether, and End
- ✅ **Server-Side** - No client installation needed
- ✅ **Configurable** - Toggle on/off via config file

## How It Works

The mod uses two mechanisms to ensure `keepInventory` is always enabled:

1. **On World Load**: Immediately sets the gamerule to `true` when any world/dimension loads
2. **Periodic Check**: Every second (20 ticks), verifies the gamerule is still `true`
3. **Auto-Fix**: If it detects the gamerule was changed to `false`, it automatically corrects it back to `true`

This means even if a player or admin tries to run `/gamerule keepInventory false`, the mod will change it back within 1 second!

## Benefits

- **No Item Loss** - Players keep all items, armor, and XP on death
- **Casual-Friendly** - Perfect for casual servers or singleplayer
- **Building Focus** - Encourages exploration without fear of losing progress
- **Hardcore Alternative** - Enjoy challenging gameplay without inventory loss
- **Admin-Proof** - Even admins can't disable it (unless they remove the mod)

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder (or server mods folder)
3. Launch Minecraft 1.12.2 with Forge installed
4. The `keepInventory` gamerule will automatically be enabled!

## Configuration

The mod creates a config file at `.minecraft/config/keepinventory.cfg`:

```
general {
    B:enabled=true
}
```

Set `enabled` to `false` to disable the mod's enforcement without removing it.

## Compatibility

- **Client-Side**: Optional (not needed on client)
- **Server-Side**: Required (handles gamerule enforcement)
- **Mod Compatibility**: Works with all mods
- **Multiplayer**: Fully compatible

## Technical Details

The mod listens to two events:
- `WorldEvent.Load` - Sets gamerule when world loads
- `TickEvent.WorldTickEvent` - Checks gamerule every 20 ticks (1 second)

It only runs on the server side (`!world.isRemote`) to avoid conflicts and ensure proper synchronization.

## Use Cases

Perfect for:
- Casual survival servers
- Creative building servers
- Family-friendly servers
- Learning/tutorial worlds
- Modpack development testing
- Hardcore mode without item loss

## Credits

**Author**: Itamio  
**Package**: asd.itamio.keepinventory  
**Version**: 1.0.0
