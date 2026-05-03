---
id: FABRIC-26-SERVER-LIFECYCLE-EVENTS-UNCHANGED
title: Fabric 26.1.x — ServerLifecycleEvents (server-side) unchanged, only client HUD APIs removed
tags: [fabric, api-change, 26.1, server, lifecycle, ServerLifecycleEvents]
versions: [26.1, 26.1.1, 26.1.2]
loaders: [fabric]
symbols: [ServerLifecycleEvents, SERVER_STARTING, HudRenderCallback, fabric-lifecycle-events-v1]
error_patterns: []
---

## Issue

When porting a server-side Fabric mod to 26.1.x, it is tempting to assume all
Fabric API callbacks were removed or changed — especially after reading that
`HudRenderCallback` was removed in 26.1. This is incorrect.

## Root Cause

The Fabric 26.1 release removed **client-side rendering APIs** (specifically
`HudRenderCallback`, which was replaced by `HudElementRegistry`). It did NOT
remove server-side lifecycle events.

`fabric-lifecycle-events-v1` — including `ServerLifecycleEvents.SERVER_STARTING`
— is present and unchanged in Fabric API 0.145.x–0.146.x for 26.1.x.

## Fix

For server-side mods using `ServerLifecycleEvents.SERVER_STARTING`, use the
**exact same source as 1.21.11**. No changes needed.

```java
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public final class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    }

    private void onServerStarting(MinecraftServer server) {
        // server-side logic here
    }
}
```

## What IS removed in Fabric 26.1.x

Only skip Fabric 26.1.x for mods that use these removed APIs:

| Removed API | Replacement |
|-------------|-------------|
| `HudRenderCallback` | `HudElementRegistry` (not yet fully available at 26.1 launch) |
| `fabric-convention-tags-v1` | removed entirely |
| `fabric-loot-api-v2` | removed entirely |

See DIF entry `FABRIC-26-HUD-CALLBACK-REMOVED` for the HUD rendering case.

## Verified

Confirmed in Allow Offline LAN Join port (run-20260503-085932).
Fabric 26.1, 26.1.1, and 26.1.2 all built and published successfully using
the same `ServerLifecycleEvents.SERVER_STARTING` source as 1.21.11.
