# Stackable Totems (up to 64) — All Versions Port

## Overview

| Field | Value |
|-------|-------|
| Mod | Stackable Totems (up to 64) |
| Modrinth URL | https://modrinth.com/mod/stackable-totems-(up-to-64) |
| Mod ID | `stackabletotems` |
| Starting coverage | 1 version (Forge 1.20.1–1.20.6 as a single multi-version entry) |
| Final coverage | 42 versions |
| New versions added | 41 (across 4 build runs) |
| Build runs | 4 |
| Generator script | `scripts/generate_stackabletotems_bundle.py` |
| Completed | April 2026 |

## What the Mod Does

Stackable Totems of Undying is a **server-side only** Forge/NeoForge mod that:
1. Changes the Totem of Undying's max stack size from 1 to 64
2. When a totem is used (player would die), consumes only 1 from the stack instead of the whole item

Key characteristics:
- Server-side only (`runtime_side=server`)
- No client code, no keybinds, no GUI
- Uses `LivingUseTotemEvent` to intercept totem consumption
- Uses reflection to modify the totem's max stack size at startup
- Single Java class — no separate handler file

## Starting State (from diagnosis)

```bash
python3 scripts/fetch_modrinth_project.py \
    --project "https://modrinth.com/mod/stackable-totems-(up-to-64)" \
    --output-dir /tmp/stackable-totems-diag
cat /tmp/stackable-totems-diag/summary.txt
```

Already published: 1 version covering Forge 1.20.1–1.20.6 (all in one entry).

Missing targets (36 computed, 33 actually built after removing unsupported versions):

```
Forge:    1.19.3, 1.19.4,
          1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5,
          1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
          26.1.2
NeoForge: 1.20.2, 1.20.4, 1.20.5, 1.20.6,
          1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
          1.21.6, 1.21.7, 1.21.8,
          1.21.9, 1.21.10, 1.21.11,
          26.1, 26.1.1, 26.1.2
```

Note: 1.19, 1.19.1, 1.19.2 were initially included but removed after run 1
(see Challenge 1 below).

## Step-by-Step Session

### Step 1: Diagnose

```bash
python3 scripts/fetch_modrinth_project.py \
    --project "https://modrinth.com/mod/stackable-totems-(up-to-64)" \
    --output-dir /tmp/stackable-totems-diag
cat /tmp/stackable-totems-diag/summary.txt
```

Found: 1 version, Forge only, 1.20.1–1.20.6. Server-side only mod.

### Step 2: Inspect the published jar

```bash
curl -L "https://cdn.modrinth.com/data/f79mRG0L/versions/AYQH7cJD/stackabletotems-1.0.0.jar" \
    -o /tmp/stackabletotems-1.0.0.jar
jar tf /tmp/stackabletotems-1.0.0.jar
unzip -p /tmp/stackabletotems-1.0.0.jar META-INF/mods.toml
```

Findings:
- Single class: `net.itamio.stackabletotems.StackableTotemsMod`
- Group: `net.itamio.stackabletotems`
- Server-side only (`runtime_side=server`)

### Step 3: Look up APIs in DecompiledMinecraftSourceCode

```bash
# Find LivingUseTotemEvent across versions
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.19-forge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.19.4-forge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.20.1-forge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.21.6-forge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.21.9-forge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.20.2-neoforge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/1.21.9-neoforge/ --include="*.java" | head -3
grep -rn "LivingUseTotemEvent" DecompiledMinecraftSourceCode/26.1.2-neoforge/ --include="*.java" | head -3

# Find max stack size storage approach
grep -n "maxStackSize\|MAX_STACK_SIZE\|DataComponents\|components" \
    DecompiledMinecraftSourceCode/1.21.5-forge/net/minecraft/world/item/Item.java | head -20
grep -rn "MAX_STACK_SIZE" \
    DecompiledMinecraftSourceCode/1.20.5-neoforge/net/minecraft/component/ --include="*.java" | head -5

# Find EventBus 7 mod bus registration pattern
grep -n "modBusGroup\|BusGroup\|FMLCommonSetupEvent\|getModBusGroup" \
    DecompiledMinecraftSourceCode/1.21.6-forge/net/minecraftforge/common/ForgeMod.java | head -15
grep -n "modBusGroup\|BusGroup\|FMLCommonSetupEvent\|getModBusGroup" \
    DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraftforge/common/ForgeMod.java | head -10

# Check LivingUseTotemEvent structure in EventBus 7 era
cat DecompiledMinecraftSourceCode/1.21.6-forge/net/minecraftforge/event/entity/living/LivingUseTotemEvent.java
cat DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraftforge/event/entity/living/LivingUseTotemEvent.java
cat DecompiledMinecraftSourceCode/26.1.2-forge/net/minecraftforge/event/entity/living/LivingUseTotemEvent.java
```

### Step 4: Write the generator script

Created `scripts/generate_stackabletotems_bundle.py` using bash heredoc.

**Source string strategy:**

| Version range | Loader | Stack size approach | Event approach |
|--------------|--------|--------------------|-----------------|
| 1.19.3–1.20.4 | Forge | Reflection on `f_41370_` (SRG) / `maxStackSize` | `@SubscribeEvent` + `event.setCanceled(true)` |
| 1.20.5–1.21.5 | Forge | Reflection on `components` + `DataComponents.MAX_STACK_SIZE` | `@SubscribeEvent` + `event.setCanceled(true)` |
| 1.21.6–26.1.2 | Forge | Same DataComponents reflection | `LivingUseTotemEvent.BUS.addListener(true, handler)` + return true |
| 1.20.2–1.20.4 | NeoForge | Reflection on `f_41370_` / `maxStackSize` | `@SubscribeEvent` + `event.setCanceled(true)` |
| 1.20.5–1.21.8 | NeoForge | DataComponents reflection | `@SubscribeEvent` + `event.setCanceled(true)` |
| 1.21.9–1.21.11 | NeoForge | DataComponents reflection | Same + `ModContainer` in constructor |
| 26.1–26.1.2 | NeoForge | DataComponents reflection | Same + standalone `@EventBusSubscriber` |

### Step 5: Run 1 — first attempt (36 targets)

```bash
python3 scripts/generate_stackabletotems_bundle.py
git add scripts/generate_stackabletotems_bundle.py \
    incoming/stackabletotems-all-versions.zip \
    incoming/stackabletotems-all-versions/
git commit -m "Add Stackable Totems all-versions bundle (36 missing targets)"
git push
python3 scripts/run_build.py incoming/stackabletotems-all-versions.zip \
    --modrinth "https://modrinth.com/mod/stackable-totems-(up-to-64)" \
    --max-parallel all
```

**Result**: 27 passed, 9 failed.

Passed: all NeoForge targets, Forge 1.19.2–1.19.4, Forge 1.21–1.21.5

Failed:
- `forge-1-19`, `forge-1-19-1` — `LivingUseTotemEvent` not in Forge 41.x
- `forge-1-21-6` through `forge-1-21-11`, `forge-26-1-2` — `context.getModEventBus()` removed in EventBus 7

### Step 6: Diagnose run 1 failures

```bash
grep -A 5 "error:" ModCompileRuns/run-20260429-113458/artifacts/all-mod-builds/mods/stackabletotems-forge-1-19/build.log | head -20
grep -A 5 "error:" ModCompileRuns/run-20260429-113458/artifacts/all-mod-builds/mods/stackabletotems-forge-1-21-6/build.log | head -20
grep -A 5 "error:" ModCompileRuns/run-20260429-113458/artifacts/all-mod-builds/mods/stackabletotems-forge-1-21-9/build.log | head -20
```

**Errors found:**

| Target group | Error | Root cause |
|-------------|-------|-----------|
| Forge 1.19, 1.19.1 | `cannot find symbol: class LivingUseTotemEvent` | Forge 41.x doesn't have this event — added in 44.x (1.19.3) |
| Forge 1.21.6–26.1.2 | `cannot find symbol: method getModEventBus()` | EventBus 7 removed `getModEventBus()` — use `FMLCommonSetupEvent.getBus(context.getModBusGroup())` |

Also confirmed from build log: Forge 1.19 uses version `41.1.0`, Forge 1.19.3 uses `44.1.21`.

### Step 7: Fix and run 2 (7 targets)

Fixes applied:
1. Removed 1.19, 1.19.1, 1.19.2 from TARGETS entirely
2. Forge 1.21.6+ source: replaced `context.getModEventBus().addListener(this::setup)` with `FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup)`

```bash
python3 scripts/generate_stackabletotems_bundle.py
# Filter to only the 7 failed targets
python3 -c "
import zipfile
from pathlib import Path
already_green = { ... }  # 26 green targets from run 1
# ... filter zip ...
"
git add ... && git commit -m "Fix run 2 errors" && git push
python3 scripts/run_build.py incoming/stackabletotems-failed-only.zip \
    --modrinth "https://modrinth.com/mod/stackable-totems-(up-to-64)" \
    --max-parallel all
```

**Result**: All 7 passed. ✅

### Step 8: Verify final state

```bash
python3 scripts/fetch_modrinth_project.py \
    --project "https://modrinth.com/mod/stackable-totems-(up-to-64)" \
    --output-dir /tmp/stackabletotems-final
grep "Game versions\|Loaders\|Downloads" /tmp/stackabletotems-final/summary.txt
```

Output:
```
Game versions: 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
               1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
               1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
               26.1, 26.1.1, 26.1.2
Loaders: forge, neoforge
```

## Build Run Summary

| Run | Targets | Passed | Failed | Key Fix |
|-----|---------|--------|--------|---------|
| 1 | 36 | 27 | 9 | Initial attempt |
| 2 | 7 | 7 | 0 | Remove 1.19/1.19.1/1.19.2; fix EventBus7 mod bus registration |
| 3 | 7 | 6 | 1 | Add 1.16.5–1.19.1 reflection-only targets (vanilla handles stack shrink) |
| 4 | 1 | 1 | 0 | Fix 1.16.5: use `net.minecraft.item` not `net.minecraft.world.item` |

## API Reference Table

### LivingUseTotemEvent — Forge

| Forge version | Package | Pattern | Cancel method |
|---------------|---------|---------|---------------|
| 1.19.3–1.20.4 | `net.minecraftforge.event.entity.living` | `@SubscribeEvent` on instance method | `event.setCanceled(true)` |
| 1.20.5–1.21.5 | `net.minecraftforge.event.entity.living` | `@SubscribeEvent` on instance method | `event.setCanceled(true)` |
| 1.21.6–1.21.8 | `net.minecraftforge.event.entity.living` | `LivingUseTotemEvent.BUS.addListener(true, handler)` | return `true` |
| 1.21.9–26.1.2 | `net.minecraftforge.event.entity.living` | `LivingUseTotemEvent.BUS.addListener(true, handler)` | return `true` (record-based) |

### LivingUseTotemEvent — NeoForge

| NeoForge version | Package | Pattern | Cancel method |
|-----------------|---------|---------|---------------|
| 1.20.2–1.21.11 | `net.neoforged.neoforge.event.entity.living` | `@SubscribeEvent` on instance method | `event.setCanceled(true)` |
| 26.1–26.1.2 | `net.neoforged.neoforge.event.entity.living` | `@SubscribeEvent` on instance method | `event.setCanceled(true)` |

### Mod Bus Registration — Forge EventBus 7

| Forge version | Pattern |
|---------------|---------|
| 1.16.5–1.21.5 | `FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup)` |
| 1.21.6–26.1.2 | `FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup)` |

### Item Max Stack Size Modification

| MC Version | Approach |
|------------|---------|
| 1.19.3–1.20.4 | Reflection on `"f_41370_"` (SRG) or `"maxStackSize"` int field |
| 1.20.5+ | Reflection on `"components"` DataComponentMap field, then set `DataComponents.MAX_STACK_SIZE` |

### NeoForge Constructor

| NeoForge version | Constructor |
|-----------------|-------------|
| 1.20.2–1.21.8 | `public MyMod(IEventBus modBus)` |
| 1.21.9–26.1.2 | `public MyMod(IEventBus modBus, ModContainer modContainer)` |

## Files Modified

- `scripts/generate_stackabletotems_bundle.py` — created (generator script)
- `incoming/stackabletotems-all-versions.zip` — build bundle
- `incoming/stackabletotems-failed-only.zip` — failed-only bundle (run 2)

## New DIF Entries Added

| DIF ID | Issue |
|--------|-------|
| `FORGE-LIVINGUSETOTEM-NOT-IN-41X` | Forge 1.19–1.19.2 (41.x) doesn't have LivingUseTotemEvent |
| `FORGE-EB7-FMLCOMMONSETUPEVENT-GETBUS` | Forge 1.21.6+ EventBus 7 — use `FMLCommonSetupEvent.getBus(context.getModBusGroup())` |
| `FORGE-DATACOMPONENTS-ITEM-STACK-SIZE` | 1.20.5+ item max stack size stored in DataComponents, not a field |

## Key Lessons

1. **`LivingUseTotemEvent` was added in Forge 44.x (1.19.3), not 41.x (1.19).** The decompiled sources show it for 1.19 because they were regenerated with a newer Forge. Always check the actual Forge version in the build log, not just the decompiled sources.

2. **For 1.16.5–1.19.2, vanilla already handles the stack shrink.** `LivingEntity.tryUseTotem()` calls `itemStack.decrement(1)` / `shrink(1)` itself. You only need to set `maxStackSize=64` via reflection — no event subscription needed.

3. **Forge 1.21.6+ EventBus 7 removed `getModEventBus()`.** The new pattern for registering mod-bus events is `EventClass.getBus(context.getModBusGroup()).addListener(handler)`. This applies to `FMLCommonSetupEvent`, `RegisterEvent`, `GatherDataEvent`, etc.

4. **Item max stack size moved to DataComponents in 1.20.5.** Pre-1.20.5: reflect on `maxStackSize` int field. Post-1.20.5: reflect on `components` DataComponentMap and set `DataComponents.MAX_STACK_SIZE`.

5. **1.16.5 uses `net.minecraft.item` not `net.minecraft.world.item`.** The package rename happened in 1.17. Always use the correct package for the target version.

6. **NeoForge `LivingUseTotemEvent` stays in `net.neoforged.neoforge.event.entity.living` across all versions** — no package change unlike Forge's EventBus 7 migration.

7. **Server-side mods don't need `runtime_side=server` to work** — but setting it correctly ensures the mod is marked server-only on Modrinth and won't be required on clients.

8. **Always re-run the diagnosis after completing a port** — the user noticed we were still missing 1.16.5–1.19.1 because we incorrectly assumed those versions had no totem support.
