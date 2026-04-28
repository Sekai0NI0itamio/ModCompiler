# Optimized Vein Miner — All-Versions Port

## Overview

**Mod**: Optimized Vein Miner (https://modrinth.com/mod/optimized-vein-miner)  
**Starting coverage**: 54 versions (1.8.9 through 1.21.8, partial loaders)  
**Final coverage**: 72 versions — all supported MC versions 1.8.9–1.21.11 across Forge, Fabric, NeoForge  
**Build runs**: ~12 runs to reach full coverage  
**Key lesson**: Always run the Profile Diagnosis workflow first. Never build targets already on Modrinth.

---

## Mod Description

Server-side vein mining mod. When a player breaks an ore or log, all connected
blocks of the same type are mined at once. Features: sneak-to-activate, toggle
keybind (V), durability/hunger consumption, configurable block limits, single
sound, items dropped at one location.

- Server-side logic (block break event handler)
- Client-side keybind (toggle key, V)
- 4 classes: `VeinMinerMod`, `VeinMinerHandler`, `VeinMinerKeyHandler`, `VeinMinerConfig`
- No mixins — pure event-based

---

## Critical Workflow Mistakes Made (Learn From These)

### Mistake 1: Not running the diagnosis workflow first

**What happened**: Jumped straight into writing source code for all versions
without first checking what was already published on Modrinth.

**Result**: Wasted multiple build runs rebuilding already-published versions,
risking duplicate uploads and wasting GitHub Actions minutes.

**Correct workflow**:
1. Run `fetch_modrinth_project.py` (or the Profile Diagnosis workflow) first
2. Compare published versions against `version-manifest.json`
3. Build ONLY the missing targets

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/optimized-vein-miner \
    --output-dir /tmp/diagnosis
cat /tmp/diagnosis/summary.txt
```

### Mistake 2: Using hand-written source instead of the existing generator script

**What happened**: Wrote all source files manually instead of using the existing
`scripts/generate_veinminer_bundle.py` which already had correct, tested source
for every version.

**Result**: Multiple compile failures from incorrect API usage that the generator
already had fixed.

**Correct workflow**: Always check `scripts/` for an existing generator before
writing source manually. Run it with `--failed-only` to regenerate only failed targets.

### Mistake 3: Rebuilding already-green targets on each retry

**What happened**: Used `--failed-only` from the generator but then included
already-published targets in the zip because the generator's failure list included
targets that failed in earlier runs (before they were published).

**Fix**: After each run, manually filter the zip to exclude already-published targets:

```python
import zipfile
already_published = {'VeinMiner-1.21-1.21.1-forge', 'VeinMiner-1.21.5-1.21.8-fabric', ...}
with zipfile.ZipFile('incoming/veinminer-all-versions.zip') as src:
    with zipfile.ZipFile('incoming/veinminer-missing-only.zip', 'w', zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            top = item.filename.split('/')[0]
            if top not in already_published:
                dst.writestr(item, src.read(item.filename))
```

---

## API Issues Encountered and Fixed

### Issue 1: Forge 1.17.1 — `RegisterKeyMappingsEvent` does not exist

**Error**:
```
error: cannot find symbol
  symbol: class RegisterKeyMappingsEvent
```

**Root cause**: `RegisterKeyMappingsEvent` was added in Forge 1.18+. In 1.17.1,
key bindings are registered via `ClientRegistry.registerKeyBinding()` in the
constructor.

**Wrong (1.18+ API)**:
```java
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
```

**Correct (1.17.1)**:
```java
import net.minecraftforge.fmlclient.registry.ClientRegistry;
public VeinMinerKeyHandler() {
    ClientRegistry.registerKeyBinding(toggleKey);
}
```

Also: the `VeinMinerMod` for 1.17.1 must NOT call `bus.addListener(VeinMinerKeyHandler::register)` — just register the handler instance directly in `setup()`.

**Verified in**: `DecompiledMinecraftSourceCode/1.17.1-forge/net/minecraftforge/fmlclient/registry/ClientRegistry.java`

---

### Issue 2: Fabric 1.19–1.19.2 — Registry package path

**Error**:
```
error: package net.minecraft.util.registry does not exist
```
or
```
error: cannot find symbol — class Registries (in package net.minecraft.registry)
```

**Root cause**: The registry package moved between Fabric yarn versions:

| MC Version | Yarn path | Access pattern |
|------------|-----------|----------------|
| 1.16.5–1.18.2 | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` |
| 1.19–1.19.2 | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` |
| 1.19.3–1.20.x | `net.minecraft.registry.Registries` | `Registries.BLOCK.getId(b)` |
| 1.21+ | `net.minecraft.core.registries.BuiltInRegistries` (Mojang) | `BuiltInRegistries.BLOCK.getKey(b)` |

**Critical detail**: The `1.19-1.19.4` fabric template uses `anchor_version=1.19.4`
with `yarn_mappings=1.19.4+build.2`. When building for 1.19/1.19.1/1.19.2, the
adapter sets `minecraft_version=1.19` in gradle.properties. Loom then downloads
the yarn for that exact MC version — which is the **old** `net.minecraft.util.registry`
path, NOT the 1.19.4 path.

**Fix**: Add dependency overrides in `version-manifest.json` for 1.19, 1.19.1, 1.19.2
to pin their yarn and fabric-api versions, AND use the old registry path in source:

```json
"dependency_overrides": {
  "1.19": {
    "yarn_mappings": "1.19+build.1",
    "fabric_version": "0.58.0+1.19",
    "minecraft_version": "1.19"
  },
  "1.19.1": {
    "yarn_mappings": "1.19.1+build.6",
    "fabric_version": "0.58.5+1.19.1",
    "minecraft_version": "1.19.1"
  },
  "1.19.2": {
    "yarn_mappings": "1.19.2+build.28",
    "fabric_version": "0.77.0+1.19.2",
    "minecraft_version": "1.19.2"
  }
}
```

Source for 1.19–1.19.2 fabric:
```java
import net.minecraft.util.registry.Registry;
// ...
String n = Registry.BLOCK.getId(b).toString();
```

Source for 1.19.3–1.19.4 fabric:
```java
import net.minecraft.registry.Registries;
// ...
String n = Registries.BLOCK.getId(b).toString();
```

**Note**: The decompiled sources in `DecompiledMinecraftSourceCode/1.19-fabric/`
show `net.minecraft.registry.Registries` because they were generated with 1.19.4
yarn. Do NOT trust the decompiled sources for the exact package path when the
template uses `anchor_only` mode — check what yarn version the build actually
downloads for that exact MC version.

---

### Issue 3: Forge/NeoForge 1.21.9+ — `KeyMapping` constructor requires `Category`

**Error**:
```
error: incompatible types: String cannot be converted to Category
```

**Root cause**: In 1.21.9+, `KeyMapping` changed its constructor. The third
argument is now `KeyMapping.Category` (a record), not a plain `String`.

**Wrong (pre-1.21.9)**:
```java
new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner")
```

**Correct (1.21.9+)**:
```java
new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC)
```

Use one of the built-in categories: `MISC`, `MOVEMENT`, `GAMEPLAY`, `INVENTORY`, etc.
Do NOT try to call `KeyMapping.Category.lookup()` or `KeyMapping.Category.create()` —
those methods do not exist.

**Verified in**: `DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraft/client/KeyMapping.java`

```java
// Available constructors in 1.21.9+:
public KeyMapping(String description, int keyCode, KeyMapping.Category category)
public KeyMapping(String description, InputConstants.Type inputType, int keyCode, KeyMapping.Category category)
```

This applies to: Forge 1.21.9–1.21.11, NeoForge 1.21.9–1.21.11, Fabric 1.21.9–1.21.11.

For **Fabric** 1.21.9+, use `KeyBindingHelper.registerKeyBinding()` which wraps the
`KeyMapping` — still pass `KeyMapping.Category.MISC` as the category:
```java
toggleKey = KeyBindingHelper.registerKeyBinding(
    new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC)
);
```

---

### Issue 4: NeoForge 1.21.9+ — `KeyMapping(String, KeyConflictContext, Key, String)` constructor missing

**Error**:
```
error: no suitable constructor found for KeyMapping(String,KeyConflictContext,Key,String)
```

**Root cause**: The NeoForge-specific `KeyMapping` constructor that takes
`KeyConflictContext` and `InputConstants.Key` was removed in 1.21.9. Use the
vanilla constructor with `KeyMapping.Category` instead (see Issue 3 above).

---

### Issue 5: NeoForge 1.21.9+ — `ModContainer` required in constructor

**Root cause**: In NeoForge 26.x / 1.21.9+, the `@Mod` class constructor must
accept `(IEventBus modBus, ModContainer modContainer)` — the `ModContainer`
parameter is required.

**Wrong (pre-1.21.9)**:
```java
public VeinMinerMod(IEventBus modBus) { ... }
```

**Correct (1.21.9+)**:
```java
public VeinMinerMod(IEventBus modBus, ModContainer modContainer) { ... }
```

---

### Issue 6: Forge 1.21.9+ — `BlockEvent.BreakEvent` uses `getLevel()` not `getWorld()`

**Root cause**: In 1.21.9+, `BlockEvent.BreakEvent` no longer has `getWorld()`.
Use `getLevel()` and cast to `ServerLevel` directly:

```java
Level world = (Level) event.getLevel();
if (!(world instanceof net.minecraft.server.level.ServerLevel)) return;
```

Do NOT use `world.isClientSide` — use `instanceof ServerLevel` check instead.

---

### Issue 7: Forge 26.x — `FMLJavaModLoadingContext` constructor changed

**Root cause**: In Forge 26.x (Minecraft 26.1+), the `@Mod` class constructor
receives `FMLJavaModLoadingContext context` as a parameter instead of using
`FMLJavaModLoadingContext.get()`.

**Wrong (pre-26.x)**:
```java
public VeinMinerMod() {
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
}
```

**Correct (26.x)**:
```java
public VeinMinerMod(FMLJavaModLoadingContext context) {
    context.getModEventBus().addListener(VeinMinerKeyHandler::register);
    MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
    MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
}
```

---

### Issue 8: Fabric 1.19.1 — wrong fabric-api version in dependency overrides

**Error**:
```
Could not find net.fabricmc.fabric-api:fabric-api:0.60.0+1.19.1
```

**Root cause**: The fabric-api version `0.60.0+1.19.1` does not exist on Maven.

**Fix**: Use `0.58.5+1.19.1` for 1.19.1 and `0.77.0+1.19.2` for 1.19.2.

**How to find correct versions**: Search https://mvnrepository.com/artifact/net.fabricmc.fabric-api/fabric-api
or check https://www.curseforge.com/minecraft/mc-mods/fabric-api/files for the
exact version string.

---

### Issue 9: Fabric 1.19.2 — transient HTTP 502 from GitHub

**Error**:
```
Exception in thread "main" java.io.IOException: Server returned HTTP response code: 502
```

**Root cause**: GitHub infrastructure error downloading Gradle distribution.
Not a code issue — retry the build.

---

## Version-Specific API Reference Table

| MC Version | Loader | Registry import | Registry access | KeyMapping constructor |
|------------|--------|-----------------|-----------------|----------------------|
| 1.8.9 | Forge | `net.minecraft.init.Blocks` | `Blocks.coal_ore` | `new KeyBinding(name, key, category_string)` |
| 1.12.2 | Forge | `net.minecraft.init.Blocks` | `Blocks.COAL_ORE` | `new KeyBinding(name, key, category_string)` |
| 1.16.5 | Forge | `b.getRegistryName()` | `.toString()` | `new KeyBinding(name, GLFW_KEY, category_string)` |
| 1.16.5 | Fabric | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` | `new KeyBinding(name, GLFW_KEY, category_string)` |
| 1.17.1 | Forge | `net.minecraft.core.Registry` | `Registry.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, category_string)` |
| 1.17.1 | Fabric | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` | `new KeyBinding(name, GLFW_KEY, category_string)` |
| 1.18–1.19.2 | Forge | `net.minecraft.core.Registry` | `Registry.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, category_string)` |
| 1.18–1.19.2 | Fabric | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` | `new KeyBinding(name, GLFW_KEY, category_string)` |
| 1.19.3–1.20.x | Fabric | `net.minecraft.registry.Registries` | `Registries.BLOCK.getId(b)` | `new KeyBinding(name, GLFW_KEY, category_string)` |
| 1.20–1.21.8 | Forge | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, category_string)` |
| 1.20–1.21.8 | NeoForge | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, category_string)` |
| 1.21–1.21.8 | Fabric | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `new KeyMapping(name, InputConstants.Type.KEYSYM, GLFW_KEY, category_string)` |
| 1.21.9+ | Forge | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, KeyMapping.Category.MISC)` |
| 1.21.9+ | NeoForge | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `new KeyMapping(name, GLFW_KEY, KeyMapping.Category.MISC)` |
| 1.21.9+ | Fabric | `net.minecraft.core.registries.BuiltInRegistries` | `BuiltInRegistries.BLOCK.getKey(b)` | `KeyBindingHelper.registerKeyBinding(new KeyMapping(name, GLFW_KEY, KeyMapping.Category.MISC))` |

---

## Event Package Changes (Forge)

| MC Version | BlockEvent package |
|------------|-------------------|
| 1.12.2–1.19.4 | `net.minecraftforge.event.world.BlockEvent.BreakEvent` |
| 1.20–1.21.11 | `net.minecraftforge.event.level.BlockEvent.BreakEvent` |
| 1.21.9+ | `net.minecraftforge.event.level.BlockEvent.BreakEvent` + use `getLevel()` not `getWorld()` |

| MC Version | NeoForge BlockEvent package |
|------------|----------------------------|
| 1.20.2–1.21.11 | `net.neoforged.neoforge.event.level.BlockEvent.BreakEvent` |

---

## hurtAndBreak / tool damage API changes

| MC Version | Loader | Method |
|------------|--------|--------|
| 1.12.2 | Forge | `tool.damageItem(1, player)` |
| 1.16.5–1.19.4 | Forge | `tool.hurtAndBreak(1, player, p -> {})` |
| 1.20–1.21.4 | Forge/NeoForge | `tool.hurtAndBreak(1, player, p -> {})` |
| 1.21.5+ | Forge/NeoForge | `tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)` |
| 1.16.5–1.19.2 | Fabric | `tool.damage(1, player, p -> {})` |
| 1.19.3+ | Fabric | `tool.damage(1, player, EquipmentSlot.MAINHAND)` |

---

## Fabric Key Binding API changes

| MC Version | Key class | Registration |
|------------|-----------|-------------|
| 1.16.5 | `net.minecraft.client.options.KeyBinding` | `KeyBindingHelper.registerKeyBinding()` |
| 1.17.1–1.18.2 | `net.minecraft.client.option.KeyBinding` | `KeyBindingHelper.registerKeyBinding()` |
| 1.19–1.20.x | `net.minecraft.client.KeyMapping` | `KeyBindingHelper.registerKeyBinding()` |
| 1.21–1.21.8 | `net.minecraft.client.KeyMapping` | `KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, key, category_string))` |
| 1.21.9+ | `net.minecraft.client.KeyMapping` | `KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, KeyMapping.Category.MISC))` |

---

## Fabric `anchor_only` Mode Warning

When a fabric range uses `exact_dependency_mode: anchor_only` (e.g. `1.19-1.19.4`
anchored to 1.19.4), the template's `yarn_mappings` is set to the anchor version.
BUT the adapter overwrites `minecraft_version` in gradle.properties with the exact
version being built (e.g. `1.19`). Loom then downloads the yarn for that exact
MC version, which may be a different yarn generation than the anchor.

**Rule**: For `anchor_only` ranges, add explicit `dependency_overrides` for each
non-anchor version to pin their yarn and fabric-api versions. Without overrides,
the build will use whatever yarn Loom resolves for that MC version — which may
have different package paths than the anchor.

**Example fix in `version-manifest.json`**:
```json
"dependency_overrides": {
  "1.19": {
    "yarn_mappings": "1.19+build.1",
    "fabric_version": "0.58.0+1.19",
    "minecraft_version": "1.19"
  }
}
```

---

## Build Run Summary

| Run | Targets | Result | Issue |
|-----|---------|--------|-------|
| 1 | All 28 (hand-written) | Many failures | Wrong APIs, wrong registry paths |
| 2 | 6 missing (generator) | forge 1.21.6-1.21.8 ✓, rest fail | RegisterKeyMappingsEvent, Category, registry |
| 3 | 6 missing (generator fixed) | forge 1.21.6-1.21.8 ✓ again (already published), rest fail | Same errors — generator not fixed yet |
| 4 | 5 missing (generator fixed) | forge 1.17.1 ✓, fabric 1.19.3-1.19.4 ✓, rest fail | Category.lookup() doesn't exist |
| 5 | 4 missing | fabric 1.21.9-1.21.11 ✓, neoforge 1.21.9-1.21.11 ✓, forge 1.21.9-1.21.11 ✓, fabric 1.19-1.19.2 fail | Registry path wrong |
| 6 | 1.19-1.19.2 fabric | 1.19 ✓, 1.19.1 fail (wrong fabric-api), 1.19.2 fail (HTTP 502) | Wrong fabric-api version |
| 7 | 1.19-1.19.2 fabric | All 3 ✓ | Fixed fabric-api versions |

**Final result**: 72 versions published, all supported MC versions covered.

---

## Files Modified

- `scripts/generate_veinminer_bundle.py` — fixed source for all failing versions
- `version-manifest.json` — added dependency overrides for fabric 1.19, 1.19.1, 1.19.2
