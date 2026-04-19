# Keep Equipment - Death Protection Mod

**Version:** 1.0.0  
**Minecraft Version:** 1.12.2  
**Author:** Itamio

## Overview

Keep Equipment is a simple yet powerful mod that lets you keep your armor, tools, and weapons when you die. No more losing your precious diamond gear to lava or the void! Highly configurable to match your preferred balance between convenience and challenge.

## Key Features

### What You Can Keep
- ✅ **Armor** - Helmet, Chestplate, Leggings, Boots (individually configurable)
- ✅ **Hotbar** - All items or just tools/weapons (configurable)
- ✅ **Offhand** - Shield, totem, or whatever you're holding
- ✅ **Main Inventory** - All inventory rows (enabled by default)
- ✅ **Experience** - Keep all, keep percentage, or lose all (keep all by default)
- ✅ **Potion Effects** - Keep all active effects (enabled by default)

### Quality of Life
- 🎮 **Toggle Keybind** - Press 'K' to enable/disable on the fly
- 💬 **Death Messages** - Shows what you kept when you die
- ⚙️ **Highly Configurable** - 15+ config options
- 🚫 **No New Blocks** - No textures or art needed
- 📦 **Lightweight** - Minimal performance impact

## How to Use

### Basic Usage
1. Install the mod
2. Die in Minecraft
3. Respawn with your equipment intact!
4. Other items (food, blocks, etc.) drop normally

### Toggle On/Off
- Press 'K' (configurable keybind)
- Chat message confirms current state
- Useful when you want vanilla death behavior

### Default Behavior
By default, you keep:
- All armor pieces
- All hotbar items
- Offhand item
- Main inventory (rows 1-3)
- All XP
- All potion effects

This provides maximum convenience while still requiring you to respawn.

## Configuration

Config file: `config/keepequipment.cfg`

### General Settings
```
Enable Keep Equipment (default: true)
- Master on/off switch
```

### Armor Settings
```
Keep Helmet (default: true)
Keep Chestplate (default: true)
Keep Leggings (default: true)
Keep Boots (default: true)
```

### Hotbar Settings
```
Keep Hotbar (default: true)
- Keep hotbar items on death

Keep Only Tools (default: false)
- If true: Only keep tools/weapons/useful items
- If false: Keep ALL hotbar items
```

**What counts as "tools":**
- All tools (pickaxe, axe, shovel, hoe)
- All weapons (sword, bow)
- Shields
- Shears
- Flint and steel
- Fishing rod

### Offhand Settings
```
Keep Offhand (default: true)
- Keep offhand item (shield, totem, etc.)
```

### Inventory Settings
```
Keep Main Inventory (default: true)
- Keep all main inventory items (rows 1-3)
- Enabled by default for maximum convenience
```

### Experience Settings
```
Keep All XP (default: true)
- Keep all experience levels and points

XP Kept Percentage (default: 0%, range: 0-100%)
- Percentage of XP to keep if not keeping all
- Example: 50% means you keep half your XP
```

### Potion Effects Settings
```
Keep Potion Effects (default: true)
- Keep all active potion effects (buffs and debuffs)
- Includes effects from potions, beacons, and other sources
```

### Message Settings
```
Show Death Message (default: true)
- Show message when you die

Show Kept Items (default: true)
- List what was kept in the death message
```

## Configuration Examples

### Maximum Convenience (Default)
```
Keep Armor: true
Keep Hotbar: true
Keep Only Tools: false
Keep Offhand: true
Keep Main Inventory: true
Keep All XP: true
Keep Potion Effects: true
```
**Result:** Keep everything! Death is just a respawn.

### Balanced Survival
```
Keep Armor: true
Keep Hotbar: true
Keep Only Tools: true
Keep Offhand: true
Keep Main Inventory: false
Keep All XP: false
XP Kept Percentage: 0%
Keep Potion Effects: false
```
**Result:** Keep equipment, lose everything else including XP and effects

### Casual Play
```
Keep Armor: true
Keep Hotbar: true
Keep Only Tools: false
Keep Offhand: true
Keep Main Inventory: false
Keep All XP: false
XP Kept Percentage: 50%
Keep Potion Effects: true
```
**Result:** Keep equipment, all hotbar items, and effects; keep half your XP

### Hardcore Lite
```
Keep Armor: true
Keep Hotbar: true
Keep Only Tools: true
Keep Offhand: false
Keep Main Inventory: false
Keep All XP: false
XP Kept Percentage: 25%
Keep Potion Effects: false
```
**Result:** Keep armor and tools only, keep 25% XP, lose effects

### Creative-Like (Same as Default)
```
Keep Armor: true
Keep Hotbar: true
Keep Only Tools: false
Keep Offhand: true
Keep Main Inventory: true
Keep All XP: true
Keep Potion Effects: true
```
**Result:** Keep everything (no death penalty)

## Why This Mod?

### Problem It Solves
- **Frustration:** Losing hours of work to one mistake
- **Lava Deaths:** Diamond gear gone forever
- **Void Deaths:** Items disappear completely
- **Long Runbacks:** Dying far from home

### Balanced Approach
- **Maximum Convenience by Default:** Keep everything to eliminate death frustration
- **Highly Configurable:** Adjust to your preferred difficulty
- **Toggle On/Off:** Press 'K' to enable vanilla death when desired
- **Fair Options:** Can be configured for balanced gameplay

## Technical Details

### How It Works
- Uses Forge's `PlayerEvent.Clone` event
- Copies items from old player to new player on death
- Only triggers on actual death (not End portal return)
- Server-side processing ensures no cheating

### Compatibility
- ✅ Works in singleplayer and multiplayer
- ✅ Server-side mod (clients don't need it)
- ✅ Compatible with most mods
- ✅ No conflicts with vanilla mechanics
- ✅ Works with modded armor and tools

### Performance
- Minimal CPU usage (only runs on death)
- No continuous background processing
- No network overhead
- Lightweight memory footprint

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. Configure settings in `config/keepequipment.cfg` if desired
5. Press 'K' in-game to toggle on/off

## Keybinds

**Toggle Keep Equipment:** K (default)
- Can be changed in Controls menu
- Category: "Keep Equipment"

## Chat Commands

No commands needed! Everything is handled automatically and through the config file.

## Death Message Examples

```
[Keep Equipment] Kept 7 item(s): Helmet, Chestplate, Leggings, Boots, Hotbar Tools, Offhand
```

```
[Keep Equipment] Kept 12 item(s): Helmet, Chestplate, Leggings, Boots, Hotbar, Offhand, 50% XP
```

## FAQ

**Q: Does this work on servers?**  
A: Yes! The mod only needs to be installed on the server. Clients don't need it.

**Q: Can I keep some items but not others?**  
A: Yes! Every slot type is individually configurable.

**Q: Does this work with modded armor?**  
A: Yes! It works with any item in armor slots.

**Q: What if I want vanilla death behavior sometimes?**  
A: Press 'K' to toggle the mod on/off anytime.

**Q: Does this prevent item drops entirely?**  
A: No! Only configured items are kept. Everything else drops normally.

**Q: Can I keep my entire inventory?**  
A: Yes, enable "Keep Main Inventory" in the config. But this is very overpowered!

**Q: Does this work in creative mode?**  
A: Creative mode already keeps inventory, so this mod isn't needed there.

## Known Limitations

- Does not create gravestones or markers
- Does not prevent XP loss unless configured
- Does not work with some death-related mods that override vanilla behavior
- Requires Forge (not compatible with Fabric)

## Comparison to Alternatives

### vs. /gamerule keepInventory
- **Keep Equipment:** Selective (only equipment)
- **keepInventory:** All or nothing
- **Winner:** Keep Equipment (more balanced)

### vs. Gravestone Mods
- **Keep Equipment:** Instant, no retrieval needed
- **Gravestone:** Must return to death point
- **Winner:** Depends on preference

### vs. Vanilla
- **Keep Equipment:** Keep equipment, lose resources
- **Vanilla:** Lose everything
- **Winner:** Keep Equipment (less frustrating)

## Credits

**Author:** Itamio  
**Package:** `asd.itamio.keepequipment`  
**Version:** 1.0.0  
**Minecraft:** 1.12.2  
**Mod Loader:** Forge

## License

All rights reserved.

---

**Enjoy never losing your diamond armor to lava again!** 💎🔥
