---
id: FABRIC-YARN-OPENSCREEN-VS-SETSCREEN-1165
title: Use openScreen() for 1.16.5 Yarn mappings, setScreen() for 1.17+
tags: [fabric, yarn, mapping, api-change, 1.16.5]
versions: [1.16.5]
loaders: [fabric]
symbols: [openScreen, setScreen, MinecraftClient, Yarn]
error_patterns: ["cannot find symbol.*setScreen", "cannot find symbol.*openScreen"]
---

## Issue
In Fabric 1.16.5 (Yarn mappings), the method to set the current screen is `client.openScreen()`. In 1.17+ (also Yarn), the method was renamed to `client.setScreen()`. Using the wrong method name causes a compilation error.

## Error
```
error: cannot find symbol
  symbol:   method setScreen(MultiplayerScreen)
  location: variable client of type MinecraftClient
```

## Root Cause
The Yarn mapping changed between 1.16.5 and 1.17. The 1.16.5 Yarn mappings use `openScreen` while 1.17+ use `setScreen`. The underlying method is the same, but the mapped name differs.

## Fix
For 1.16.5 Fabric source code, use `client.openScreen(screen)` instead of `client.setScreen(screen)`. For 1.17+, use `client.setScreen(screen)`.

ALWAYS check the decompiled Minecraft source for the correct method name before writing code for a specific version.

## Verified
PingFix mod -- 1.16.5 Fabric mixin used `client.setScreen()` which failed to compile. Changed to `client.openScreen()` and it compiled successfully.