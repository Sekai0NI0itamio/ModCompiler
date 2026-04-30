---
id: FORGE-SCREEN-EVENT-RENDER-SUBCLASS-HISTORY
title: Forge — ScreenEvent render subclass name changed across versions (DrawScreenEvent.Post → Render.Post), and getPoseStack() → getGuiGraphics() in 1.20.1+
tags: [forge, compile-error, api-change, ScreenEvent, GuiScreenEvent, DrawScreenEvent, Render, getPoseStack, getGuiGraphics, GuiGraphics, 1.17, 1.18, 1.19, 1.20]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6, 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11]
loaders: [forge]
symbols: [GuiScreenEvent, ScreenEvent, DrawScreenEvent, Render, getPoseStack, getGuiGraphics, GuiGraphics, PoseStack, MatrixStack]
error_patterns: ["cannot find symbol.*DrawScreenEvent", "cannot find symbol.*Render.Post", "cannot find symbol.*getPoseStack", "cannot find symbol.*getGuiGraphics", "cannot find symbol.*getMatrixStack"]
---

## Issue

Forge screen render events have changed class names, subclass names, and graphics
accessor methods multiple times across versions. Using the wrong combination causes
compile errors.

## Full History

| MC Version | Import | Event class | Graphics accessor | Graphics type |
|------------|--------|-------------|-------------------|---------------|
| 1.16.5 | `net.minecraftforge.client.event.GuiScreenEvent` | `GuiScreenEvent.DrawScreenEvent.Post` | `event.getMatrixStack()` | `MatrixStack` (blaze3d) |
| 1.17.1 | `net.minecraftforge.client.event.GuiScreenEvent` | `GuiScreenEvent.DrawScreenEvent.Post` | `event.getMatrixStack()` | `PoseStack` |
| 1.18–1.18.2 | `net.minecraftforge.client.event.ScreenEvent` | `ScreenEvent.DrawScreenEvent.Post` | `event.getPoseStack()` | `PoseStack` |
| 1.19–1.19.4 | `net.minecraftforge.client.event.ScreenEvent` | `ScreenEvent.Render.Post` | `event.getPoseStack()` | `PoseStack` |
| 1.20.1–1.21.5 | `net.minecraftforge.client.event.ScreenEvent` | `ScreenEvent.Render.Post` | `event.getGuiGraphics()` | `GuiGraphics` |
| 1.21.6–1.21.11 | `net.minecraftforge.client.event.ScreenEvent` | `ScreenEvent.Render.Post` | `event.getGuiGraphics()` | `GuiGraphics` |
| 26.1.2 | `net.minecraftforge.client.event.ScreenEvent` | `ScreenEvent.Render.Post` | `event.getGuiGraphics()` | `GuiGraphicsExtractor` |

**Additional 1.16.5 note**: The `mc.screen` and `mc.font` fields use SRG obfuscated
names (`field_71462_r`, `field_71466_p`) in 1.16.5. Access them via reflection or
use the SRG names directly.

**26.1.2 note**: `getGuiGraphics()` returns `GuiGraphicsExtractor`, not `GuiGraphics`.
Use `gg.text(font, str, x, y, color)` instead of `gg.drawString(font, str, x, y, color)`.
See `GUIGRAPHICSEXTRACTOR-VS-GUIGRAPHICS`.

## Fix

Use separate source strings per era in the generator:

```python
# 1.16.5 — GuiScreenEvent, MatrixStack (blaze3d), SRG field names
FORGE_1165_SRC = _forge_mod_src(
    "import net.minecraftforge.client.event.GuiScreenEvent;",
    "GuiScreenEvent.DrawScreenEvent.Post",
    render_body_1165)  # uses event.getMatrixStack(), SRG field names

# 1.17.1 — GuiScreenEvent, PoseStack, getMatrixStack()
FORGE_171_SRC = _forge_mod_src(
    "import net.minecraftforge.client.event.GuiScreenEvent;",
    "GuiScreenEvent.DrawScreenEvent.Post",
    render_body_171)  # uses event.getMatrixStack(), mc.screen, mc.font

# 1.18–1.18.2 — ScreenEvent, DrawScreenEvent.Post, getPoseStack()
FORGE_118_SRC = _forge_mod_src(
    "import net.minecraftforge.client.event.ScreenEvent;",
    "ScreenEvent.DrawScreenEvent.Post",
    render_body_118)  # uses event.getPoseStack()

# 1.19–1.19.4 — ScreenEvent, Render.Post, getPoseStack()
FORGE_119_SRC = _forge_mod_src(
    "import net.minecraftforge.client.event.ScreenEvent;",
    "ScreenEvent.Render.Post",
    render_body_119)  # uses event.getPoseStack()

# 1.20.1–1.21.11 — ScreenEvent, Render.Post, getGuiGraphics() → GuiGraphics
FORGE_120_SRC = _forge_mod_src(
    "import net.minecraftforge.client.event.ScreenEvent;",
    "ScreenEvent.Render.Post",
    render_body_120)  # uses event.getGuiGraphics(), gg.drawString()

# 26.1.2 — ScreenEvent.Render.Post.BUS.addListener(), GuiGraphicsExtractor.text()
FORGE_26_SRC = f"""...
    ScreenEvent.Render.Post.BUS.addListener(this::onRenderScreen);
...
    private void onRenderScreen(ScreenEvent.Render.Post event) {{
        GuiGraphicsExtractor gg = event.getGuiGraphics();
        gg.text(font, text, x, y, 0xFFFFFF);
    }}
"""
```

## Verified

Confirmed in Account Switcher all-versions port (April 2026).
All 26 Forge targets (1.16.5–26.1.2) passed with the correct per-era source strings.

## See Also

- `FORGE-SCREEN-EVENT-CLASS-RENAME` — GuiScreenEvent → ScreenEvent rename in 1.18
- `GUIGRAPHICSEXTRACTOR-VS-GUIGRAPHICS` — 26.1 GuiGraphicsExtractor details
- `FORGE-EB7-EVENTBUS7-PATTERN` — EventBus 7 migration for 1.21.6+
