---
id: FORGE-LIVINGUSETOTEM-NOT-IN-41X
title: Forge 1.19–1.19.2 (41.x) — LivingUseTotemEvent does not exist, added in 44.x (1.19.3+)
tags: [forge, compile-error, api-change, LivingUseTotemEvent, 1.19, 1.19.1, 1.19.2]
versions: [1.19, 1.19.1, 1.19.2]
loaders: [forge]
symbols: [LivingUseTotemEvent]
error_patterns: ["cannot find symbol.*class LivingUseTotemEvent", "package net.minecraftforge.event.entity.living.*LivingUseTotemEvent"]
---

## Issue

Forge 1.19–1.19.2 fails to compile when importing `LivingUseTotemEvent` even though
the class appears in `DecompiledMinecraftSourceCode/1.19-forge/`.

## Error

```
error: cannot find symbol
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
                                             ^
  symbol:   class LivingUseTotemEvent
  location: package net.minecraftforge.event.entity.living
```

## Root Cause

The decompiled sources for 1.19 were generated from a **newer** Forge version than
what the build template uses. The build template pins:

| MC Version | Forge version used |
|------------|-------------------|
| 1.19       | 41.1.0            |
| 1.19.1     | 42.0.9            |
| 1.19.2     | 43.4.22           |
| 1.19.3     | 44.1.21 ✅        |
| 1.19.4     | 45.4.3  ✅        |

`LivingUseTotemEvent` was added in Forge **44.x** (1.19.3). It does not exist in
Forge 41.x, 42.x, or 43.x.

The decompiled sources show it because they were regenerated with a later Forge
version — this is the `SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE` pitfall.

## Fix

**Skip 1.19, 1.19.1, 1.19.2 entirely** for any mod that depends on `LivingUseTotemEvent`.
Only target 1.19.3+ for Forge.

In the generator script:
```python
# WRONG — LivingUseTotemEvent not in Forge 41.x/42.x/43.x
("MyMod-1.19-forge",   "1.19",   "forge", SRC, GROUP, EP),
("MyMod-1.19.1-forge", "1.19.1", "forge", SRC, GROUP, EP),
("MyMod-1.19.2-forge", "1.19.2", "forge", SRC, GROUP, EP),

# CORRECT — start from 1.19.3
("MyMod-1.19.3-forge", "1.19.3", "forge", SRC, GROUP, EP),
("MyMod-1.19.4-forge", "1.19.4", "forge", SRC, GROUP, EP),
```

## Verified

Confirmed in Stackable Totems all-versions port (run 1, April 2026).
1.19, 1.19.1, 1.19.2 all failed with this error. 1.19.3 and 1.19.4 passed.
