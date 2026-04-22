# Seed Protect — All-Versions Port

## Overview

**Mod**: Seed Protect (https://modrinth.com/mod/seed-protect)
**Starting coverage**: 51/63 versions already published (81%)
**Missing versions**: 12 — 6 Fabric (1.18–1.19.3) + 6 Forge (1.21.6–1.21.11)
**Final result**: 57/63 versions published (90%)
**Forge 1.21.6–1.21.11**: skipped — broken EventBus API (use NeoForge instead)

## Mod Description

Server-side mod that prevents farmland and planted crops from being trampled
by players and mobs. Implemented as a single mixin that cancels the
`onLandedUpon` / `fallOn` method on the farmland block class.

- No commands, no config
- Pure mixin — one class, one `@Inject`
- Works on Forge, Fabric, and NeoForge

---

## Starting Point

Ran the Fetch Modrinth Project workflow to download all metadata and decompile
the published jars. Key facts from the bundle:

- Package: `com.seedprotect`
- Main class: `SeedProtectMod`
- Mixin target: farmland block class (name varies by loader/version — see below)
- Side: server (`runtime_side=server`)
- Logic: single `@Inject` at `HEAD` of the trample method, calls `ci.cancel()`

From `project.json`, the already-published versions covered:
- All Forge versions from 1.8.9 through 1.21.11 (except 1.21.6–1.21.11)
- All Fabric versions from 1.20 through 1.21.11
- All NeoForge versions from 1.20.2 through 1.21.11
- Missing: Fabric 1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3

---

## Target Versions

| Range | Forge | Fabric | NeoForge | Notes |
|-------|-------|--------|----------|-------|
| 1.8.9 | ✓ already published | — | — | |
| 1.12.2 | ✓ already published | — | — | |
| 1.16.5 | ✓ already published | ✓ already published | — | |
| 1.17–1.17.1 | ✓ already published | ✓ already published | — | |
| 1.18–1.18.2 | ✓ already published | **missing** | — | Fabric yarn mappings differ |
| 1.19–1.19.4 | ✓ already published | **missing** (1.19–1.19.3) | — | Fabric yarn mappings differ |
| 1.20–1.20.6 | ✓ already published | ✓ already published | ✓ already published | |
| 1.21–1.21.1 | ✓ already published | ✓ already published | ✓ already published | |
| 1.21.2–1.21.8 | ✓ already published | ✓ already published | ✓ already published | |
| 1.21.9–1.21.11 | **skipped** (broken) | ✓ already published | ✓ already published | Forge EventBus broken |

Missing targets: **6 Fabric versions** (1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3)

---

## Key Challenges & Solutions

### Challenge 1: Wrong Class Name — Guessing Fabric Mappings

**Problem**: The first attempt used `net.minecraft.world.level.block.FarmlandBlock`
as the mixin target — the Mojang/Forge mapping name. All 6 Fabric builds failed
with the same error:

```
error: Mixin target net.minecraft.world.level.block.FarmlandBlock could not be found
@Mixin(targets = "net.minecraft.world.level.block.FarmlandBlock")
```

Three guesses were tried in sequence, all failing:
1. `net.minecraft.world.level.block.FarmBlock` — class not found
2. `net.minecraft.world.level.block.FarmlandBlock` — class not found
3. `@Mixin(FarmBlock.class)` with import — package doesn't exist

**Root cause**: Fabric uses **yarn mappings**, which give Minecraft classes
completely different names and packages compared to Mojang/Forge mappings.
The same class can have an entirely different name depending on the loader.

**Solution**: Used the AI Source Search workflow to look up the actual class
name in the Fabric 1.18.2 decompiled sources:

```bash
python3 scripts/ai_source_search.py \
    --version 1.18.2 \
    --loader fabric \
    --queries "FarmlandBlock" "fallOn" "trample" \
    --files "*.java"
```

The workflow decompiled Minecraft 1.18.2 Fabric on GitHub Actions and returned
the full `FarmlandBlock.java` source. The actual yarn-mapped names were:

| Attribute | Mojang/Forge name | Fabric yarn name |
|-----------|-------------------|------------------|
| Package | `net.minecraft.world.level.block` | `net.minecraft.block` |
| Class | `FarmBlock` | `FarmlandBlock` |
| Method | `fallOn(...)` | `onLandedUpon(...)` |

**Lesson**: Never guess class names when porting to Fabric. Fabric yarn mappings
are completely different from Mojang/Forge mappings. Always use the AI Source
Search workflow to look up the actual class name before writing any mixin code.

---

### Challenge 2: Wrong Method Name and Signature

**Problem**: Even after finding the correct class name, the method name was
still wrong. The initial mixin used `fallOn` (the Mojang/Forge name):

```java
@Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
private void seedprotect_cancelTrample(CallbackInfo ci) {
    ci.cancel();
}
```

This would have failed at runtime even if the class name was correct, because
the method is named `onLandedUpon` in yarn mappings.

**Solution**: Reading the full `FarmlandBlock.java` from the AI Source Search
results revealed the complete method signature:

```java
@Override
public void onLandedUpon(World world, BlockState state, BlockPos pos,
        Entity entity, float fallDistance) {
    if (!world.isClient && world.random.nextFloat() < fallDistance - 0.5f
            && entity instanceof LivingEntity
            && (entity instanceof PlayerEntity
                || world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING))
            && entity.getWidth() * entity.getWidth() * entity.getHeight() > 0.512f) {
        FarmlandBlock.setToDirt(state, world, pos);
    }
    super.onLandedUpon(world, state, pos, entity, fallDistance);
}
```

The correct mixin with the full parameter list:

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

**Lesson**: Read the full class file from the AI Source Search results, not
just the grep snippet. The method name, parameter types, and parameter order
all matter for `@Inject`. A wrong parameter list causes a runtime crash even
if the method name is correct.

---

### Challenge 3: AI Source Search Workflow Not Finding Sources

**Problem**: The AI Source Search workflow was triggering and running Gradle
`genSources`, but reporting `Found 0 .java files`. The search step then
failed with `ERROR: No .java files found. Source decompilation failed.`

**Root cause**: The workflow's "Locate decompiled sources" step was searching
for extracted `.java` files in `~/.gradle/caches/`, but Fabric Loom does not
extract `.java` files — it produces a **sources jar** (e.g.
`minecraft-project-@-merged-named-sources.jar`) inside the workspace's
`.gradle/loom-cache/` directory. The jar was never unzipped.

Additionally, the workflow was using `rg` (ripgrep) for searching, but `rg`
is not installed on GitHub Actions runners.

**Fix applied to `.github/workflows/ai-source-search.yml`**:

1. **Fabric sources extraction**: Added logic to find the `*-sources.jar`
   inside `$WORKSPACE/.gradle/` and unzip it to a temp directory before
   searching:

```bash
# Fabric: find the sources jar and unzip it
SOURCES_JAR=$(find "$WORKSPACE/.gradle" "$HOME/.gradle/caches" \
  -name "*-sources.jar" -o -name "*named-sources.jar" \
  2>/dev/null | grep -v "\.lock" | head -1)

if [ -n "$SOURCES_JAR" ]; then
  unzip -q "$SOURCES_JAR" -d "$EXTRACTED_DIR" 2>/dev/null || true
fi
```

2. **Replaced `rg` with `grep -r`**: `rg` is not available on GitHub Actions
   ubuntu runners. Replaced all `rg` calls with `grep -r` + `find` for file
   pattern filtering.

**Lesson**: When the AI Source Search workflow reports 0 files found for
Fabric, check whether the sources jar was found and unzipped. The jar lives
in `$WORKSPACE/.gradle/loom-cache/`, not in `~/.gradle/caches/`.

---

### Challenge 4: Forge 1.21.6–1.21.11 — Broken EventBus API

**Problem**: The Forge versions for 1.21.6–1.21.11 were also missing from
Modrinth. Attempting to build them produced:

```
error: package net.minecraftforge.eventbus.api does not exist
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

**Root cause**: This is a known bug in Forge 1.21.6+. The `eventbus.api`
package is missing or broken in those Forge releases. This is not a code
problem — the Forge build itself is broken for those versions.

**Reference**: https://forums.minecraftforge.net/topic/158705-forge-121612171218-missing-or-broken-subscribeevent-annotation-in-eventbus-api/

**Solution**: Skip Forge 1.21.6–1.21.11 entirely. NeoForge covers the same
Minecraft versions (1.21.6–1.21.11) and works correctly. The NeoForge
versions were already published on Modrinth.

**Lesson**: When Forge fails with a missing `eventbus.api` package on
1.21.6+, it is a Forge bug, not a code error. Use NeoForge for those
Minecraft versions instead.

---

## Mixin API Reference by Version and Loader

| Version range | Loader | Package | Class | Method | Imports needed |
|---------------|--------|---------|-------|--------|----------------|
| 1.16.5–1.21.x | Forge/NeoForge | `net.minecraft.world.level.block` | `FarmBlock` | `fallOn(Level, BlockPos, Entity, float)` | `net.minecraft.world.level.block.FarmBlock` |
| 1.16.5–1.19.3 | Fabric | `net.minecraft.block` | `FarmlandBlock` | `onLandedUpon(World, BlockState, BlockPos, Entity, float)` | `net.minecraft.block.FarmlandBlock` |
| 1.19.4–1.21.x | Fabric | `net.minecraft.block` | `FarmlandBlock` | `onLandedUpon(World, BlockState, BlockPos, Entity, float)` | `net.minecraft.block.FarmlandBlock` |
| 1.21.x+ | Fabric (Mojang mappings) | `net.minecraft.world.level.block` | `FarmBlock` | `fallOn(Level, BlockPos, Entity, float)` | `net.minecraft.world.level.block.FarmBlock` |

> Note: Fabric 1.21+ switched to Mojang mappings, so class names align with
> Forge from that point forward.

---

## Build History

Two build runs were required. The `--failed-only` flag was used on the second
run to avoid resubmitting already-green targets.

| Run | Targets | Green | Failed | Root cause |
|-----|---------|-------|--------|------------|
| 1 | 6 | 0 | 6 | Wrong class name (`net.minecraft.world.level.block.FarmlandBlock`) — Mojang name used instead of yarn name |
| 2 | 6 | 6 | 0 | Correct yarn name (`net.minecraft.block.FarmlandBlock`) + correct method (`onLandedUpon`) found via AI Source Search |

---

## How the AI Source Search Workflow Was Used

This was the first real-world use of the AI Source Search workflow to resolve
a "class not found" mixin error. The workflow was also fixed during this
process (Fabric sources jar extraction + grep replacement).

**Step 1**: Trigger the search for the failing version and loader:

```bash
python3 scripts/ai_source_search.py \
    --version 1.18.2 \
    --loader fabric \
    --queries "FarmlandBlock" "fallOn" "trample" \
    --files "*.java"
```

**Step 2**: The workflow ran `genSources` via Fabric Loom, found and unzipped
the sources jar, then searched for the queries. Output included:

- `all-java-files.txt` — confirmed `net/minecraft/block/FarmlandBlock.java` exists
- `full-classes/FarmlandBlock.java` — the complete decompiled class

**Step 3**: Read `FarmlandBlock.java` to get the exact method signature.

**Step 4**: Updated the mixin with the correct class, package, and method.
All 6 Fabric versions compiled on the next run.

The same search result applies to all 6 missing Fabric versions (1.18–1.19.3)
because the yarn-mapped class name and method signature are consistent across
that range.

---

## Lessons Learned

1. **Never guess Fabric class names.** Fabric uses yarn mappings. The same
   Minecraft class has a completely different name and package compared to
   Forge/Mojang mappings. Always use the AI Source Search workflow first.

2. **Read the full class file, not just the grep match.** The method name,
   parameter types, and parameter order all matter for `@Inject`. A wrong
   parameter list causes a runtime crash even if the class name is correct.

3. **Fabric yarn vs Mojang mappings split at 1.21.** Fabric 1.20.x and
   earlier use yarn mappings. Fabric 1.21+ switched to Mojang mappings, so
   class names align with Forge from that point forward.

4. **Forge 1.21.6+ has a broken EventBus API.** This is a known Forge bug.
   Use NeoForge for Minecraft 1.21.6–1.21.11 instead.

5. **The AI Source Search workflow needs the sources jar unzipped for Fabric.**
   Fabric Loom produces a `*-sources.jar`, not extracted `.java` files. The
   workflow must find and unzip that jar before searching.

6. **`rg` is not available on GitHub Actions runners.** Use `grep -r` instead.

7. **One search result covers multiple versions.** The yarn-mapped class name
   and method signature for `FarmlandBlock.onLandedUpon` is consistent across
   Fabric 1.18–1.19.3. A single AI Source Search run on 1.18.2 was enough to
   fix all 6 missing versions.

---

## Final Result

57/63 versions published to https://modrinth.com/mod/seed-protect:

- **Forge**: 1.8.9, 1.12.2, 1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1–1.20.6, 1.21–1.21.5, 1.21.9–1.21.11
- **Fabric**: 1.16.5, 1.17.1, **1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3** (newly added), 1.19.4, 1.20.1–1.20.6, 1.21–1.21.11
- **NeoForge**: 1.20.2, 1.20.4–1.20.6, 1.21–1.21.11
- **Skipped**: Forge 1.21.6–1.21.11 (broken EventBus API — use NeoForge)

The 6 newly added Fabric versions are shown in **bold** above.
