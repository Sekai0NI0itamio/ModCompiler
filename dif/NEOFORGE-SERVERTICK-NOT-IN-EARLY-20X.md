---
id: NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X
title: NeoForge 1.20.2–1.20.6 — net.neoforged.neoforge.event.tick.ServerTickEvent does not exist in early builds
tags: [neoforge, compile-error, api-change, tick-event, 1.20.2, 1.20.4, 1.20.5, 1.20.6]
versions: [1.20.2, 1.20.4, 1.20.5, 1.20.6]
loaders: [neoforge]
symbols: [ServerTickEvent, LevelTickEvent, event.tick]
error_patterns: ["package net.neoforged.neoforge.event.tick.ServerTickEvent does not exist", "package net.neoforged.neoforge.event.tick does not exist"]
---

## Issue

NeoForge 1.20.2 and 1.20.4 fail to compile when importing
`net.neoforged.neoforge.event.tick.ServerTickEvent`.

## Error

```
error: package net.neoforged.neoforge.event.tick.ServerTickEvent does not exist
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
```

## Root Cause

The `net.neoforged.neoforge.event.tick` package exists in the decompiled sources
committed to `DecompiledMinecraftSourceCode/1.20.2-neoforge/` — but those sources
were generated with a **newer** NeoForge version than what the build template actually
uses.

The 1.20-1.20.6 NeoForge template uses `neo_version=20.2.+` for 1.20.2, which resolves
to NeoForge **20.2.93**. This early build predates the `event.tick` package split.

The decompiled sources reflect a later NeoForge 20.2.x build where the package exists.
This mismatch causes the compile error.

## Fix

For NeoForge 1.20.2–1.20.6, avoid `ServerTickEvent` entirely. Use only
`ServerStartingEvent` to set the gamerule once when the server starts:

```java
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@SubscribeEvent
public void onServerStarting(ServerStartingEvent event) {
    MinecraftServer server = event.getServer();
    for (ServerLevel level : server.getAllLevels()) {
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
    }
}
```

For NeoForge 1.21+, `ServerTickEvent.Post` from `net.neoforged.neoforge.event.tick`
is available and works correctly.

## Key Rule

**Never trust the decompiled sources for NeoForge 1.20.x tick events.**
The decompiled sources may have been generated with a newer NeoForge build than
what the template actually resolves to. Always test with a minimal build first.

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
NeoForge 1.20.2 and 1.20.4 both compiled successfully after removing `ServerTickEvent`
and using `ServerStartingEvent` only.
