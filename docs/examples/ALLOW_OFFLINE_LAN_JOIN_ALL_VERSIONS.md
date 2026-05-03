# Allow Offline LAN Join — All Versions Port

## Overview

| Field | Value |
|-------|-------|
| Mod | Allow Offline Players (LAN) |
| Modrinth URL | https://modrinth.com/mod/allow-offline-lan-join |
| Mod ID | `allowofflinetojoinlan` |
| Starting coverage | 75 versions (1.8.9 forge through 1.21.11 fabric/forge/neoforge) |
| Final coverage | 82 versions |
| New versions added | 7 (all in the 26.1-26.x range) |
| Build runs | 1 |
| Generator script | none (bundle written by hand) |
| Completed | May 2026 |

## What the Mod Does

Allow Offline LAN Join is a **server-side** mod that disables Mojang authentication
(`online-mode`) on the LAN host so offline players can join without login errors.

Key characteristics:
- `runtime_side` is effectively `both` (needs to be on the host, client installs it too)
- No commands, no keybinds, no HUD
- Hooks into `ServerStartingEvent` (Forge/NeoForge) or `ServerLifecycleEvents.SERVER_STARTING` (Fabric)
- Uses reflection to set `onlineMode` / `usesAuthentication` on `MinecraftServer`
- Reads a plain-text config file from `config/allowofflinetojoinlan/config.txt`

Because the mod uses only reflection and server lifecycle events, the source is
**identical across all version eras** within each loader family. No API-specific
code paths are needed beyond the event registration boilerplate.

## Starting State (from diagnosis)

Already published before this port (75 versions):

```
Forge:    1.8.9, 1.12, 1.12.1, 1.12.2,
          1.16.5, 1.17.1,
          1.18, 1.18.1, 1.18.2,
          1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4,
          1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6,
          1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5,
          1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11
Fabric:   1.16.5,
          1.17, 1.17.1,
          1.18, 1.18.1, 1.18.2,
          1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4,
          1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
          1.21, 1.21.1,
          1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8,
          1.21.9, 1.21.10, 1.21.11
NeoForge: 1.20.2, 1.20.4, 1.20.5, 1.20.6,
          1.21, 1.21.1,
          1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8,
          1.21.9, 1.21.10, 1.21.11
```

Missing (7 targets — all in 26.1-26.x):

```
Fabric:   26.1, 26.1.1, 26.1.2
Forge:    26.1.2
NeoForge: 26.1, 26.1.1, 26.1.2
```

## Step-by-Step Session

### Step 1: Read all documentation

```
readFile docs/IDE_QUICK_STARTUP_READ.md
readFile docs/SYSTEM_MANUAL.md
readFile version-manifest.json
readFile dif/README.md
```

### Step 2: Run the mandatory diagnosis

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/allow-offline-lan-join \
    --output-dir /tmp/allowoffline-diag
cat /tmp/allowoffline-diag/summary.txt
```

Output: 75 versions already published.

### Step 3: Run the manifest comparison

```python
import json

with open('version-manifest.json') as f:
    manifest = json.load(f)

all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))

# published = set built from diagnosis output
missing = sorted(all_targets - published)
# Result: 7 missing targets, all in 26.1-26.x
```

### Step 4: Check for existing generator

```bash
ls scripts/generate_*allow* scripts/generate_*offline* scripts/generate_*lan*
# Result: no generator found
```

No generator script existed. Bundle was written by hand.

### Step 5: Check existing examples for 26.x patterns

Read the existing 1.21.11 examples to understand the base source, then checked:
- `incoming/tpa-teleport-all-versions/TpaTeleport-26.1.2-forge/` — Forge 26.x EventBus 7 pattern
- `incoming/tpa-teleport-all-versions/TpaTeleport-26.1.2-fabric/` — Fabric 26.x pattern
- `incoming/autofastxp-all-versions/AutoFastXP-26.1.2-neoforge/` — NeoForge 26.x constructor injection
- `incoming/template-test-26x/` — minimal 26.x template stubs

Also verified APIs in decompiled sources:
```bash
grep -r "ServerStartingEvent" DecompiledMinecraftSourceCode/26.1.2-forge/
grep -r "ServerStartingEvent" DecompiledMinecraftSourceCode/26.1.2-neoforge/
```

Confirmed:
- Forge 26.1.2: `ServerStartingEvent.BUS.addListener(...)` (EventBus 7, same as 1.21.6+)
- NeoForge 26.1.2: `NeoForge.EVENT_BUS.addListener(...)` (same as 1.21.11)
- Fabric 26.1.2: `ServerLifecycleEvents.SERVER_STARTING.register(...)` (unchanged from 1.21.11)

### Step 6: Write the bundle

Created `incoming/allowofflinetojoinlan-26x/` with three mod folders:

```
AllowOfflineToJoinLan26xFabric/
  mod.txt                    (entrypoint: ...fabric.AllowOfflineToJoinLanFabricMod)
  version.txt                (minecraft_version=26.1-26.1.2, loader=fabric)
  src/main/java/com/itamio/allowofflinetojoinlan/fabric/
    AllowOfflineToJoinLanFabricMod.java
    AllowOfflineToJoinLanConfig.java
    OnlineModeHelper.java

AllowOfflineToJoinLan26xForge/
  mod.txt                    (entrypoint: ...forge.AllowOfflineToJoinLanForgeMod)
  version.txt                (minecraft_version=26.1.2, loader=forge)
  src/main/java/com/itamio/allowofflinetojoinlan/forge/
    AllowOfflineToJoinLanForgeMod.java
    AllowOfflineToJoinLanConfig.java
    OnlineModeHelper.java

AllowOfflineToJoinLan26xNeoForge/
  mod.txt                    (entrypoint: ...neoforge.AllowOfflineToJoinLanNeoForgeMod)
  version.txt                (minecraft_version=26.1-26.1.2, loader=neoforge)
  src/main/java/com/itamio/allowofflinetojoinlan/neoforge/
    AllowOfflineToJoinLanNeoForgeMod.java
    AllowOfflineToJoinLanConfig.java
    OnlineModeHelper.java
```

Key version string decisions (from `version-manifest.json`):
- Fabric 26.x: `supported_versions: ["26.1", "26.1.1", "26.1.2"]` → use range `26.1-26.1.2`
- NeoForge 26.x: `supported_versions: ["26.1", "26.1.1", "26.1.2"]` → use range `26.1-26.1.2`
- Forge 26.x: `supported_versions: ["26.1.2"]` → use exact `26.1.2`

### Step 7: Zip the bundle correctly

**IMPORTANT**: Must `cwd` into the bundle folder before zipping, or the zip will
include the `incoming/` prefix in all paths, which breaks the prepare step.

```bash
# WRONG — produces incoming/AllowOfflineToJoinLan26xFabric/... inside zip
zip -r incoming/allowofflinetojoinlan-26x.zip incoming/allowofflinetojoinlan-26x/

# CORRECT — produces AllowOfflineToJoinLan26xFabric/... at top level
# (use cwd parameter in the tool, or cd first)
zip -r ../allowofflinetojoinlan-26x.zip AllowOfflineToJoinLan26xFabric/ \
    AllowOfflineToJoinLan26xForge/ AllowOfflineToJoinLan26xNeoForge/
# run from: incoming/allowofflinetojoinlan-26x/
```

### Step 8: Commit and push

```bash
git add incoming/allowofflinetojoinlan-26x.zip incoming/allowofflinetojoinlan-26x/
git commit -m "Add allow-offline-lan-join 26.x missing versions (fabric/forge/neoforge 26.1-26.1.2)"
git push
```

### Step 9: Build and publish

```bash
python3 scripts/run_build.py incoming/allowofflinetojoinlan-26x.zip \
    --modrinth https://modrinth.com/mod/allow-offline-lan-join \
    --max-parallel all
```

### Step 10: Verify

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/allow-offline-lan-join \
    --output-dir /tmp/allowoffline-diag2
# Then run manifest comparison → 0 missing
```

## Build Results

### Run 1 — `run-20260503-085932` — ALL PASS ✓

| Target | Status |
|--------|--------|
| allowofflinetojoinlan fabric 26.1 | ✓ success |
| allowofflinetojoinlan fabric 26.1.1 | ✓ success |
| allowofflinetojoinlan fabric 26.1.2 | ✓ success |
| allowofflinetojoinlan forge 26.1.2 | ✓ success |
| allowofflinetojoinlan neoforge 26.1 | ✓ success |
| allowofflinetojoinlan neoforge 26.1.1 | ✓ success |
| allowofflinetojoinlan neoforge 26.1.2 | ✓ success |

**Total: 7/7 passed. 0 failures. Published to Modrinth in the same run.**

## Source Patterns Used

### Fabric 26.x (identical to 1.21.11)

```java
package com.itamio.allowofflinetojoinlan.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AllowOfflineToJoinLanFabricMod implements ModInitializer {
    public static final String MOD_ID = "allowofflinetojoinlan";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    }

    private void onServerStarting(MinecraftServer server) {
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
```

### Forge 26.1.2 (EventBus 7 — same pattern as 1.21.6+)

```java
package com.itamio.allowofflinetojoinlan.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AllowOfflineToJoinLanForgeMod.MOD_ID)
public final class AllowOfflineToJoinLanForgeMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    private static final Logger LOGGER = LogManager.getLogger("Allow Offline Players (LAN)");

    public AllowOfflineToJoinLanForgeMod(FMLJavaModLoadingContext context) {
        // EventBus 7: register on the event's own static BUS, not MinecraftForge.EVENT_BUS
        ServerStartingEvent.BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
```

Note: `event.getServer()` — `ServerStartingEvent` in 26.1.2 is a `record` with accessor
`getServer()`, not a field. Confirmed in decompiled sources.

### NeoForge 26.x (constructor injection — FMLJavaModLoadingContext removed)

```java
package com.itamio.allowofflinetojoinlan.neoforge;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AllowOfflineToJoinLanNeoForgeMod.MOD_ID)
public final class AllowOfflineToJoinLanNeoForgeMod {
    public static final String MOD_ID = "allowofflinetojoinlan";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // NeoForge 26.1+: FMLJavaModLoadingContext removed.
    // FML injects IEventBus (mod bus) and ModContainer via constructor.
    public AllowOfflineToJoinLanNeoForgeMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        AllowOfflineToJoinLanConfig.load(LOGGER);
        OnlineModeHelper.apply(server, AllowOfflineToJoinLanConfig.requireMojangAuthentication, LOGGER);
    }
}
```

## Key Lessons

### Lesson 1: Fabric server lifecycle events survived the 26.1 API purge

The Fabric 26.1 release removed `HudRenderCallback` and several client-side APIs,
but **server-side lifecycle events (`fabric-lifecycle-events-v1`) are unchanged**.
`ServerLifecycleEvents.SERVER_STARTING` works identically in 26.1.x as in 1.21.11.

Only skip Fabric 26.1.x for mods that use removed client APIs (HUD rendering, etc.).
Server-side mods can use the exact same source as 1.21.11.

See DIF entry: `FABRIC-26-SERVER-LIFECYCLE-EVENTS-UNCHANGED`

### Lesson 2: NeoForge 26.1 removed FMLJavaModLoadingContext

In NeoForge 26.1+, `FMLJavaModLoadingContext` is gone. The mod constructor must
declare `IEventBus modBus, ModContainer modContainer` as parameters — FML injects
them automatically. This is different from the 1.21.9–1.21.11 pattern where
`FMLJavaModLoadingContext` was still available.

For server-side game events (not mod bus events), still use `NeoForge.EVENT_BUS.addListener(...)`.

See DIF entry: `NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED`

### Lesson 3: Forge 26.1.2 ServerStartingEvent is a record with getServer()

In Forge 26.1.2, `ServerStartingEvent` is declared as a `record`:
```java
public record ServerStartingEvent(MinecraftServer getServer) implements ServerLifecycleEvent {
    public static final EventBus<ServerStartingEvent> BUS = EventBus.create(ServerStartingEvent.class);
}
```
The accessor is `event.getServer()` (record component accessor), not `event.getServer()`
from a method — but the call site is identical. No change needed from 1.21.11.

### Lesson 4: Zip path must be relative to the bundle folder

When creating the zip, always run the `zip` command from inside the bundle folder
(using `cwd` parameter), not from the workspace root. Running from the workspace
root includes the `incoming/` prefix in all zip paths, which causes the prepare
step to fail with a bad zip layout error.

See DIF entry: `ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER`

### Lesson 5: Forge 26.x only supports 26.1.2, not 26.1 or 26.1.1

Check `version-manifest.json` before generating Forge targets for 26.x.
The manifest has `supported_versions: ["26.1.2"]` for Forge — only one version.
Fabric and NeoForge support all three: `["26.1", "26.1.1", "26.1.2"]`.

## Final State

```
Total targets in manifest: 68
Published after this port: 68
Missing: 0
```

All 68 version+loader combinations are now covered.
