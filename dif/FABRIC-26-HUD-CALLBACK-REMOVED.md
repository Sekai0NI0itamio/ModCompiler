---
id: FABRIC-26-HUD-CALLBACK-REMOVED
title: Fabric 26.1.x — HudRenderCallback removed, no replacement available yet
tags: [fabric, compile-error, hud, api-removed, 26.1, skip]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [fabric]
symbols: [HudRenderCallback]
error_patterns: ["cannot find symbol.*HudRenderCallback", "import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.*cannot find"]
---

## Issue

Fabric 26.1.x fails to compile any mod that uses `HudRenderCallback` for HUD rendering.

## Error

```
error: cannot find symbol
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
                                                   ^
  symbol:   class HudRenderCallback
  location: package net.fabricmc.fabric.api.client.rendering.v1
```

## Root Cause

`HudRenderCallback` was removed in Fabric API 26.1 and no replacement has been added yet. The Fabric API for 26.1.x only includes basic infrastructure (event system, lookups). The client rendering callback system has not been ported to the new Minecraft 26.1 rendering pipeline.

## Fix

**Skip all Fabric 26.1.x targets** for any mod that uses HUD rendering. There is no workaround until the Fabric API adds a replacement.

In the generator script, omit Fabric 26.1.x from the TARGETS list and add a comment:
```python
# Fabric 26.1.x: HudRenderCallback removed, no replacement in Fabric API yet — skip
```

Monitor the Fabric API changelog for when a replacement is added.

## Verified

Confirmed in Day Counter port. Source search for Fabric 26.1.2 found no Fabric API rendering classes at all — only basic Fabric API (event system, lookups).
