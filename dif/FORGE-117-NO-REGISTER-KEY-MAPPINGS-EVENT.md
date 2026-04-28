---
id: FORGE-117-NO-REGISTER-KEY-MAPPINGS-EVENT
title: Forge 1.17.1 — RegisterKeyMappingsEvent does not exist, use ClientRegistry
tags: [forge, compile-error, api-change, 1.17, keybinding]
versions: [1.17, 1.17.1]
loaders: [forge]
symbols: [RegisterKeyMappingsEvent, ClientRegistry, KeyMapping, KeyBinding]
error_patterns: ["cannot find symbol.*RegisterKeyMappingsEvent", "package net.minecraftforge.client.event.*RegisterKey"]
---

## Issue

Forge 1.17.1 fails to compile when using `RegisterKeyMappingsEvent` for key binding registration.

## Error

```
error: cannot find symbol
  symbol: class RegisterKeyMappingsEvent
  location: package net.minecraftforge.client.event
```

## Root Cause

`RegisterKeyMappingsEvent` was introduced in Forge 1.18. In Forge 1.17.1, key bindings
are registered by calling `ClientRegistry.registerKeyBinding()` directly in the
constructor of the key handler class.

| Forge version | Key registration method |
|---------------|------------------------|
| 1.12.2–1.17.1 | `ClientRegistry.registerKeyBinding(key)` in constructor |
| 1.18+ | `bus.addListener(VeinMinerKeyHandler::register)` + `RegisterKeyMappingsEvent` |

## Fix

**Wrong (1.18+ API used in 1.17.1)**:
```java
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public static void register(RegisterKeyMappingsEvent event) {
    event.register(toggleKey);
}
```

**Correct (1.17.1)**:
```java
import net.minecraftforge.fmlclient.registry.ClientRegistry;

public VeinMinerKeyHandler() {
    ClientRegistry.registerKeyBinding(toggleKey);
}
```

Also remove `bus.addListener(VeinMinerKeyHandler::register)` from `VeinMinerMod` —
just register the handler instance directly in `setup()`:

```java
// VeinMinerMod.java for 1.17.1
public VeinMinerMod() {
    var bus = FMLJavaModLoadingContext.get().getModEventBus();
    bus.addListener(this::setup);
    // DO NOT add: bus.addListener(VeinMinerKeyHandler::register)
}

private void setup(FMLCommonSetupEvent event) {
    MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
    MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler()); // constructor registers key
}
```

## Verified

Confirmed in Optimized Vein Miner all-versions port (run 4).
See `DecompiledMinecraftSourceCode/1.17.1-forge/net/minecraftforge/fmlclient/registry/ClientRegistry.java`.
