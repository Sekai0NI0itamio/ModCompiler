---
id: FORGE-SCREEN-EVENT-CLASS-RENAME
title: Forge — GuiScreenEvent (1.8.9-1.17.1) renamed to ScreenEvent (1.18+)
tags: [forge, compile-error, api-change, GuiScreenEvent, ScreenEvent, 1.18]
versions: [1.8.9, 1.12, 1.12.2, 1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2]
loaders: [forge]
symbols: [GuiScreenEvent, ScreenEvent]
error_patterns: ["cannot find symbol.*GuiScreenEvent", "cannot find symbol.*ScreenEvent", "package.*GuiScreenEvent.*does not exist"]
---

## Issue

Forge GUI screen event class name changed between versions, causing compile errors when using the wrong name.

## Error

```
error: cannot find symbol
import net.minecraftforge.client.event.ScreenEvent;
                                      ^
```

or

```
error: cannot find symbol
import net.minecraftforge.client.event.GuiScreenEvent;
                                      ^
```

## Root Cause

Forge renamed the screen event class in 1.18:

| Forge version | Class | Init event |
|---------------|-------|------------|
| 1.8.9–1.17.1 | `GuiScreenEvent` | `GuiScreenEvent.InitGuiEvent.Post` |
| 1.18+ | `ScreenEvent` | `ScreenEvent.Init.Post` |

## Fix

Use the correct class for the target version:

```java
// Forge 1.8.9–1.17.1
import net.minecraftforge.client.event.GuiScreenEvent;
// Usage: GuiScreenEvent.InitGuiEvent.Post

// Forge 1.18+
import net.minecraftforge.client.event.ScreenEvent;
// Usage: ScreenEvent.Init.Post
```

In the generator script:
```python
if vt < (1, 18):
    event_class = "GuiScreenEvent.InitGuiEvent.Post"
    event_import = "net.minecraftforge.client.event.GuiScreenEvent"
else:
    event_class = "ScreenEvent.Init.Post"
    event_import = "net.minecraftforge.client.event.ScreenEvent"
```

## Verified

Confirmed in Sort Chest port (Step 3, Forge API differences table).
