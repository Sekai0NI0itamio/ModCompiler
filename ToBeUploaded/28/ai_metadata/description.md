# Auto Fast XP

Gain XP levels lightning fast! This mod automatically throws XP bottles rapidly when you hold right-click, making leveling up quick and effortless.

## Features

- ✅ **Rapid Throwing** - Throws up to 20 XP bottles per second (default)
- ✅ **Hold to Throw** - Just hold right-click, no need to spam click
- ✅ **Both Hands** - Works with main hand and offhand
- ✅ **Configurable Speed** - Adjust throw rate from 1-20 ticks
- ✅ **Server Compatible** - Works in both singleplayer and multiplayer
- ✅ **Creative Mode** - Doesn't consume bottles in creative
- ✅ **Lightweight** - Minimal performance impact

## How It Works

1. Hold XP bottles in your hand (main or offhand)
2. Hold down right-click
3. Watch as bottles are thrown rapidly!
4. Release right-click to stop

The mod uses proper server synchronization, so bottles are consumed correctly and XP is gained properly in both singleplayer and multiplayer.

## Use Cases

- **Quick Leveling** - Gain levels fast for enchanting
- **Repairing Items** - Use with Mending enchantment
- **XP Storage** - Quickly convert bottled XP back to levels
- **Enchanting Prep** - Get to level 30 in seconds
- **Speedruns** - Save time when using XP bottles

## Configuration

The mod creates a config file at `.minecraft/config/autofastxp.cfg`:

```
general {
    B:enabled=true
    I:throwDelay=1
}
```

**Settings:**
- `enabled` - Toggle the mod on/off
- `throwDelay` - Ticks between throws (1 = fastest, 20 = 1 per second)

**Examples:**
- `throwDelay=1` - 20 bottles/second (super fast, default)
- `throwDelay=2` - 10 bottles/second
- `throwDelay=5` - 4 bottles/second
- `throwDelay=10` - 2 bottles/second
- `throwDelay=20` - 1 bottle/second

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. Hold right-click with XP bottles to use!

## Compatibility

- **Client-Side**: Required (handles input and throwing)
- **Server-Side**: Optional (works on vanilla servers)
- **Mod Compatibility**: Works with all mods
- **Multiplayer**: Fully compatible

## Performance

This mod has virtually no performance impact. It simply automates right-clicking, which you could do manually (but much slower).

## Tips

- **Stack Management** - Keep multiple stacks of XP bottles in your hotbar for continuous throwing
- **Offhand Use** - Put bottles in offhand to throw while holding another item
- **Creative Mode** - Test different throw rates without consuming bottles
- **Enchanting** - Perfect for quickly getting to level 30 for enchanting

## Technical Details

The mod uses `PlayerControllerMP.processRightClick()` to properly simulate item usage, ensuring:
- Correct server-side item consumption
- Proper XP bottle entity spawning
- Full multiplayer compatibility
- Vanilla-like behavior at high speed

## Credits

**Author**: Itamio  
**Package**: asd.itamio.autofastxp  
**Version**: 1.0.0
