---
id: FORGE-BUTTON-API-CHANGE-1194
title: Forge — Button constructor (pre-1.19.4) vs Button.builder() (1.19.4+)
tags: [forge, compile-error, api-change, Button, 1.19.4, gui, screen]
versions: [1.8.9, 1.12, 1.12.2, 1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4]
loaders: [forge]
symbols: [Button, GuiButton]
error_patterns: ["cannot find symbol.*Button.*builder", "no suitable constructor.*Button", "cannot find symbol.*GuiButton"]
---

## Issue

Forge GUI button creation API changed in 1.19.4, causing compile errors when using the wrong pattern.

## Error

```
error: no suitable constructor found for Button(int,int,int,int,Component,Button.OnPress)
```

or

```
error: cannot find symbol
    Button button = Button.builder(Component.literal("Sort"), handler)
                          ^
  symbol:   method builder(Component, Button.OnPress)
```

## Root Cause

The `Button` constructor API changed across Forge versions:

| Forge version | Button creation |
|---------------|----------------|
| 1.8.9–1.12.2 | `new GuiButton(id, x, y, w, h, text)` |
| 1.16.5–1.18.x | `new Button(x, y, w, h, text, handler)` |
| 1.19.4+ | `Button.builder(text, handler).pos(x, y).size(w, h).build()` |

## Fix

Use the correct pattern for the target version:

**1.8.9–1.12.2:**
```java
import net.minecraft.client.gui.GuiButton;
GuiButton btn = new GuiButton(0, x, y, w, h, "Sort");
event.getButtonList().add(btn);
```

**1.16.5–1.18.x:**
```java
import net.minecraft.client.gui.components.Button;
Button btn = new Button(x, y, w, h, Component.literal("Sort"), handler);
event.addListener(btn);
```

**1.19.4+:**
```java
import net.minecraft.client.gui.components.Button;
Button btn = Button.builder(Component.literal("Sort"), handler)
    .pos(x, y).size(w, h).build();
event.addListener(btn);
```

In the generator script:
```python
if vt < (1, 16, 5):
    button_style = "legacy"   # GuiButton
elif vt < (1, 19, 4):
    button_style = "constructor"  # new Button(...)
else:
    button_style = "builder"  # Button.builder(...)
```

## Verified

Confirmed in Sort Chest port (Step 3, Forge API differences table, Lesson 7).
