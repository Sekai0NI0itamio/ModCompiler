# Instant Leaf Decay - Minecraft 1.12.2 Forge Mod

## Description
Tired of waiting for leaves to slowly decay after cutting down a tree? This mod makes leaves decay instantly when you break a log! All drops are collected and stacked at the center to reduce lag.

## Features
- ✅ Instant leaf decay when tree is cut down
- ✅ No particles (reduces lag significantly)
- ✅ Items stack and drop at center location
- ✅ Works with all tree types (oak, birch, spruce, jungle, acacia, dark oak)
- ✅ Respects player-placed leaves (won't decay)
- ✅ Lightweight and efficient
- ✅ No configuration needed

## How It Works

1. Break a log from a tree
2. All leaves that should decay are detected instantly
3. Leaves are removed without particles
4. All drops (saplings, apples, sticks) are collected
5. Items are stacked and dropped at the center of the leaf cluster

## Installation

```bash
# Copy to your Minecraft mods folder
cp "Mod Developement/1.12.2-forge/ReadyMods/Instant-Leaf-Decay-1.0.0.jar" \
   ~/Library/Application\ Support/minecraft/mods/
```

## Testing Guide

### Test 1: Oak Tree
1. Find or grow an oak tree
2. Break the bottom log
3. ✅ All leaves should instantly disappear
4. ✅ Saplings and apples drop at center
5. ✅ No leaf particles

### Test 2: Large Tree
1. Find a large tree (jungle or dark oak)
2. Break a log
3. ✅ All connected leaves decay instantly
4. ✅ Items stack properly
5. ✅ No lag from particles

### Test 3: Player-Placed Leaves
1. Place leaves manually
2. Break a nearby log
3. ✅ Player-placed leaves should NOT decay
4. ✅ Only natural leaves decay

### Test 4: Multiple Trees
1. Find trees close together
2. Break a log from one tree
3. ✅ Only that tree's leaves decay
4. ✅ Other trees remain intact

## Technical Details

- **Package**: asd.itamio.instantleafdecay
- **Author**: Itamio
- **Version**: 1.0.0
- **Minecraft**: 1.12.2
- **Mod Loader**: Forge

## Performance

This mod is designed for performance:
- No particle rendering (major lag reduction)
- Items are stacked before spawning
- Efficient leaf detection algorithm
- Single drop location reduces entity count

## Compatibility

- Works with all vanilla tree types
- Compatible with most tree mods
- No known conflicts
- Server-side only (clients don't need it)

## Notes

- Leaves must be "decayable" (not player-placed) to decay
- Decay triggers when a log is broken
- 8-block radius search for leaves
- 4-block radius check for nearby logs
- 100ms delay to ensure log break completes

## Version History

### 1.0.0 - Initial Release
- Instant leaf decay on tree cutting
- No particles for performance
- Stacked item drops at center
- Support for all vanilla trees

## License
Feel free to use and modify as needed.
