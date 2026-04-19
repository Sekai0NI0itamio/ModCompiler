# Minecraft 1.12.2 Forge Mod Ideas

A collection of mod ideas for future development. All mods follow the standard package structure `asd.itamio.<modname>` with author "Itamio".

## Quality of Life Mods

### 1. Auto Torch Placer
Automatically places torches from your inventory when light level is too low (prevents mob spawning while mining).

**Difficulty**: Medium
**Features**:
- Monitors light level around player
- Places torch when light < 7
- Consumes torches from inventory
- Configurable light threshold

### 2. Quick Stack
Press a key to quickly move matching items from your inventory to nearby chests (like Terraria).

**Difficulty**: Medium
**Features**:
- Keybind to trigger quick stack
- Scans nearby chests
- Moves matching items automatically
- Configurable range

### 3. Ore Highlighter
Makes ores glow slightly or adds a subtle outline when you're near them.

**Difficulty**: Hard (requires rendering)
**Features**:
- Highlights ores within range
- Configurable ore types
- Configurable highlight color
- Toggle on/off

### 4. Auto Tool Swap
Automatically switches to the correct tool when you start breaking a block.

**Difficulty**: Medium
**Features**:
- Detects block being broken
- Finds best tool in hotbar
- Switches automatically
- Configurable enable/disable

### 5. Instant Leaf Decay ✅ IMPLEMENTED
Leaves decay instantly when you cut down a tree (no waiting).

**Difficulty**: Easy
**Features**:
- Instant decay when tree is cut
- No particles (reduces lag)
- Items stack and drop at center
- Works with all tree types

**Status**: Completed - Version 1.0.0

## Farming/Resource Mods

### 6. Crop Growth Accelerator
Crops grow faster when you're nearby or sleeping.

**Difficulty**: Easy
**Features**:
- Configurable growth speed multiplier
- Radius-based effect
- Works while sleeping
- Configurable crop types

### 7. Auto Feeder
Animals automatically eat from nearby chests/hoppers to breed.

**Difficulty**: Medium
**Features**:
- Scans for animals and food sources
- Automatic feeding
- Configurable range
- Works with all breedable animals

### 8. Infinite Water Bucket
Water bucket never empties (configurable).

**Difficulty**: Easy
**Features**:
- Water bucket doesn't consume
- Configurable enable/disable
- Works in survival
- Optional lava bucket support

## Combat/Survival Mods

### 9. Auto Eat
Automatically eats food from inventory when hunger is low.

**Difficulty**: Easy
**Features**:
- Monitors hunger level
- Eats best food automatically
- Configurable hunger threshold
- Prioritizes food by saturation

### 10. Damage Indicators
Shows damage numbers when you hit mobs.

**Difficulty**: Hard (requires rendering)
**Features**:
- Floating damage numbers
- Color-coded by damage type
- Configurable display time
- Critical hit indicators

## Building Mods

### 11. Block Placer Extended
Place blocks in a line/wall/floor pattern with one click.

**Difficulty**: Medium
**Features**:
- Multiple placement modes
- Keybind to switch modes
- Preview before placing
- Consumes blocks from inventory

### 12. Instant Bridge
Hold a key while walking to place blocks beneath you automatically.

**Difficulty**: Easy
**Features**:
- Places blocks under player
- Works while walking/running
- Configurable block type
- Consumes blocks from inventory

## Death/Survival Mods

### 13. Keep Equipment on Death ⭐ HIGH PRIORITY
Keep your armor, tools, and weapons when you die (other items drop normally).

**Difficulty**: Easy
**Features**:
- Keep armor slots on death
- Keep hotbar tools/weapons (configurable)
- Keep offhand item (configurable)
- Other items drop normally
- XP handling (configurable)
- Toggle keybind
- No new blocks or art needed

### 14. Auto Totem ⭐ HIGH PRIORITY
Automatically equips Totem of Undying to offhand when available.

**Difficulty**: Easy
**Features**:
- Auto-equip totem to offhand
- Toggle keybind
- Works when health is low
- No new blocks or art needed

### 15. Simple Death Improvements
Items never despawn, drop in one location, avoid lava/void.

**Difficulty**: Easy
**Features**:
- Items never despawn on death
- Items drop in one location (no scatter)
- Items avoid lava/void (teleport to safe spot)
- No new blocks or art needed

## Completed Mods

1. ✅ **No Hostile Mobs** - Prevents hostile mob spawning (configurable)
2. ✅ **Area Dig** - Enchantment that breaks blocks in a cube around mined block
3. ✅ **Auto Replant** - Automatically replants crops and trees when harvested
4. ✅ **Instant Leaf Decay** - Leaves decay instantly with no particles
5. ✅ **Auto Eat** - Automatically eats food when hunger is low
6. ✅ **Crop Growth Accelerator** - Crops grow faster near player
7. ✅ **Infinite Water Bucket** - Water bucket never empties
8. ✅ **Instant Bridge** - Places blocks beneath you while walking
9. ✅ **Auto Tool Swap** - Switches to correct tool automatically
10. ✅ **Quick Stack** - Terraria-style quick stacking to nearby chests
11. ✅ **Auto Torch Placer** - Places torches when light level is low
12. ✅ **Auto Feeder** - Animals automatically eat from nearby chests
13. ✅ **Vein Miner** - Mine entire ore veins at once (performance optimized)

## Development Priority

**Easy & High Impact** (Recommended Next):
1. Auto Eat
2. Crop Growth Accelerator
3. Infinite Water Bucket
4. Instant Bridge

**Medium Complexity**:
1. Auto Tool Swap
2. Quick Stack
3. Block Placer Extended
4. Auto Feeder

**Advanced** (Requires rendering/complex logic):
1. Ore Highlighter
2. Damage Indicators
3. Auto Torch Placer (with light level detection)

## Notes

- All mods use package structure: `asd.itamio.<modname>`
- Author: Itamio
- Target: Minecraft 1.12.2 Forge
- Focus on performance and minimal lag
- Remove debug logging before release
- Include source code in Modrinth uploads
