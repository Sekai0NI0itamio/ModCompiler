---
id: FABRIC-PARTICLEENGINE-CLEARPARTICLES-PRIVATE
title: Fabric 1.21–1.21.8 — ParticleEngine.clearParticles() has private access, use reflection
tags: [fabric, compile-error, api-change, particles, reflection, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8]
versions: [1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8]
loaders: [fabric]
symbols: [ParticleEngine, clearParticles, particleEngine]
error_patterns: ["clearParticles\\(\\) has private access in ParticleEngine", "clearParticles.*private access"]
---

## Issue

Fabric 1.21–1.21.8 mods that call `client.particleEngine.clearParticles()` directly
fail to compile with a private access error.

## Error

```
error: clearParticles() has private access in ParticleEngine
                client.particleEngine.clearParticles();
                                     ^
```

## Root Cause

In Fabric 1.21–1.21.8 (Mojang mappings), `ParticleEngine.clearParticles()` is a
**private** method. The Forge decompiled sources for 1.21.9+ show it as `public`,
which creates a false impression that it's always public in the 1.21 era.

The visibility boundary is:
- **1.21–1.21.8 Fabric**: `clearParticles()` is **private**
- **1.21.9+ Forge**: `clearParticles()` is **public**

The Fabric 1.21–1.21.8 template uses the `1.21.2-1.21.8` range folder (anchor_only),
so the actual Fabric API version may differ from what the decompiled sources suggest.

## Fix

Use reflection to call `clearParticles()` instead of calling it directly:

```java
// Works for Fabric 1.21–1.21.8 (Mojang mappings, Minecraft class)
import net.minecraft.client.Minecraft;
import java.lang.reflect.Method;

private static Method clearParticlesMethod = null;

private static void clearParticles(Minecraft client) {
    try {
        if (client.particleEngine == null) return;
        if (clearParticlesMethod == null) {
            clearParticlesMethod = client.particleEngine.getClass()
                .getDeclaredMethod("clearParticles");
            clearParticlesMethod.setAccessible(true);
        }
        clearParticlesMethod.invoke(client.particleEngine);
    } catch (Exception e) {
        // ignore
    }
}
```

This same reflection pattern also works for Fabric 1.21.9+ and 26.x even though
`clearParticles()` is public there — reflection is safe to use across all versions.

## Particle field name history (Fabric)

| Version range | Loader | Client class | Particle manager field | Manager class |
|---------------|--------|-------------|----------------------|---------------|
| 1.16.5–1.20.x | Fabric (Yarn) | `MinecraftClient` | `particleManager` | `ParticleManager` |
| 1.21–26.x | Fabric (Mojang) | `Minecraft` | `particleEngine` | `ParticleEngine` |

## clearParticles() visibility history

| Version range | Loader | Visibility |
|---------------|--------|-----------|
| 1.16.5–1.20.x | Fabric | private (use reflection) |
| 1.21–1.21.8 | Fabric | **private** (use reflection) |
| 1.21.9+ | Forge | public |
| 1.21+ | NeoForge | public |

## Verified

Confirmed in World No Particles all-versions port (May 2026, run 2).
All Fabric 1.21–1.21.8 targets compiled and published successfully after switching
to reflection.

## See Also

- `FABRIC-121-MOJANG-MAPPINGS-SWITCH` — Fabric 1.21+ uses Minecraft not MinecraftClient
- `NEOFORGE-120-LEVELTICK-NOT-IN-EARLY-20X` — NeoForge 1.20.x missing tick events
