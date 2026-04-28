---
id: FABRIC-COMMAND-API-V1-VS-V2
title: Fabric CommandRegistrationCallback — v1 (1.16.5–1.18.x) vs v2 (1.19+)
tags: [fabric, compile-error, api-change, command, CommandRegistrationCallback, 1.16.5, 1.17, 1.18, 1.19, 1.20]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6]
loaders: [fabric]
symbols: [CommandRegistrationCallback, CommandRegistrationCallback.EVENT, register]
error_patterns: ["package net.fabricmc.fabric.api.command.v2 does not exist", "package net.fabricmc.fabric.api.command.v1 does not exist"]
---

## Issue

Fabric's `CommandRegistrationCallback` moved from `command.v1` to `command.v2`
between 1.18.x and 1.19. The callback signature also changed — v1 takes 2 args,
v2 takes 3 args.

## Error

```
error: package net.fabricmc.fabric.api.command.v2 does not exist
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
```

or

```
error: package net.fabricmc.fabric.api.command.v1 does not exist
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
```

## Root Cause

The Fabric API command module was versioned:
- `command.v1` — available in 1.16.5 through 1.18.x
- `command.v2` — available in 1.19+

The callback lambda signature also changed:
- v1: `(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated)`
- v2: `(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment)`

## Fix

**1.16.5 through 1.18.x — use v1:**
```java
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
    register(dispatcher);
});
```

**1.19 through 1.20.x — use v2:**
```java
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, dedicated) -> {
    register(dispatcher);
});
```

## Version Matrix

| MC Version | Package | Callback args |
|------------|---------|---------------|
| 1.16.5 | `command.v1` | `(dispatcher, dedicated)` |
| 1.17.1 | `command.v1` | `(dispatcher, dedicated)` |
| 1.18–1.18.2 | `command.v1` | `(dispatcher, dedicated)` |
| 1.19–1.20.6 | `command.v2` | `(dispatcher, registryAccess, dedicated)` |
| 1.21+ | `command.v2` | `(dispatcher, registryAccess, dedicated)` |

## Verified

Confirmed in TPA Teleport all-versions port (runs 5–7, April 2026).
