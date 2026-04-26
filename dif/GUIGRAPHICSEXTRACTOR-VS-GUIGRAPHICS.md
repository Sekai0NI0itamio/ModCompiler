---
id: GUIGRAPHICSEXTRACTOR-VS-GUIGRAPHICS
title: Forge/NeoForge 26.1 — GuiGraphicsExtractor replaces GuiGraphics for layer rendering
tags: [forge, neoforge, compile-error, api-change, GuiGraphicsExtractor, GuiGraphics, 26.1, ForgeLayer]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [forge, neoforge]
symbols: [GuiGraphicsExtractor, GuiGraphics, ForgeLayer, text]
error_patterns: ["cannot find symbol.*GuiGraphicsExtractor", "method text.*not found.*GuiGraphics", "cannot find symbol.*gg.text"]
---

## Issue

Forge/NeoForge 26.1.x uses `GuiGraphicsExtractor` instead of `GuiGraphics` for layer rendering, and the text drawing method is `text()` not `drawString()`.

## Error

```
error: cannot find symbol
import net.minecraft.client.gui.GuiGraphicsExtractor;
                               ^
```

or

```
error: cannot find symbol
    gg.drawString(font, text, x, y, color);
      ^
  symbol:   method drawString(Font, String, int, int, int)
  location: variable gg of type GuiGraphics
```

## Root Cause

In Forge/NeoForge 26.1.x, the layer rendering interface changed:

| Version | Layer interface | Graphics type | Text method |
|---------|----------------|---------------|-------------|
| Forge 52-58.x (1.21.1–1.21.8) | `LayeredDraw.Layer` | `GuiGraphics` | `drawString(Font, String, x, y, color, shadow)` |
| Forge 59-60.x (1.21.9–1.21.10) | `ForgeLayer.render(GuiGraphics, DeltaTracker)` | `GuiGraphics` | `drawString(...)` |
| Forge 64.x (26.1.2) | `ForgeLayer.extract(GuiGraphicsExtractor, DeltaTracker)` | `GuiGraphicsExtractor` | `text(Font, String, x, y, color, shadow)` |
| NeoForge 26.1.x | `GuiLayer.render(GuiGraphicsExtractor, DeltaTracker)` | `GuiGraphicsExtractor` | `text(Font, String, x, y, color, shadow)` |

## Fix

**Forge 26.1.2:**
```java
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraft.resources.Identifier;

// In AddGuiOverlayLayersEvent handler:
ForgeLayer layer = (gg, dt) -> {
    // gg is GuiGraphicsExtractor, not GuiGraphics
    gg.text(font, text, x, y, 0xFFFFFF, true);  // NOT drawString!
};
draw.add(Identifier.fromNamespaceAndPath("mymod", "hud"), layer);
```

**NeoForge 26.1.x:**
```java
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.resources.Identifier;

// In RegisterGuiLayersEvent handler:
GuiLayer layer = (gg, dt) -> {
    gg.text(font, text, x, y, 0xFFFFFF, true);
};
event.registerAboveAll(Identifier.fromNamespaceAndPath("mymod", "hud"), layer);
```

Key points:
- `GuiGraphicsExtractor` is the parameter type, not `GuiGraphics`
- Use `gg.text(font, str, x, y, color, shadow)` — NOT `drawString()`
- Use `Identifier` (not `ResourceLocation`) in 26.1.x

## Verified

Confirmed in Day Counter port (Challenges 7, 9, 11) and NeoForge 26.1 source search.
