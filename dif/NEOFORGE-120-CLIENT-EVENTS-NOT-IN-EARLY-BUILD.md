---
id: NEOFORGE-120-CLIENT-EVENTS-NOT-IN-EARLY-BUILD
title: NeoForge 1.20.2–1.20.4 — ClientTickEvent and RenderFrameEvent do not exist in early build (20.2.93)
tags: [neoforge, compile-error, api-change, tick-event, client, 1.20.2, 1.20.4, decompiled-sources-mismatch]
versions: [1.20.2, 1.20.4]
loaders: [neoforge]
symbols: [ClientTickEvent, RenderFrameEvent, FMLClientSetupEvent]
error_patterns: ["cannot find symbol.*ClientTickEvent", "package ClientTickEvent does not exist", "cannot find symbol.*RenderFrameEvent", "package RenderFrameEvent does not exist"]
---

## Issue

NeoForge 1.20.2 and 1.20.4 fail to compile when importing either
`net.neoforged.neoforge.client.event.ClientTickEvent` or
`net.neoforged.neoforge.client.event.RenderFrameEvent`.

## Errors

```
error: cannot find symbol
import net.neoforged.neoforge.client.event.ClientTickEvent;
                                          ^
  symbol:   class ClientTickEvent

error: package RenderFrameEvent does not exist
    public void onRenderFrame(RenderFrameEvent.Pre event) {
```

## Root Cause

The decompiled sources in `DecompiledMinecraftSourceCode/1.20.2-neoforge/` show
both `ClientTickEvent.java` and `RenderFrameEvent.java` in
`net/neoforged/neoforge/client/event/` — but those sources were generated with a
**newer** NeoForge build than what the build template actually resolves to.

The `1.20-1.20.6/neoforge/template` uses `neo_version=20.2.+` which resolves to
NeoForge **20.2.93**. This early build predates both `ClientTickEvent` and
`RenderFrameEvent` in the client event package.

This is the same root cause as `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` — the
decompiled sources reflect a later NeoForge 20.2.x build, not the one the template
actually uses.

**Do not trust the decompiled sources for NeoForge 1.20.2 or 1.20.4 client events.**

## Fix

For client-side mods targeting NeoForge 1.20.2 and 1.20.4, avoid all client tick
and render frame events. Instead, use `FMLClientSetupEvent.enqueueWork()` to run
client-side initialization once at startup:

```java
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.client.Minecraft;

@Mod("mymod")
public class MyMod {
    public MyMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // enqueueWork runs on the main thread after Minecraft is initialized
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            // do client-side setup here
        });
    }
}
```

For mods that need per-tick behavior on NeoForge 1.20.2/1.20.4, consider:
- Setting state once in `FMLClientSetupEvent` if the effect persists (e.g. gamma)
- Using a Mixin as an alternative (more complex but avoids the missing event)

For NeoForge 1.20.5+ and 1.21+, `ClientTickEvent` from
`net.neoforged.neoforge.client.event` is available and works correctly.

## Affected versions

| NeoForge version | ClientTickEvent available? | RenderFrameEvent available? |
|-----------------|---------------------------|----------------------------|
| 1.20.2 (20.2.93) | ❌ No | ❌ No |
| 1.20.4 (20.4.x early) | ❌ No | ❌ No |
| 1.20.5+ | ✅ Yes | ✅ Yes |
| 1.20.6+ | ✅ Yes | ✅ Yes |
| 1.21+ | ✅ Yes | ✅ Yes |

## Verified

Confirmed in Working Full Bright all-versions port (May 2026).
NeoForge 1.20.2 and 1.20.4 compiled successfully after switching to
`FMLClientSetupEvent.enqueueWork()`.

## See Also

- `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` — same root cause for server tick events
- `NEOFORGE-TICKEVENT-CLIENT-PACKAGE` — correct ClientTickEvent package for 1.21.2+
- `OPTIONS-GAMMA-PRIVATE-FIELD` — how to set gamma via reflection (the use case that triggered this)
