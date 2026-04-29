---
id: NEOFORGE-FMLENVIRONMENT-GETDIST
title: NeoForge 1.21.9+ and 26.1 — FMLEnvironment.dist field removed, use FMLEnvironment.getDist()
tags: [neoforge, compile-error, api-change, FMLEnvironment, dist, 1.21.9, 26.1]
versions: [1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [neoforge]
symbols: [FMLEnvironment, dist, getDist, Dist]
error_patterns: ["cannot find symbol.*variable dist.*FMLEnvironment", "package net.minecraftforge.fml.loading does not exist"]
---

## Issue

NeoForge mods fail to compile when using `FMLEnvironment.dist` (field access) or
importing from `net.minecraftforge.fml.loading.FMLEnvironment`.

## Error

```
error: cannot find symbol
        if (FMLEnvironment.dist == Dist.CLIENT) {
                          ^
  symbol:   variable dist
  location: class FMLEnvironment
```

or

```
error: package net.minecraftforge.fml.loading does not exist
import net.minecraftforge.fml.loading.FMLEnvironment;
                                     ^
```

## Root Cause

Two separate issues depending on NeoForge version:

**NeoForge 1.20.x–1.21.8**: `FMLEnvironment` is from `net.neoforged.fml.loading`
and exposes `dist` as a **field**: `FMLEnvironment.dist == Dist.CLIENT`

**NeoForge 1.21.9+**: `FMLEnvironment` is still from `net.neoforged.fml.loading`
but `dist` became a **method**: `FMLEnvironment.getDist() == Dist.CLIENT`

**NeoForge 26.1**: Same as 1.21.9+ — use `net.neoforged.fml.loading.FMLEnvironment.getDist()`.
The `net.minecraftforge.fml.loading` package does NOT exist in NeoForge 26.1.

| NeoForge version | Import | Access |
|-----------------|--------|--------|
| 1.20.x–1.21.8 | `net.neoforged.fml.loading.FMLEnvironment` | `FMLEnvironment.dist` (field) |
| 1.21.9–26.1.x | `net.neoforged.fml.loading.FMLEnvironment` | `FMLEnvironment.getDist()` (method) |

## Fix

```java
// NeoForge 1.20.x–1.21.8
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

if (FMLEnvironment.dist == Dist.CLIENT) { ... }

// NeoForge 1.21.9+ and 26.1.x
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

if (FMLEnvironment.getDist() == Dist.CLIENT) { ... }
```

In the generator script, use separate source strings:

```python
SRC_120_NEO_MOD = "... FMLEnvironment.dist == Dist.CLIENT ..."    # 1.20.x–1.21.8
SRC_1219_NEO_MOD = "... FMLEnvironment.getDist() == Dist.CLIENT ..." # 1.21.9+
SRC_261_NEO_MOD = SRC_1219_NEO_MOD  # 26.1 same as 1.21.9+
```

## Verified

Confirmed in Auto Fast XP all-versions port (runs 3–4, April 2026).
NeoForge 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2 all passed after this fix.
