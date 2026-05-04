---
id: FORGE-189-ENTITYLIVINGBASE-NOT-ENTITYLIVING
title: Forge 1.8.9 — LivingSpawnEvent.CheckSpawn uses EntityLivingBase, not EntityLiving
tags: [forge, compile-error, api-change, LivingSpawnEvent, 1.8.9, EntityLivingBase]
versions: [1.8.9]
loaders: [forge]
symbols: [LivingSpawnEvent, CheckSpawn, EntityLivingBase, EntityLiving]
error_patterns: ["incompatible types: EntityLivingBase cannot be converted to EntityLiving"]
---

## Issue

Forge 1.8.9 `LivingSpawnEvent.CheckSpawn` fails to compile when the handler
references `event.entityLiving` as `EntityLiving`.

## Error

```
error: incompatible types: EntityLivingBase cannot be converted to EntityLiving
```

## Root Cause

In Forge 1.8.9, `LivingSpawnEvent` stores the entity as `EntityLivingBase` (the
base class), not `EntityLiving`. The field is `public EntityLivingBase entityLiving`.

In 1.12.2+, the field type changed to `EntityLiving` (and later `MobEntity` / `Mob`
in modern versions).

## Fix

Use `EntityLivingBase` in 1.8.9:

```java
// WRONG — 1.8.9
import net.minecraft.entity.EntityLiving;
EntityLiving entity = event.entityLiving;  // compile error

// CORRECT — 1.8.9
import net.minecraft.entity.EntityLivingBase;
EntityLivingBase entity = event.entityLiving;
if (entity instanceof IMob) { ... }
```

## Verified

Confirmed in No Hostile Mobs all-versions port (run 2, May 2026).
1.8.9 forge failed with this error. Fixed by switching to `EntityLivingBase`.
