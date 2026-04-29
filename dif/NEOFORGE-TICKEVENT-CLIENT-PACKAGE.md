---
id: NEOFORGE-TICKEVENT-CLIENT-PACKAGE
title: NeoForge 1.21.2+ — ClientTickEvent moved to net.neoforged.neoforge.client.event, not net.neoforged.neoforge.event
tags: [neoforge, compile-error, api-change, tick-event, client, 1.21.2, 1.21.9, 26.1]
versions: [1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [neoforge]
symbols: [ClientTickEvent, TickEvent]
error_patterns: ["cannot find symbol.*TickEvent", "package.*neoforge.event.*TickEvent.*does not exist"]
---

## Issue

NeoForge mods fail to compile when importing `net.neoforged.neoforge.event.TickEvent`
for client tick handling. That class does not contain `ClientTickEvent` in NeoForge 1.21.2+.

## Error

```
error: cannot find symbol
import net.neoforged.neoforge.event.TickEvent;
                                   ^
error: package TickEvent does not exist
    public void onClientTick(TickEvent.ClientTickEvent event) {
                                      ^
```

## Root Cause

In NeoForge 1.21.2+, `ClientTickEvent` was moved to the client-specific event package:

| NeoForge version | ClientTickEvent location |
|-----------------|--------------------------|
| 1.20.x (old pattern) | `net.neoforged.neoforge.event.TickEvent.ClientTickEvent` |
| 1.21.2+ | `net.neoforged.neoforge.client.event.ClientTickEvent` |

The class structure also changed — it now has `Pre` and `Post` inner classes:

```java
// NeoForge 1.21.2+
net.neoforged.neoforge.client.event.ClientTickEvent.Pre
net.neoforged.neoforge.client.event.ClientTickEvent.Post
```

## Fix

Use the correct import and event class for the target version:

```java
// NeoForge 1.20.x (old)
import net.neoforged.neoforge.event.TickEvent;
// handler: public void onClientTick(TickEvent.ClientTickEvent event) {
//   if (event.phase != TickEvent.Phase.END) return;

// NeoForge 1.21.2+ (correct)
import net.neoforged.neoforge.client.event.ClientTickEvent;
// handler: public void onClientTick(ClientTickEvent.Post event) {
//   (no phase check needed — Post fires after tick)
```

Full handler example for NeoForge 1.21.2+:

```java
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class MyHandler {
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        // runs every client tick, after tick work
    }
}
```

## Verified

Confirmed in Auto Fast XP all-versions port (run 2, April 2026).
NeoForge 1.21.2, 1.21.9, 1.21.10, 1.21.11 all passed after this fix.
NeoForge 26.1, 26.1.1, 26.1.2 also use this same package.
