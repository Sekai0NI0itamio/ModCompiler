---
id: FABRIC-26-GAMERULES-NEW-API
title: Fabric 26.1+ — GameRules moved to gamerules subpackage with GameRule<Boolean> API
tags: [fabric, compile-error, api-change, gamerules, 26.1]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [fabric]
symbols: [GameRules, KEEP_INVENTORY, GameRule, gamerules, getGameRules]
error_patterns: ["cannot find symbol.*class GameRules.*net.minecraft.world.level", "package net.minecraft.world.level.*GameRules.*does not exist"]
---

## Issue

Fabric 26.1+ mods fail to compile when importing `net.minecraft.world.level.GameRules`.

## Error

```
error: cannot find symbol
import net.minecraft.world.level.GameRules;
                                ^
  symbol:   class GameRules
  location: package net.minecraft.world.level
```

## Root Cause

In Minecraft 26.1 (the calendar-versioned release), `GameRules` was moved to
`net.minecraft.world.level.gamerules.GameRules`. The API also changed from
`BooleanValue` with `getRule(key).set(value, server)` to `GameRule<Boolean>` with
`set(key, value, server)` and `get(key)`.

This affects Fabric 26.1 because it uses Mojang mappings (same as Forge/NeoForge 1.21.9+).

## Fix

```java
// Fabric 1.21–1.21.8 (Mojang mappings, old API)
import net.minecraft.world.level.GameRules;

level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
boolean val = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

// Fabric 26.1+ (Mojang mappings, new API)
import net.minecraft.world.level.gamerules.GameRules;

level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
boolean val = level.getGameRules().get(GameRules.KEEP_INVENTORY);
```

Note: `getGameRules()` is available on `ServerLevel` in Fabric 26.1 (via `server.getAllLevels()`).

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
Fabric 26.1, 26.1.1, and 26.1.2 all compiled successfully after switching to the
new `gamerules` package and `GameRule<Boolean>` API.

## See Also

- `FORGE-GAMERULES-PACKAGE-MOVED-1219` — same change applies to Forge/NeoForge 1.21.9+
