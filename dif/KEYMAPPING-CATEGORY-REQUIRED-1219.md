---
id: KEYMAPPING-CATEGORY-REQUIRED-1219
title: 1.21.9+ — KeyMapping constructor requires KeyMapping.Category, not String
tags: [forge, neoforge, fabric, compile-error, api-change, 1.21.9, keybinding]
versions: [1.21.9, 1.21.10, 1.21.11]
loaders: [forge, neoforge, fabric]
symbols: [KeyMapping, KeyBinding, Category, KeyConflictContext]
error_patterns: ["incompatible types: String cannot be converted to Category", "no suitable constructor found for KeyMapping\\(String,KeyConflictContext,Key,String\\)", "cannot find symbol.*method lookup\\(String\\).*Category", "cannot find symbol.*method create\\(String,int\\).*Category"]
---

## Issue

In Minecraft 1.21.9+, the `KeyMapping` constructor changed. Passing a plain `String`
as the category argument now fails to compile.

## Error

```
error: incompatible types: String cannot be converted to Category
    new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner")
                                                          ^
```

Or for NeoForge with `KeyConflictContext`:
```
error: no suitable constructor found for KeyMapping(String,KeyConflictContext,Key,String)
```

Or when trying `Category.lookup()` / `Category.create()`:
```
error: cannot find symbol
  symbol: method lookup(String)
  location: class Category
```

## Root Cause

In 1.21.9+, `KeyMapping.Category` became a `record` type. The constructor now
requires a `KeyMapping.Category` instance, not a plain `String`. The methods
`Category.lookup()` and `Category.create()` do not exist.

Available constructors in 1.21.9+:
```java
public KeyMapping(String description, int keyCode, KeyMapping.Category category)
public KeyMapping(String description, InputConstants.Type inputType, int keyCode, KeyMapping.Category category)
```

Built-in categories: `MISC`, `MOVEMENT`, `GAMEPLAY`, `INVENTORY`, `MULTIPLAYER`,
`CREATIVE`, `SPECTATOR`, `DEBUG`.

## Fix

Use `KeyMapping.Category.MISC` (or another built-in category):

**Forge 1.21.9+**:
```java
public static final KeyMapping toggleKey = new KeyMapping(
    "Toggle Vein Miner", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC
);
```

**NeoForge 1.21.9+** (remove `KeyConflictContext` — use vanilla constructor):
```java
public static final KeyMapping toggleKey = new KeyMapping(
    "Toggle Vein Miner", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC
);
```

**Fabric 1.21.9+** (via `KeyBindingHelper`):
```java
toggleKey = KeyBindingHelper.registerKeyBinding(
    new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC)
);
```

Do NOT use `KeyMapping.Category.lookup()` or `KeyMapping.Category.create()` —
those methods do not exist in the vanilla API.

## Verified

Confirmed in Optimized Vein Miner all-versions port (run 5).
See `DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraft/client/KeyMapping.java`
lines 312–328 for the full `Category` record definition.
