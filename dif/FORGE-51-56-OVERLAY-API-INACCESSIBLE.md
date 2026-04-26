---
id: FORGE-51-56-OVERLAY-API-INACCESSIBLE
title: Forge 1.21 (51.x) and 1.21.6-1.21.8 (56-58.x) — HUD overlay API not in public mod classpath
tags: [forge, compile-error, hud, overlay, api-inaccessible, 1.21, 1.21.6, 1.21.7, 1.21.8]
versions: [1.21, 1.21.6, 1.21.7, 1.21.8]
loaders: [forge]
symbols: [AddGuiOverlayLayersEvent, ForgeLayeredDraw, ForgeLayer, RenderGuiEvent]
error_patterns: ["package net.minecraftforge.client.gui.overlay does not exist", "cannot find symbol.*AddGuiOverlayLayersEvent", "cannot find symbol.*ForgeLayeredDraw"]
---

## Issue

Forge 1.21 (51.x) and Forge 1.21.6-1.21.8 (56-58.x) fail to compile any mod that uses the HUD overlay API (`AddGuiOverlayLayersEvent`, `ForgeLayeredDraw`, `ForgeLayer`).

## Error

```
package net.minecraftforge.client.gui.overlay does not exist
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
                                            ^
error: cannot find symbol
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
                                      ^
  symbol:   class AddGuiOverlayLayersEvent
  location: package net.minecraftforge.client.event
```

## Root Cause

The classes `AddGuiOverlayLayersEvent`, `ForgeLayeredDraw`, and `ForgeLayer` exist in the **decompiled Forge sources** (visible in `DecompiledMinecraftSourceCode/1.21-forge/`) but are **NOT exported to the public mod API jar** on the compile classpath in Forge 51.x and 56-58.x.

This is a known Forge limitation for these specific versions. The old `RenderGuiOverlayEvent` and `VanillaGuiOverlay` were removed in 1.20.5, and the new system wasn't accessible until Forge 52.x (1.21.1).

**Important**: The AI source search finding a class in decompiled sources does NOT guarantee it's accessible from the mod classpath. Always verify by actually building.

Also note: `RenderGuiEvent.java` is entirely commented out in Forge 51.x (the whole class is inside a block comment `/* ... */`), so there is NO accessible HUD rendering API for mods in Forge 1.21 (51.x).

## Fix

**Skip these versions entirely.** There is no workaround.

- Skip Forge 1.21 (51.x) — use Forge 1.21.1 (52.x) as the minimum instead
- Skip Forge 1.21.6-1.21.8 (56-58.x) — NeoForge equivalents work fine via `RegisterGuiLayersEvent`

In the generator script, simply omit these versions from the TARGETS list and add a comment explaining why.

## Verified

Confirmed in run-20260425-232140 and run-20260426-005930 for the Day Counter mod port.
NeoForge 1.21.6-1.21.8 works fine — only Forge is affected.
