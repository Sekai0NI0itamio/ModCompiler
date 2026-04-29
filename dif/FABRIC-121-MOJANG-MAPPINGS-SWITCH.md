---
id: FABRIC-121-MOJANG-MAPPINGS-SWITCH
title: Fabric 1.21+ switched to Mojang mappings — MinecraftClient→Minecraft, net.minecraft.item→net.minecraft.world.item
tags: [fabric, compile-error, api-change, yarn, mojang-mappings, 1.21, package-rename]
versions: [1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [fabric]
symbols: [MinecraftClient, Minecraft, Items, ItemStack, Hand, InteractionHand, getMainHandStack, getMainHandItem]
error_patterns: ["cannot find symbol.*MinecraftClient", "package net.minecraft.item does not exist", "cannot find symbol.*Hand"]
---

## Issue

Fabric mods targeting 1.21+ fail to compile when using yarn-mapped class names
(`MinecraftClient`, `net.minecraft.item`, `Hand`) that were correct for 1.20.x and below.

## Error

```
error: cannot find symbol
import net.minecraft.client.MinecraftClient;
                           ^
error: package net.minecraft.item does not exist
import net.minecraft.item.Items;
                         ^
error: cannot find symbol
import net.minecraft.util.Hand;
                         ^
```

## Root Cause

Fabric switched from yarn mappings to Mojang (official) mappings at the 1.21 boundary.
This means class names, package paths, and method names all changed to match Forge/NeoForge.

| API | Fabric 1.16.5–1.20.x (yarn) | Fabric 1.21+ (Mojang) |
|-----|-----------------------------|-----------------------|
| Client class | `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| Items | `net.minecraft.item.Items` | `net.minecraft.world.item.Items` |
| ItemStack | `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| Hand enum | `net.minecraft.util.Hand` | `net.minecraft.world.InteractionHand` |
| Main hand item | `player.getMainHandStack()` | `player.getMainHandItem()` |
| Off hand item | `player.getOffHandStack()` | `player.getOffhandItem()` |
| Screen field | `client.currentScreen` | `client.screen` |
| World field | `client.world` | `client.level` |
| Use key | `options.useKey` | `options.keyUse` |
| Interaction manager | `client.interactionManager` | `client.gameMode` |
| Use item | `interactItem(player, hand)` | `gameMode.useItem(player, hand)` |

## Fix

Write separate source strings for Fabric 1.20.x (yarn) and Fabric 1.21+ (Mojang):

```java
// Fabric 1.16.5–1.20.x (yarn mappings)
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
// client.interactionManager.interactItem(client.player, hand)
// client.options.useKey.isPressed()
// client.player.getMainHandStack()

// Fabric 1.21+ (Mojang mappings — same as Forge)
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
// client.gameMode.useItem(client.player, hand)
// client.options.keyUse.isDown()
// client.player.getMainHandItem()
```

In the generator script, use separate source strings:

```python
SRC_120_FABRIC = "... MinecraftClient, net.minecraft.item ..."   # yarn
SRC_121_FABRIC = "... Minecraft, net.minecraft.world.item ..."   # Mojang
```

## Verified

Confirmed in Auto Fast XP all-versions port (run 3, April 2026).
All Fabric 1.21–26.1.2 targets passed after this fix.

## See Also

- `FABRIC-YARN-VS-MOJANG-MAPPINGS` — general yarn vs Mojang class name differences
- `FABRIC-SPLIT-VS-PRESPLIT-SOURCE-DIR` — client code must go in `src/client/java/` for 1.20+
