---
id: FORGE-TICKEVENT-PACKAGE-HISTORY
title: Forge TickEvent package history — fml.common.gameevent (1.8.9–1.12.2) vs event.TickEvent (1.16.5+)
tags: [forge, compile-error, api-change, tick-event, 1.8.9, 1.12.2, 1.16.5, 1.17, 1.18, 1.19, 1.20, 1.21]
versions: [1.8.9, 1.12.2, 1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5]
loaders: [forge]
symbols: [TickEvent, WorldTickEvent, LevelTickEvent, ServerTickEvent]
error_patterns: ["package net.minecraftforge.fml.common.gameevent does not exist", "package TickEvent does not exist"]
---

## Issue

Forge mods fail to compile when using `net.minecraftforge.fml.common.gameevent.TickEvent`
on versions 1.16.5 and later.

## Error

```
error: package net.minecraftforge.fml.common.gameevent does not exist
import net.minecraftforge.fml.common.gameevent.TickEvent;
```

## Root Cause

`TickEvent` moved packages between Forge eras:

| Forge version | TickEvent package |
|---------------|-------------------|
| 1.8.9–1.12.2  | `net.minecraftforge.fml.common.gameevent.TickEvent` |
| 1.16.5+       | `net.minecraftforge.event.TickEvent` |

Additionally, the inner class name changed:

| Forge version | Server/World tick event class |
|---------------|-------------------------------|
| 1.8.9–1.18.2  | `TickEvent.WorldTickEvent` |
| 1.19+         | `TickEvent.LevelTickEvent` |

## Fix

Use the correct import for the target version:

```java
// 1.8.9–1.12.2
import net.minecraftforge.fml.common.gameevent.TickEvent;
// handler: public void onTick(TickEvent.WorldTickEvent event)

// 1.16.5–1.18.2
import net.minecraftforge.event.TickEvent;
// handler: public void onTick(TickEvent.WorldTickEvent event)

// 1.19–1.21.5 (EventBus 6)
import net.minecraftforge.event.TickEvent;
// handler: public void onTick(TickEvent.LevelTickEvent event)

// 1.21.6–1.21.8 (EventBus 7, field access)
import net.minecraftforge.event.TickEvent;
// register: TickEvent.LevelTickEvent.Post.BUS.addListener(this::onTick)
// handler: private void onTick(TickEvent.LevelTickEvent.Post event) { event.level ... }

// 1.21.9+ (EventBus 7, record accessor)
import net.minecraftforge.event.TickEvent;
// register: TickEvent.LevelTickEvent.Post.BUS.addListener(this::onTick)
// handler: private void onTick(TickEvent.LevelTickEvent.Post event) { event.level() ... }
```

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
All Forge versions 1.8.9 through 26.1.2 compiled successfully after using the correct package.
