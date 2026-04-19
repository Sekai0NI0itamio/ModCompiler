# Ore Highlighter - Testing Guide

## Overview
Makes ores glow so you can easily spot them while mining! No more missing diamonds in the dark.

## How It Works
- Scans for ores within 16 blocks of the player
- Makes them emit light (level 14 by default)
- Updates every second
- Toggle on/off with H key

## Supported Ores
- Coal
- Iron
- Gold
- Diamond
- Emerald
- Lapis Lazuli
- Redstone
- Nether Quartz

## Testing Instructions

1. **Install the mod** - Copy `Ore-Highlighter-1.0.0.jar` to your mods folder

2. **Test in a cave**:
   - Go underground or into a cave
   - Ores within 16 blocks should start glowing
   - Press H to toggle on/off

3. **What to expect**:
   - Ores emit light (like torches)
   - You can see them through darkness
   - Light updates every second
   - Toggle message appears when pressing H

4. **Things to verify**:
   - ✓ Ores glow when nearby
   - ✓ All ore types work (coal, iron, gold, diamond, etc.)
   - ✓ H key toggles the feature
   - ✓ Toggle message shows ON/OFF status
   - ✓ Light disappears when ore is mined
   - ✓ Works in caves and underground
   - ✓ No performance issues

## Configuration
Config file: `config/orehighlighter.cfg`

- `enableHighlighter` - Enable/disable the mod (default: true)
- `searchRange` - Range to search for ores (default: 16 blocks)
- `lightLevel` - Light level for ores (default: 14, range 1-15)
- `highlightCoal/Iron/Gold/Diamond/Emerald/Lapis/Redstone/Quartz` - Enable per ore type

## Controls
- **H Key** - Toggle ore highlighting on/off

## Performance Notes
- Scans every second (not every tick)
- Only scans within configured range
- Minimal performance impact
- Light updates are handled by Minecraft's lighting engine

## Known Limitations
- Only works within 16 blocks (configurable)
- Requires server-side installation
- Light updates every second (not instant)

## Testing Checklist
- [ ] Ores glow in caves
- [ ] All ore types work
- [ ] H key toggles feature
- [ ] Toggle message displays
- [ ] Light disappears when mined
- [ ] Config options work
- [ ] No lag or performance issues
