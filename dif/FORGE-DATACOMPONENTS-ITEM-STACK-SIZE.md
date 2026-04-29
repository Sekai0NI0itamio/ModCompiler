---
id: FORGE-DATACOMPONENTS-ITEM-STACK-SIZE
title: Forge/NeoForge 1.20.5+ — Item max stack size stored in DataComponents.MAX_STACK_SIZE, not a field
tags: [forge, neoforge, api-change, DataComponents, MAX_STACK_SIZE, maxStackSize, reflection, 1.20.5]
versions: [1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [forge, neoforge]
symbols: [DataComponents, MAX_STACK_SIZE, DataComponentMap, maxStackSize, Item, stacksTo]
error_patterns: []
---

## Issue

Mods that modify an item's max stack size at runtime (e.g. to make totems stackable)
need different approaches for pre-1.20.5 vs 1.20.5+ due to the DataComponents system.

## Root Cause

In Minecraft 1.20.5, item properties were migrated from plain fields to a
`DataComponentMap`. The max stack size is now stored as `DataComponents.MAX_STACK_SIZE`
(an `Integer` component) rather than a plain `int maxStackSize` field on `Item`.

| MC Version | Stack size storage | Reflection target |
|------------|-------------------|-------------------|
| 1.19.3–1.20.4 | `int maxStackSize` field on `Item` | `"f_41370_"` (SRG) or `"maxStackSize"` |
| 1.20.5+ | `DataComponentMap components` field on `Item` | `"components"` field, then set `DataComponents.MAX_STACK_SIZE` |

NeoForge 1.20.5 uses `DataComponentTypes` (not `DataComponents`) and
`net.minecraft.component` (not `net.minecraft.core.component`).

## Fix

**1.19.3–1.20.4 (Forge/NeoForge):**
```java
// Try SRG name first, fall back to Mojang name
try {
    Field f = Item.class.getDeclaredField("f_41370_");
    f.setAccessible(true);
    f.set(Items.TOTEM_OF_UNDYING, 64);
} catch (Exception e) {
    Field f = Item.class.getDeclaredField("maxStackSize");
    f.setAccessible(true);
    f.set(Items.TOTEM_OF_UNDYING, 64);
}
```

**1.20.5+ Forge (net.minecraft.core.component.DataComponents):**
```java
Field f = Item.class.getDeclaredField("components");
f.setAccessible(true);
DataComponentMap map = (DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
DataComponentMap newMap = DataComponentMap.builder()
    .addAll(map)
    .set(DataComponents.MAX_STACK_SIZE, 64)
    .build();
f.set(Items.TOTEM_OF_UNDYING, newMap);
```

**1.20.5 NeoForge (net.minecraft.component.DataComponentTypes — different package!):**
```java
// NeoForge 1.20.5 uses net.minecraft.component not net.minecraft.core.component
import net.minecraft.component.DataComponentTypes;
Field f = Item.class.getDeclaredField("components");
f.setAccessible(true);
// ... same pattern but with DataComponentTypes.MAX_STACK_SIZE
```

## Verified

Confirmed in Stackable Totems all-versions port (April 2026).
All 1.20.5+ Forge and NeoForge targets passed using the DataComponentMap reflection approach.
