---
id: NEOFORGE-120-LEVELTICK-NOT-IN-EARLY-20X
title: NeoForge 1.20.2–1.20.4 — net.neoforged.neoforge.event.tick package does not exist in early build (20.2.93)
tags: [neoforge, compile-error, api-change, tick-event, level-tick, 1.20.2, 1.20.4, decompiled-sources-mismatch]
versions: [1.20.2, 1.20.4]
loaders: [neoforge]
symbols: [LevelTickEvent, event.tick, RenderLevelStageEvent]
error_patterns: ["package net.neoforged.neoforge.event.tick does not exist", "package LevelTickEvent does not exist", "cannot find symbol.*LevelTickEvent"]
---

## Issue

NeoForge 1.20.2 and 1.20.4 fail to compile when importing anything from
`net.neoforged.neoforge.event.tick`, including `LevelTickEvent`.

## Error

```
error: package net.neoforged.neoforge.event.tick does not exist
import net.neoforged.neoforge.event.tick.LevelTickEvent;
                                        ^
error: package LevelTickEvent does not exist
    public void onLevelTick(LevelTickEvent.Post event) {
```

## Root Cause

The decompiled sources in `DecompiledMinecraftSourceCode/1.20.2-neoforge/` show
`LevelTickEvent.java` in `net/neoforged/neoforge/event/tick/` — but those sources
were generated with a **newer** NeoForge build than what the build template actually
resolves to.

The `1.20-1.20.6/neoforge/template` uses `neo_version=20.2.+` which resolves to
NeoForge **20.2.93**. This early build predates the `event.tick` package entirely.

This is the same root cause as `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` and
`NEOFORGE-120-CLIENT-EVENTS-NOT-IN-EARLY-BUILD` — the decompiled sources reflect
a later NeoForge 20.2.x build, not the one the template actually uses.

**Do not trust the decompiled sources for NeoForge 1.20.2 or 1.20.4 tick events.**

## Fix

For client-side mods that need per-tick behavior on NeoForge 1.20.2/1.20.4, use
`RenderLevelStageEvent` from `net.neoforged.neoforge.client.event` instead.
This event fires every render frame and is available in early NeoForge 20.2.x builds:

```java
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("mymod")
public class MyMod {
    public MyMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new MyHandler());
    }
}
```

```java
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MyHandler {

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        // do per-frame client work here
    }
}
```

For NeoForge 1.20.5+ and 1.21+, `LevelTickEvent.Post` from
`net.neoforged.neoforge.event.tick` is available and works correctly.

## Affected versions

| NeoForge version | event.tick package available? | RenderLevelStageEvent available? |
|-----------------|------------------------------|----------------------------------|
| 1.20.2 (20.2.93) | ❌ No | ✅ Yes |
| 1.20.4 (20.4.x early) | ❌ No | ✅ Yes |
| 1.20.5+ | ✅ Yes | ✅ Yes |
| 1.20.6+ | ✅ Yes | ✅ Yes |
| 1.21+ | ✅ Yes | ✅ Yes |

## Verified

Confirmed in World No Particles all-versions port (May 2026, run 2).
NeoForge 1.20.2 and 1.20.4 compiled successfully after switching from
`LevelTickEvent` to `RenderLevelStageEvent`.

## See Also

- `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` — same root cause for server tick events
- `NEOFORGE-120-CLIENT-EVENTS-NOT-IN-EARLY-BUILD` — ClientTickEvent and RenderFrameEvent also missing
- `FABRIC-PARTICLEENGINE-CLEARPARTICLES-PRIVATE` — Fabric 1.21-1.21.8 clearParticles() private
