---
id: FORGE-EB7-EVENTBUS7-PATTERN
title: Forge 1.21.6+ EventBus 7 — @SubscribeEvent removed, use Event.BUS.addListener()
tags: [forge, compile-error, eventbus, eventbus7, api-change, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge]
symbols: [SubscribeEvent, EventBusSubscriber, FarmlandTrampleEvent, BUS, addListener]
error_patterns: ["package net.minecraftforge.eventbus.api does not exist", "cannot find symbol.*SubscribeEvent", "cannot find symbol.*EventBusSubscriber"]
---

## Issue

Forge 1.21.6+ uses EventBus 7, a complete rewrite of the event system. The old `@SubscribeEvent` and `@Mod.EventBusSubscriber` patterns no longer work.

## Error

```
error: package net.minecraftforge.eventbus.api does not exist
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

or

```
error: cannot find symbol
@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.FORGE)
    ^
  symbol:   class EventBusSubscriber
  location: @interface Mod
```

## Root Cause

Forge 1.21.6 migrated to EventBus 7. Key changes:
- Each event has its own static `BUS` field (e.g. `BlockEvent.FarmlandTrampleEvent.BUS`)
- Cancellation is done by **returning `true`** (boolean return type), not `event.setCanceled(true)`
- `@SubscribeEvent` is no longer used for cancellable event listeners
- `@Mod.EventBusSubscriber` is replaced by direct `BUS.addListener()`
- The `alwaysCancelling` parameter tells EventBus 7 this listener always cancels

## Fix

**Forge 1.21.6+ (EventBus 7) pattern:**

```java
@Mod("mymod")
public final class MyMod {
    public MyMod(FMLJavaModLoadingContext context) {
        // Register on the game/Forge bus (not the mod bus).
        // alwaysCancelling = true tells EventBus 7 this listener always cancels.
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            MyMod::onFarmlandTrample
        );
    }

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        // Return true to cancel the event.
        return true;
    }
}
```

**EventBus registration patterns by version:**

| Forge version | Pattern |
|---------------|---------|
| 52-55.x (1.21.1–1.21.5) | `context.getModEventBus().addListener(...)` (EventBus 6) |
| 56-58.x (1.21.6–1.21.8) | `AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(...)` |
| 59-60.x (1.21.9–1.21.10) | `Event.BUS.addListener(...)` (static BUS, `getBus` deprecated) |
| 61.x+ (1.21.11+) | `Event.BUS.addListener(...)` (same) |

## Verified

Confirmed in Seed Protect port (Phase 2) for Forge 1.21.6-1.21.11.
Reference: https://gist.github.com/PaintNinja/ad82c224aecee25efac1ea3e2cf19b91
