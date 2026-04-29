---
id: FABRIC-118-INTERACTITEM-WORLD-ARG
title: Fabric 1.18.x — interactItem() requires World argument (3-arg), 1.16.5 and 1.19+ use 2-arg
tags: [fabric, compile-error, api-change, interactItem, 1.18, 1.18.1, 1.18.2]
versions: [1.18, 1.18.1, 1.18.2]
loaders: [fabric]
symbols: [interactItem, ClientPlayerInteractionManager, Hand]
error_patterns: ["method interactItem.*cannot be applied.*given types", "required.*PlayerEntity.*World.*Hand"]
---

## Issue

Fabric 1.18.x fails to compile when calling `interactItem(player, hand)` with 2 arguments.
The method requires a `World` argument in 1.18.x.

## Error

```
error: method interactItem in class ClientPlayerInteractionManager cannot be applied to given types;
    client.interactionManager.interactItem(client.player, hand);
                             ^
  required: PlayerEntity,World,Hand
  found: PlayerEntity,Hand
```

## Root Cause

The `interactItem` signature changed across Fabric versions:

| Fabric version | Signature |
|---------------|-----------|
| 1.16.5–1.17.x | `interactItem(PlayerEntity player, Hand hand)` — 2 args |
| 1.18–1.18.2 | `interactItem(PlayerEntity player, World world, Hand hand)` — 3 args |
| 1.19+ | `interactItem(PlayerEntity player, Hand hand)` — 2 args again |

Also note: in 1.18.x, the key binding is `options.useKey` (not `options.keyUse` as in 1.16.5).

## Fix

```java
// Fabric 1.16.5–1.17.x
client.interactionManager.interactItem(client.player, hand);
// key: client.options.keyUse.isPressed()

// Fabric 1.18–1.18.2
client.interactionManager.interactItem(client.player, client.world, hand);
// key: client.options.useKey.isPressed()

// Fabric 1.19+
client.interactionManager.interactItem(client.player, hand);
// key: client.options.useKey.isPressed()
```

In the generator script, use a separate source string for 1.18.x:

```python
SRC_1165_FABRIC = "... interactItem(client.player, hand) ... options.keyUse ..."
SRC_118_FABRIC  = "... interactItem(client.player, client.world, hand) ... options.useKey ..."
SRC_119_FABRIC  = "... interactItem(client.player, hand) ... options.useKey ..."
```

## Verified

Confirmed in Auto Fast XP all-versions port (run 2, April 2026).
Fabric 1.18, 1.18.1, 1.18.2 all passed after this fix.
