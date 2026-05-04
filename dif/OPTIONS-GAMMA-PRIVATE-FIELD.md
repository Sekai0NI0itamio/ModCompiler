---
id: OPTIONS-GAMMA-PRIVATE-FIELD
title: Forge 1.19+ / NeoForge all / Fabric 1.21+ — Options.gamma is a private SimpleOption<Double>, direct field access fails
tags: [forge, neoforge, fabric, compile-error, api-change, gamma, brightness, reflection, 1.19, 1.20, 1.21, 26.1]
versions: [1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [forge, neoforge, fabric]
symbols: [Options, GameOptions, gamma, SimpleOption, gammaSetting]
error_patterns: ["gamma has private access in Options", "gamma has private access in GameOptions", "cannot find symbol.*gamma"]
---

## Issue

Full-bright mods and any mod that sets the gamma/brightness value fail to compile
on Forge 1.19+, all NeoForge versions, and Fabric 1.21+ with a private access error.

## Error

```
error: gamma has private access in Options
    mc.options.gamma = 15.0;
              ^
error: gamma has private access in Options
    gammaValueField = mc.options.gamma.getClass().getDeclaredField("value");
                                ^
```

## Root Cause

The `gamma` field on `Options` (Forge/NeoForge) / `GameOptions` (Fabric) changed
visibility and type across versions:

| Version range | Loader | Field | Type | Access |
|---------------|--------|-------|------|--------|
| 1.8.9–1.12.2 | Forge | `gameSettings.gammaSetting` | `float` | public |
| 1.16.5–1.18.2 | Forge | `options.gamma` | `double` | public |
| 1.19+ | Forge/NeoForge | `options.gamma` | `SimpleOption<Double>` | **private** |
| 1.16.5–1.18.2 | Fabric | `options.gamma` | `double` | public |
| 1.19–1.20.x | Fabric | `options.gamma` (via `getGamma()`) | `SimpleOption<Double>` | **private** |
| 1.21+ | Fabric (Mojang) | `options.gamma` | `SimpleOption<Double>` | **private** |

Additionally, `SimpleOption<Double>` uses `DoubleSliderCallbacks.INSTANCE` which
**clamps values to [0.0, 1.0]**. Calling `setValue(15.0)` silently resets to the
default value instead of setting 15.0.

## Fix

Use **double reflection** to bypass both the private field access and the clamping:

1. Get the `gamma` field from `Options` via `getDeclaredField("gamma")` + `setAccessible(true)`
2. Get the `value` field from `SimpleOption` via `getDeclaredField("value")` + `setAccessible(true)`
3. Set the value directly, bypassing `DoubleSliderCallbacks` validation

```java
// Works for Forge 1.19+, NeoForge all, Fabric 1.21+ (Mojang mappings)
import java.lang.reflect.Field;

private static Field optionsGammaField = null;
private static Field simpleOptionValueField = null;

private static void setGamma(Minecraft mc, double value) {
    try {
        if (optionsGammaField == null) {
            optionsGammaField = mc.options.getClass().getDeclaredField("gamma");
            optionsGammaField.setAccessible(true);
        }
        Object gammaOption = optionsGammaField.get(mc.options);
        if (gammaOption == null) return;
        if (simpleOptionValueField == null) {
            simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
            simpleOptionValueField.setAccessible(true);
        }
        simpleOptionValueField.set(gammaOption, value);
    } catch (Exception e) {
        // ignore — gamma stays at whatever it was
    }
}
```

For Fabric 1.19–1.20.x (Yarn mappings, `MinecraftClient`):
```java
// Same pattern but with MinecraftClient instead of Minecraft
private static void setGamma(MinecraftClient client, double value) {
    try {
        if (optionsGammaField == null) {
            optionsGammaField = client.options.getClass().getDeclaredField("gamma");
            optionsGammaField.setAccessible(true);
        }
        Object gammaOption = optionsGammaField.get(client.options);
        if (gammaOption == null) return;
        if (simpleOptionValueField == null) {
            simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
            simpleOptionValueField.setAccessible(true);
        }
        simpleOptionValueField.set(gammaOption, value);
    } catch (Exception e) {
        // ignore
    }
}
```

## Version-by-version gamma access summary

| Version | Loader | How to set gamma to 15.0 |
|---------|--------|--------------------------|
| 1.8.9–1.12.2 | Forge | `mc.gameSettings.gammaSetting = 15.0F;` |
| 1.16.5–1.18.2 | Forge | `mc.options.gamma = 15.0;` |
| 1.19–1.21.x | Forge/NeoForge | double reflection (see above) |
| 26.1.x | Forge/NeoForge | double reflection (see above) |
| 1.16.5–1.18.2 | Fabric | `client.options.gamma = 15.0;` |
| 1.19–1.20.x | Fabric | double reflection with `MinecraftClient` |
| 1.21+ | Fabric (Mojang) | double reflection with `Minecraft` |

## Why setValue() doesn't work

`SimpleOption.setValue()` calls `callbacks.validate(value)` before setting.
`DoubleSliderCallbacks.validate()` returns `Optional.empty()` for values outside
[0.0, 1.0], which causes `setValue()` to fall back to `defaultValue` (0.5).
Setting 15.0 via `setValue()` silently sets gamma to 0.5 instead.

## Verified

Confirmed in Working Full Bright all-versions port (May 2026).
All 79 targets (1.8.9–26.1.2, Forge/Fabric/NeoForge) compiled and published successfully.

## See Also

- `NEOFORGE-120-CLIENT-EVENTS-NOT-IN-EARLY-BUILD` — NeoForge 1.20.x early build missing client events
- `FABRIC-121-MOJANG-MAPPINGS-SWITCH` — Fabric 1.21+ uses Minecraft not MinecraftClient
