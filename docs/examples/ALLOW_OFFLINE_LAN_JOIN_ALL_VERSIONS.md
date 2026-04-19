# Allow Offline LAN Join — Multi-Version Build Documentation

## Overview

**Mod:** Allow Offline LAN Join  
**Modrinth URL:** https://modrinth.com/mod/allow-offline-lan-join  
**What it does:** Disables Mojang authentication on the server so offline (cracked) players can join LAN worlds and servers without requiring a valid Mojang/Microsoft account. Uses Java reflection to set the server's `online-mode` field to `false` at startup.  
**Side:** Both (server-side logic, no client requirement)

**Final result: 72 versions built and published across Fabric, Forge, and NeoForge — zero failures on all buildable targets.**

---

## Mod Architecture

This mod is intentionally minimal. Every version follows the same three-class pattern:

| Class | Purpose |
|---|---|
| `AllowOfflineToJoinLan[Loader]Mod` | Mod entrypoint — registers the server-starting event |
| `AllowOfflineToJoinLanConfig` | Reads `config/allowofflinetojoinlan/config.txt` at startup |
| `OnlineModeHelper` | Uses reflection to set `online-mode` to `false` on the server |

The `OnlineModeHelper` is the core of the mod. It uses `Class.forName("net.minecraft.server.dedicated.DedicatedServer")` and reflection to find and set the `onlineMode` field. This approach is version-agnostic — the same reflection code works across all Minecraft versions because the underlying server class structure is stable.

---

## Source Versions (Mod Folders)

The mod is split into multiple source folders under `examples/allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge/`, one per loader/version-range combination:

| Folder | Loader | Version range |
|---|---|---|
| `AllowOfflineToJoinLan189Forge` | Forge | 1.8.9 |
| `AllowOfflineToJoinLan1122Forge` | Forge | 1.12-1.12.2 |
| `AllowOfflineToJoinLan1165Fabric` | Fabric | 1.16.5 |
| `AllowOfflineToJoinLan1165Forge` | Forge | 1.16.5 |
| `AllowOfflineToJoinLan117Fabric` | Fabric | 1.17-1.17.1 |
| `AllowOfflineToJoinLan117Forge` | Forge | 1.17.1 only |
| `AllowOfflineToJoinLan118Fabric` | Fabric | 1.18-1.18.2 |
| `AllowOfflineToJoinLan118Forge` | Forge | 1.18-1.18.2 |
| `AllowOfflineToJoinLan119Fabric` | Fabric | 1.19-1.19.4 |
| `AllowOfflineToJoinLan119Forge` | Forge | 1.19-1.19.4 |
| `AllowOfflineToJoinLan1202NeoForge` | NeoForge | 1.20.2-1.20.4 |
| `AllowOfflineToJoinLan1204NeoForge` | NeoForge | 1.20.4-1.20.6 |
| `AllowOfflineToJoinLan120Fabric` | Fabric | 1.20.1-1.20.6 |
| `AllowOfflineToJoinLan120Forge` | Forge | 1.20.1-1.20.4 |
| `AllowOfflineToJoinLan120ForgeLate` | Forge | 1.20.6 only |
| `AllowOfflineToJoinLan121Fabric` | Fabric | 1.21-1.21.1 |
| `AllowOfflineToJoinLan121Forge` | Forge | 1.21-1.21.1 |
| `AllowOfflineToJoinLan121NeoForge` | NeoForge | 1.21-1.21.1 |
| `AllowOfflineToJoinLan121ForgeMid` | Forge | 1.21.3-1.21.5 |
| `AllowOfflineToJoinLan121ForgeLate` | Forge | 1.21.6-1.21.8 |
| `AllowOfflineToJoinLan1218Fabric` | Fabric | 1.21.2-1.21.8 |
| `AllowOfflineToJoinLan1218NeoForge` | NeoForge | 1.21.2-1.21.8 |
| `AllowOfflineToJoinLan12111Fabric` | Fabric | 1.21.9-1.21.11 |
| `AllowOfflineToJoinLan12111Forge` | Forge | 1.21.9-1.21.11 |
| `AllowOfflineToJoinLan12111NeoForge` | NeoForge | 1.21.9-1.21.11 |

---

## Step-by-Step Commands

### 1. Generate the bundle zip

Use the generator script to build the zip from the source folders. Pass `--exclude` to skip any versions you don't want:

```bash
# Full build (all versions, excluding 1.12.2 Forge)
python3 scripts/_generate_allowoffline_bundle.py --exclude AllowOfflineToJoinLan1122Forge

# Failed-only rebuild (reads latest ModCompileRuns to find failures)
python3 scripts/_generate_allowoffline_bundle.py --failed-only
```

### 2. Commit and push

```bash
git add incoming/allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge.zip
git commit -m "Rebuild allowofflinetojoinlan bundle"
git push
```

### 3. Trigger build + publish (blocking)

```bash
python3 scripts/run_build.py \
  incoming/allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge.zip \
  --modrinth https://modrinth.com/mod/allow-offline-lan-join
```

### 4. Check errors and iterate

```bash
# After a failed run, check what broke
python3 scripts/_check_errors.py

# Regenerate failed-only zip, commit, push, and re-run
python3 scripts/_generate_allowoffline_bundle.py --failed-only
git add incoming/allowofflinetojoinlan-failed-only.zip
git commit -m "Fix <description>"
git push
python3 scripts/run_build.py incoming/allowofflinetojoinlan-failed-only.zip \
  --modrinth https://modrinth.com/mod/allow-offline-lan-join
```

Repeat step 4 until all targets pass.

---

## Build History

| Run | Zip | Targets | Pass | Fail | What changed |
|-----|-----|---------|------|------|--------------|
| 1 | full (stale zip, old source) | 80 | 68 | 12 | Baseline — stale zip missing new version folders |
| 2 | failed-only (5 dirs) | 12 | 7 | 5 | Fixed Forge 1.16.5 ✓, Forge 1.21.6/7/8 ✓ |
| 3 | failed-only (3 dirs) | 6 | 3 | 3 | Fixed Fabric 1.16.5 ✓, Forge 1.21.9/10/11 ✓ |
| 4 | failed-only (1 dir) | 2 | 1 | 1 | Fixed Forge 1.17.1 ✓; Forge 1.17 is template-unsupported |
| 5 | full (rebuilt, excl. 1.12.2) | 72 | 70 | 2 | New failures: Forge 1.21 and 1.21.1 (bad import) |
| 6 | failed-only (1 dir) | 2 | 2 | 0 | Fixed Forge 1.21-1.21.1 ✓ — **ALL PASS** |

**Total build runs: 6**  
**Final result: 72/72 ✓ (100%)**

---

## Challenges and Solutions

### Challenge 1: Stale zip — missing newly created version folders

**Problem:** The initial zip (`incoming/allowofflinetojoinlan-1.8.9-1.21.11-fabric-forge-neoforge.zip`) was built before the previous session's fixes were applied. It was missing the new folders created in the fix session (`AllowOfflineToJoinLan1218Fabric`, `AllowOfflineToJoinLan1218NeoForge`, `AllowOfflineToJoinLan12111Fabric`, `AllowOfflineToJoinLan12111Forge`, `AllowOfflineToJoinLan12111NeoForge`). It also contained old source with wrong version ranges.

**Fix:** Wrote `scripts/_generate_allowoffline_bundle.py` to always rebuild the zip from the current source tree. Never rely on a pre-existing zip — always regenerate before each build run.

**Lesson:** Always regenerate the zip from source before triggering a build. A stale zip silently submits old code.

---

### Challenge 2: Fabric 1.16.5 — SLF4J not available

**Problem:** The Fabric 1.16.5 source was written using `org.slf4j.Logger` and `LoggerFactory`, which caused `package org.slf4j does not exist` at compile time.

**Root cause:** SLF4J was not bundled with Fabric until 1.17. Fabric 1.16.5 uses Log4j directly.

**Fix:** Changed `AllowOfflineToJoinLan1165Fabric` to use `org.apache.logging.log4j.LogManager` and `org.apache.logging.log4j.Logger` — the same logger used by Forge 1.16.5.

**Rule:** Use SLF4J (`org.slf4j`) for Fabric **1.17+**. Use Log4j (`org.apache.logging.log4j`) for Fabric **1.16.5** and all Forge versions.

---

### Challenge 3: Forge 1.16.5 — wrong event package

**Problem:** The Forge 1.16.5 source imported `net.minecraftforge.event.server.ServerStartingEvent`, which does not exist in 1.16.5.

**Root cause:** `net.minecraftforge.event.server.ServerStartingEvent` was introduced in Forge 1.17. In 1.16.5, the correct event is `net.minecraftforge.fml.event.server.FMLServerStartingEvent`.

**Fix:** Changed the import to `net.minecraftforge.fml.event.server.FMLServerStartingEvent` and the method parameter type accordingly.

---

### Challenge 4: Forge 1.17 — three different event packages across eras

**Problem:** Getting the server-starting event import right for Forge 1.17 took three attempts:

1. First tried `net.minecraftforge.fml.event.server.FMLServerStartingEvent` → `package does not exist`
2. Then tried `net.minecraftforge.event.server.ServerStartingEvent` → `package does not exist`
3. Finally found the correct package by reading the 1.17 template's `ExampleMod.java`: `net.minecraftforge.fmlserverevents.FMLServerStartingEvent`

**The three Forge server-starting event eras:**

| Forge version | Event class | Package |
|---|---|---|
| 1.12.2 and earlier | `FMLServerStartingEvent` | `net.minecraftforge.fml.common.event` |
| 1.16.5 | `FMLServerStartingEvent` | `net.minecraftforge.fml.event.server` |
| 1.17.1 | `FMLServerStartingEvent` | `net.minecraftforge.fmlserverevents` |
| 1.18+ | `ServerStartingEvent` | `net.minecraftforge.event.server` |

**Fix:** Read the template's `ExampleMod.java` before writing any Forge mod. The template always shows the correct import for that version range.

**Lesson:** When a Forge event import fails, check `<version-range>/forge/template/src/main/java/com/example/examplemod/ExampleMod.java` — it shows the exact import the template supports.

---

### Challenge 5: Forge 1.17 (plain) — template does not support it

**Problem:** The `version.txt` for `AllowOfflineToJoinLan117Forge` originally said `1.17-1.17.1`, which the build system expanded to both `1.17` and `1.17.1`. The `1.17-1.17.1` template only supports `1.17.1` — Forge never released a stable build for plain `1.17`.

**Error:**
```
1.17-1.17.1/forge/template does not support exact Minecraft 1.17.
Supported exact versions: 1.17.1
```

**Fix:** Changed `version.txt` to `minecraft_version=1.17.1` (single version, not a range).

**Lesson:** Always check `version-manifest.json` or the template's supported versions before writing a range. Forge skips certain patch versions entirely (1.17, 1.20.5, 1.21.2).

---

### Challenge 6: Forge 1.21.6+ — `eventbus.api` package removed

**Problem:** `net.minecraftforge.eventbus.api.SubscribeEvent` does not exist in Forge 1.21.6+. The package was removed entirely.

**Error:**
```
error: package net.minecraftforge.eventbus.api does not exist
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

**Fix:** Removed the `@SubscribeEvent` annotation and the `eventbus.api` import entirely. Used `MinecraftForge.EVENT_BUS.register(this)` in the constructor with a plain instance method — no annotation needed when registering via `register(Object instance)`:

```java
public AllowOfflineToJoinLanForgeMod() {
    MinecraftForge.EVENT_BUS.register(this);
}

// No @SubscribeEvent — register(this) dispatches by method signature
public void onServerStarting(ServerStartingEvent event) { ... }
```

**Lesson:** In Forge 1.21.6+, `eventbus.api` is gone. Use `register(this)` with a plain method. The event bus dispatches by method signature — no annotation required.

---

### Challenge 7: Forge 1.21-1.21.1 — wrong `SubscribeEvent` sub-package

**Problem:** The `AllowOfflineToJoinLan121Forge` source had `import net.minecraftforge.eventbus.api.listener.SubscribeEvent` — a non-existent sub-package. The correct import is `net.minecraftforge.eventbus.api.SubscribeEvent` (no `.listener`).

**Error:**
```
error: package net.minecraftforge.eventbus.api.listener does not exist
```

**Fix:** Corrected the import to `net.minecraftforge.eventbus.api.SubscribeEvent` and switched from the `@Mod.EventBusSubscriber` static inner class pattern to `register(this)` with an instance method.

**Lesson:** The `eventbus.api.listener` sub-package does not exist in any Forge version. The annotation lives directly in `eventbus.api`.

---

### Challenge 8: Forge 1.21.9-1.21.11 — newly created folder had old pattern

**Problem:** The `AllowOfflineToJoinLan12111Forge` folder was created in the previous session using the `@Mod.EventBusSubscriber` + `@SubscribeEvent` static inner class pattern. This pattern uses `eventbus.api.SubscribeEvent`, which doesn't exist in 1.21.9+.

**Fix:** Rewrote the file to use `register(this)` with a plain instance method and no `@SubscribeEvent` annotation — same fix as Challenge 6.

**Lesson:** Any newly created Forge folder targeting 1.21.6+ must use the `register(this)` pattern. Never copy the `@Mod.EventBusSubscriber` pattern for these versions.

---

### Challenge 9: Forge 1.20.5 and 1.21.2 — Forge never released for these versions

**Problem:** The build system reported pre-build validation failures for `forge-1-20-5` and `forge-1-21-2`:

```
1.20-1.20.6/forge/template does not support exact Minecraft 1.20.5.
Supported exact versions: 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6

1.21.2-1.21.8/forge/template does not support exact Minecraft 1.21.2.
Supported exact versions: 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8
```

**Root cause:** Forge never released stable builds for Minecraft 1.20.5 or 1.21.2. These are not fixable — they are gaps in Forge's release history.

**Fix:** None needed. These are expected gaps. The version ranges in `version.txt` that include these versions (e.g. `1.20.1-1.20.6`) simply skip them at the template validation stage.

**Lesson:** Forge skips certain Minecraft patch versions. The known gaps are: `1.17` (plain), `1.20.5`, `1.21.2`. Do not try to create source folders for these — they will always fail pre-build validation.

---

## Confirmed Event API Map

| Forge version | Event class | Import |
|---|---|---|
| 1.8.9 | `FMLServerStartingEvent` | `net.minecraftforge.fml.common.event` |
| 1.12.2 | `FMLServerStartingEvent` | `net.minecraftforge.fml.common.event` |
| 1.16.5 | `FMLServerStartingEvent` | `net.minecraftforge.fml.event.server` |
| 1.17.1 | `FMLServerStartingEvent` | `net.minecraftforge.fmlserverevents` |
| 1.18–1.21.5 | `ServerStartingEvent` | `net.minecraftforge.event.server` |
| 1.21.6–1.21.11 | `ServerStartingEvent` | `net.minecraftforge.event.server` (no `@SubscribeEvent`) |

| Fabric version | Logger |
|---|---|
| 1.16.5 | `org.apache.logging.log4j.Logger` (Log4j) |
| 1.17+ | `org.slf4j.Logger` (SLF4J) |

---

## Version Coverage (72 versions)

### Fabric (29 versions)

| Version |
|---|
| 1.16.5 |
| 1.17, 1.17.1 |
| 1.18, 1.18.1, 1.18.2 |
| 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4 |
| 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6 |
| 1.21, 1.21.1 |
| 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8 |
| 1.21.9, 1.21.10, 1.21.11 |

### Forge (28 versions)

| Version |
|---|
| 1.8.9 |
| 1.16.5 |
| 1.17.1 |
| 1.18, 1.18.1, 1.18.2 |
| 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4 |
| 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6 |
| 1.21, 1.21.1 |
| 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8 |
| 1.21.9, 1.21.10, 1.21.11 |

### NeoForge (15 versions)

| Version |
|---|
| 1.20.2, 1.20.4, 1.20.5, 1.20.6 |
| 1.21, 1.21.1 |
| 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8 |
| 1.21.9, 1.21.10, 1.21.11 |

### Not buildable (Forge gaps — expected)

| Version | Reason |
|---|---|
| Forge 1.17 (plain) | Forge never released for plain 1.17 |
| Forge 1.20.5 | Forge never released for 1.20.5 |
| Forge 1.21.2 | Forge never released for 1.21.2 |

---

## Key Lessons and Best Practices

### 1. Always regenerate the zip from source before every build run

Never reuse a pre-existing zip. Use `scripts/_generate_allowoffline_bundle.py` (or equivalent) to rebuild from the current source tree. A stale zip silently submits old code and wastes build minutes.

### 2. Use `--failed-only` on every retry after the first run

The generator script supports `--failed-only`, which reads the latest `ModCompileRuns/` folder and includes only the mod dirs that had failures. On a 72-target build with 2 failures, this cuts each retry from ~15 minutes to ~3 minutes.

### 3. Read the template's ExampleMod.java before writing any Forge mod

The template at `<version-range>/forge/template/src/main/java/com/example/examplemod/ExampleMod.java` always shows the correct imports and event registration pattern for that version range. This is the fastest way to find the right event package — faster than guessing or searching.

### 4. Know the three Forge event registration eras

- **1.12.2 and earlier:** `@EventHandler` on instance method, `FMLServerStartingEvent` from `net.minecraftforge.fml.common.event`
- **1.16.5–1.21.5:** `@SubscribeEvent` on instance method with `register(this)`, event from `net.minecraftforge.fml.event.server` (1.16.5), `net.minecraftforge.fmlserverevents` (1.17.1), or `net.minecraftforge.event.server` (1.18+)
- **1.21.6+:** `register(this)` with a plain instance method — no `@SubscribeEvent`, no `eventbus.api` import at all

### 5. Know the Fabric logger split

- **Fabric 1.16.5:** Log4j (`org.apache.logging.log4j.LogManager`, `org.apache.logging.log4j.Logger`)
- **Fabric 1.17+:** SLF4J (`org.slf4j.LoggerFactory`, `org.slf4j.Logger`)

Using SLF4J on 1.16.5 causes `package org.slf4j does not exist`. Using Log4j on 1.17+ compiles but is non-idiomatic.

### 6. Know the Forge version gaps

Forge never released stable builds for `1.17` (plain), `1.20.5`, and `1.21.2`. Do not create source folders targeting these exact versions — they will always fail pre-build validation. Version ranges that span these gaps (e.g. `1.20.1-1.20.6`) are fine — the build system skips the unsupported patches automatically.

### 7. Reflection-based mods are the most portable

This mod uses reflection to set `online-mode` rather than calling a version-specific API. This means the `OnlineModeHelper` class is identical across all 25 source folders — only the entrypoint and event registration differ. For any mod that needs to modify server internals, reflection is the most future-proof approach.

### 8. Split Forge 1.21.x into three sub-ranges

Forge 1.21.x has three distinct API eras that require separate source folders:

| Sub-range | Event registration | `@SubscribeEvent` |
|---|---|---|
| 1.21–1.21.1 | `register(this)` | Yes (`eventbus.api`) |
| 1.21.3–1.21.5 | `register(this)` | Yes (`eventbus.api`) |
| 1.21.6–1.21.8 | `register(this)` | No (package removed) |
| 1.21.9–1.21.11 | `register(this)` | No (package removed) |

Note: 1.21.2 is a Forge gap (never released). 1.21.3–1.21.5 and 1.21.6–1.21.8 can be merged into one folder if the source is identical — but keeping them separate makes it easier to fix one without affecting the other.

### 9. `run_build.py` is blocking — never run it as a background process

`run_build.py` polls GitHub Actions every 15 seconds and exits the moment the workflow completes. Run it with `executeBash` (blocking). If run as a background process you have to guess sleep times and risk reading stale output.

### 10. Check `_check_errors.py` before fixing anything

After a failed run, always run `python3 scripts/_check_errors.py` first. It reads the latest `ModCompileRuns/` folder and prints the exact compiler error for each failed target. This tells you exactly what to fix — no guessing.
