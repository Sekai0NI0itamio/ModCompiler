---
id: FABRIC-119-REGISTRY-PATH-SPLIT
title: Fabric 1.19–1.19.2 uses net.minecraft.util.registry.Registry, not net.minecraft.registry.Registries
tags: [fabric, compile-error, api-change, 1.19, registry, yarn]
versions: [1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4]
loaders: [fabric]
symbols: [Registry, Registries, BuiltInRegistries]
error_patterns: ["package net.minecraft.util.registry does not exist", "package net.minecraft.registry does not exist", "cannot find symbol.*class Registries.*net.minecraft.registry", "cannot find symbol.*class Registry.*net.minecraft.util.registry"]
---

## Issue

Fabric builds for 1.19–1.19.2 fail with a registry package error. The correct
registry path differs between 1.19–1.19.2 and 1.19.3+.

## Error

For 1.19–1.19.2 when using the new path:
```
error: package net.minecraft.registry does not exist
import net.minecraft.registry.Registries;
```

For 1.19.3+ when using the old path:
```
error: package net.minecraft.util.registry does not exist
import net.minecraft.util.registry.Registry;
```

## Root Cause

The Fabric yarn registry package moved between MC versions:

| MC Version | Yarn path | Access pattern |
|------------|-----------|----------------|
| 1.16.5–1.19.2 | `net.minecraft.util.registry.Registry` | `Registry.BLOCK.getId(b)` |
| 1.19.3–1.20.x | `net.minecraft.registry.Registries` | `Registries.BLOCK.getId(b)` |
| 1.21+ | `net.minecraft.core.registries.BuiltInRegistries` (Mojang) | `BuiltInRegistries.BLOCK.getKey(b)` |

**Critical pitfall with `anchor_only` mode**: The `1.19-1.19.4` fabric template uses
`anchor_version=1.19.4` with `yarn_mappings=1.19.4+build.2`. When building for 1.19,
the adapter sets `minecraft_version=1.19` in gradle.properties. Loom then downloads
the yarn for MC 1.19 (not 1.19.4), which uses the OLD `net.minecraft.util.registry`
path. The decompiled sources in `DecompiledMinecraftSourceCode/1.19-fabric/` show
`net.minecraft.registry.Registries` because they were generated with 1.19.4 yarn —
do NOT trust them for the exact package path when `anchor_only` is in use.

## Fix

**Source code**: Use the correct import for each version range:

```java
// Fabric 1.16.5–1.19.2
import net.minecraft.util.registry.Registry;
String n = Registry.BLOCK.getId(b).toString();

// Fabric 1.19.3–1.20.x
import net.minecraft.registry.Registries;
String n = Registries.BLOCK.getId(b).toString();

// Fabric 1.21+
import net.minecraft.core.registries.BuiltInRegistries;
String n = BuiltInRegistries.BLOCK.getKey(b).toString();
```

**version-manifest.json**: Add explicit dependency overrides for 1.19, 1.19.1, 1.19.2
to pin their yarn and fabric-api versions so Loom downloads the correct yarn:

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

Without these overrides, Loom resolves yarn for the exact MC version being built,
which may differ from the anchor's yarn generation.

## Verified

Confirmed in Optimized Vein Miner all-versions port (runs 6–7).
1.19 succeeded first, 1.19.1 and 1.19.2 needed correct fabric-api versions.
