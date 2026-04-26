---
id: NEOFORGE-BUS-FORGE-NOT-GAME
title: NeoForge 1.20.x-1.21.x — use Bus.FORGE for game events, not Bus.GAME
tags: [neoforge, compile-error, api-change, EventBusSubscriber, Bus.FORGE, Bus.GAME]
versions: [1.20.2, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11]
loaders: [neoforge]
symbols: [EventBusSubscriber, Bus]
error_patterns: ["cannot find symbol.*Bus.GAME", "symbol:.*variable GAME.*location: class.*Bus"]
---

## Issue

NeoForge 1.20.x–1.21.x fails to compile when using `Mod.EventBusSubscriber.Bus.GAME`.

## Error

```
error: cannot find symbol
@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.GAME)
                                                                          ^
  symbol:   variable GAME
  location: class Mod.EventBusSubscriber.Bus
```

## Root Cause

`Bus.GAME` does not exist in NeoForge. The correct bus name for game/world events (like `BlockEvent.FarmlandTrampleEvent`) is `Bus.FORGE`.

NeoForge has two buses:
- `Bus.FORGE` — game/world events (most events go here)
- `Bus.MOD` — mod lifecycle events (setup, registration)

## Fix

Use `Bus.FORGE` for game events:

```java
@Mod("mymod")
@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MyMod {
    @SubscribeEvent
    public static void onEvent(BlockEvent.FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
```

Note: For NeoForge 26.1+, use the standalone `@EventBusSubscriber` annotation instead (see NEOFORGE-26-EVENTBUSSUBSCRIBER-STANDALONE).

## Verified

Confirmed in Seed Protect port (Phase 2, Challenge 6).
