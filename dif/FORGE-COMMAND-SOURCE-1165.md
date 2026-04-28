---
id: FORGE-COMMAND-SOURCE-1165
title: Forge 1.16.5 — CommandSource.asPlayer() does not exist, use (ServerPlayerEntity) src.getEntity()
tags: [forge, compile-error, api-change, CommandSource, asPlayer, 1.16.5]
versions: [1.16.5]
loaders: [forge]
symbols: [CommandSource, asPlayer, getEntity, ServerPlayerEntity]
error_patterns: ["cannot find symbol.*method asPlayer\\(\\)"]
---

## Issue

In Forge 1.16.5, `CommandSource` does not have an `asPlayer()` method. Code
written for 1.17+ that calls `src.asPlayer()` will fail to compile.

## Error

```
error: cannot find symbol
    ServerPlayerEntity from = src.asPlayer();
                                  ^
  symbol:   method asPlayer()
  location: variable src of type CommandSource
```

## Root Cause

`CommandSource.asPlayer()` (which throws `CommandSyntaxException` if the source
is not a player) was added in 1.17 as `CommandSourceStack.getPlayerOrException()`.
In 1.16.5, the equivalent is a manual cast with a null check.

Note: In 1.16.5, the class is `CommandSource` (not `CommandSourceStack`).

## Fix

**1.16.5 — manual cast:**
```java
// Check and cast manually
if (!(src.getEntity() instanceof ServerPlayerEntity)) {
    src.sendSuccess(new StringTextComponent("Players only."), false);
    return 0;
}
ServerPlayerEntity player = (ServerPlayerEntity) src.getEntity();
```

**1.17+ — use getPlayerOrException():**
```java
ServerPlayer player = src.getPlayerOrException();
```

## Version Matrix

| MC Version | Source class | Get player method |
|------------|-------------|-------------------|
| 1.16.5 | `CommandSource` | `(ServerPlayerEntity) src.getEntity()` |
| 1.17+ | `CommandSourceStack` | `src.getPlayerOrException()` |

## Verified

Confirmed in TPA Teleport all-versions port (run 3, April 2026).
