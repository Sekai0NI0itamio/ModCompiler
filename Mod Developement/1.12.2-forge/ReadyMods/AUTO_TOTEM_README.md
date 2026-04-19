# Auto Totem - Automatic Totem of Undying Equipment

**Version:** 1.0.0  
**Minecraft Version:** 1.12.2  
**Author:** Itamio

## Overview

Auto Totem is a survival-enhancing mod that automatically equips Totem of Undying to your offhand when you need it most. Never die unexpectedly because you forgot to equip your totem! Perfect for hardcore players, PvP, and dangerous adventures.

## Key Features

### Automatic Protection
- ✅ **Low Health Detection** - Auto-equips totem when health drops below threshold
- ✅ **Fatal Damage Protection** - Instantly equips totem when taking lethal damage
- ✅ **Auto Re-equip** - Automatically equips new totem after one is consumed
- ✅ **Smart Priority** - Can prioritize totem over other offhand items

### Quality of Life
- 🎮 **Toggle Keybind** - Press 'T' to enable/disable on the fly
- 💬 **Chat Messages** - Shows when totem is equipped (configurable)
- ⚙️ **Highly Configurable** - 7+ config options
- 🚫 **No New Blocks** - Pure gameplay mod
- 📦 **Lightweight** - Minimal performance impact
- 🖥️ **Server-Side** - Works on servers

## How to Use

### Basic Usage
1. Install the mod
2. Have Totem of Undying in your inventory
3. When health gets low or you take fatal damage, totem auto-equips!
4. After totem is consumed, a new one auto-equips (if available)

### Toggle On/Off
- Press 'T' (configurable keybind)
- Chat message confirms current state
- Useful when you want manual control

### Default Behavior
By default:
- Equips totem when health ≤ 3 hearts (6.0 HP)
- Equips totem when taking fatal damage
- Re-equips after totem is consumed (5 tick delay)
- Prioritizes totem over other offhand items
- Shows chat messages

## Configuration

Config file: `config/autototem.cfg`

### General Settings
```
Enable Auto Totem (default: true)
- Master on/off switch
```

### Behavior Settings
```
Equip On Low Health (default: true)
- Automatically equip totem when health is low

Health Threshold (default: 6.0, range: 1.0-20.0)
- Health level in half-hearts to trigger auto-equip
- Default: 6.0 (3 hearts)
- Examples:
  * 2.0 = 1 heart (very risky)
  * 6.0 = 3 hearts (balanced)
  * 10.0 = 5 hearts (safe)
  * 20.0 = 10 hearts (very safe)

Equip After Use (default: true)
- Automatically equip new totem after one is consumed

Equip Delay (default: 5, range: 0-100)
- Delay in ticks before equipping after totem use
- 20 ticks = 1 second
- Default: 5 ticks (0.25 seconds)

Prioritize Totem (default: true)
- Always prioritize totem over other offhand items when health is low
- If false, only equips if offhand is empty
```

### Message Settings
```
Show Messages (default: true)
- Show chat messages when totem is equipped
```

## Configuration Examples

### Maximum Safety (Default)
```
Enable Auto Totem: true
Equip On Low Health: true
Health Threshold: 6.0 (3 hearts)
Equip After Use: true
Equip Delay: 5 ticks
Prioritize Totem: true
Show Messages: true
```
**Result:** Maximum protection, always ready

### Hardcore Mode
```
Enable Auto Totem: true
Equip On Low Health: true
Health Threshold: 2.0 (1 heart)
Equip After Use: true
Equip Delay: 20 ticks
Prioritize Totem: false
Show Messages: false
```
**Result:** Only equips at critical health, slower re-equip

### PvP Optimized
```
Enable Auto Totem: true
Equip On Low Health: true
Health Threshold: 10.0 (5 hearts)
Equip After Use: true
Equip Delay: 0 ticks
Prioritize Totem: true
Show Messages: false
```
**Result:** Fast, aggressive totem management

### Manual Control
```
Enable Auto Totem: false
```
**Result:** Mod disabled, manual totem management

## Why This Mod?

### Problem It Solves
- **Forgotten Totems:** Die because you forgot to equip totem
- **Inventory Management:** Fumbling in inventory during combat
- **Hardcore Deaths:** Losing hardcore worlds to preventable deaths
- **PvP Disadvantage:** Slower totem swapping than opponents
- **Totem Waste:** Using totems when not needed

### Benefits
- ✅ Never forget to equip totem
- ✅ Instant protection in emergencies
- ✅ Focus on combat, not inventory
- ✅ Saves hardcore worlds
- ✅ Competitive advantage in PvP
- ✅ Configurable for any playstyle

## Technical Details

### How It Works
- Monitors player health every tick
- Detects fatal damage before it kills
- Searches inventory for totems
- Swaps totem to offhand instantly
- Tracks totem consumption
- Re-equips after configurable delay

### Compatibility
- ✅ Singleplayer and multiplayer
- ✅ Server-side mod (clients don't need it)
- ✅ Works with modded totems
- ✅ Compatible with most mods
- ✅ No conflicts with vanilla mechanics

### Performance
- Minimal CPU usage (only checks when needed)
- No continuous inventory scanning
- Lightweight memory footprint
- Server-side processing

## Keybinds

**Toggle Auto Totem:** T (default)
- Can be changed in Controls menu
- Category: "Auto Totem"

## Chat Message Examples

```
[Auto Totem] Low health! Equipping totem...
```

```
[Auto Totem] Totem consumed! Equipping backup...
```

```
[Auto Totem] Fatal damage! Equipping totem!
```

```
[Auto Totem] No totem available!
```

```
[Auto Totem] Enabled
```

```
[Auto Totem] Disabled
```

## FAQ

**Q: Does this work on servers?**  
A: Yes! The mod only needs to be installed on the server.

**Q: Will this make me invincible?**  
A: No! You still need totems in your inventory. If you run out, you can still die.

**Q: Does this work in creative mode?**  
A: Yes, but it's not very useful since you can't die in creative.

**Q: Can I use this in hardcore?**  
A: Absolutely! This mod is perfect for hardcore survival.

**Q: Does this work with modded totems?**  
A: It works with vanilla Totem of Undying. Modded totems may or may not work depending on implementation.

**Q: Will this get me banned on servers?**  
A: If the server allows the mod, no. Check server rules first.

**Q: Does this work in PvP?**  
A: Yes! Many players use this for competitive advantage.

**Q: Can I disable messages but keep the mod active?**  
A: Yes! Set "Show Messages" to false in the config.

## Known Limitations

- Only works with vanilla Totem of Undying
- Requires totem in inventory (doesn't create totems)
- May not work with some death-related mods
- Requires Forge (not compatible with Fabric)

## Comparison to Alternatives

### vs. Manual Totem Management
- **Auto Totem:** Instant, automatic, never forget
- **Manual:** Slow, requires attention, easy to forget
- **Winner:** Auto Totem

### vs. Other Auto Totem Mods
- **This Mod:** Low health detection + fatal damage protection + configurable
- **Others:** Usually only one trigger method
- **Winner:** This mod (more features)

### vs. Vanilla
- **Auto Totem:** Automatic protection
- **Vanilla:** Manual management only
- **Winner:** Auto Totem (convenience)

## Use Cases

### Hardcore Survival
Set health threshold to 6.0 hearts for balanced protection without being overpowered.

### PvP Combat
Set health threshold to 10.0 hearts and 0 tick delay for maximum competitive advantage.

### Exploration
Enable all features for worry-free exploration of dangerous areas.

### Boss Fights
Perfect for Ender Dragon, Wither, and modded boss fights.

### Speedrunning
Reduce deaths and save time with automatic totem management.

## Credits

**Author:** Itamio  
**Package:** `asd.itamio.autototem`  
**Version:** 1.0.0  
**Minecraft:** 1.12.2  
**Mod Loader:** Forge

## License

All rights reserved.

---

**Never die unexpectedly again!** 💀➡️💚

