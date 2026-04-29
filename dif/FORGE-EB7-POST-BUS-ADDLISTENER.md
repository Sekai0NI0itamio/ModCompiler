---
id: FORGE-EB7-POST-BUS-ADDLISTENER
title: Forge 1.21.6+ EventBus 7 — TickEvent.ClientTickEvent.Post.BUS.addListener() for client tick
tags: [forge, compile-error, eventbus7, tick-event, client, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge]
symbols: [ClientTickEvent, TickEvent, BUS, addListener, Post]
error_patterns: ["no suitable method found for addListener.*ClientTickEvent", "cannot find symbol.*variable BUS.*ClientTickEvent"]
---

## Issue

Forge 1.21.6+ (EventBus 7) fails to compile when using `TickEvent.ClientTickEvent.BUS.addListener(handler::method)`
where the handler method takes a `Post` parameter.

## Errors

**Forge 1.21.6–1.21.8** (BUS exists on ClientTickEvent but method ref type mismatch):
```
error: no suitable method found for addListener(AutoFastXp[...]tTick)
    TickEvent.ClientTickEvent.BUS.addListener(AutoFastXpHandler::onClientTick);
                                         ^
  method EventBus.addListener(Consumer<ClientTickEvent>) is not applicable
```

**Forge 1.21.9–26.1.2** (record-based TickEvent, no BUS on ClientTickEvent):
```
error: cannot find symbol
    TickEvent.ClientTickEvent.BUS.addListener(AutoFastXpHandler::onClientTick);
                             ^
  symbol:   variable BUS
```

## Root Cause

In Forge 1.21.6–1.21.8, `TickEvent.ClientTickEvent` has a `BUS` but the handler
must accept the specific `Post` subtype, not the parent `ClientTickEvent`.

In Forge 1.21.9+, `TickEvent` became a sealed interface with record-based subtypes.
`ClientTickEvent` is now an interface with `Pre` and `Post` records, each having
their own static `BUS`.

| Forge version | Pattern |
|---------------|---------|
| 1.16.5–1.21.5 | `@SubscribeEvent public void onTick(TickEvent.ClientTickEvent event)` |
| 1.21.6–1.21.8 | `TickEvent.ClientTickEvent.Post.BUS.addListener(Handler::method)` |
| 1.21.9–26.1.2 | `TickEvent.ClientTickEvent.Post.BUS.addListener(Handler::method)` (same) |

## Fix

Use `Post.BUS` (not `ClientTickEvent.BUS`) and make the handler accept `Post`:

```java
// In mod constructor (1.21.6+):
TickEvent.ClientTickEvent.Post.BUS.addListener(AutoFastXpHandler::onClientTick);

// Handler method:
public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
    // runs after each client tick
    Minecraft mc = Minecraft.getInstance();
    // ...
}
```

For 1.21.9+ (record-based), the same pattern works because `Post` is a record
that implements `ClientTickEvent`, and `Post.BUS` is a static field on the record.

## Verified

Confirmed in Auto Fast XP all-versions port (run 2, April 2026).
Forge 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 all passed after this fix.

## See Also

- `FORGE-EB7-EVENTBUS7-PATTERN` — general EventBus 7 migration guide
