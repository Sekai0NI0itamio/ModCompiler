---
id: FORGE-17-EVENT-WORLD-VS-LEVEL
title: Forge 1.17.x uses event.world package, not event.level (introduced in 1.18)
tags: [forge, compile-error, api-change, 1.17, event-package]
versions: [1.17, 1.17.1]
loaders: [forge]
symbols: [BlockEvent, FarmlandTrampleEvent]
error_patterns: ["package net.minecraftforge.event.level does not exist", "cannot find symbol.*BlockEvent.*event.level"]
---

## Issue

Forge 1.17.x fails to compile when using `net.minecraftforge.event.level.BlockEvent`.

## Error

```
error: package net.minecraftforge.event.level does not exist
import net.minecraftforge.event.level.BlockEvent;
```

## Root Cause

The `net.minecraftforge.event.level` package was introduced in Forge 1.18. In Forge 1.17.x, the equivalent package is `net.minecraftforge.event.world`.

| Forge version | Package |
|---------------|---------|
| 1.12.x–1.17.x | `net.minecraftforge.event.world` |
| 1.18+ | `net.minecraftforge.event.level` |

## Fix

Use the correct package for the target version:

```java
// Forge 1.17.x
import net.minecraftforge.event.world.BlockEvent;

// Forge 1.18+
import net.minecraftforge.event.level.BlockEvent;
```

In the generator script, detect the version and use the appropriate import:
```python
event_pkg = "net.minecraftforge.event.world" if vt < (1, 18) else "net.minecraftforge.event.level"
```

## Verified

Confirmed in Seed Protect port (Phase 2, Challenge 4).
