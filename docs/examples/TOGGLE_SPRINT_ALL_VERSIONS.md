# Toggle Sprint — Multi-Version Build Documentation

## Overview

**Mod:** Toggle Sprint  
**Modrinth URL:** https://modrinth.com/mod/toggle-sprint  
**What it does:** Keeps the player sprinting when sprint is toggled — press the sprint key once to lock sprint on, press again to turn it off. Stops sprinting automatically when sneaking, using an item, moving backward, or in a menu.  
**Side:** Client-only (`runtime_side=client`)

**Final result: 42 new versions built and published — zero failures.**  
Combined with the 30 already on Modrinth, the mod now covers every supported version and loader.

---

## Step-by-Step Commands (in exact order)

### 1. Trigger the Fetch Modrinth Project workflow

```bash
gh workflow run fetch-modrinth-project.yml \
  --repo Sekai0NI0itamio/ModCompiler \
  --field modrinth_project_url=https://modrinth.com/mod/toggle-sprint
```

### 2. Wait for it to complete

```bash
gh run watch <run_id> --repo Sekai0NI0itamio/ModCompiler
```

> **Note:** The first attempt timed out downloading a jar (transient network error). Simply re-run the workflow — it succeeded on the second attempt. One decompile job (`gYICWzzs`) also failed to download Vineflower, but the assemble job still ran and the bundle was usable.

### 3. Download the bundle artifact

```bash
gh run download <run_id> \
  --repo Sekai0NI0itamio/ModCompiler \
  --name modrinth-project-bundle \
  --dir modrinth-downloads/toggle-sprint
```

### 4. Read the bundle

Key files to read:
- `modrinth-downloads/toggle-sprint/summary.txt` — existing versions, loaders, MC versions
- `modrinth-downloads/toggle-sprint/versions/<id>/decompiled/mod_info.txt` — metadata
- Decompiled Java source for 1.12.2 Forge, 1.20.1 Forge, 1.20.1 Fabric, 1.21.8 Forge

### 5. Run the generator script

```bash
python3 scripts/_generate_toggle_sprint_bundle.py
```

This creates all missing version folders under `incoming/toggle-sprint-all-versions/` and produces the zip.

### 6. Commit and push

```bash
git add incoming/toggle-sprint-all-versions/ \
        incoming/toggle-sprint-all-versions.zip \
        scripts/_generate_toggle_sprint_bundle.py \
        modrinth-downloads/toggle-sprint/
git commit -m "Add toggle-sprint all-versions bundle (missing Forge/Fabric/NeoForge versions)"
git push
```

### 7. Trigger build + publish (repeat until all green)

```bash
python3 scripts/run_build.py \
  incoming/toggle-sprint-all-versions.zip \
  --modrinth https://modrinth.com/mod/toggle-sprint
```

This was run **5 times total** — each run fixed a new batch of API errors. See Challenges section below.

---

## Challenges and Solutions

### Challenge 1: Transient network timeout on Fetch workflow

**Problem:** The first `Fetch Modrinth Project` run timed out downloading one of the 30 jars.  
**Fix:** Re-ran the workflow. Succeeded on the second attempt.

---

### Challenge 2: Wrong zip structure (first build attempt)

**Problem:** The zip was created by zipping the `incoming/` parent folder, producing paths like `incoming/craftable-slime-balls-all-versions/CSB.../`. The build system expects mod folders at the top level.  
**Error:** `incoming: missing required entries: src, mod.txt, version.txt`  
**Fix:** Use Python's `zipfile` module with `item.relative_to(bundle_dir)` so each mod folder sits at the zip root. The generator script handles this automatically.

---

### Challenge 3: 1.20.5 Forge not supported

**Problem:** The `version-manifest.json` `1.20-1.20.6` Forge range does not include 1.20.5 in its `supported_versions` list (Forge skipped 1.20.5). The prepare step rejected it.  
**Fix:** Removed `TS1205Forge` from the bundle. 1.20.5 is only available for NeoForge and Fabric.

---

### Challenge 4: Fabric presplit vs fabric_split — different API names

**Problem:** Fabric 1.16.5–1.19.4 uses the `fabric_presplit` adapter, which compiles against Yarn-mapped deobfuscated names (`MinecraftClient`, `Text`, etc.). The `class_310`/`class_2561` intermediary names only work with `fabric_split` (1.20+).

**Fix:** Two separate controller implementations:
- **1.16.5–1.17.1** (`fabric_presplit`): `client.options.keySprint`, `client.options.keyForward`, `client.options.keyBack`
- **1.18.x–1.19.x** (`fabric_presplit`): `client.options.sprintKey`, `client.options.forwardKey`, `client.options.backKey` (Yarn renamed these fields in 1.18)
- **1.20+** (`fabric_split`): `class_310.field_1690.field_1867` intermediary names (same as existing published versions)

---

### Challenge 5: Forge `Component` API differences across versions

**Problem:** `net.minecraft.network.chat.Component.literal()` was not available in all Forge versions.

| Version | Chat API |
|---|---|
| 1.16.5 | `net.minecraft.util.text.StringTextComponent` |
| 1.17.1 – 1.18.1 | `net.minecraft.network.chat.TextComponent` |
| 1.18.2+ | `net.minecraft.network.chat.Component.literal()` |

---

### Challenge 6: NeoForge `ClientTickEvent` package changed across versions

**Problem:** The `ClientTickEvent` class moved between NeoForge versions.

| NeoForge version | Package |
|---|---|
| 1.20.4 | `net.neoforged.neoforge.event.TickEvent.ClientTickEvent` |
| 1.20.5–1.21.11 | `net.neoforged.neoforge.client.event.ClientTickEvent.Post` |

---

### Challenge 7: NeoForge 1.21.9+ — `FMLEnvironment.dist` removed

**Problem:** `FMLEnvironment.dist` field was removed in NeoForge 1.21.9+. The `if (FMLEnvironment.dist == Dist.CLIENT)` guard no longer compiles.  
**Fix:** Register the event listener unconditionally. The `runtime_side=client` in `mod.txt` causes the NeoForge adapter to set the correct `dist` restriction in `neoforge.mods.toml`, so the mod only loads on the client anyway.

---

### Challenge 8: Forge 1.21.9+ — new `@EventBusSubscriber` pattern

**Problem:** The existing published Forge 1.21.9+ versions use `@EventBusSubscriber` with `SubscribeEvent` from `net.minecraftforge.eventbus.api.listener` and `TickEvent.ClientTickEvent.Post` as an inner class. However, attempting to replicate this with `EventBusSubscriber.Bus.GAME` failed because that enum value doesn't exist.  
**Fix:** Used `MinecraftForge.EVENT_BUS.addListener()` in the mod constructor instead — simpler and works across all Forge versions.

---

## Version Coverage (42 new versions)

### Forge (15 new versions)

| Version |
|---|
| 1.8.9 |
| 1.16.5 |
| 1.17.1 |
| 1.18, 1.18.1, 1.18.2 |
| 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4 |
| 1.21, 1.21.1 |
| 1.21.3, 1.21.4, 1.21.5 |

### Fabric (9 new versions)

| Version |
|---|
| 1.16.5 |
| 1.17.1 |
| 1.18, 1.18.1, 1.18.2 |
| 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4 |

### NeoForge (18 new versions)

| Version |
|---|
| 1.20.4, 1.20.5, 1.20.6 |
| 1.21, 1.21.1 |
| 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8 |
| 1.21.9, 1.21.10, 1.21.11 |

---

## Key Lessons Learned

- **`fabric_presplit` vs `fabric_split` is a hard split at 1.20.** Never use `class_XXX` intermediary names for 1.16.5–1.19.4 Fabric — use the Yarn-mapped names (`MinecraftClient`, `Text`, etc.).

- **Yarn field names changed in 1.18.** `keySprint`/`keyForward`/`keyBack` (1.16.5–1.17.1) became `sprintKey`/`forwardKey`/`backKey` (1.18+) in the presplit era.

- **Forge `Component` API has three eras.** `StringTextComponent` → `TextComponent` → `Component.literal()`. Always check which era you're targeting.

- **NeoForge `ClientTickEvent` moved in 1.20.5.** `neoforge.event.TickEvent` (1.20.4) → `neoforge.client.event.ClientTickEvent` (1.20.5+).

- **`FMLEnvironment.dist` was removed in NeoForge 1.21.9+.** Use `runtime_side=client` in `mod.txt` and register unconditionally.

- **Forge 1.20.5 doesn't exist.** The `version-manifest.json` Forge range for 1.20-1.20.6 skips 1.20.5. Always check `supported_versions` before adding a target.

- **Transient network failures happen.** If the Fetch workflow times out downloading a jar, just re-run it — don't try to work around it.

- **Read the decompiled source of multiple versions.** The 1.12.2 version used LWJGL `Keyboard`, the 1.20.1 version used `MinecraftForge.EVENT_BUS.addListener`, and the 1.21.9+ version used `@EventBusSubscriber`. Each era needs its own implementation.

- **`run_build.py` is blocking — never run it as a background process.** If the connection drops mid-run, use `gh run view <run_id>` to check the result and `gh run download` to get the artifacts manually.
