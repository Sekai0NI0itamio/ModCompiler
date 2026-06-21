---
id: FABRIC-API-CLIENT-TICK-MIXIN-REPLACEMENT
title: Replace ClientTickEvents with mixin to remove fabric-api dependency
tags: [fabric, fabric-api, mixin, dependency, compile-error, runtime-error]
versions: [1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.21.1, 1.21.8, 1.21.11, 26.1.2]
loaders: [fabric]
symbols: [ClientTickEvents, END_CLIENT_TICK, MinecraftClient, MinecraftClient.tick]
error_patterns: ["fabric-api", "Could not find required mod", "requires fabric-api", "ClientTickEvents"]
---

## Issue
Fabric mods built with the template use `ClientTickEvents.END_CLIENT_TICK.register()` from the Fabric API. The generated `fabric.mod.json` includes `"fabric-api": "*"` in `depends`. When the mod is tested in headless Minecraft without Fabric API installed, the game crashes with "Could not find required mod: fabric-api".

## Error
```
Could not find required mod: fabric-api requires any version of fabric-api, which is missing!
```

## Root Cause
The mod uses `ClientTickEvents.END_CLIENT_TICK` from `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents`, which is part of the Fabric API. The Fabric API is not installed in the headless Minecraft test environment used by the ModrinthProjectDiagnosis and Build Mods workflows.

## Fix
1. Replace `ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick)` with a mixin that injects into `MinecraftClient.tick()` at `@At("HEAD")`
2. Add `requires_fabric_api=false` to `mod.txt`
3. The mixin class goes in the correct source set:
   - **1.16.5-1.19.4**: `src/main/java/` (no split source sets in these templates)
   - **1.20+**: `src/client/java/` (split source sets via `splitEnvironmentSourceSets()`)
4. The mixin JSON goes in the corresponding resources directory:
   - **1.16.5-1.19.4**: `src/main/resources/pingfix.mixins.json`
   - **1.20+**: `src/client/resources/pingfix.mixins.json`
5. **1.16.5 special**: Use `client.openScreen()` instead of `client.setScreen()` (Yarn mapping difference)

## Verified
PingFix mod -- all fabric versions (1.16.5 through 26.1.2) fixed by this approach.