---
id: SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE
title: AI Source Search finds a class but it's still not accessible from mod classpath
tags: [source-search, compile-error, api-inaccessible, forge, classpath]
versions: [1.21, 1.21.6, 1.21.7, 1.21.8]
loaders: [forge]
symbols: [AddGuiOverlayLayersEvent, ForgeLayeredDraw, ForgeLayer]
error_patterns: ["package.*does not exist", "cannot find symbol"]
---

## Issue

The AI Source Search finds a class in the decompiled sources, but the build still fails with "cannot find symbol" or "package does not exist".

## Root Cause

The decompiled sources in `DecompiledMinecraftSourceCode/` contain the **full Minecraft + Forge source tree**, including internal classes that are NOT exported to the public mod API jar on the compile classpath.

A class can exist in the decompiled sources but be inaccessible to mods because:
1. It's in an internal package not exported by the Forge API jar
2. It was added to the source tree but not yet exposed as a public API
3. The class is commented out (e.g. `RenderGuiEvent.java` in Forge 51.x is entirely inside a `/* ... */` block comment)

**The only reliable way to know if a class is accessible is to actually build.**

## Examples

- Forge 51.x (1.21): `AddGuiOverlayLayersEvent` and `ForgeLayeredDraw` exist in decompiled sources but are NOT in the public API jar
- Forge 56-58.x (1.21.6-1.21.8): Same issue — entire overlay package exists in sources but not in API jar

## Fix

When source search finds a class but the build still fails:

1. **Accept the limitation** — the class is internal and not accessible to mods
2. **Look for an alternative API** — check if there's a different event or hook that IS accessible
3. **Skip the version** if no accessible API exists for the required functionality
4. **Check if the class is commented out** — read the actual source file to see if it's inside a block comment

To check if a class is commented out:
```bash
# Read the actual source file
cat DecompiledMinecraftSourceCode/1.21-forge/net/minecraftforge/client/event/RenderGuiEvent.java
```

If the entire class body is inside `/* ... */`, it's not accessible.

## Verified

Confirmed in Day Counter port (Challenges 1, 2, 12). This is the most important lesson from the entire port process.
