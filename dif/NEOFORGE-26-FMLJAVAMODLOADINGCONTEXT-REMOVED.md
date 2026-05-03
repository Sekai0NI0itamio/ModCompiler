---
id: NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED
title: NeoForge 26.1+ — FMLJavaModLoadingContext removed, use constructor injection (IEventBus, ModContainer)
tags: [neoforge, compile-error, api-change, 26.1, FMLJavaModLoadingContext, IEventBus, ModContainer]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [neoforge]
symbols: [FMLJavaModLoadingContext, IEventBus, ModContainer, NeoForge, EVENT_BUS]
error_patterns: ["cannot find symbol.*FMLJavaModLoadingContext", "package net.neoforged.fml.javafmlmod does not exist"]
---

## Issue

NeoForge 26.1+ fails to compile when using `FMLJavaModLoadingContext` to get
the mod event bus or register event listeners.

## Error

```
error: cannot find symbol
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
                                   ^
  symbol:   class FMLJavaModLoadingContext
  location: package net.neoforged.fml.javafmlmod
```

## Root Cause

`FMLJavaModLoadingContext` was removed in NeoForge 26.1. FML now injects
dependencies directly into the mod constructor. The mod class must declare
`IEventBus` and/or `ModContainer` as constructor parameters — FML provides
them automatically.

This is a different change from the 1.21.9–1.21.11 era where `ModContainer`
was added as a required parameter but `FMLJavaModLoadingContext` still existed.

## Fix

**NeoForge 1.21.9–1.21.11 (old pattern — FMLJavaModLoadingContext still present):**
```java
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod("mymod")
public final class MyMod {
    public MyMod(ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }
}
```

**NeoForge 26.1+ (new pattern — constructor injection, FMLJavaModLoadingContext gone):**
```java
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod("mymod")
public final class MyMod {
    // FML injects IEventBus (mod bus) and ModContainer automatically.
    // Declare only what you need — both are optional individually.
    public MyMod(IEventBus modBus, ModContainer modContainer) {
        // For game/Forge bus events (server lifecycle, world events, etc.):
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        // For mod bus events (registration, setup, etc.):
        // modBus.addListener(this::onCommonSetup);
    }

    private void onServerStarting(ServerStartingEvent event) {
        // ...
    }
}
```

## Key Points

- `IEventBus modBus` = the **mod event bus** (registration, setup events)
- `NeoForge.EVENT_BUS` = the **game/Forge bus** (server lifecycle, world events, player events)
- Both parameters are optional — declare only what you need
- `@EventBusSubscriber` annotation also changed in 26.1 — see `NEOFORGE-26-EVENTBUSSUBSCRIBER-STANDALONE`

## Verified

Confirmed in Allow Offline LAN Join port (run-20260503-085932).
NeoForge 26.1, 26.1.1, and 26.1.2 all built successfully using constructor
injection with `IEventBus modBus, ModContainer modContainer`.
