---
id: FORGE-112-OBFUSCATED-SRG-NAMES
title: Forge 1.12.x — AI uses SRG/obfuscated names instead of MCP-mapped names
tags: [forge, compile-error, srg, mcp, mappings, deobfuscation, 1.12, 1.12.1, 1.12.2]
versions: [1.12, 1.12.1, 1.12.2]
loaders: [forge]
symbols: [Minecraft, getMinecraft, func_71410_x, field_71439_g, field_71441_e, field_71474_y, field_71442_b, field_151062_by, func_151470_d, func_184614_ca, func_184592_cb, func_190926_b, func_77973_b, func_187101_a, func_184609_a, field_74313_G, Items, ItemStack, XP_BOTTLE]
error_patterns: ["cannot find symbol", "func_71410_x", "func_151470_d", "field_71439_g", "field_71441_e", "field_71474_y", "field_71442_b", "field_151062_by"]
---

## Issue

The AI reads obfuscated (SRG-name) Minecraft source code from projectinfo.txt
and writes code using those SRG names. Forge 1.12.x uses **MCP-mapped names**
at compile time, so SRG names like `func_71410_x()` and `field_71439_g` do not
compile.

## Error

```
error: cannot find symbol
    Minecraft mc = Minecraft.func_71410_x();
                            ^
  symbol:   method func_71410_x()
  location: class Minecraft
```

Multiple "cannot find symbol" errors for `func_*` methods and `field_*` fields.

## Root Cause

The decompiled Minecraft source shown to the AI in projectinfo.txt uses SRG
(obfuscated) names. When the AI references these in generated code, the
ForgeGradle build environment expects MCP-mapped (deobfuscated) names.

Forge 1.12.x uses MCP mappings (not Yarn, not Mojang mappings). The correct
names are:

| SRG Name | MCP-Mapped Name |
|---|---|
| `Minecraft.func_71410_x()` | `Minecraft.getMinecraft()` |
| `mc.field_71439_g` | `mc.player` |
| `mc.field_71441_e` | `mc.world` |
| `mc.field_71474_y` | `mc.gameSettings` |
| `mc.field_71442_b` | `mc.playerController` |
| `field_74313_G` (GameSettings) | `.keyBindUseItem` |
| `func_151470_d()` | `isKeyDown()` |
| `func_184614_ca()` | `getHeldItemMainhand()` |
| `func_184592_cb()` | `getHeldItemOffhand()` |
| `func_190926_b()` | `isEmpty()` |
| `func_77973_b()` | `getItem()` |
| `field_151062_by` (Items) | `Items.EXPERIENCE_BOTTLE` |
| `func_187101_a()` | `processRightClick()` |
| `func_184609_a()` | `swingItem()` |

## Fix

ALWAYS translate SRG names to MCP-mapped names when writing Forge 1.12.x code.
The following table covers the most common SRG→MCP translations for Minecraft
class:

```java
// SRG (WRONG — will not compile):
Minecraft mc = Minecraft.func_71410_x();
mc.field_71439_g  // the player
mc.field_71441_e  // the world
mc.field_71474_y  // game settings
mc.field_71442_b  // player controller

// MCP (CORRECT — compiles in Forge 1.12.x):
Minecraft mc = Minecraft.getMinecraft();
mc.player          // EntityPlayer
mc.world           // WorldClient
mc.gameSettings    // GameSettings
mc.playerController // PlayerControllerMP
```

For ItemStack and Items:

```java
// SRG (WRONG):
heldItem.func_190926_b()           // isEmpty check
heldItem.func_77973_b()            // getItem
Items.field_151062_by              // XP bottle item

// MCP (CORRECT):
heldItem.isEmpty()
heldItem.getItem()
Items.EXPERIENCE_BOTTLE            // or Items.SPLASH_POTION etc.
```

For player actions:

```java
// SRG (WRONG):
mc.player.func_184609_a(hand)                          // swing arm
mc.field_71442_b.func_187101_a(player, world, hand)    // right click

// MCP (CORRECT):
mc.player.swingItem(hand)
mc.playerController.processRightClick(player, world, hand)
```

## General Rule

For Forge 1.12.x, NEVER use `func_***()` or `field_***` names.
Always use the deobfuscated MCP names. If you see a `func_` or `field_` prefix
in the source code you were given, treat it as a hint — look up the correct
MCP name using the table above or the Minecraft source files provided.

## Verified

Run 25714625012 — auto-fast-xp 1.12-forge failed with 19 "cannot find symbol"
errors for SRG names. Fixed by replacing with MCP-mapped equivalents.

## See Also

- `FORGE-ITEM-COMPARISON-API-CHANGE-1205.md` for Forge 1.20.5+ item comparison
