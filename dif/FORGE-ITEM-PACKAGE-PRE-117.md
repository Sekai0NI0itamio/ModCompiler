---
id: FORGE-ITEM-PACKAGE-PRE-117
title: Forge pre-1.17 uses net.minecraft.item, 1.17+ uses net.minecraft.world.item
tags: [forge, compile-error, api-change, package-rename, 1.16.5, 1.17, 1.18]
versions: [1.16.5, 1.17, 1.17.1, 1.18, 1.18.1, 1.18.2]
loaders: [forge]
symbols: [Items, ItemStack, Hand, InteractionHand]
error_patterns: ["package net.minecraft.item does not exist", "package net.minecraft.util does not exist.*Hand"]
---

## Issue

Forge mods targeting 1.17+ fail to compile when using `net.minecraft.item.Items`,
`net.minecraft.item.ItemStack`, or `net.minecraft.util.Hand` — the pre-1.17 package paths.

## Error

```
error: package net.minecraft.item does not exist
import net.minecraft.item.Items;
                         ^
error: package net.minecraft.item does not exist
import net.minecraft.item.ItemStack;
                         ^
error: cannot find symbol
import net.minecraft.util.Hand;
                         ^
```

## Root Cause

Minecraft 1.17 reorganized its package structure. Items and ItemStack moved from
`net.minecraft.item` to `net.minecraft.world.item`. The `Hand` enum moved from
`net.minecraft.util` to `net.minecraft.world.InteractionHand`.

| MC Version | Items/ItemStack package | Hand class |
|------------|------------------------|------------|
| 1.8.9–1.16.5 | `net.minecraft.item` | `net.minecraft.util.Hand` |
| 1.17+ | `net.minecraft.world.item` | `net.minecraft.world.InteractionHand` |

## Fix

Use the correct package for the target version:

```java
// 1.8.9–1.16.5 Forge
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

// 1.17+ Forge/NeoForge
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
```

In the generator script, use separate source strings per era:

```python
# 1.16.5 and below
SRC_1165_HANDLER = "... import net.minecraft.item.Items; ..."

# 1.17+
SRC_117_HANDLER = "... import net.minecraft.world.item.Items; ..."
```

## Verified

Confirmed in Auto Fast XP all-versions port (run 2, April 2026).
Forge 1.17.1, 1.18, 1.18.1, 1.18.2 all passed after this fix.
