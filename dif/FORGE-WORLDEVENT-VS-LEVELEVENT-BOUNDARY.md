---
id: FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY
title: Forge WorldEvent renamed to LevelEvent in 1.19 (not 1.18 — common mistake)
tags: [forge, compile-error, api-change, world-event, level-event, 1.18, 1.19]
versions: [1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4]
loaders: [forge]
symbols: [WorldEvent, LevelEvent, WorldTickEvent, LevelTickEvent]
error_patterns: ["package net.minecraftforge.event.level does not exist", "package LevelEvent does not exist"]
---

## Issue

Forge 1.18.x fails to compile when using `net.minecraftforge.event.level.LevelEvent`.
Forge 1.19+ fails to compile when using `net.minecraftforge.event.world.WorldEvent`.

## Error

```
// On Forge 1.18.x when using LevelEvent:
error: package net.minecraftforge.event.level does not exist
import net.minecraftforge.event.level.LevelEvent;

// On Forge 1.19+ when using WorldEvent:
error: package net.minecraftforge.event.world does not exist
import net.minecraftforge.event.world.WorldEvent;
```

## Root Cause

The rename from `WorldEvent` → `LevelEvent` happened in **Forge 1.19**, not 1.18.
Forge 1.18.x still uses the old `world` package.

| Forge version | World load event | World tick event |
|---------------|-----------------|-----------------|
| 1.12.2–1.18.2 | `net.minecraftforge.event.world.WorldEvent.Load` | `TickEvent.WorldTickEvent` |
| 1.19+         | `net.minecraftforge.event.level.LevelEvent.Load` | `TickEvent.LevelTickEvent` |

Note: `TickEvent` itself stays in `net.minecraftforge.event.TickEvent` for all versions 1.16.5+.

## Fix

```java
// Forge 1.12.2–1.18.2
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.TickEvent;

@SubscribeEvent
public void onWorldLoad(WorldEvent.Load event) {
    if (!(event.getWorld() instanceof Level)) return;
    Level level = (Level) event.getWorld();
    // ...
}

@SubscribeEvent
public void onWorldTick(TickEvent.WorldTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    if (event.world.isClientSide()) return;
    // ...
}

// Forge 1.19+
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;

@SubscribeEvent
public void onLevelLoad(LevelEvent.Load event) {
    if (!(event.getLevel() instanceof Level)) return;
    Level level = (Level) event.getLevel();
    // ...
}

@SubscribeEvent
public void onLevelTick(TickEvent.LevelTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    if (event.level.isClientSide()) return;
    // ...
}
```

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
Forge 1.18, 1.18.1, 1.18.2 all compiled with `WorldEvent`/`WorldTickEvent`.
Forge 1.19–1.21.x all compiled with `LevelEvent`/`LevelTickEvent`.
