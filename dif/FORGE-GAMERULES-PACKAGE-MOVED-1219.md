---
id: FORGE-GAMERULES-PACKAGE-MOVED-1219
title: Forge/NeoForge/Fabric 1.21.9+ — GameRules moved to net.minecraft.world.level.gamerules with new API
tags: [forge, neoforge, fabric, compile-error, api-change, gamerules, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [forge, neoforge, fabric]
symbols: [GameRules, RULE_KEEPINVENTORY, KEEP_INVENTORY, BooleanValue, GameRule, gamerules]
error_patterns: ["cannot find symbol.*class GameRules.*net.minecraft.world.level", "package net.minecraft.world.level.*GameRules.*does not exist"]
---

## Issue

Forge, NeoForge, and Fabric mods fail to compile on 1.21.9+ when importing
`net.minecraft.world.level.GameRules`.

## Error

```
error: cannot find symbol
import net.minecraft.world.level.GameRules;
                                ^
  symbol:   class GameRules
  location: package net.minecraft.world.level
```

## Root Cause

`GameRules` was moved to a new subpackage in Minecraft 1.21.9. The API also changed
from `BooleanValue` with `getRule(key).set(value, server)` to `GameRule<Boolean>` with
`set(key, value, server)` and `get(key)`.

| MC version | GameRules package | API style |
|------------|-------------------|-----------|
| 1.19–1.21.8 | `net.minecraft.world.level.GameRules` | `getRule(Key).set(true, server)` |
| 1.21.9+     | `net.minecraft.world.level.gamerules.GameRules` | `set(GameRule, true, server)` |

The constant names also changed:

| MC version | keepInventory constant |
|------------|------------------------|
| 1.19–1.21.8 (Forge/NeoForge) | `GameRules.RULE_KEEPINVENTORY` |
| 1.21.9+ (Forge/NeoForge/Fabric) | `GameRules.KEEP_INVENTORY` |
| 1.16.5–1.21.8 (Fabric yarn) | `GameRules.KEEP_INVENTORY` |

## Fix

```java
// Forge/NeoForge 1.19–1.21.8
import net.minecraft.world.level.GameRules;

serverLevel.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
boolean val = serverLevel.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

// Forge/NeoForge/Fabric 1.21.9+
import net.minecraft.world.level.gamerules.GameRules;

serverLevel.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
boolean val = serverLevel.getGameRules().get(GameRules.KEEP_INVENTORY);
```

Note: `getGameRules()` is only available on `ServerLevel`, not `Level`, in 1.21.3+.
See `FORGE-LEVEL-GETGAMERULES-REMOVED-1213`.

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
Forge 1.21.11, NeoForge 1.21.11, NeoForge 26.1.2, Fabric 26.1–26.1.2 all compiled
after switching to the new package and API.
