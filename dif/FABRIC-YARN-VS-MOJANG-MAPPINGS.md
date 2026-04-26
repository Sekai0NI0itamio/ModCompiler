---
id: FABRIC-YARN-VS-MOJANG-MAPPINGS
title: Fabric — Yarn mappings (1.16.5-1.20.x) vs Mojang mappings (1.21+) class name differences
tags: [fabric, compile-error, mappings, yarn, mojang, FarmBlock, FarmlandBlock, mixin]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1]
loaders: [fabric]
symbols: [FarmBlock, FarmlandBlock, onLandedUpon, fallOn]
error_patterns: ["Mixin target.*could not be found", "cannot find symbol.*FarmBlock", "cannot find symbol.*FarmlandBlock", "package net.minecraft.block does not exist", "package net.minecraft.world.level.block does not exist"]
---

## Issue

Fabric mods fail to compile or mixin targets fail at runtime because the wrong class names are used. Fabric uses **yarn mappings** for 1.16.5–1.20.x and **Mojang mappings** for 1.21+.

## Error

```
error: Mixin target net.minecraft.world.level.block.FarmlandBlock could not be found
```

or

```
error: cannot find symbol
import net.minecraft.block.FarmlandBlock;
                          ^
```

## Root Cause

Fabric uses two completely different mapping systems:

| Fabric version | Mappings | Package | Class | Method |
|----------------|----------|---------|-------|--------|
| 1.16.5–1.20.x | **Yarn** | `net.minecraft.block` | `FarmlandBlock` | `onLandedUpon(World, BlockState, BlockPos, Entity, float)` |
| 1.21–1.21.x | **Mojang** | `net.minecraft.world.level.block` | `FarmBlock` | `fallOn(Level, BlockState, BlockPos, Entity, float)` |
| 26.1+ | **Mojang** | `net.minecraft.world.level.block` | `FarmlandBlock` | `fallOn(Level, BlockState, BlockPos, Entity, double)` |

Note: In 26.1+, the class was renamed back to `FarmlandBlock` (from `FarmBlock`), and `fallDistance` changed from `float` to `double`.

## Fix

Use the correct class names for each era:

**Fabric 1.16.5–1.20.x (Yarn):**
```java
import net.minecraft.block.FarmlandBlock;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void cancelTrample(World world, BlockState state,
            BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
```

**Fabric 1.21–1.21.x (Mojang):**
```java
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

@Mixin(FarmBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void cancelTrample(Level level, BlockState state,
            BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
```

**Fabric 26.1+ (Mojang, renamed back + double):**
```java
import net.minecraft.world.level.block.FarmlandBlock;  // renamed back!
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void cancelTrample(Level level, BlockState state,
            BlockPos pos, Entity entity, double fallDistance, CallbackInfo ci) {  // double!
        ci.cancel();
    }
}
```

Always use the AI Source Search to verify the exact class name and method signature:
```bash
python3 scripts/ai_source_search.py --version 1.20.1 --loader fabric \
    --queries "FarmlandBlock" "fallOn" "onLandedUpon" --local-only
```

## Verified

Confirmed in Seed Protect port (Phase 1 and Phase 2) and Day Counter port.
