# Seed Protect — All-Versions Port

## Overview

**Mod**: Seed Protect (https://modrinth.com/mod/seed-protect)
**Starting coverage**: 51/63 versions (81%)
**Phase 1 result**: 57/63 versions (90%) — 6 Fabric versions added
**Phase 2 result**: 73/63+ versions — 16 more versions added across all loaders
**26.x versions**: skipped — all loaders still in beta/unstable for Minecraft 26.1.x

## Mod Description

Server-side mod that prevents farmland and planted crops from being trampled
by players and mobs. Implemented as a single mixin (Fabric) or event handler
(Forge/NeoForge) that cancels the trample action.

- No commands, no config
- Pure mixin on Fabric — one class, one `@Inject`
- Event handler on Forge/NeoForge — one `@SubscribeEvent` or EventBus 7 listener
- Works on Forge, Fabric, and NeoForge

---

## Phase 1: Fabric 1.18–1.19.3 (6 versions)

### How missing versions were discovered

Ran the Fetch Modrinth Project workflow to download all metadata and decompile
the published jars. From `project.json`, the missing versions were:
- Fabric 1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3

### Challenge 1: Wrong class name — guessing Fabric mappings

**Problem**: First attempt used `net.minecraft.world.level.block.FarmlandBlock`
(Mojang/Forge name). All 6 Fabric builds failed:

```
error: Mixin target net.minecraft.world.level.block.FarmlandBlock could not be found
```

Three guesses all failed. Root cause: Fabric uses **yarn mappings** — completely
different class names and packages from Mojang/Forge.

**Fix**: Used the AI Source Search workflow:

```bash
python3 scripts/ai_source_search.py \
    --version 1.18.2 \
    --loader fabric \
    --queries "FarmlandBlock" "fallOn" "trample" \
    --files "*.java" \
    --context 20
```

The workflow decompiled Minecraft 1.18.2 Fabric and returned `FarmlandBlock.java`.
Actual yarn-mapped names:

| Attribute | Mojang/Forge | Fabric yarn (1.18–1.20.x) |
|-----------|-------------|--------------------------|
| Package | `net.minecraft.world.level.block` | `net.minecraft.block` |
| Class | `FarmBlock` | `FarmlandBlock` |
| Method | `fallOn(...)` | `onLandedUpon(...)` |

**Correct mixin (Fabric 1.18–1.19.x)**:

```java
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(World world, BlockState state,
            BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
```

### Challenge 2: AI Source Search workflow broken for Fabric

**Problem**: The workflow reported `Found 0 .java files` — Fabric Loom produces
a `*-sources.jar` inside `.gradle/loom-cache/`, not extracted `.java` files.
Also, `rg` (ripgrep) is not installed on GitHub Actions runners.

**Fix applied to `.github/workflows/ai-source-search.yml`**:
1. Find and unzip the sources jar from `$WORKSPACE/.gradle/`
2. Replace `rg` with `grep -r`

**Result**: All 6 Fabric versions compiled and published on the next run.

---

## Phase 2: All Remaining Missing Versions (16 versions)

### How missing versions were discovered

Used the new **Profile Diagnosis** workflow to scan all public projects:

```bash
gh workflow run profile-diagnosis.yml -f modrinth_username=Itamio
# Download report:
gh run download <run_id> -n profile-diagnosis-report -D /tmp/report
```

The report identified Seed Protect was missing:

| Loader | Missing versions |
|--------|-----------------|
| Fabric | 1.17, 1.20, 1.21, 1.21.2, 1.21.9 |
| Forge | 1.12, 1.17, 1.20, 1.21.2, 1.21.6–1.21.11 |
| NeoForge | 1.20 |

After correcting version strings to match `version-manifest.json` supported_versions:

| Loader | Correct targets |
|--------|----------------|
| Fabric | 1.17.1, 1.20.1, 1.21, 1.21.2, 1.21.9 |
| Forge | 1.12, 1.17.1, 1.20.1, 1.21.3, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 |
| NeoForge | 1.20.2 |

### Challenge 3: Version strings must match manifest supported_versions exactly

**Problem**: First build attempt failed at the prepare step:

```
1.17-1.17.1/forge/template does not support exact Minecraft 1.17.
Supported exact versions: 1.17.1
```

The build system requires exact versions from `supported_versions` in the manifest.
Using range min/max values that aren't in the list causes prepare to fail.

**Fix**: Map each missing version to the correct exact version:

| Wrong | Correct | Reason |
|-------|---------|--------|
| `1.17` fabric | `1.17.1` | Only `1.17.1` in fabric supported_versions |
| `1.17` forge | `1.17.1` | Only `1.17.1` in forge supported_versions |
| `1.20` fabric | `1.20.1` | First entry in fabric supported_versions |
| `1.20` forge | `1.20.1` | First entry in forge supported_versions |
| `1.21.2` forge | `1.21.3` | `1.21.2` not in forge list, starts at `1.21.3` |
| `1.20` neoforge | `1.20.2` | First entry in neoforge supported_versions |

### Challenge 4: Forge 1.17.1 — wrong event package

**Problem**: `net.minecraftforge.event.level.BlockEvent` doesn't exist in 1.17.

```
error: package net.minecraftforge.event.level does not exist
```

**Fix**: Forge 1.17 uses `net.minecraftforge.event.world` (not `event.level`).
The `event.level` package was introduced in 1.18.

```java
// Forge 1.17.x
import net.minecraftforge.event.world.BlockEvent;

// Forge 1.18+
import net.minecraftforge.event.level.BlockEvent;
```

### Challenge 5: Forge 1.21.6–1.21.11 — EventBus 7 migration

**Problem**: The old `@SubscribeEvent` pattern fails in Forge 1.21.6+:

```
error: package net.minecraftforge.eventbus.api does not exist
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

Forge 1.21.6+ uses **EventBus 7** — a complete rewrite of the event system.

**Key changes in EventBus 7**:
- Each event has its own static `BUS` field
- Cancellation is done by returning `true` (boolean return type), not `setCanceled(true)`
- `@SubscribeEvent` is no longer used for cancellable listeners
- `@Mod.EventBusSubscriber` is replaced by direct `BUS.addListener()`

**Fix** (Forge 1.21.6+):

```java
@Mod("seedprotect")
public final class SeedProtectMod {
    public SeedProtectMod(FMLJavaModLoadingContext context) {
        // alwaysCancelling = true tells EventBus 7 this listener always cancels
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            SeedProtectMod::onFarmlandTrample
        );
    }

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        return true; // return true = cancel the event
    }
}
```

Reference: https://gist.github.com/PaintNinja/ad82c224aecee25efac1ea3e2cf19b91

### Challenge 6: NeoForge 1.20.x — Bus.GAME doesn't exist

**Problem**: `Mod.EventBusSubscriber.Bus.GAME` is not a valid bus name in NeoForge.

```
error: cannot find symbol
@Mod.EventBusSubscriber(modid = "seedprotect", bus = Mod.EventBusSubscriber.Bus.GAME)
```

**Fix**: Use `Bus.FORGE` for game/world events in NeoForge 1.20.x–1.21.x.

```java
@Mod.EventBusSubscriber(modid = "seedprotect", bus = Mod.EventBusSubscriber.Bus.FORGE)
```

### Challenge 7: Fabric 1.20.1 — still using Mojang mappings

**Problem**: Fabric 1.20.x uses **yarn mappings**, not Mojang mappings.
The generator was using Mojang class names for 1.20.1:

```
error: package net.minecraft.world.level.block does not exist
import net.minecraft.world.level.block.FarmBlock;
```

**Fix**: Fabric uses yarn mappings through 1.20.x. Only Fabric 1.21+ switched
to Mojang mappings. The mapping era boundary is:

| Fabric version | Mappings | Class | Method |
|---------------|----------|-------|--------|
| 1.16.5–1.20.x | Yarn | `net.minecraft.block.FarmlandBlock` | `onLandedUpon` |
| 1.21+ | Mojang | `net.minecraft.world.level.block.FarmBlock` | `fallOn` |

### Challenge 8: 26.x versions — all loaders unstable

All three loaders for Minecraft 26.1.x had blocking issues:

**Fabric 26.1**:
- `loom_version=1.16-SNAPSHOT` — not a valid stable release (use `1.16.1`)
- `fabric_version` key in manifest — wrong, template uses `fabric_api_version`
- Even after fixes, `FarmBlock cannot find symbol` — Minecraft classes not on
  classpath in the new loom setup

**Forge 26.1**:
- Forge only released for MC 26.1.2 (not 26.1 or 26.1.1)
- ForgeGradle 7 for 26.1.2 fails: `Invalid MCP Dependency: mcp_config:26.1.2-...
  Could not find 'mappings' task` — the `mappings channel: 'official'` syntax
  is broken for 26.1.2

**NeoForge 26.1**:
- All versions are still `-beta` (e.g. `26.1.0.19-beta`) — Maven `+` wildcard
  doesn't match pre-release suffixes
- `FMLJavaModLoadingContext` was removed in NeoForge 26.1
- `FarmlandTrampleEvent.BUS` field doesn't exist yet in the beta

**Decision**: Skip all 26.x versions. Add once the ecosystem stabilizes.

---

## Build History

| Run | Targets | Green | Failed | Root cause |
|-----|---------|-------|--------|------------|
| 1 | 6 | 0 | 6 | Wrong class name (Mojang names for Fabric yarn) |
| 2 | 6 | 6 | 0 | Correct yarn names from AI Source Search ✓ |
| 3 | 25 | 0 | 25 | Prepare failed — version strings don't match manifest |
| 4 | 25 | 14 | 11 | Fixed version strings; Forge 1.17 pkg, NeoForge Bus.GAME |
| 5 | 25 | 16 | 9 | Fixed Forge 1.17 pkg, NeoForge Bus.FORGE |
| 6 | 25 | 16 | 9 | Fabric 1.20.1 still Mojang names; 26.x all failing |
| 7 | 25 | 16 | 9 | NeoForge 26.1 beta versions; manifest fabric_api_version |
| 8 | 23 | 14 | 9 | loom 1.16 not found; NeoForge 26.1 EventBus7 |
| 9 | 16 | 16 | 0 | Removed 26.x; loom 1.16.1; Fabric 1.20.x yarn ✓ |

---

## Mixin / Event API Reference by Version and Loader

### Fabric

| Version range | Mappings | Package | Class | Method |
|---------------|----------|---------|-------|--------|
| 1.16.5–1.20.x | Yarn | `net.minecraft.block` | `FarmlandBlock` | `onLandedUpon(World, BlockState, BlockPos, Entity, float)` |
| 1.21+ | Mojang | `net.minecraft.world.level.block` | `FarmBlock` | `fallOn(Level, BlockState, BlockPos, Entity, float)` |

### Forge

| Version range | Event package | Pattern | Notes |
|---------------|--------------|---------|-------|
| 1.12.x | `net.minecraftforge.event.world` | `@SubscribeEvent` + `event.setCanceled(true)` | Legacy mcmod.info |
| 1.17.x | `net.minecraftforge.event.world` | `@SubscribeEvent` + `event.setCanceled(true)` | `event.world` not `event.level` |
| 1.18–1.21.5 | `net.minecraftforge.event.level` | `@SubscribeEvent` + `event.setCanceled(true)` | Standard mods.toml |
| 1.21.6+ | `net.minecraftforge.event.level` | `FarmlandTrampleEvent.BUS.addListener(true, ...)` returns `boolean` | EventBus 7 |

### NeoForge

| Version range | Event package | Pattern | Notes |
|---------------|--------------|---------|-------|
| 1.20.x–1.21.x | `net.neoforged.neoforge.event.level` | `@SubscribeEvent` + `Bus.FORGE` + `event.setCanceled(true)` | Use `Bus.FORGE` not `Bus.GAME` |

---

## Key Lessons Learned

1. **Version strings must match `supported_versions` exactly.** The build system
   validates against the manifest. `1.17` fails if only `1.17.1` is listed.
   Always check the manifest before writing version.txt.

2. **Fabric yarn vs Mojang mappings split at 1.21.** Fabric 1.20.x and earlier
   use yarn. Fabric 1.21+ uses Mojang. The class names are completely different.
   Always use the AI Source Search workflow to verify.

3. **Forge 1.17.x uses `event.world`, not `event.level`.** The `event.level`
   package was introduced in 1.18. Using the wrong package causes a compile error.

4. **Forge 1.21.6+ uses EventBus 7.** `@SubscribeEvent` is gone. Use
   `EventName.BUS.addListener(true, handler)` where the handler returns `boolean`.

5. **NeoForge uses `Bus.FORGE` for game events**, not `Bus.GAME`. The latter
   doesn't exist.

6. **26.x loaders are all unstable.** NeoForge is in beta, Forge has broken
   tooling, Fabric loom has classpath issues. Skip until ecosystem stabilizes.

7. **The Profile Diagnosis workflow is the right tool for finding missing versions.**
   Run it before starting any update to get a complete picture of what's missing.

8. **AI Source Search needs version support updates.** When a new MC version
   range is added to the repo, also update the version mapping in
   `.github/workflows/ai-source-search.yml` and add Java version detection.

---

## Final Result

**73 versions published** to https://modrinth.com/mod/seed-protect:

**Phase 1 additions** (Fabric 1.18–1.19.3):
- Fabric: 1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3

**Phase 2 additions** (all remaining 1.x):
- Fabric: 1.17.1, 1.20.1, 1.21, 1.21.2, 1.21.9
- Forge: 1.12, 1.17.1, 1.20.1, 1.21.3, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11
- NeoForge: 1.20.2

**Skipped**:
- Forge 1.21.6–1.21.11 (Phase 1) — broken EventBus API → fixed in Phase 2 with EventBus 7
- 26.x all loaders — ecosystem still in beta/unstable

## Files Modified

- `scripts/generate_seedprotect_bundle.py` — full generator with all version-specific code
- `.github/workflows/ai-source-search.yml` — Fabric sources jar extraction, grep fix, 26.x support
- `.github/workflows/profile-diagnosis.yml` — new workflow for scanning all projects
- `version-manifest.json` — 26.1-26.x range, NeoForge beta versions, fabric_api_version key
- `26.1-26.x/fabric/template/` — new Fabric 26.1 template (loom 1.16.1, no yarn)
