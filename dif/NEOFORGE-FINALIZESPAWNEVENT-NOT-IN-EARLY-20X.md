---
id: NEOFORGE-FINALIZESPAWNEVENT-NOT-IN-EARLY-20X
title: NeoForge 1.20.2–1.20.6 — FinalizeSpawnEvent not accessible in API jar, use EntityJoinLevelEvent
tags: [neoforge, compile-error, api-inaccessible, FinalizeSpawnEvent, EntityJoinLevelEvent, 1.20.2, 1.20.4, 1.20.5, 1.20.6]
versions: [1.20.2, 1.20.4, 1.20.5, 1.20.6]
loaders: [neoforge]
symbols: [FinalizeSpawnEvent, EntityJoinLevelEvent]
error_patterns: ["cannot find symbol.*class FinalizeSpawnEvent", "package net.neoforged.neoforge.event.entity.living.*FinalizeSpawnEvent"]
---

## Issue

NeoForge 1.20.2–1.20.6 fails to compile when importing `FinalizeSpawnEvent`
even though the class appears in `DecompiledMinecraftSourceCode/1.20.2-neoforge/`.

## Error

```
error: cannot find symbol
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
                                                 ^
  symbol:   class FinalizeSpawnEvent
  location: package net.neoforged.neoforge.event.entity.living
```

## Root Cause

`FinalizeSpawnEvent` exists in the decompiled sources but is NOT exported to the
public mod API jar in early NeoForge 20.x builds. This is the
`SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE` pitfall.

The decompiled sources were generated from a newer NeoForge build than what the
template actually resolves to (NeoForge 20.2.93 for 1.20.2, etc.).

## Fix

For NeoForge 1.20.2–1.20.6, use `EntityJoinLevelEvent` (which IS accessible)
to block hostile mob spawns:

```java
// CORRECT for NeoForge 1.20.2–1.20.6
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@Mod(MyMod.MODID)
public class MyMod {
    public MyMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setCanceled(true);
        }
    }
}
```

For NeoForge 1.21+, `FinalizeSpawnEvent` is accessible and preferred:

```java
// CORRECT for NeoForge 1.21+
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@SubscribeEvent
public void onFinalizeSpawn(FinalizeSpawnEvent event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
        event.setSpawnCancelled(true);
    }
}
```

## Verified

Confirmed in No Hostile Mobs all-versions port (run 1 failed, run 2 fixed, May 2026).
NeoForge 1.20.2, 1.20.4, 1.20.5 all failed with this error.
Fixed by switching to `EntityJoinLevelEvent`. NeoForge 1.20.6 also fixed.
