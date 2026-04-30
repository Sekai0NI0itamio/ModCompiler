# Account Switcher — All Versions Port

## Overview

| Field | Value |
|-------|-------|
| Mod | Account Switcher |
| Modrinth URL | https://modrinth.com/mod/account-switcher |
| Mod ID | `accountswitcher` |
| Starting coverage | 36 versions (Forge 1.8.9–1.21.11, Fabric 1.21.1–1.21.8) |
| Final coverage | 79 targets |
| New versions added | 43 (across 3 build runs) |
| Build runs | 3 |
| Generator script | `scripts/generate_account_switcher_bundle.py` |
| Completed | April 2026 |

## What the Mod Does

Account Switcher is a **client-side only** mod that:
1. Monitors `config/account.txt` for changes every second
2. When the `account=` value changes, switches the Minecraft session via reflection
3. Displays the current account name on the title screen

Key characteristics:
- Client-side only (`runtime_side=client`)
- Uses a mixin on `TitleScreen` for Fabric (to draw the account name overlay)
- Uses `ScreenEvent.Render.Post` for Forge/NeoForge
- `SessionManager` and `ConfigHandler` are pure Java — no MC API, identical across all versions
- The only version-specific code is the title screen rendering

## Starting State (from diagnosis)

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/account-switcher \
    --output-dir /tmp/account-switcher-diag
cat /tmp/account-switcher-diag/summary.txt
```

Already published: 36 versions — Forge 1.8.9, 1.12.2, 1.16.5–1.21.11; Fabric 1.21.1–1.21.8.

## Manifest Comparison (missing targets)

```bash
python3 -c "
import json
with open('version-manifest.json') as f:
    manifest = json.load(f)
all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))
published = {
    ('1.8.9','forge'), ('1.12.2','forge'),
    ('1.16.5','forge'), ('1.17.1','forge'),
    ('1.18','forge'), ('1.18.1','forge'), ('1.18.2','forge'),
    ('1.19','forge'), ('1.19.1','forge'), ('1.19.2','forge'), ('1.19.3','forge'), ('1.19.4','forge'),
    ('1.20.1','forge'), ('1.20.2','forge'), ('1.20.3','forge'), ('1.20.4','forge'), ('1.20.6','forge'),
    ('1.21','forge'), ('1.21.1','forge'),
    ('1.21.3','forge'), ('1.21.4','forge'), ('1.21.5','forge'), ('1.21.6','forge'),
    ('1.21.7','forge'), ('1.21.8','forge'), ('1.21.9','forge'), ('1.21.10','forge'), ('1.21.11','forge'),
    ('1.21.1','fabric'), ('1.21.2','fabric'), ('1.21.3','fabric'), ('1.21.4','fabric'),
    ('1.21.5','fabric'), ('1.21.6','fabric'), ('1.21.7','fabric'), ('1.21.8','fabric'),
}
missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)}):')
for t in missing: print(f'  {t[0]:12} {t[1]}')
"
```

Result: 40 missing targets — Forge 1.12 + 26.1.2, NeoForge 1.20.2–26.1.2, Fabric 1.16.5–26.1.2.

## Source Strategy

### Shared classes (identical across all versions)

- `ConfigHandler.java` — pure Java file I/O, no MC API
- `SessionManager.java` — reflection-based session field access, no MC API

These are copied verbatim into every target folder.

### Version-specific: Forge

| Version range | Event class | Graphics accessor | Notes |
|--------------|-------------|-------------------|-------|
| 1.12 | `GuiScreenEvent.DrawScreenEvent.Post` | none (fontRenderer.drawStringWithShadow) | `@Mod.EventHandler`, `FMLInitializationEvent`, `GuiMainMenu` |
| 1.16.5 | `GuiScreenEvent.DrawScreenEvent.Post` | `event.getMatrixStack()` (blaze3d) | SRG field names via reflection |
| 1.17.1 | `GuiScreenEvent.DrawScreenEvent.Post` | `event.getMatrixStack()` | `mc.screen`, `mc.font` |
| 1.18–1.18.2 | `ScreenEvent.DrawScreenEvent.Post` | `event.getPoseStack()` | |
| 1.19–1.19.4 | `ScreenEvent.Render.Post` | `event.getPoseStack()` | |
| 1.20.1–1.21.5 | `ScreenEvent.Render.Post` | `event.getGuiGraphics()` → `GuiGraphics` | `gg.drawString()` |
| 1.21.6–1.21.11 | `ScreenEvent.Render.Post` | `event.getGuiGraphics()` → `GuiGraphics` | EventBus 7: `register(this)` not `@SubscribeEvent` |
| 26.1.2 | `ScreenEvent.Render.Post.BUS.addListener()` | `event.getGuiGraphics()` → `GuiGraphicsExtractor` | `gg.text()` not `gg.drawString()` |

### Version-specific: NeoForge

| Version range | Constructor | Event class | Graphics accessor |
|--------------|-------------|-------------|-------------------|
| 1.20.2–1.21.8 | `(IEventBus modBus)` | `ScreenEvent.Render.Post` | `getGuiGraphics()` → `GuiGraphics` |
| 1.21.9–1.21.11 | `(IEventBus modBus, ModContainer modContainer)` | `ScreenEvent.Render.Post` | `getGuiGraphics()` → `GuiGraphics` |
| 26.1–26.1.2 | `(IEventBus modBus, ModContainer modContainer)` | `ScreenEvent.Render.Post` | `getGuiGraphics()` → `GuiGraphicsExtractor`, `gg.text()` |

### Version-specific: Fabric (mixin approach)

| Version range | Adapter | TitleScreen package | Mixin target method | Graphics type | Text method |
|--------------|---------|---------------------|---------------------|---------------|-------------|
| 1.16.5–1.19.4 | `fabric_presplit` | `net.minecraft.client.gui.screen` | `render(MatrixStack, int, int, float)` | `MatrixStack` | `tr.drawWithShadow()` |
| 1.20.1–1.20.6 | `fabric_split` | `net.minecraft.client.gui.screen` | `render(DrawContext, int, int, float)` | `DrawContext` | `context.drawText()` |
| 1.21–1.21.11 | `fabric_split` | `net.minecraft.client.gui.screens` (Mojang) | `render(GuiGraphics, int, int, float)` | `GuiGraphics` | `gg.drawString()` |
| 26.1–26.1.2 | `fabric_split` | `net.minecraft.client.gui.screens` (Mojang) | `extractRenderState(GuiGraphicsExtractor, int, int, float)` | `GuiGraphicsExtractor` | `gg.text()` |

## Build Run Summary

| Run | Targets | Passed | Failed | Key Fix |
|-----|---------|--------|--------|---------|
| 1 | 41 | 37 | 4 | Initial bundle — forge-26-1-2 had f-string bug; neoforge-1-20-2 and neoforge-1-21-6 had transient network failures |
| 2 | 3 | 3 | 0 | Fixed f-string escaping in `FORGE_26_SRC`; retried transient network failures |
| 3 | 2 | 2 | 0 | Added missing `fabric-1-17` and `fabric-1-21` (manifest uses `1.17`/`1.21` not `1.17.1`/`1.21.1` for Fabric) |

## Step-by-Step Session

### Step 1: Diagnose + inspect jar

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/account-switcher \
    --output-dir /tmp/account-switcher-diag

# Inspect the Fabric 2.0.0 jar
curl -sL "https://cdn.modrinth.com/data/5SHW9JoR/versions/UeiCtbGY/accountswitcher-2.0.0.jar" \
    -o /tmp/accountswitcher-fabric-2.0.0.jar
jar tf /tmp/accountswitcher-fabric-2.0.0.jar
unzip -p /tmp/accountswitcher-fabric-2.0.0.jar fabric.mod.json
unzip -p /tmp/accountswitcher-fabric-2.0.0.jar accountswitcher.mixins.json
```

Findings:
- Fabric version uses a mixin on `TitleScreen` with a `TitleScreenMixin` class
- `SessionManager` and `ConfigHandler` are pure Java — no MC API
- `runtime_side=client` (client-side only)

### Step 2: Look up APIs in decompiled sources

```bash
# Check TitleScreen render signature per era
grep -n "render\|MatrixStack\|DrawContext\|GuiGraphics" \
    DecompiledMinecraftSourceCode/1.16.5-fabric/net/minecraft/client/gui/screen/TitleScreen.java | head -5
grep -n "render\|MatrixStack\|DrawContext\|GuiGraphics" \
    DecompiledMinecraftSourceCode/1.18.2-fabric/net/minecraft/client/gui/screen/TitleScreen.java | head -5
grep -n "render\|MatrixStack\|DrawContext\|GuiGraphics" \
    DecompiledMinecraftSourceCode/1.19-fabric/net/minecraft/client/gui/screen/TitleScreen.java | head -5

# Check 26.x — render() is gone, replaced by extractRenderState()
grep -n "render\|extractRenderState\|GuiGraphicsExtractor" \
    DecompiledMinecraftSourceCode/26.1.2-forge/net/minecraft/client/gui/screens/TitleScreen.java | head -10

# Check NeoForge ScreenEvent structure
grep -n "class\|Render\|Post\|getGuiGraphics\|GuiGraphicsExtractor" \
    DecompiledMinecraftSourceCode/1.20.2-neoforge/net/neoforged/neoforge/client/event/ScreenEvent.java | head -20
grep -n "class\|Render\|Post\|getGuiGraphics\|GuiGraphicsExtractor" \
    DecompiledMinecraftSourceCode/26.1.2-neoforge/net/neoforged/neoforge/client/event/ScreenEvent.java | head -20

# Check NeoForge 1.21.9+ ModContainer requirement
grep -n "public NeoForgeMod\|IEventBus\|ModContainer" \
    DecompiledMinecraftSourceCode/1.21.9-neoforge/net/neoforged/neoforge/common/NeoForgeMod.java | head -5

# Check GuiGraphicsExtractor.text() method
grep -n "drawString\|text\|Font" \
    DecompiledMinecraftSourceCode/26.1.2-forge/net/minecraft/client/gui/GuiGraphicsExtractor.java | head -10
```

### Step 3: Compute missing targets

```bash
python3 -c "
import json
with open('version-manifest.json') as f:
    manifest = json.load(f)
all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))
# ... fill published set from diagnosis ...
missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)}):')
for t in missing: print(f'  {t[0]:12} {t[1]}')
"
```

Result: 40 missing targets.

### Step 4: Write generator script

Created `scripts/generate_account_switcher_bundle.py` with:
- `FORGE_122_SRC` — 1.12.2 Forge API, `GuiMainMenu`, `fontRenderer.drawStringWithShadow()`
- `_forge_mod_src()` helper — parameterized by event import, event class, render body
- `NEO_STD_SRC` — NeoForge 1.20.2–1.21.8 (IEventBus constructor)
- `NEO_1219_SRC` — NeoForge 1.21.9–1.21.11 (IEventBus + ModContainer constructor)
- `NEO_26_SRC` — NeoForge 26.1–26.1.2 (GuiGraphicsExtractor.text())
- `FABRIC_PRESPLIT_MIXIN` — 1.16.5–1.19.4 (MatrixStack, drawWithShadow)
- `FABRIC_SPLIT_120_MIXIN` — 1.20.1–1.20.6 (DrawContext, drawText)
- `FABRIC_SPLIT_121_MIXIN` — 1.21–1.21.11 (GuiGraphics, drawString)
- `FABRIC_26_MIXIN` — 26.1–26.1.2 (GuiGraphicsExtractor, extractRenderState, text)

### Step 5: Run 1 — 41 targets

```bash
python3 scripts/generate_account_switcher_bundle.py
git add scripts/generate_account_switcher_bundle.py \
    incoming/account-switcher-all-versions.zip \
    incoming/account-switcher-all-versions/
git commit -m "Add Account Switcher all-versions bundle (41 missing targets)"
git push
python3 scripts/run_build.py incoming/account-switcher-all-versions.zip \
    --modrinth https://modrinth.com/mod/account-switcher \
    --max-parallel all
```

**Result**: 37 passed, 4 failed.

Failed:
- `accountswitcher-forge-26-1-2` — compile error: `illegal start of expression` at `{{`
- `accountswitcher-neoforge-1-20-2` — `Could not find any matches for net.neoforged:neoforge:20.2.+` (transient)
- `accountswitcher-neoforge-1-21-6` — `Could not find any matches for net.neoforged:neoforge:21.6.+` (transient)

### Step 6: Diagnose run 1 failures

```bash
grep -A 5 "error:" ModCompileRuns/run-*/artifacts/all-mod-builds/mods/accountswitcher-forge-26-1-2/build.log | head -10
# → illegal start of expression at line with {{
# → class, interface, enum, or record expected at }}

grep -A 5 "Could not resolve" ModCompileRuns/run-*/artifacts/all-mod-builds/mods/accountswitcher-neoforge-1-20-2/build.log | head -5
# → Could not find any matches for net.neoforged:neoforge:20.2.+ (transient network)
```

Root causes:
1. `FORGE_26_SRC` was a regular string (`"""\..."""`) not an f-string (`f"""\..."""`).
   Python did not substitute `{MOD_ID}` or convert `{{`/`}}` to `{`/`}`.
2. NeoForge 1.20.2 and 1.21.6 were transient Gradle dependency resolution failures.

### Step 7: Fix and run 2 (3 targets)

```bash
# Fix: add f prefix to FORGE_26_SRC in generator
# Verify the fix:
python3 scripts/generate_account_switcher_bundle.py --failed-only
cat incoming/account-switcher-all-versions/accountswitcher-forge-26-1-2/src/main/java/.../AccountSwitcherMod.java | head -15
# Should show: @Mod("accountswitcher") not @Mod("{MOD_ID}")
# Should show: public class AccountSwitcherMod { not {{

git add ... && git commit -m "Fix run 1 failures: forge-26-1-2 f-string, neoforge transient retry"
git push
python3 scripts/run_build.py incoming/account-switcher-all-versions.zip \
    --modrinth https://modrinth.com/mod/account-switcher \
    --max-parallel all
```

**Result**: All 3 passed. ✅

### Step 8: Manifest comparison reveals 2 more missing

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/account-switcher \
    --output-dir /tmp/account-switcher-final
# → 121 versions published

python3 -c "
import json, os
# ... manifest comparison using version.json files ...
"
# → MISSING (2): 1.17 fabric, 1.21 fabric
```

Root cause: The manifest's `supported_versions` for Fabric differs from Forge:
- `1.17-1.17.1/fabric`: `["1.17"]` — not `1.17.1`
- `1.21-1.21.1/fabric`: `["1.21"]` — not `1.21.1`

The generator was targeting `1.17.1` and `1.21.1` for Fabric (matching Forge), but
the manifest requires `1.17` and `1.21`.

### Step 9: Add missing targets and run 3 (2 targets)

```bash
# Add fabric-1-17 and fabric-1-21 to FABRIC_TARGETS in generator
# Filter zip to only those 2 targets
python3 -c "
import zipfile
needed = {'accountswitcher-fabric-1-17', 'accountswitcher-fabric-1-21'}
with zipfile.ZipFile('incoming/account-switcher-all-versions.zip') as src:
    with zipfile.ZipFile('incoming/account-switcher-missing-only.zip', 'w', zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            top = item.filename.split('/')[0]
            if top in needed:
                dst.writestr(item, src.read(item.filename))
"
git add ... && git commit -m "Add missing Fabric 1.17 and 1.21 targets"
git push
python3 scripts/run_build.py incoming/account-switcher-missing-only.zip \
    --modrinth https://modrinth.com/mod/account-switcher \
    --max-parallel all
```

**Result**: Both passed. ✅

### Step 10: Final verification

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/account-switcher \
    --output-dir /tmp/account-switcher-final2
# → 123 versions

# Manifest comparison → MISSING (0) — port complete! ✅
```

## API Reference Tables

### Forge ScreenEvent — Full History

| MC Version | Import | Subclass | Graphics accessor | Graphics type |
|------------|--------|----------|-------------------|---------------|
| 1.12 | `net.minecraftforge.client.event.GuiScreenEvent` | `DrawScreenEvent.Post` | none | `FontRenderer.drawStringWithShadow()` |
| 1.16.5 | `net.minecraftforge.client.event.GuiScreenEvent` | `DrawScreenEvent.Post` | `getMatrixStack()` | `MatrixStack` (blaze3d) |
| 1.17.1 | `net.minecraftforge.client.event.GuiScreenEvent` | `DrawScreenEvent.Post` | `getMatrixStack()` | `PoseStack` |
| 1.18–1.18.2 | `net.minecraftforge.client.event.ScreenEvent` | `DrawScreenEvent.Post` | `getPoseStack()` | `PoseStack` |
| 1.19–1.19.4 | `net.minecraftforge.client.event.ScreenEvent` | `Render.Post` | `getPoseStack()` | `PoseStack` |
| 1.20.1–1.21.5 | `net.minecraftforge.client.event.ScreenEvent` | `Render.Post` | `getGuiGraphics()` | `GuiGraphics` |
| 1.21.6–1.21.11 | `net.minecraftforge.client.event.ScreenEvent` | `Render.Post` | `getGuiGraphics()` | `GuiGraphics` |
| 26.1.2 | `net.minecraftforge.client.event.ScreenEvent` | `Render.Post` (via BUS) | `getGuiGraphics()` | `GuiGraphicsExtractor` |

### NeoForge ScreenEvent

| NeoForge version | Constructor | Event class | Graphics type |
|-----------------|-------------|-------------|---------------|
| 1.20.2–1.21.8 | `(IEventBus modBus)` | `net.neoforged.neoforge.client.event.ScreenEvent.Render.Post` | `GuiGraphics` |
| 1.21.9–1.21.11 | `(IEventBus modBus, ModContainer modContainer)` | same | `GuiGraphics` |
| 26.1–26.1.2 | `(IEventBus modBus, ModContainer modContainer)` | same | `GuiGraphicsExtractor` |

### Fabric TitleScreen Mixin

| Version | Adapter | Mixin target | Param type | Text method |
|---------|---------|--------------|------------|-------------|
| 1.16.5–1.19.4 | `fabric_presplit` | `render` | `MatrixStack` | `tr.drawWithShadow(matrices, text, x, y, color)` |
| 1.20.1–1.20.6 | `fabric_split` | `render` | `DrawContext` | `context.drawText(tr, text, x, y, color, shadow)` |
| 1.21–1.21.11 | `fabric_split` | `render` | `GuiGraphics` | `gg.drawString(font, text, x, y, color)` |
| 26.1–26.1.2 | `fabric_split` | `extractRenderState` | `GuiGraphicsExtractor` | `gg.text(font, text, x, y, color)` |

### Fabric Manifest Version Quirks

| Range folder | Forge versions | Fabric versions |
|-------------|----------------|-----------------|
| `1.17-1.17.1` | `1.17.1` | `1.17` |
| `1.21-1.21.1` | `1.21`, `1.21.1` | `1.21` |

## Files Modified

- `scripts/generate_account_switcher_bundle.py` — updated (added Fabric, NeoForge, Forge 1.12 + 26.1.2)
- `incoming/account-switcher-all-versions.zip` — full bundle
- `incoming/account-switcher-missing-only.zip` — 2-target bundle for run 3

## New DIF Entries Added

| DIF ID | Issue |
|--------|-------|
| `FORGE-FSTRING-ESCAPING-IN-GENERATOR` | Generator source string missing `f` prefix — `{MOD_ID}` and `{{` not substituted |
| `FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION` | Fabric `supported_versions` differs from Forge for same range (1.17 not 1.17.1, 1.21 not 1.21.1) |
| `FORGE-SCREEN-EVENT-RENDER-SUBCLASS-HISTORY` | Full history of Forge ScreenEvent subclass names and graphics accessors across all versions |
| `FABRIC-TITLESCREEN-MIXIN-RENDER-SIGNATURE-HISTORY` | Full history of Fabric TitleScreen mixin render() signature across all versions |

## Key Lessons

1. **Always use f-strings for generator source blocks.** If a source string contains
   `{VAR}` placeholders or `{{`/`}}` for literal braces, it must be `f"""..."""`.
   Spot-check the generated output before committing.

2. **Fabric `supported_versions` differs from Forge for the same range folder.**
   Always read per-loader from `version-manifest.json`. The `1.17-1.17.1` range
   targets `1.17` for Fabric but `1.17.1` for Forge. The `1.21-1.21.1` range
   targets `1.21` for Fabric but `1.21` and `1.21.1` for Forge.

3. **Transient NeoForge dependency failures are just retries.** `Could not find any
   matches for net.neoforged:neoforge:20.2.+` is a Maven network blip. Retry with
   `--failed-only` — no code change needed.

4. **Fabric TitleScreen mixin has 4 distinct eras.** MatrixStack (1.16.5–1.19.4),
   DrawContext (1.20.1–1.20.6), GuiGraphics (1.21–1.21.11), GuiGraphicsExtractor
   with `extractRenderState` (26.1+). Each needs a separate mixin class.

5. **26.x Forge uses `ScreenEvent.Render.Post.BUS.addListener()` not `@SubscribeEvent`.**
   The EventBus 7 pattern applies to screen events too. The graphics type is
   `GuiGraphicsExtractor` and text is drawn with `.text()` not `.drawString()`.

6. **NeoForge 1.21.9+ requires `ModContainer` in the `@Mod` constructor.**
   Signature: `public MyMod(IEventBus modBus, ModContainer modContainer)`.
   This applies to 1.21.9, 1.21.10, 1.21.11, and all 26.x NeoForge.

7. **Fabric client-side mods use `ClientModInitializer`, not `ModInitializer`.**
   The entrypoint in `fabric.mod.json` must be under `"client"`, not `"main"`.
   The `runtime_side=client` in `mod.txt` handles this automatically.
