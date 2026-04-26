---
id: FORGE-ITEM-COMPARISON-API-CHANGE-1205
title: Forge/Fabric 1.20.5+ — isSameItemSameTags and getTag() removed, use isSameItemSameComponents and getComponents()
tags: [forge, fabric, compile-error, api-change, 1.20.5, ItemStack, components, tags]
versions: [1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11]
loaders: [forge, fabric, neoforge]
symbols: [isSameItemSameTags, getTag, isSameItemSameComponents, getComponents]
error_patterns: ["cannot find symbol.*isSameItemSameTags", "cannot find symbol.*getTag", "method isSameItemSameTags.*not found"]
---

## Issue

Forge/Fabric 1.20.5+ fails to compile when using `isSameItemSameTags()` or `getTag()` on `ItemStack`.

## Error

```
error: cannot find symbol
    if (ItemStack.isSameItemSameTags(stack1, stack2)) {
                 ^
  symbol:   method isSameItemSameTags(ItemStack, ItemStack)
```

or

```
error: cannot find symbol
    CompoundTag tag = stack.getTag();
                           ^
  symbol:   method getTag()
```

## Root Cause

Minecraft 1.20.5 replaced the NBT tag system with a component system:

| Version | Item comparison | Tag access |
|---------|----------------|------------|
| 1.20.4 and earlier | `ItemStack.isSameItemSameTags(a, b)` | `stack.getTag()` |
| 1.20.5+ | `ItemStack.isSameItemSameComponents(a, b)` | `stack.getComponents()` |

Fabric yarn mappings also changed:
- 1.20.4 and earlier: `canCombine()` + `getNbt()`
- 1.20.5+: `areItemsAndComponentsEqual()` + `getComponents()`

## Fix

**Forge/NeoForge:**
```java
// Before 1.20.5
if (ItemStack.isSameItemSameTags(stack1, stack2)) { ... }
CompoundTag tag = stack.getTag();

// 1.20.5+
if (ItemStack.isSameItemSameComponents(stack1, stack2)) { ... }
DataComponentMap components = stack.getComponents();
```

**Fabric (Yarn mappings):**
```java
// Before 1.20.5 (yarn)
if (ItemStack.canCombine(stack1, stack2)) { ... }
NbtCompound nbt = stack.getNbt();

// 1.20.5+ (yarn)
if (ItemStack.areItemsAndComponentsEqual(stack1, stack2)) { ... }
ComponentMap components = stack.getComponents();
```

In the generator script, detect the version:
```python
is_components = vt >= (1, 20, 5)
if is_components:
    compare_method = "ItemStack.isSameItemSameComponents"
else:
    compare_method = "ItemStack.isSameItemSameTags"
```

## Verified

Confirmed in Sort Chest port (Step 3, Forge and Fabric API differences tables).
