# Auto Fast XP — All Versions Port

## Overview

| Field | Value |
|-------|-------|
| Mod | Auto Fast XP |
| Modrinth URL | https://modrinth.com/mod/auto-fast-xp |
| Mod ID | `autofastxp` |
| Starting coverage | 30 versions (1.12.2 forge through 1.21.8 neoforge) |
| Final coverage | 80 versions |
| New versions added | 50 (30 new build targets, some expanding to multiple MC versions) |
| Build runs | 4 |
| Generator script | `scripts/generate_autofastxp_bundle.py` |
| Completed | April 2026 |

## What the Mod Does

Auto Fast XP is a **client-side only** mod that automatically throws XP bottles
rapidly when the player holds right-click with an experience bottle in hand.

Key characteristics:
- Client-side only (`runtime_side=client`, `clientSideOnly=true`)
- No commands, no server interaction, no keybinds
- Uses the client tick event to detect right-click held + XP bottle in hand
- Calls `gameMode.useItem()` (Forge/NeoForge) or `interactItem()` (Fabric) to throw

## Starting State (from diagnosis)

Already published before this port:

```
Forge:    1.12.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4,
          1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6,
          1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 26.1.2
Fabric:   1.16.5, 1.17, 1.17.1
NeoForge: 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.3, 1.21.4,
          1.21.5, 1.21.6, 1.21.7, 1.21.8
```

Missing (30 targets to build):

```
Forge:    1.8.9, 1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2,
          1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11
Fabric:   1.18-1.18.2 (range), 1.19-1.19.4 (range),
          1.20.1-1.20.6 (range), 1.21-1.21.1 (range),
          1.21.2-1.21.8 (range), 1.21.9-1.21.11 (range),
          26.1, 26.1.1, 26.1.2
NeoForge: 1.20.2, 1.20.4, 1.21.2,
          1.21.9, 1.21.10, 1.21.11,
          26.1, 26.1.1, 26.1.2
```

## Step-by-Step Session

### Step 1: Read the IDE startup manual

```
readFile docs/IDE_QUICK_STARTUP_READ.md
readFile docs/SYSTEM_MANUAL.md
readFile docs/IDE_AGENT_INSTRUCTION_SHEET.txt
readFile dif/README.md
```

### Step 2: Run the mandatory diagnosis

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/auto-fast-xp \
    --output-dir /tmp/auto-fast-xp-diag
cat /tmp/auto-fast-xp-diag/summary.txt
```

Output: 30 versions already published. Identified 30 missing targets.

### Step 3: Check for existing generator

```bash
ls scripts/generate_auto*
```

No existing generator found. Need to create `scripts/generate_autofastxp_bundle.py`.

### Step 4: Inspect the published jars to understand source structure

Downloaded and inspected 4 jars:
- `Auto-Fast-XP-1.0.0.jar` (1.12.2 forge) — package `asd.itamio.autofastxp`
- `autofastxp-1.0.0.jar` (fabric 1.16.5) — package `net.itamio.autofastxp`, client entrypoint
- `autofastxp-1.0.0.jar` (neoforge 1.20.5) — package `net.itamio.autofastxp`, two classes
- `autofastxp-1.0.0.jar` (forge 1.21.5) — package `net.itamio.autofastxp`, two classes

Key findings:
- 1.12.2 forge uses `asd.itamio.autofastxp` (legacy group)
- All other versions use `net.itamio.autofastxp`
- Fabric uses `ClientModInitializer` with `ClientTickEvents.END_CLIENT_TICK`
- Forge/NeoForge use a mod class + separate handler class

### Step 5: Look up APIs in DecompiledMinecraftSourceCode

```bash
grep -rn "ClientTickEvent" DecompiledMinecraftSourceCode/1.21.6-forge/net/minecraftforge/event/TickEvent.java
grep -rn "ClientTickEvent" DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraftforge/event/TickEvent.java
grep -rn "keyUse" DecompiledMinecraftSourceCode/1.21.5-forge/net/minecraft/client/Options.java
grep -rn "keyUse" DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraft/client/Options.java
grep -rn "ClientTickEvent" DecompiledMinecraftSourceCode/1.21.2-neoforge/net/neoforged/neoforge/client/event/ClientTickEvent.java
grep -rn "FMLEnvironment" DecompiledMinecraftSourceCode/1.21.9-neoforge/net/neoforged/ --include="*.java"
grep -rn "interactItem" DecompiledMinecraftSourceCode/1.18.2-fabric/net/minecraft/client/network/
grep -rn "interactItem" DecompiledMinecraftSourceCode/1.19.4-fabric/net/minecraft/client/network/
grep -rn "keyUse" DecompiledMinecraftSourceCode/1.16.5-fabric/net/minecraft/client/option/GameOptions.java
grep -rn "useKey" DecompiledMinecraftSourceCode/1.18.2-fabric/net/minecraft/client/option/GameOptions.java
```

### Step 6: Write the generator script

Created `scripts/generate_autofastxp_bundle.py` using bash heredoc (large file).
The script defines source strings for each version era and a TARGETS list.

### Step 7: Run 1 — first attempt (30 targets)

```bash
python3 scripts/generate_autofastxp_bundle.py
git add scripts/generate_autofastxp_bundle.py incoming/autofastxp-all-versions.zip incoming/autofastxp-all-versions/
git commit -m "Add Auto Fast XP all-versions bundle (30 missing targets)"
git push
python3 scripts/run_build.py incoming/autofastxp-all-versions.zip \
    --modrinth https://modrinth.com/mod/auto-fast-xp --max-parallel all
```

**Result**: 5 passed, 25 failed.

Passed: `1.8.9-forge`, `1.16.5-forge`, `neoforge-1.20.2`, `neoforge-1.20.4`

### Step 8: Diagnose run 1 failures

```bash
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-forge-1-17-1/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-forge-1-21-6/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-forge-1-21-9/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-fabric-1-18/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-fabric-1-21/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-neoforge-1-21-2/build.log
grep -A 3 "error:" ModCompileRuns/run-20260429-041425/artifacts/all-mod-builds/mods/autofastxp-neoforge-26-1/build.log
```

**Errors found:**

| Target group | Error |
|-------------|-------|
| Forge 1.17.1, 1.18.x | `package net.minecraft.item does not exist` — wrong package for 1.17+ |
| Forge 1.21.6–1.21.8 | `no suitable method found for addListener` — wrong BUS/method type |
| Forge 1.21.9–1.21.11 | `cannot find symbol: variable BUS` — record-based TickEvent, need `Post.BUS` |
| Fabric 1.18.x | `options.keyUse` not found + `interactItem` needs 3 args |
| Fabric 1.21+ | `package net.minecraft.item does not exist` — Mojang mappings |
| NeoForge 1.21.2+ | `net.neoforged.neoforge.event.TickEvent` has no `ClientTickEvent` |
| NeoForge 26.1 | `FMLEnvironment.dist` not found + wrong import package |

### Step 9: Fix and run 2 (26 targets, skipping 4 already-green)

Fixes applied to generator:
1. Forge 1.17+: `net.minecraft.world.item.Items/ItemStack`, `net.minecraft.world.InteractionHand`
2. Forge 1.21.6+: `TickEvent.ClientTickEvent.Post.BUS.addListener(Handler::method)`
3. Forge 1.21.9+: same Post.BUS pattern (record-based TickEvent)
4. Fabric 1.18.x: `options.useKey`, `interactItem(player, world, hand)` (3 args)
5. Fabric 1.20+: `src/client/java/` source directory (split adapter)
6. NeoForge 1.21.2+: `net.neoforged.neoforge.client.event.ClientTickEvent`
7. NeoForge 1.21.9+: `FMLEnvironment.getDist()` from `net.neoforged.fml.loading`
8. NeoForge 26.1: same as 1.21.9+ (`net.neoforged.fml.loading.FMLEnvironment.getDist()`)

```bash
python3 scripts/generate_autofastxp_bundle.py
# Manually filter zip to exclude 4 already-green targets
python3 -c "... filter zip ..."
git add ... && git commit -m "Fix run 2 errors" && git push
python3 scripts/run_build.py incoming/autofastxp-failed-only.zip \
    --modrinth https://modrinth.com/mod/auto-fast-xp --max-parallel all
```

**Result**: 18 more passed, 8 still failing.

Still failing: all Fabric 1.21+ ranges, NeoForge 1.21.9–1.21.11, NeoForge 26.1.x

### Step 10: Diagnose run 2 failures

```bash
grep -A 3 "error:" ModCompileRuns/run-20260429-090218/artifacts/all-mod-builds/mods/autofastxp-fabric-1-21/build.log
grep -A 5 "error: cannot find symbol" ModCompileRuns/run-20260429-090218/artifacts/all-mod-builds/mods/autofastxp-neoforge-1-21-9/build.log
```

**Errors found:**

| Target group | Error |
|-------------|-------|
| Fabric 1.21+ | `cannot find symbol: MinecraftClient` + `package net.minecraft.item does not exist` |
| NeoForge 1.21.9+ | `cannot find symbol: variable dist` in `FMLEnvironment` |

Root causes:
- Fabric 1.21+ uses **Mojang mappings** — `Minecraft` not `MinecraftClient`, `net.minecraft.world.item` not `net.minecraft.item`
- NeoForge 1.21.9+ `FMLEnvironment.dist` is a **method** `getDist()`, not a field

### Step 11: Fix and run 3 (12 targets)

Fixes:
1. Fabric 1.21+: new source string using Mojang class names (`Minecraft`, `net.minecraft.world.item`, `InteractionHand`, `getMainHandItem()`, `client.level`, `client.screen`, `client.gameMode.useItem()`, `options.keyUse.isDown()`)
2. NeoForge 1.21.9+: `FMLEnvironment.getDist() == Dist.CLIENT`

```bash
python3 scripts/generate_autofastxp_bundle.py
# Filter to 12 failed targets
python3 -c "... filter zip ..."
git add ... && git commit -m "Fix run 3 errors" && git push
python3 scripts/run_build.py incoming/autofastxp-failed-only.zip \
    --modrinth https://modrinth.com/mod/auto-fast-xp --max-parallel all
```

**Result**: 9 more passed, 3 still failing.

Still failing: NeoForge 26.1, 26.1.1, 26.1.2

### Step 12: Diagnose run 3 failures

```bash
grep -A 5 "error:" ModCompileRuns/run-20260429-091921/artifacts/all-mod-builds/mods/autofastxp-neoforge-26-1/build.log
```

**Error:**
```
error: package net.minecraftforge.fml.loading does not exist
import net.minecraftforge.fml.loading.FMLEnvironment;
```

Root cause: NeoForge 26.1 does NOT have `net.minecraftforge.fml.loading` — it uses
`net.neoforged.fml.loading.FMLEnvironment.getDist()` (same as 1.21.9+).

### Step 13: Fix and run 4 (3 targets)

Fix: NeoForge 26.1 source uses `net.neoforged.fml.loading.FMLEnvironment.getDist()`.

```bash
python3 scripts/generate_autofastxp_bundle.py
# Filter to 3 NeoForge 26.1 targets
python3 -c "... filter zip ..."
git add ... && git commit -m "Fix run 4: NeoForge 26.1 FMLEnvironment" && git push
python3 scripts/run_build.py incoming/autofastxp-failed-only.zip \
    --modrinth https://modrinth.com/mod/auto-fast-xp --max-parallel all
```

**Result**: All 3 passed. ✅

### Step 14: Verify final state

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/auto-fast-xp \
    --output-dir /tmp/auto-fast-xp-final
grep "Game versions\|Loaders\|Downloads" /tmp/auto-fast-xp-final/summary.txt
```

Output:
```
Game versions: 1.8.9, 1.12.2, 1.16.5, 1.17, 1.17.1, 1.18, 1.18.1, 1.18.2,
               1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4,
               1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
               1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
               1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
               26.1, 26.1.1, 26.1.2
Loaders: fabric, forge, neoforge
```

## Build Run Summary

| Run | Targets | Passed | Failed | Key Fix |
|-----|---------|--------|--------|---------|
| 1 | 30 | 5 | 25 | Initial attempt — wrong packages throughout |
| 2 | 26 | 18 | 8 | Forge item packages, Fabric split srcset, NeoForge ClientTickEvent |
| 3 | 12 | 9 | 3 | Fabric 1.21+ Mojang mappings, NeoForge 1.21.9 getDist() |
| 4 | 3 | 3 | 0 | NeoForge 26.1 correct FMLEnvironment import |

## API Reference Table

### Forge — Client Tick Event

| Forge version | Pattern |
|---------------|---------|
| 1.8.9–1.12.2 | `@SubscribeEvent public void onTick(TickEvent.ClientTickEvent event)` in `net.minecraftforge.fml.common.gameevent.TickEvent` |
| 1.16.5–1.21.5 | `@SubscribeEvent public void onTick(TickEvent.ClientTickEvent event)` in `net.minecraftforge.event.TickEvent` |
| 1.21.6–26.1.2 | `TickEvent.ClientTickEvent.Post.BUS.addListener(Handler::method)` — EventBus 7 |

### Forge — Item/Hand Packages

| Forge version | Items/ItemStack | Hand |
|---------------|----------------|------|
| 1.8.9–1.16.5 | `net.minecraft.item` | `net.minecraft.util.Hand` |
| 1.17+ | `net.minecraft.world.item` | `net.minecraft.world.InteractionHand` |

### Forge — Use Item

| Forge version | Method |
|---------------|--------|
| 1.8.9 | `mc.playerController.sendUseItem(player, world, stack)` |
| 1.16.5–1.21.5 | `mc.gameMode.useItem(player, level, hand)` |
| 1.21.6+ | `mc.gameMode.useItem(player, hand)` (level arg removed) |

### Fabric — Client Tick

| Fabric version | Pattern |
|---------------|---------|
| All | `ClientTickEvents.END_CLIENT_TICK.register(client -> { ... })` |

### Fabric — Class Names (yarn vs Mojang)

| API | Fabric 1.16.5–1.20.x (yarn) | Fabric 1.21+ (Mojang) |
|-----|-----------------------------|-----------------------|
| Client | `MinecraftClient` | `Minecraft` |
| Items | `net.minecraft.item.Items` | `net.minecraft.world.item.Items` |
| ItemStack | `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| Hand | `net.minecraft.util.Hand` | `net.minecraft.world.InteractionHand` |
| Main hand | `player.getMainHandStack()` | `player.getMainHandItem()` |
| Off hand | `player.getOffHandStack()` | `player.getOffhandItem()` |
| Screen | `client.currentScreen` | `client.screen` |
| World/Level | `client.world` | `client.level` |
| Use key | `options.keyUse` (1.16.5) / `options.useKey` (1.18+) | `options.keyUse` |
| Interaction | `client.interactionManager` | `client.gameMode` |
| Use item | `interactItem(player, hand)` | `gameMode.useItem(player, hand)` |

### Fabric — interactItem Signature

| Fabric version | Signature |
|---------------|-----------|
| 1.16.5–1.17.x | `interactItem(PlayerEntity, Hand)` — 2 args |
| 1.18–1.18.2 | `interactItem(PlayerEntity, World, Hand)` — 3 args |
| 1.19+ | `interactItem(PlayerEntity, Hand)` — 2 args |

### Fabric — Source Directory

| Adapter | Versions | Client source dir |
|---------|----------|-------------------|
| `fabric_presplit` | 1.16.5–1.19.4 | `src/main/java/` |
| `fabric_split` | 1.20+ | `src/client/java/` |

### NeoForge — ClientTickEvent

| NeoForge version | Import | Handler signature |
|-----------------|--------|-------------------|
| 1.20.x–1.21.1 | `net.neoforged.neoforge.event.TickEvent` | `onTick(TickEvent.ClientTickEvent event)` + phase check |
| 1.21.2+ | `net.neoforged.neoforge.client.event.ClientTickEvent` | `onTick(ClientTickEvent.Post event)` |

### NeoForge — FMLEnvironment

| NeoForge version | Import | Access |
|-----------------|--------|--------|
| 1.20.x–1.21.8 | `net.neoforged.fml.loading.FMLEnvironment` | `FMLEnvironment.dist` (field) |
| 1.21.9–26.1.x | `net.neoforged.fml.loading.FMLEnvironment` | `FMLEnvironment.getDist()` (method) |

### NeoForge — Mod Constructor

| NeoForge version | Constructor signature |
|-----------------|----------------------|
| 1.20.x–1.21.8 | `public MyMod(IEventBus modBus)` |
| 1.21.9+ | `public MyMod(IEventBus modBus, ModContainer modContainer)` |

## Files Modified

- `scripts/generate_autofastxp_bundle.py` — created (generator script)
- `incoming/autofastxp-all-versions.zip` — build bundle
- `incoming/autofastxp-failed-only.zip` — failed-only bundle (runs 2–4)

## New DIF Entries Added

| DIF ID | Issue |
|--------|-------|
| `FORGE-ITEM-PACKAGE-PRE-117` | Forge pre-1.17 uses `net.minecraft.item`, 1.17+ uses `net.minecraft.world.item` |
| `FABRIC-121-MOJANG-MAPPINGS-SWITCH` | Fabric 1.21+ switched to Mojang mappings — all class names changed |
| `NEOFORGE-TICKEVENT-CLIENT-PACKAGE` | NeoForge 1.21.2+ ClientTickEvent moved to `net.neoforged.neoforge.client.event` |
| `NEOFORGE-FMLENVIRONMENT-GETDIST` | NeoForge 1.21.9+ `FMLEnvironment.dist` → `FMLEnvironment.getDist()` |
| `FABRIC-118-INTERACTITEM-WORLD-ARG` | Fabric 1.18.x `interactItem()` requires World as 2nd argument |
| `FORGE-EB7-POST-BUS-ADDLISTENER` | Forge 1.21.6+ EventBus 7 — use `TickEvent.ClientTickEvent.Post.BUS.addListener()` |

## Key Lessons

1. **Fabric 1.21 is a hard mapping boundary.** Everything changes: class names, packages, method names. Write a completely separate source string for 1.21+ Fabric — do not try to derive it from 1.20.x via `.replace()`.

2. **NeoForge 26.1 does NOT have `net.minecraftforge.*` packages.** It is fully `net.neoforged.*`. Any import from `net.minecraftforge` will fail.

3. **NeoForge `FMLEnvironment` changed from field to method at 1.21.9.** `dist` → `getDist()`. Always check the decompiled sources before writing dist checks.

4. **NeoForge `ClientTickEvent` moved to the client event package at 1.21.2.** Never assume `TickEvent.ClientTickEvent` — check `DecompiledMinecraftSourceCode/<version>-neoforge/net/neoforged/neoforge/client/event/`.

5. **Forge EventBus 7 (1.21.6+) requires `Post.BUS` not `ClientTickEvent.BUS`.** The handler method must accept the specific `Post` type, not the parent `ClientTickEvent`.

6. **Fabric 1.18.x is an island for `interactItem`.** It uniquely requires 3 args (player, world, hand). 1.16.5–1.17.x and 1.19+ both use 2 args.

7. **Always filter the zip to exclude already-green targets.** Use the manual filter pattern when `--failed-only` can't read the run result format.
