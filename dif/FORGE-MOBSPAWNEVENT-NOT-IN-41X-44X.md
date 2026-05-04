---
id: FORGE-MOBSPAWNEVENT-NOT-IN-41X-44X
title: Forge 1.19–1.19.3 (41.x–44.x) — MobSpawnEvent not accessible in API jar, use EntityJoinLevelEvent
tags: [forge, compile-error, api-inaccessible, MobSpawnEvent, EntityJoinLevelEvent, 1.19, 1.19.1, 1.19.2, 1.19.3]
versions: [1.19, 1.19.1, 1.19.2, 1.19.3]
loaders: [forge]
symbols: [MobSpawnEvent, FinalizeSpawn, EntityJoinLevelEvent]
error_patterns: ["cannot find symbol.*class MobSpawnEvent", "package MobSpawnEvent does not exist"]
---

## Issue

Forge 1.19–1.19.3 fails to compile when importing `MobSpawnEvent` even though
the class appears in `DecompiledMinecraftSourceCode/1.19-forge/`.

## Error

```
error: cannot find symbol
import net.minecraftforge.event.entity.living.MobSpawnEvent;
                                             ^
  symbol:   class MobSpawnEvent
  location: package net.minecraftforge.event.entity.living
```

## Root Cause

The decompiled sources for 1.19 were generated from a **newer** Forge version than
what the build template uses. The build template pins:

| MC Version | Forge version used | Has MobSpawnEvent? |
|------------|-------------------|--------------------|
| 1.19       | 41.1.0            | ✗ Not in API jar   |
| 1.19.1     | 42.0.9            | ✗ Not in API jar   |
| 1.19.2     | 43.4.22           | ✗ Not in API jar   |
| 1.19.3     | 44.1.21           | ✗ Not in API jar   |
| 1.19.4     | 45.4.3            | ✅ Accessible      |

`MobSpawnEvent` exists in the decompiled sources (generated from a later Forge)
but is NOT exported to the public mod API jar in Forge 41.x–44.x.
This is the `SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE` pitfall.

## Fix

For Forge 1.19–1.19.3, use `EntityJoinLevelEvent` (which IS accessible) to block
hostile mob spawns. Cancel the event when the entity's `MobCategory` is `MONSTER`.

```java
// CORRECT for Forge 1.19–1.19.3
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@SubscribeEvent
public void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
        event.setCanceled(true);
    }
}
```

For Forge 1.19.4+, `MobSpawnEvent.FinalizeSpawn` is accessible and preferred:

```java
// CORRECT for Forge 1.19.4+
import net.minecraftforge.event.entity.living.MobSpawnEvent;

@SubscribeEvent
public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
        event.setSpawnCancelled(true);
    }
}
```

## Verified

Confirmed in No Hostile Mobs all-versions port (run 1 failed, run 2 fixed, May 2026).
1.19, 1.19.1, 1.19.2, 1.19.3 all failed with this error.
Fixed by switching to `EntityJoinLevelEvent`. 1.19.4 passed with `MobSpawnEvent`.
