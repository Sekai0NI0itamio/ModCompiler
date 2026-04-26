---
id: FABRIC-PROTECTED-FIELDS-REFLECTION
title: Fabric — HandledScreen/AbstractContainerScreen fields are protected, use reflection or access wideners
tags: [fabric, compile-error, reflection, protected-fields, HandledScreen, AbstractContainerScreen]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1]
loaders: [fabric]
symbols: [x, y, backgroundWidth, backgroundHeight, guiLeft, guiTop, xSize, ySize]
error_patterns: ["x.*has protected access", "y.*has protected access", "backgroundWidth.*has protected access", "guiLeft.*has protected access"]
---

## Issue

Fabric mods fail to compile when trying to access `x`, `y`, `backgroundWidth`, or `backgroundHeight` fields on `HandledScreen` / `AbstractContainerScreen`.

## Error

```
error: x has protected access in AbstractContainerScreen
    int screenX = screen.x;
                        ^
```

## Root Cause

In Fabric (all versions), the position and size fields of `HandledScreen` / `AbstractContainerScreen` are **protected**. They cannot be accessed directly from outside the class hierarchy.

Affected fields:
- Yarn (1.16.5–1.20.x): `x`, `y`, `backgroundWidth`, `backgroundHeight`
- Mojang (1.21+): `leftPos`, `topPos`, `imageWidth`, `imageHeight`

## Fix

**Option 1: Reflection (works in all versions)**
```java
private static int getField(AbstractContainerScreen<?> screen, String... names) {
    for (String name : names) {
        try {
            java.lang.reflect.Field f = AbstractContainerScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return (int) f.get(screen);
        } catch (Exception ignored) {}
    }
    return 0;
}

// Usage (handles both yarn and mojang field names):
int x = getField(screen, "x", "leftPos");
int y = getField(screen, "y", "topPos");
int w = getField(screen, "backgroundWidth", "imageWidth");
```

**Option 2: Access widener** (cleaner but requires build config changes)
```
# src/main/resources/mymod.accesswidener
accessWidener v2 named
accessible field net/minecraft/client/gui/screen/ingame/HandledScreen x I
accessible field net/minecraft/client/gui/screen/ingame/HandledScreen y I
```

Reflection is simpler and works across all versions without build config changes.

## Verified

Confirmed in Sort Chest port (Lesson 5) — all Fabric versions require reflection for these fields.
