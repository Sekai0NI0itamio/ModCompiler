# Instant Experience Orb

Level up instantly! This mod revolutionizes XP collection with instant absorption, automatic orb clumping, and a simplified 1 orb = 1 level system.

## Features

- ✅ **Instant Absorption** - XP orbs absorbed immediately, no delay
- ✅ **Orb Clumping** - Nearby orbs merge together to reduce lag
- ✅ **Simplified Leveling** - 1 XP orb = 1 level (no complex XP curves)
- ✅ **Lag Reduction** - Fewer entities = better performance
- ✅ **Fully Configurable** - Toggle each feature independently
- ✅ **Server Compatible** - Works in multiplayer

## How It Works

### Instant Absorption
When you pick up an XP orb, it's instantly added to your level - no waiting for the slow vanilla animation!

### Orb Clumping
Every second, the mod scans for XP orbs within 3 blocks of each other and merges them into a single orb. This dramatically reduces entity count and improves performance.

**Example:**
- 10 separate 1-XP orbs → 1 merged 10-XP orb
- Fewer entities = less lag
- Still get the same total XP

### Simplified Leveling
Forget vanilla's complex XP curve! With this mod:
- 1 XP orb = 1 level (regardless of current level)
- Kill a zombie (5 XP) = gain 5 levels
- No more grinding for hours to reach level 30

## Benefits

- **Faster Leveling** - Reach enchanting levels in seconds
- **Better Performance** - Fewer XP orbs = less lag
- **Quality of Life** - No waiting for XP to slowly add up
- **Fair Progression** - Consistent leveling speed at all levels
- **Multiplayer Friendly** - Server-side logic ensures sync

## Configuration

The mod creates a config file at `.minecraft/config/instantxp.cfg`:

```
general {
    B:enabled=true
    B:instantAbsorption=true
    B:clumpOrbs=true
    B:simplifiedLeveling=true
    D:clumpRadius=3.0
}
```

**Settings:**
- `enabled` - Master toggle for all features
- `instantAbsorption` - Instant XP pickup (no delay)
- `clumpOrbs` - Merge nearby orbs together
- `simplifiedLeveling` - Use 1 orb = 1 level system
- `clumpRadius` - Distance in blocks to search for orbs to merge (1.0 - 10.0)

**Mix and Match:**
You can enable/disable features independently:
- Want instant absorption but keep vanilla leveling? Set `simplifiedLeveling=false`
- Want orb clumping but not instant pickup? Set `instantAbsorption=false`
- Want to adjust clump radius? Change `clumpRadius` value

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder (or server mods folder)
3. Launch Minecraft 1.12.2 with Forge installed
4. Enjoy instant XP collection!

## Compatibility

- **Client-Side**: Optional (not needed on client)
- **Server-Side**: Required (handles XP logic)
- **Mod Compatibility**: Works with all mods
- **Multiplayer**: Fully compatible

## Performance Impact

**Positive Impact:**
- Reduces XP orb entity count by up to 90%
- Less entity ticking = better TPS
- Fewer render calls = better FPS
- Ideal for mob grinders and XP farms

**Negligible Cost:**
- Checks for nearby orbs once per second
- Efficient AABB-based search
- Only runs on server side

## Use Cases

Perfect for:
- **XP Farms** - Massive performance boost with many orbs
- **Mob Grinders** - Instant leveling from kills
- **Enchanting** - Reach level 30 in seconds
- **Modpacks** - Reduce lag from XP-heavy mods
- **Casual Play** - Skip the grind, enjoy the game
- **Servers** - Better TPS with many players collecting XP

## Technical Details

**Instant Absorption:**
- Intercepts `PlayerPickupXpEvent`
- Directly calls `addExperienceLevel()` for simplified mode
- Cancels vanilla XP handling to prevent double-adding

**Orb Clumping:**
- Runs every 20 ticks (1 second) on `WorldTickEvent`
- Uses AABB search for efficient nearby orb detection
- Merges orb XP values and removes duplicates
- Only runs server-side to prevent desync

**Simplified Leveling:**
- Bypasses vanilla XP curve calculation
- Direct level addition instead of XP points
- Consistent progression at all levels

## Credits

**Author**: Itamio  
**Package**: asd.itamio.instantxp  
**Version**: 1.0.0
