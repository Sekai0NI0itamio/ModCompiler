---
id: FABRIC-MOBSPAWNTYPE-RENAMED-ENTITYSPAWNREASON-1212
title: Fabric/NeoForge 1.21.2+ — MobSpawnType renamed to EntitySpawnReason
tags: [fabric, neoforge, compile-error, api-change, MobSpawnType, EntitySpawnReason, 1.21.2, 1.21.9, 26.1]
versions: [1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [fabric, neoforge]
symbols: [MobSpawnType, EntitySpawnReason]
error_patterns: ["cannot find symbol.*class MobSpawnType", "package.*entity.*MobSpawnType does not exist"]
---

## Issue

Fabric and NeoForge 1.21.2+ fail to compile when importing `MobSpawnType` in
Mixin method signatures or event handlers.

## Error

```
error: cannot find symbol
import net.minecraft.world.entity.MobSpawnType;
                                 ^
  symbol:   class MobSpawnType
  location: package net.minecraft.world.entity
```

## Root Cause

In Minecraft 1.21.2, `MobSpawnType` was renamed to `EntitySpawnReason`.
The class moved from `net.minecraft.world.entity.MobSpawnType` to
`net.minecraft.world.entity.EntitySpawnReason`.

This affects:
- Fabric 1.21.2–1.21.8 (Mojang mappings)
- Fabric 1.21.9–26.1.2 (Mojang mappings)
- NeoForge 1.21.2+ (Mojang mappings)

The `checkSpawnRules` method signature on `Mob` also changed:

```java
// 1.21–1.21.1 (MobSpawnType)
public boolean checkSpawnRules(LevelAccessor levelAccessor, MobSpawnType mobSpawnType)

// 1.21.2+ (EntitySpawnReason)
public boolean checkSpawnRules(LevelAccessor levelAccessor, EntitySpawnReason entitySpawnReason)
```

## Fix

Use `EntitySpawnReason` for 1.21.2+:

```java
// WRONG — 1.21.2+
import net.minecraft.world.entity.MobSpawnType;
private void blockHostileSpawn(LevelAccessor level, MobSpawnType spawnType, ...) { ... }

// CORRECT — 1.21.2+
import net.minecraft.world.entity.EntitySpawnReason;
private void blockHostileSpawn(LevelAccessor level, EntitySpawnReason spawnReason, ...) { ... }
```

## Version Boundary

| Version | Class name |
|---------|-----------|
| 1.21–1.21.1 | `MobSpawnType` |
| 1.21.2+ | `EntitySpawnReason` |

## Verified

Confirmed in No Hostile Mobs all-versions port (run 1 failed, run 2 fixed, May 2026).
Fabric 1.21.2, 1.21.9, 26.1, 26.1.1, 26.1.2 all failed with this error.
Fixed by switching to `EntitySpawnReason` for 1.21.2+ targets.
