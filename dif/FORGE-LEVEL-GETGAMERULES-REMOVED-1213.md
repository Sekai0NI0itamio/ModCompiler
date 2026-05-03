---
id: FORGE-LEVEL-GETGAMERULES-REMOVED-1213
title: Forge 1.21.3+ — Level.getGameRules() removed, must cast to ServerLevel
tags: [forge, compile-error, api-change, gamerules, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge]
symbols: [Level, ServerLevel, GameRules, getGameRules, LevelAccessor]
error_patterns: ["cannot find symbol.*method getGameRules.*Level", "cannot find symbol.*getGameRules.*location.*Level"]
---

## Issue

Forge 1.21.3+ fails to compile when calling `level.getGameRules()` on a `Level` variable.

## Error

```
error: cannot find symbol
    GameRules rules = level.getGameRules();
                           ^
  symbol:   method getGameRules()
  location: variable level of type Level
```

## Root Cause

`getGameRules()` was removed from `Level` in Forge 1.21.3. It now only exists on
`ServerLevel`. The `LevelEvent.getLevel()` method returns `LevelAccessor`, and
`TickEvent.LevelTickEvent.level` is typed as `Level` — neither has `getGameRules()`.

| Forge version | `Level.getGameRules()` |
|---------------|------------------------|
| 1.19–1.21.2   | ✅ Available |
| 1.21.3+       | ❌ Removed — use `ServerLevel` |

## Fix

Cast to `ServerLevel` before calling `getGameRules()`:

```java
// Forge 1.21.3–1.21.5 (EventBus 6, event.level field)
@SubscribeEvent
public void onLevelLoad(LevelEvent.Load event) {
    LevelAccessor la = event.getLevel();
    if (la instanceof ServerLevel) {
        ServerLevel sl = (ServerLevel) la;
        sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
    }
}

@SubscribeEvent
public void onLevelTick(TickEvent.LevelTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    if (!(event.level instanceof ServerLevel)) return;
    ServerLevel sl = (ServerLevel) event.level;
    // use sl.getGameRules()
}

// Forge 1.21.6–1.21.8 (EventBus 7, field access on Post)
private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
    if (!(event.level instanceof ServerLevel)) return;
    ServerLevel sl = (ServerLevel) event.level;
    // use sl.getGameRules()
}

// Forge 1.21.9+ (EventBus 7, record accessor)
private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
    if (!(event.level() instanceof ServerLevel)) return;
    ServerLevel sl = (ServerLevel) event.level();
    // use sl.getGameRules()
}
```

Note: In 1.21.9+, `GameRules` also moved to `net.minecraft.world.level.gamerules.GameRules`
with a new `GameRule<Boolean>` API. See `FORGE-GAMERULES-PACKAGE-MOVED-1219`.

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
Forge 1.21.3, 1.21.4, 1.21.5 all compiled after casting to `ServerLevel`.
