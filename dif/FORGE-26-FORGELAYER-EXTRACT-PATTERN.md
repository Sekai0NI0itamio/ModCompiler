---
id: FORGE-26-FORGELAYER-EXTRACT-PATTERN
title: Forge 26.1.2 — ForgeLayer uses extract(GuiGraphicsExtractor) not render(GuiGraphics)
tags: [forge, compile-error, api-change, ForgeLayer, GuiGraphicsExtractor, 26.1, hud, overlay]
versions: [26.1.2]
loaders: [forge]
symbols: [ForgeLayer, GuiGraphicsExtractor, extract, render, AddGuiOverlayLayersEvent, Identifier]
error_patterns: ["cannot find symbol.*ForgeLayer", "method render.*not found.*ForgeLayer", "cannot find symbol.*extract"]
---

## Issue

Forge 26.1.2 HUD layer registration fails because the `ForgeLayer` interface method changed from `render(GuiGraphics, DeltaTracker)` to `extract(GuiGraphicsExtractor, DeltaTracker)`.

## Root Cause

In Forge 26.1.2 (64.x), the layer rendering interface changed:

| Forge version | Interface | Method signature |
|---------------|-----------|-----------------|
| 59-60.x (1.21.9–1.21.10) | `ForgeLayer` | `render(GuiGraphics gg, DeltaTracker dt)` |
| 61.x (1.21.11) | `ForgeLayer` | `render(GuiGraphics gg, DeltaTracker dt)` |
| 64.x (26.1.2) | `ForgeLayer` | `extract(GuiGraphicsExtractor gg, DeltaTracker dt)` |

Also in 26.1.2:
- `Identifier` is used instead of `ResourceLocation`
- `AddGuiOverlayLayersEvent.BUS.addListener(...)` is the registration pattern
- `gg.text(font, str, x, y, color, shadow)` is the text method (not `drawString`)

## Fix

**Forge 26.1.2 complete HUD layer pattern:**

```java
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

// In mod constructor:
AddGuiOverlayLayersEvent.BUS.addListener(this::registerLayers);

// Handler:
private void registerLayers(AddGuiOverlayLayersEvent event) {
    ForgeLayeredDraw draw = event.getLayeredDraw();
    ForgeLayer layer = (gg, dt) -> {
        // gg is GuiGraphicsExtractor
        Font font = Minecraft.getInstance().font;
        gg.text(font, "Day 1", x, y, 0xFFFFFF, true);
    };
    draw.add(Identifier.fromNamespaceAndPath("mymod", "hud"), layer);
}
```

## Verified

Confirmed in Day Counter port (Challenge 9) for Forge 26.1.2.
