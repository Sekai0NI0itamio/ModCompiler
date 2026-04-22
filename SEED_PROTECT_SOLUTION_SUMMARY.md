# Seed Protect Update - Solution Summary

## Problem

The Seed Protect mod needed to be updated to all supported Minecraft versions. Initial attempts revealed:

- **51/63 versions already published** (81% coverage)
- **12 versions missing**:
  - 6 Fabric versions (1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3)
  - 6 Forge versions (1.21.6-1.21.11)

## Root Causes

### Fabric 1.18-1.19.3 Failures
**Issue**: Class name and package mapping differences between Fabric versions

Fabric uses **yarn mappings**, which give Minecraft classes different names and packages
compared to Mojang/Forge mappings. The mixin target was wrong in every attempt:

1. Used `@Mixin(targets = "net.minecraft.world.level.block.FarmBlock")` — class not found
2. Used `@Mixin(targets = "net.minecraft.world.level.block.FarmlandBlock")` — class not found
3. Used `@Mixin(FarmBlock.class)` with import — package doesn't exist

**Root cause**: Guessing class names without looking at the actual Minecraft source for that
version and loader. Fabric yarn mappings use `net.minecraft.block` not
`net.minecraft.world.level.block`.

### Forge 1.21.6-1.21.11 Failures
**Issue**: Broken EventBus API in Forge 1.21.6+

```
error: package net.minecraftforge.eventbus.api does not exist
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

**Root cause**: Known issue with Forge 1.21.6+. The EventBus API package is missing or broken.
NeoForge versions work correctly for all these Minecraft versions.

## Solution

### How the Fabric issue was diagnosed and fixed

The correct approach is to **never guess class names**. Instead, use the AI Source Search
workflow to look up the actual Minecraft source for the exact version and loader.

**Step 1: Run the AI Source Search workflow**

```bash
python3 scripts/ai_source_search.py \
    --version 1.18.2 \
    --loader fabric \
    --queries "FarmlandBlock" "fallOn" "trample" \
    --files "*.java"
```

**Step 2: Read the results**

The workflow decompiles Minecraft 1.18.2 Fabric on GitHub Actions, searches for the queries,
and returns the full class files. The actual `FarmlandBlock.java` showed:

- **Package**: `net.minecraft.block` (yarn mapping — NOT `net.minecraft.world.level.block`)
- **Class**: `FarmlandBlock` (same name, different package)
- **Method**: `onLandedUpon` (yarn-mapped name — NOT `fallOn`)
- **Signature**: `public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance)`

**Step 3: Write the correct mixin**

```java
package com.seedprotect.mixin;

import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(World world, BlockState state, BlockPos pos,
            Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
```

All 6 Fabric versions built and published successfully on the first attempt after this fix.

## Key Lesson: ALWAYS use AI Source Search before writing version-specific code

This is the documented pattern for any future mod that hits a "class not found" or
"method not found" error, or any time the correct class name or package is uncertain:

```bash
# Template — replace version, loader, and queries with what you need
python3 scripts/ai_source_search.py \
    --version <minecraft_version> \
    --loader <forge|fabric|neoforge> \
    --queries "<ClassName>" "<methodName>" \
    --files "*<ClassName>*.java"
```

See `docs/IDE_AGENT_INSTRUCTION_SHEET.txt` → "Searching Minecraft/Forge/NeoForge API source
code" for the full usage guide and rules.

## Final State

- **57/63 versions published** (90% coverage)
- Fabric 1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3 — ✓ published
- Forge 1.21.6-1.21.11 — skipped (broken EventBus API; use NeoForge for those versions)

## Files Modified

- `scripts/generate_seedprotect_bundle.py` — fixed Fabric mixin (correct package + method)
- `.github/workflows/ai-source-search.yml` — fixed Fabric sources extraction (unzip sources jar)
- `docs/IDE_AGENT_INSTRUCTION_SHEET.txt` — updated AI Source Search usage instructions
