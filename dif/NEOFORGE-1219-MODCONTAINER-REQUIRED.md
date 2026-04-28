---
id: NEOFORGE-1219-MODCONTAINER-REQUIRED
title: NeoForge 1.21.9+ — @Mod constructor must accept ModContainer parameter
tags: [neoforge, compile-error, api-change, 1.21.9, mod-constructor]
versions: [1.21.9, 1.21.10, 1.21.11]
loaders: [neoforge]
symbols: [ModContainer, IEventBus, FMLCommonSetupEvent]
error_patterns: ["no suitable constructor found.*IEventBus", "cannot find symbol.*ModContainer", "Failed to create mod instance.*VeinMinerMod"]
---

## Issue

NeoForge 1.21.9+ requires the `@Mod` class constructor to accept a `ModContainer`
parameter in addition to `IEventBus`. Using only `IEventBus` causes a runtime
failure or compile error.

## Root Cause

Starting in NeoForge 1.21.9, the mod loading system injects `ModContainer` as a
second constructor parameter. The constructor signature must match exactly.

## Fix

**Wrong (pre-1.21.9)**:
```java
public VeinMinerMod(IEventBus modBus) {
    modBus.addListener(this::setup);
}
```

**Correct (1.21.9+)**:
```java
import net.neoforged.fml.ModContainer;

public VeinMinerMod(IEventBus modBus, ModContainer modContainer) {
    modBus.addListener(this::setup);
    modBus.addListener(VeinMinerKeyHandler::register);
}
```

The `modContainer` parameter does not need to be used — just declare it.

## Verified

Confirmed in Optimized Vein Miner all-versions port (run 5).
NeoForge 1.21.9, 1.21.10, 1.21.11 all succeeded after this fix.
