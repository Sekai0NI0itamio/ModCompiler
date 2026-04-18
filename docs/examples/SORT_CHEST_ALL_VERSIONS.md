# Example: Sort Chest — All-Versions Port

## Overview

This document records the complete workflow used to port the Sort Chest mod
(https://modrinth.com/mod/sort-chest) from its original 1.20.1 Forge release
to all supported Minecraft versions and loaders in this repository.

It is intended as a reference for future IDE agents and developers performing
the same kind of multi-version update.

---

## Starting point

- Mod: Sort Chest
- Modrinth: https://modrinth.com/mod/sort-chest
- Original version: 1.0.0, Forge 1.20.1-1.20.6, client-side only
- Goal: port to all 10 version ranges and all supported loaders

---

## Step 1 — Fetch and understand the mod

Ran the Fetch Modrinth Project workflow to download all metadata and decompile
the published jar. Key facts extracted from the bundle:

- Package: `net.itamio.sortchest`
- Main class: `SortChestMod`
- Loader: Forge (mods.toml)
- Side: client-only (`runtime_side=client`)
- Logic: hooks `ScreenEvent.Init.Post`, adds a Sort button to every
  `AbstractContainerScreen`, sorts via simulated slot clicks using
  `MultiPlayerGameMode.handleInventoryMouseClick`
- Inner class: `ItemKey` for grouping items by type+NBT/components
- Sort algorithm: mergeStacks → buildDesiredLayout → reorderStacks

---

## Step 2 — Identify all targets

From `version-manifest.json`, 28 targets were planned across:

| Range | Forge | Fabric | NeoForge |
|-------|-------|--------|----------|
| 1.8.9 | ✓ | — | — |
| 1.12.2 | ✓ | — | — |
| 1.16.5 | ✓ | ✓ | — |
| 1.17.1 | ✓ | ✓ | — |
| 1.18.2 | ✓ | ✓ | — |
| 1.19.4 | ✓ | ✓ | — |
| 1.20.1 | ✓ | ✓ | — |
| 1.20.4 | ✓ | ✓ | ✓ |
| 1.20.6 | ✓ | ✓ | ✓ |
| 1.21.1 | ✓ | ✓ | ✓ |
| 1.21.4 | ✓ | ✓ | ✓ |
| 1.21.11 | — | ✓ | ✓ |

Note: Forge 1.21.11 was dropped — it uses ForgeGradle 7.x with a
non-standard dependency setup the current adapter cannot handle.
NeoForge 1.21.11 covers the same Minecraft version range.

Final target count: **27**

---

## Step 3 — Write the source ports

All source was written in `scripts/generate_sortchest_bundle.py`.
Run `python3 scripts/generate_sortchest_bundle.py` to regenerate the bundle.

### Critical API differences discovered across versions

#### Forge

| Version | Event class | Button API | Item comparison | Carried stack |
|---------|-------------|------------|-----------------|---------------|
| 1.8.9 | `GuiScreenEvent.InitGuiEvent.Post` | `new GuiButton` | `areItemsEqual+areItemStackTagsEqual` | `player.inventory.getItemStack()` |
| 1.12.2 | `GuiScreenEvent.InitGuiEvent.Post` | `new GuiButton` | `areItemsEqual+areItemStackTagsEqual` | `player.inventory.getItemStack()` |
| 1.16.5 | `GuiScreenEvent.InitGuiEvent.Post` | `new Button(x,y,w,h,text,handler)` | item+tag equality (no helper method) | skip check (no API) |
| 1.17.1 | `GuiScreenEvent.InitGuiEvent.Post` | `new Button(x,y,w,h,text,handler)` + `addWidget()` | `isSameItemSameTags` | `getCarried()` |
| 1.18.x | `ScreenEvent.Init.Post` | `new Button(x,y,w,h,text,handler)` + `addListener()` | `isSameItemSameTags` | `getCarried()` |
| 1.19.4 | `ScreenEvent.Init.Post` | `Button.builder()` + `addListener()` | `isSameItemSameTags` | `getCarried()` |
| 1.20.x | `ScreenEvent.Init.Post` | `Button.builder()` + `addListener()` | `isSameItemSameTags` | `getCarried()` |
| 1.20.5-1.20.6 | `ScreenEvent.Init.Post` | `Button.builder()` + `addListener()` | `isSameItemSameComponents` | `getCarried()` |
| 1.21.x | `ScreenEvent.Init.Post` | `Button.builder()` + `addListener()` | `isSameItemSameComponents` | `getCarried()` |
| 1.21.11 | `ScreenEvent.Init.Post` via `@Mod.EventBusSubscriber` | `Button.builder()` | `isSameItemSameComponents` | `getCarried()` |

Key Forge notes:
- `ScreenEvent` class didn't exist until 1.18 — use `GuiScreenEvent` in 1.16.5 and 1.17.1
- `Button.builder()` was added in 1.19.4 — use `new Button(...)` before that
- `isSameItemSameTags` was removed in 1.20.5 — use `isSameItemSameComponents` from 1.20.5+
- `getTag()` was removed in 1.20.5 — use `getComponents()` from 1.20.5+
- `guiLeft`/`xSize`/`guiTop` are **protected** in `GuiContainer` (1.8.9-1.12.2) — use reflection
- `net.minecraftforge.eventbus.api.SubscribeEvent` doesn't exist in 1.21.11 — use `@Mod.EventBusSubscriber` static pattern
- 1.16.5 MCP: `Container.getCarried()` doesn't exist — skip cursor check entirely

#### Fabric

| Version | Mappings | Source dir | Screen fields | Button API | Item comparison |
|---------|----------|------------|---------------|------------|-----------------|
| 1.16.5 | Yarn | `src/main/java` | protected → reflection | `new ButtonWidget` | `areItemsEqual+areTagsEqual` |
| 1.17.1-1.18.2 | Yarn | `src/main/java` | protected → reflection | `new ButtonWidget` | `canCombine` + `getNbt()` |
| 1.19.4 | Yarn | `src/main/java` | protected → reflection | `ButtonWidget.builder()` | `canCombine` + `getNbt()` |
| 1.20.x | Yarn | `src/client/java` | protected → reflection | `ButtonWidget.builder()` | `canCombine` + `getNbt()` |
| 1.20.5-1.20.6 | Yarn | `src/client/java` | protected → reflection | `ButtonWidget.builder()` | `areItemsAndComponentsEqual` + `getComponents()` |
| 1.21.x | **Mojang** | `src/client/java` | protected → reflection | `Button.builder()` (Mojang name) | `isSameItemSameComponents` + `getComponents()` |

Key Fabric notes:
- `fabric_presplit` adapter (1.16.5-1.19.4): source goes in `src/main/java`
- `fabric_split` adapter (1.20+): source goes in `src/client/java`
- `fabric.mod.json` ALWAYS goes in `src/main/resources` regardless of adapter
- 1.20.x uses Yarn mappings; 1.21.x uses **Mojang mappings** (same package names as Forge)
- `HandledScreen.x/y/backgroundWidth` are protected in ALL Fabric versions — always use reflection
- `TranslatableText` removed in 1.19.4 → `Text.translatable()`
- `getCursorStack()` added in 1.17; in 1.16.5 use `player.inventory.getCursorStack()`
- `areTagsEqual` doesn't exist in 1.17+ — use `canCombine()` instead
- `getTag()` renamed to `getNbt()` in 1.17
- `canCombine()` removed in 1.20.5 → `areItemsAndComponentsEqual()`

---

## Step 4 — Build process

Bundle generator: `scripts/generate_sortchest_bundle.py`

```bash
# Full build (all 27 targets)
python3 scripts/generate_sortchest_bundle.py
git add incoming/sort-chest-all-versions.zip incoming/sort-chest-all-versions/
git commit -m "..."
git push
python3 scripts/run_build.py incoming/sort-chest-all-versions.zip \
    --modrinth https://modrinth.com/mod/sort-chest

# Failed-only rerun (after fixing failures)
python3 scripts/generate_sortchest_bundle.py --failed-only
git add incoming/sort-chest-all-versions.zip incoming/sort-chest-all-versions/
git commit -m "Fix failed targets: ..."
git push
python3 scripts/run_build.py incoming/sort-chest-all-versions.zip \
    --modrinth https://modrinth.com/mod/sort-chest
```

---

## Step 5 — Build history and failures encountered

10 build runs were required to reach all-green. The `--failed-only` flag
reduced each rerun to only the targets that actually needed fixing, saving
significant GitHub Actions time.

| Run | Targets | Green | Failed | Key fix |
|-----|---------|-------|--------|---------|
| 1 | 83 | 0 | 83 | Stray `build_bundle.py` in zip top level |
| 2 | 83 | 7 | 76 | Wrong source layout, wrong API names throughout |
| 3 | 28 | 14 | 14 | Fabric src/client/java layout, 1.20.5+ API |
| 4 | 28 | 19 | 9 | NeoForge imports, Fabric protected fields |
| 5 | 28 | 23 | 5 | 1.16.5 Forge API, 1.17.1 event class, Fabric 1.21 Mojang mappings |
| 6 | 9 | 5 | 4 | 1.16.5 cursor check, 1.17-1.19 Fabric API, 1.21 field names |
| 7 | 5 | 3 | 2 | 1.16.5 Forge method names, 1.21.11 Forge event bus |
| 8 | 2 | 1 | 1 | 1.21.11 Forge: addListener() approach |
| 9 | 2 | 1 | 1 | 1.21.11 Forge: @Mod.EventBusSubscriber pattern |
| 10 | 1 | 0 | 1 | Dropped Forge 1.21.11 (ForgeGradle 7.x incompatible) |
| Final | 27 | 27 | 0 | All green |

---

## Lessons learned for future agents

1. **Always fetch and decompile first.** Use the Fetch Modrinth Project workflow
   before writing any code. The decompiled source and mod_info.txt are the
   source of truth.

2. **Use --failed-only on every rerun.** Never resubmit already-green targets.
   The `--failed-only` flag reads the last run's results automatically.

3. **Fabric 1.20+ uses Mojang mappings for 1.21+, Yarn for 1.20.x.**
   The template build.gradle is the definitive source — check it before
   writing source for any Fabric version.

4. **Fabric split vs presplit matters for source directory.**
   - `fabric_presplit` (1.16.5-1.19.4): `src/main/java`
   - `fabric_split` (1.20+): `src/client/java`
   - `fabric.mod.json` always goes in `src/main/resources`

5. **HandledScreen fields are always protected in Fabric.** Use reflection
   to access `x`, `y`, `backgroundWidth` in all Fabric versions.

6. **Forge event class names changed across versions:**
   - 1.8.9-1.17.1: `GuiScreenEvent.InitGuiEvent.Post`
   - 1.18+: `ScreenEvent.Init.Post`

7. **Button API changed in Forge 1.19.4.** Use `new Button(...)` before 1.19.4,
   `Button.builder()` from 1.19.4 onwards.

8. **Item comparison API changed in 1.20.5.** `isSameItemSameTags` and `getTag()`
   were removed. Use `isSameItemSameComponents` and `getComponents()` from 1.20.5+.

9. **Forge 1.21.11 uses ForgeGradle 7.x** with a non-standard dependency setup
   that the current adapter cannot handle. Skip it and use NeoForge 1.21.11 instead.

10. **1.16.5 Forge has no cursor stack API on Container.** Skip the cursor check
    entirely rather than trying to access it.

---

## Final result

27 versions published to https://modrinth.com/mod/sort-chest covering:
- Forge: 1.8.9, 1.12.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4
- Fabric: 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.11
- NeoForge: 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.11
