---
id: FORGE-WORLDEVENT-GETWORLD-RETURNS-IWORLD
title: Forge 1.12.2–1.16.5 — WorldEvent.getWorld() returns IWorld, not World — must cast
tags: [forge, compile-error, api-change, world-event, 1.12.2, 1.16.5]
versions: [1.12.2, 1.16.5]
loaders: [forge]
symbols: [WorldEvent, IWorld, World, getWorld]
error_patterns: ["incompatible types.*IWorld.*cannot be converted to.*World", "incompatible types.*net.minecraft.world.IWorld"]
---

## Issue

Forge 1.12.2 and 1.16.5 fail to compile when assigning `event.getWorld()` directly
to a `World` variable.

## Error

```
error: incompatible types: net.minecraft.world.IWorld cannot be converted to net.minecraft.world.World
    World world = event.getWorld();
```

## Root Cause

`WorldEvent.getWorld()` returns `IWorld` (an interface), not `World` (the concrete class).
A direct assignment to `World` fails without a cast.

In 1.8.9, `event.world` is a public field of type `World` — direct access works.
In 1.12.2 and 1.16.5, `getWorld()` returns `IWorld`.

## Fix

```java
// 1.8.9 — direct field access works
@SubscribeEvent
public void onWorldLoad(WorldEvent.Load event) {
    if (event.world != null && !event.world.isRemote) {
        // use event.world directly
    }
}

// 1.12.2 — cast required
@SubscribeEvent
public void onWorldLoad(WorldEvent.Load event) {
    if (!(event.getWorld() instanceof World)) return;
    World world = (World) event.getWorld();
    if (!world.isRemote) {
        // use world
    }
}

// 1.16.5 — cast required, isClientSide() not isRemote
@SubscribeEvent
public void onWorldLoad(WorldEvent.Load event) {
    if (!(event.getWorld() instanceof World)) return;
    World world = (World) event.getWorld();
    if (!world.isClientSide()) {
        // use world
    }
}
```

## Verified

Confirmed in Keep Inventory all-versions port (run-20260503).
Forge 1.12.2 and 1.16.5 both compiled after adding the `instanceof` check and cast.
