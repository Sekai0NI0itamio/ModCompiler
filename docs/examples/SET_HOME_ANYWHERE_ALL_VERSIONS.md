# Set Home Anywhere - All Versions Port

## Project Overview
- **Modrinth**: https://modrinth.com/mod/set-home-anywhere
- **Type**: Server-side utility mod
- **Functionality**: `/sethome <name>`, `/home <name|list>`, `/delhome <name>`
- **Source of Truth**: 1.12.2 Forge 1.0.1 (original working version)
- **Total Versions on Modrinth**: 79 (40 working, 39 ghost shells < 5KB)
- **Target**: Replace 39 ghost shell versions with working implementations

## Initial Analysis

### Ghost Shell Detection
```bash
# Found 39 versions with < 5000 bytes (ghost shells)
# All versions 1.0.1+ labeled "Fixed Corrupted" are working (40 versions)
# Need to port to: 1.8.9, 1.12.2, 1.16.5, 1.17-1.21.11 (Forge/Fabric/NeoForge)
```

### Source Code Analysis (1.12.2 Forge 1.0.1)
- **Storage**: `WorldSavedData` with NBT serialization
- **Data Structure**: `Map<String, Map<String, double[]>>` (UUID → home name → [x,y,z,yaw,pitch])
- **Config**: `maxHomes` (default -1 = unlimited)
- **Commands**: Brigadier-based (1.16.5+) or CommandBase (1.8.9-1.12.2)

## Build History & Lessons Learned

### Run 1-10: Initial API Adaptations
**Status**: Fixed most versions, 23/39 green

**Key API Changes Discovered**:
1. **1.8.9**: `MapStorage.loadData/setData` (not `getOrLoadData`)
2. **1.12.2**: `getOrLoadData/setData` pattern
3. **1.16.5**: `DimensionSavedDataManager` with `get/set` pattern
4. **1.17+**: `SavedData` (not `WorldSavedData`), `setDirty()` (not `markDirty()`)
5. **1.19+**: `Component.literal()` replaces `TextComponent`
6. **1.20+**: `sendSuccess()` takes `Supplier<Component>`
7. **1.21.0-1.21.1**: `save(CompoundTag, HolderLookup.Provider)` signature

**Lesson 1**: Always check the source of truth version (1.12.2) first to understand the core logic.

**Lesson 2**: Server-side mods are simpler than client mods - no rendering, just commands and data storage.

### Run 11: SavedData API Split Discovery
**Status**: 25/39 green (1.21.1 Forge/NeoForge now working)

**Problem**: 1.21.2-1.21.11 all failing with:
```
error: method computeIfAbsent in class DimensionDataStorage cannot be applied to given types;
  required: SavedDataType<T>
  found:    SavedData.Factory<HomeData>
```

**Discovery**: The `SavedData.Factory` API was removed in 1.21.2-1.21.8, then a completely different `SavedDataType` API was introduced.

**Attempted Fix**: Used old 3-argument `computeIfAbsent(load, new, NAME)` pattern from 1.17-1.20.x
- **Result**: FAILED - API doesn't exist in 1.21.2+

**Lesson 3**: Major API changes can happen mid-version range (1.21.1 → 1.21.2)

**Lesson 4**: Don't assume API patterns - always verify with actual source code or documentation

### Run 12: SavedDataType Investigation
**Status**: Still 25/39 green

**Problem**: Tried to use `SavedDataType` based on NeoForge docs:
```java
private static final SavedDataType<HomeData> TYPE = 
    new SavedDataType<>(NAME, HomeData::new, (tag, provider) -> HomeData.load(tag));
return storage.computeIfAbsent(TYPE);
```

**Error**:
```
error: cannot find symbol
import net.minecraft.world.level.saveddata.SavedData.SavedDataType;
  symbol:   class SavedDataType
  location: class SavedData
```

**Discovery**: `SavedDataType` doesn't exist in Forge 1.21.2-1.21.8 as a nested class of `SavedData`

**Lesson 5**: NeoForge and Forge APIs diverged significantly in 1.21.2+

**Lesson 6**: Documentation for one mod loader (NeoForge) may not apply to another (Forge)

**Critical Mistake**: Trying to fix blindly without checking actual Minecraft/Forge source code

## The Right Approach: GitHub Source Code Search

### Problem
We were guessing at API signatures based on:
- Error messages (incomplete information)
- Documentation (may be for wrong mod loader or version)
- Assumptions from similar versions (APIs change)

### Solution
**Search actual Minecraft/Forge source code on GitHub** to see:
1. What classes/methods actually exist
2. Exact method signatures
3. How they're meant to be used
4. Real examples from Minecraft itself

### Workflow Created
See: `.github/workflows/grep-minecraft-source.yml`

Usage:
```bash
python3 scripts/grep_minecraft_source.py \
  --version "1.21.5" \
  --loader "forge" \
  --query "SavedDataType" \
  --file-pattern "*.java"
```

This searches the actual decompiled Minecraft + Forge source code to find the truth.

## API Version Matrix

| Version Range | SavedData API | computeIfAbsent Signature | Notes |
|--------------|---------------|---------------------------|-------|
| 1.8.9 | `WorldSavedData` | `loadData(Class, String)` | MapStorage, no computeIfAbsent |
| 1.12.2 | `WorldSavedData` | `getOrLoadData(Class, String)` | MapStorage pattern |
| 1.16.5 | `WorldSavedData` | `get(Function, String)` + `set()` | DimensionSavedDataManager |
| 1.17-1.20.x | `SavedData` | `computeIfAbsent(load, new, NAME)` | 3-argument pattern |
| 1.21.0-1.21.1 | `SavedData` | `computeIfAbsent(Factory, NAME)` | Factory with save(tag, provider) |
| 1.21.2-1.21.8 | `SavedData` | **UNKNOWN** | SavedDataType doesn't exist in Forge |
| 1.21.9-1.21.11 | `SavedData` | **UNKNOWN** | Different from 1.21.1 |

## Current Status

### ✅ Working (25/39)
- 1.8.9 Forge
- 1.12.2 Forge
- 1.16.5 Forge
- 1.17.1 Forge
- 1.18.x Forge (1.18, 1.18.1, 1.18.2)
- 1.19.x Forge (1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4)
- 1.20.x Forge (all versions)
- 1.20.2-1.20.6 NeoForge
- 1.21.0-1.21.1 Forge
- 1.21.0-1.21.1 NeoForge

### ❌ Failed (14/39)
- 1.21.2-1.21.8 Forge (7 versions)
- 1.21.2-1.21.8 NeoForge (7 versions)

**Reason**: SavedDataType API not found - needs source code investigation

## Next Steps

1. ✅ Create GitHub source search workflow
2. ⏳ Search for `DimensionDataStorage` in Forge 1.21.5 source
3. ⏳ Find actual `computeIfAbsent` method signature
4. ⏳ Search for `SavedData` usage examples in Minecraft code
5. ⏳ Implement correct API for 1.21.2-1.21.8
6. ⏳ Test and verify all 14 remaining versions
7. ⏳ Document final solution

## Key Takeaways for Future AI Agents

### ❌ DON'T:
1. **Don't guess API signatures** from error messages alone
2. **Don't assume APIs are consistent** across minor versions (1.21.1 vs 1.21.2)
3. **Don't trust documentation** without verifying it matches your mod loader
4. **Don't try fixes blindly** - each failed build wastes 4-5 minutes
5. **Don't mix NeoForge and Forge APIs** - they diverged in 1.21.2+

### ✅ DO:
1. **Search actual source code** using GitHub grep workflow
2. **Check the source of truth version** (usually 1.12.2 for older mods)
3. **Read template files** in version folders (e.g., `1.21.2-1.21.8/forge/template/`)
4. **Test incrementally** - fix one version range at a time
5. **Document API changes** as you discover them
6. **Use `--failed-only` flag** to avoid rebuilding working versions

### 🎯 Best Practice Workflow:
```
1. Identify failing version range (e.g., 1.21.2-1.21.8)
2. Read error logs to understand the problem
3. Search actual source code using grep workflow
4. Read template files for that version range
5. Implement fix based on actual source code
6. Test with --failed-only flag
7. Document the solution
8. Move to next version range
```

## Generator Script
Location: `scripts/generate_sethome_bundle.py`

Key features:
- `--failed-only` flag to rebuild only failed targets
- Separate source variants for each API version
- `_opt()` helper for Optional getter fixes in 1.21.5+
- NBT save/load logic shared across all versions

## Build Command
```bash
python3 scripts/generate_sethome_bundle.py --failed-only
git add -A && git commit -m "Fix Set Home: <description>"
git push
python3 scripts/run_build.py incoming/set-home-anywhere-all-versions.zip \
  --modrinth https://modrinth.com/mod/set-home-anywhere
```

## Time Investment
- **Total Runs**: 12
- **Time per Run**: ~5 minutes
- **Total Time**: ~60 minutes
- **Versions Fixed**: 25/39 (64%)
- **Remaining**: 14 versions (all 1.21.2+)

**Estimated time to complete with source search**: +30 minutes = 90 minutes total

## Comparison to Sort Chest
| Metric | Sort Chest | Set Home Anywhere |
|--------|-----------|-------------------|
| Complexity | Medium (inventory GUI) | Low (commands only) |
| Runs to Complete | 10 | 12+ (in progress) |
| API Challenges | Moderate | High (1.21.2+ split) |
| Success Rate | 100% (27/27) | 64% (25/39) |
| Key Difficulty | Container sync | SavedData API changes |

**Lesson**: Server-side mods are NOT always simpler - data persistence APIs can be more complex than client rendering APIs.
