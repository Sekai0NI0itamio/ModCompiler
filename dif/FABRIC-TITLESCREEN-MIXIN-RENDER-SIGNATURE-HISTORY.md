---
id: FABRIC-TITLESCREEN-MIXIN-RENDER-SIGNATURE-HISTORY
title: Fabric — TitleScreen mixin render() method signature changed across versions (MatrixStack → DrawContext → GuiGraphics → extractRenderState in 26.x)
tags: [fabric, compile-error, api-change, mixin, TitleScreen, render, MatrixStack, DrawContext, GuiGraphics, GuiGraphicsExtractor, extractRenderState, 1.16.5, 1.20, 1.21, 26.1]
versions: [1.16.5, 1.17, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [fabric]
symbols: [TitleScreen, render, MatrixStack, DrawContext, GuiGraphics, GuiGraphicsExtractor, extractRenderState, drawWithShadow, drawText, drawString, text]
error_patterns: ["cannot find symbol.*render.*MatrixStack", "cannot find symbol.*render.*DrawContext", "cannot find symbol.*render.*GuiGraphics", "mixin target.*render.*not found", "cannot find symbol.*drawWithShadow", "cannot find symbol.*drawText", "cannot find symbol.*extractRenderState"]
---

## Issue

A Fabric mixin targeting `TitleScreen.render()` fails to compile or inject because
the method signature changed across Minecraft versions. The graphics parameter type
and the text-drawing API both changed.

## Full History

| MC Version | Adapter | TitleScreen class | render() signature | Text draw method |
|------------|---------|-------------------|--------------------|-----------------|
| 1.16.5–1.19.4 | `fabric_presplit` | `net.minecraft.client.gui.screen.TitleScreen` | `render(MatrixStack, int, int, float)` | `tr.drawWithShadow(matrices, text, x, y, color)` |
| 1.20.1–1.20.6 | `fabric_split` | `net.minecraft.client.gui.screen.TitleScreen` | `render(DrawContext, int, int, float)` | `context.drawText(tr, text, x, y, color, shadow)` |
| 1.21–1.21.11 | `fabric_split` | `net.minecraft.client.gui.screens.TitleScreen` (Mojang) | `render(GuiGraphics, int, int, float)` | `gg.drawString(font, text, x, y, color)` |
| 26.1–26.1.2 | `fabric_split` | `net.minecraft.client.gui.screens.TitleScreen` (Mojang) | No `render()` — use `extractRenderState(GuiGraphicsExtractor, int, int, float)` | `gg.text(font, text, x, y, color)` |

**Key boundary points:**
- `1.20.1`: `MatrixStack` → `DrawContext` (yarn), `drawWithShadow` → `drawText`
- `1.21`: `DrawContext` → `GuiGraphics` (Mojang mappings), class package changes
- `26.1`: `render()` removed, replaced by `extractRenderState()` with `GuiGraphicsExtractor`

## Fix

Use separate mixin classes per era:

### Fabric 1.16.5–1.19.4 (presplit, yarn, MatrixStack)

```java
package com.example.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
        tr.drawWithShadow(matrices, "text", 10, 10, 0xFFFFFF);
    }
}
```

### Fabric 1.20.1–1.20.6 (split, yarn, DrawContext)

```java
package com.example.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
        context.drawText(tr, "text", 10, 10, 0xFFFFFF, true);
    }
}
```

### Fabric 1.21–1.21.11 (split, Mojang mappings, GuiGraphics)

```java
package com.example.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, "text", 10, 10, 0xFFFFFF);
    }
}
```

### Fabric 26.1–26.1.2 (split, Mojang mappings, GuiGraphicsExtractor, extractRenderState)

```java
package com.example.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        graphics.text(font, "text", 10, 10, 0xFFFFFF);
    }
}
```

## Mixins JSON Compatibility Level

| Era | compatibilityLevel |
|-----|--------------------|
| 1.16.5–1.19.4 | `JAVA_17` |
| 1.20.1–1.21.11 | `JAVA_21` |
| 26.1–26.1.2 | `JAVA_25` |

## Verified

Confirmed in Account Switcher all-versions port (April 2026).
All 23 Fabric targets (1.16.5–26.1.2) passed with the correct per-era mixin source.
