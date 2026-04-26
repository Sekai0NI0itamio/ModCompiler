---
id: NEOFORGE-26-EVENTBUSSUBSCRIBER-STANDALONE
title: NeoForge 26.1 — @EventBusSubscriber is standalone, not nested in @Mod
tags: [neoforge, compile-error, api-change, EventBusSubscriber, 26.1]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [neoforge]
symbols: [EventBusSubscriber, Mod]
error_patterns: ["cannot find symbol.*EventBusSubscriber", "symbol:.*class EventBusSubscriber.*location: @interface Mod"]
---

## Issue

NeoForge 26.1.x fails to compile when using `@Mod.EventBusSubscriber(...)` — the nested annotation no longer exists inside `@Mod`.

## Error

```
error: cannot find symbol
@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.FORGE)
    ^
  symbol:   class EventBusSubscriber
  location: @interface Mod
```

## Root Cause

In NeoForge 26.1, `EventBusSubscriber` was moved out of `@Mod` and became a **standalone annotation** from `net.neoforged.fml.common.EventBusSubscriber`. The `bus = ...` parameter was also removed — it defaults to the game/Forge bus.

NeoForge 26.1 still uses `NeoForge.EVENT_BUS.post()` with `ICancellableEvent.setCanceled(true)` — it does NOT use EventBus 7's `.BUS.addListener()` pattern (that's Forge-specific).

## Fix

**NeoForge 1.20.x–1.21.x (old pattern):**
```java
import net.neoforged.fml.common.Mod;

@Mod("mymod")
@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MyMod {
    @SubscribeEvent
    public static void onEvent(SomeEvent event) {
        event.setCanceled(true);
    }
}
```

**NeoForge 26.1+ (new pattern):**
```java
import net.neoforged.fml.common.EventBusSubscriber;  // standalone import
import net.neoforged.fml.common.Mod;

@Mod("mymod")
@EventBusSubscriber(modid = "mymod")  // no bus= parameter needed
public final class MyMod {
    @SubscribeEvent
    public static void onEvent(SomeEvent event) {
        event.setCanceled(true);
    }
}
```

Key differences:
- Import `net.neoforged.fml.common.EventBusSubscriber` directly (not from `@Mod`)
- Use `@EventBusSubscriber(modid = "mymod")` — no `bus =` parameter
- Keep `event.setCanceled(true)` — NeoForge 26.1 still uses the old cancellation pattern

## Verified

Confirmed in Seed Protect port (run-20260426-030646) for NeoForge 26.1/26.1.1/26.1.2.
