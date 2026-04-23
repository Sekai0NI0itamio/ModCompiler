# Adding a New Minecraft Version to the Repository

## Overview

This document explains the complete process for adding support for a new
Minecraft version (and its loaders) to this repository. It covers:

- How to research what's available
- How to obtain the correct official MDK templates
- How to wire everything into the build system
- How to verify the templates actually compile
- All the challenges encountered when adding Minecraft 26.1.2 and how they
  were resolved

This is intended as a reference for future IDE agents performing the same task.

---

## Step 1 — Research the new version

### 1a. Find the latest stable release

Search online to confirm the current stable Minecraft version and what loaders
support it:

```bash
# Web search — find current stable version
# Query: "Minecraft Java Edition latest version 2026"
# Query: "NeoForge 26.1 stable release site:neoforged.net"
# Query: "Fabric loom 26.1 example mod build.gradle"
```

Key things to check:
- Is the version a full release or still in snapshots?
- Which loaders have stable releases vs beta-only?
- What Java version does it require?
- Were there any major toolchain changes (e.g. obfuscation removal)?

**Example findings for Minecraft 26.1.2 (April 2026)**:
- Minecraft 26.1 removed obfuscation — Mojang now ships unobfuscated code
- Java 25 required (up from Java 21)
- Fabric: stable (Loom 1.16-SNAPSHOT, Loader 0.19.2)
- Forge: stable (64.0.4, ForgeGradle [7.0.17,8))
- NeoForge: beta only (26.1.2.22-beta, ModDevGradle 2.0.141)

### 1b. Check the Maven repos for exact version numbers

```bash
# Fetch the NeoForge Maven index to see available versions
curl -s "https://maven.neoforged.net/releases/net/neoforged/neoforge/" | grep "26\."

# Check Forge files page for the latest MDK
# https://files.minecraftforge.net/  — shows Gradle dependency string
```

**Challenge**: NeoForge 26.1 versions all have `-beta` suffix. Maven `+`
wildcard doesn't match pre-release suffixes. Must use explicit version:
`neo_version=26.1.2.22-beta` not `26.1.0.+`.

---

## Step 2 — Download the official MDKs

**Never guess the build.gradle structure.** Always download the official MDK
for each loader and use it as the template. This is the most important step.

### Fabric — clone the official example mod

```bash
# Fetch the official Fabric example mod for the target version
curl -fsSL "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/refs/heads/26.1/build.gradle" -o /tmp/fabric-26.1-build.gradle
curl -fsSL "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/refs/heads/26.1/gradle.properties" -o /tmp/fabric-26.1-gradle.properties
curl -fsSL "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/refs/heads/26.1/settings.gradle" -o /tmp/fabric-26.1-settings.gradle
```

Or use the webFetch tool to read them directly from GitHub raw URLs.

**Critical files to check**:
- `build.gradle` — plugin version, dependency declarations, Java version
- `gradle.properties` — `loom_version`, `loader_version`, `fabric_api_version`
- `settings.gradle` — plugin management repositories (needed for SNAPSHOT loom)

### Forge — download the MDK zip directly

```bash
# Download the official Forge MDK zip
curl -fsSL "https://maven.minecraftforge.net/net/minecraftforge/forge/26.1.2-64.0.4/forge-26.1.2-64.0.4-mdk.zip" \
    -o /tmp/forge-26.1.2-mdk.zip

# List contents
unzip -l /tmp/forge-26.1.2-mdk.zip

# Extract key files
unzip -p /tmp/forge-26.1.2-mdk.zip build.gradle
unzip -p /tmp/forge-26.1.2-mdk.zip gradle.properties
unzip -p /tmp/forge-26.1.2-mdk.zip settings.gradle
unzip -p /tmp/forge-26.1.2-mdk.zip gradle/wrapper/gradle-wrapper.properties
unzip -p /tmp/forge-26.1.2-mdk.zip src/main/resources/META-INF/mods.toml
unzip -p /tmp/forge-26.1.2-mdk.zip src/main/java/com/example/examplemod/ExampleMod.java
```

The Forge MDK zip URL pattern is:
`https://maven.minecraftforge.net/net/minecraftforge/forge/<MC>-<FORGE>/forge-<MC>-<FORGE>-mdk.zip`

### NeoForge — clone the official MDK repo

```bash
# NeoForge MDKs are on GitHub under the NeoForgeMDKs organization
# URL pattern: https://github.com/NeoForgeMDKs/MDK-<MC>-ModDevGradle

curl -fsSL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/main/build.gradle" -o /tmp/neo-26.1.2-build.gradle
curl -fsSL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/main/gradle.properties" -o /tmp/neo-26.1.2-gradle.properties
curl -fsSL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/main/settings.gradle" -o /tmp/neo-26.1.2-settings.gradle
curl -fsSL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/main/src/main/java/com/example/examplemod/ExampleMod.java" -o /tmp/neo-26.1.2-ExampleMod.java
```

---

## Step 3 — Read and understand the MDKs

Before touching any template files, read every file you downloaded and note
the key differences from the previous version range.

**What to look for**:
- Plugin IDs and versions
- Gradle wrapper version
- Java toolchain version
- Dependency declarations (what changed?)
- Constructor signatures in ExampleMod.java
- Any new annotation processors
- Any removed APIs

**Findings for 26.1.2**:

### Fabric changes
| Property | 1.21.x | 26.1.2 |
|----------|--------|--------|
| loom plugin | `fabric-loom` 1.15.3 | `net.fabricmc.fabric-loom` 1.16-SNAPSHOT |
| settings.gradle | No Fabric Maven repo | Needs `https://maven.fabricmc.net/` for SNAPSHOT |
| Minecraft dep | `modImplementation` | `implementation` (no remapping) |
| Fabric API key | `fabric_version` | `fabric_api_version` |
| Mappings | Yarn | None (Mojang official, unobfuscated) |
| Java | 21 | 25 |

### Forge changes
| Property | 1.21.x | 26.1.2 |
|----------|--------|--------|
| ForgeGradle | `7.0.6` (pinned) | `[7.0.17,8)` (range) |
| mappings block | `mappings channel: 'official', version: '...'` | **REMOVED** |
| @SubscribeEvent | `net.minecraftforge.eventbus.api` | `net.minecraftforge.eventbus.api.listener` |
| Constructor | `ExampleMod(FMLJavaModLoadingContext context)` | Same, but `context.getModBusGroup()` |
| New dep | — | `annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.1'` |
| Gradle | 9.3.1 | 9.3.1 |

### NeoForge changes
| Property | 1.21.x | 26.1.2 |
|----------|--------|--------|
| Plugin | `net.neoforged.gradle.userdev` | `net.neoforged.moddev` (ModDevGradle) |
| Constructor | `ExampleMod(FMLJavaModLoadingContext context)` | `ExampleMod(IEventBus modEventBus, ModContainer modContainer)` |
| FMLJavaModLoadingContext | Available | **REMOVED** |
| mods.toml location | `src/main/resources/META-INF/` | `src/main/resources/META-INF/` (static, not template) |
| Gradle | 8.13 | 9.2.1 |

---

## Step 4 — Create the version folder and templates

### 4a. Create the folder structure

```
<version-range>/
  build_adapter.py          ← copy from previous range, update folder name
  fabric/
    PROVENANCE.md           ← document source, date, versions
    template/
      build.gradle          ← from official Fabric example mod
      gradle.properties     ← from official Fabric example mod
      settings.gradle       ← from official Fabric example mod (CRITICAL for SNAPSHOT)
      gradle/wrapper/gradle-wrapper.properties
      src/main/java/...
      src/main/resources/fabric.mod.json
      src/main/resources/modid.mixins.json
      TEMPLATE_NOTES.md
  forge/
    PROVENANCE.md
    template/
      build.gradle          ← from official Forge MDK zip
      gradle.properties     ← from official Forge MDK zip
      settings.gradle       ← from official Forge MDK zip
      gradle/wrapper/gradle-wrapper.properties
      src/main/java/...
      src/main/resources/META-INF/mods.toml
      TEMPLATE_NOTES.md
  neoforge/
    PROVENANCE.md
    template/
      build.gradle          ← from official NeoForge MDK repo
      gradle.properties     ← from official NeoForge MDK repo
      settings.gradle       ← from official NeoForge MDK repo
      gradle/wrapper/gradle-wrapper.properties
      src/main/java/...
      src/main/resources/META-INF/neoforge.mods.toml
      TEMPLATE_NOTES.md
```

### 4b. Write the build_adapter.py

```python
#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from modcompiler.adapters import run_range_adapter

if __name__ == "__main__":
    raise SystemExit(run_range_adapter("26.1-26.x"))  # ← update folder name
```

### 4c. Add the range to version-manifest.json

Add a new entry to the `ranges` array. Key fields:
- `folder` — must match the directory name exactly
- `adapter_family` — `fabric_split`, `forge_mods_toml`, or `neoforge_mods_toml`
- `anchor_version` — the version the template is built for
- `supported_versions` — exact versions the build system will accept
- `dependency_overrides` — per-version overrides for gradle.properties keys
- `java_rules` — Java version per MC version range

**Important**: `supported_versions` must list the exact version strings that
will appear in `version.txt`. The build system validates against this list.
If you put `1.17` but only `1.17.1` is in the list, the build fails.

---

## Step 5 — Verify the templates compile

**Never skip this step.** Create a minimal test mod bundle and run it through
the build workflow before declaring the templates done.

### 5a. Write a minimal test generator

```python
# scripts/generate_<version>_template_test.py
# Creates a minimal mod for each loader that just has an empty main class.
# See scripts/generate_26x_template_test.py for a complete example.
```

### 5b. Generate, commit, push, build

```bash
python3 scripts/generate_26x_template_test.py

git add scripts/ incoming/
git commit -m "Add 26.x template test bundle"
git push

python3 scripts/run_build.py incoming/template-test-26x.zip
```

### 5c. Read the build logs for failures

```bash
# After the build completes, check the summary
cat ModCompileRuns/run-*/artifacts/all-mod-builds/SUMMARY.md

# For failures, read the specific log
cat ModCompileRuns/run-*/artifacts/all-mod-builds/mods/<slug>/build.log | grep -A5 "error:"
```

---

## Step 6 — Fix failures

This is where most of the work happens. Each loader had unique issues for 26.1.

### Challenge 1: Fabric — loom SNAPSHOT not found

**Error**: `Plugin 'net.fabricmc.fabric-loom' version '1.16' was not found`

**Root cause**: `1.16` is not a valid stable release. The official example uses
`1.16-SNAPSHOT` which resolves from `https://maven.fabricmc.net/`. This repo
is declared in `settings.gradle` — which we initially copied incorrectly.

**Fix**: Use `loom_version=1.16-SNAPSHOT` and ensure `settings.gradle` declares
the Fabric Maven repo:

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

**Lesson**: Always copy `settings.gradle` from the official example. It's easy
to miss and causes cryptic "plugin not found" errors.

### Challenge 2: Fabric — FarmBlock cannot find symbol

**Error**: `error: cannot find symbol — import net.minecraft.world.level.block.FarmBlock`

**Root cause**: Minecraft classes weren't on the compile classpath. The
`fabric_api_version` property wasn't being set because the manifest used
`fabric_version` (wrong key) instead of `fabric_api_version` (correct key).
Without Fabric API, Minecraft classes aren't transitively available.

**Fix**: Update `version-manifest.json` dependency_overrides to use
`fabric_api_version` as the key, matching the template's `gradle.properties`.

**Lesson**: The manifest `dependency_overrides` keys must exactly match the
property names in `gradle.properties`. A wrong key silently fails — the
property isn't set, the dependency isn't resolved, and you get confusing
"class not found" errors.

### Challenge 3: Forge — mappings block causes build failure

**Error**: `Invalid MCP Dependency: mcp_config:26.1.2-... Could not find 'mappings' task`

**Root cause**: Our template had `mappings channel: 'official', version: '26.1.2'`
copied from the 1.21.x template. But Forge 26.1.2 removed obfuscation — there
are no MCP mappings. ForgeGradle 7 for 26.1.2 doesn't support the `mappings`
block at all.

**How discovered**: Downloaded the official Forge MDK zip and read `build.gradle`:

```bash
curl -fsSL "https://maven.minecraftforge.net/net/minecraftforge/forge/26.1.2-64.0.4/forge-26.1.2-64.0.4-mdk.zip" -o /tmp/forge-mdk.zip
unzip -p /tmp/forge-mdk.zip build.gradle
# → No mappings block anywhere in the file
```

**Fix**: Remove the `mappings` block entirely from the Forge 26.1 template.

**Lesson**: Always download the official MDK zip and compare it to your
template. Don't copy from the previous version range and assume the structure
is the same.

### Challenge 4: NeoForge — FMLJavaModLoadingContext removed

**Error**: `error: cannot find symbol — import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext`

**Root cause**: NeoForge 26.1 removed `FMLJavaModLoadingContext`. The new
constructor injection pattern uses `IEventBus` and `ModContainer` directly.

**How discovered**: Downloaded the official NeoForge MDK and read `ExampleMod.java`:

```bash
curl -fsSL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/main/src/main/java/com/example/examplemod/ExampleMod.java"
# → public ExampleMod(IEventBus modEventBus, ModContainer modContainer) { ... }
```

**Fix**: Update the NeoForge ExampleMod.java to use the new constructor:

```java
// Old (1.21.x)
public ExampleMod(FMLJavaModLoadingContext context) {
    IEventBus modEventBus = context.getModEventBus();
    ...
}

// New (26.1+)
public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
    // modEventBus is injected directly by FML
    ...
}
```

**Lesson**: Always read `ExampleMod.java` from the official MDK. Constructor
signatures change between major versions.

### Challenge 5: NeoForge — duplicate neoforge.mods.toml

**Error**: `Entry META-INF/neoforge.mods.toml is a duplicate but no duplicate handling strategy has been set`

**Root cause**: The official NeoForge MDK uses a `generateModMetadata` task
that expands `src/main/templates/META-INF/neoforge.mods.toml` into
`build/generated/sources/modMetadata/`. But the build adapter also writes a
`neoforge.mods.toml` to `src/main/resources/META-INF/`. Two sources → duplicate.

**Fix**: Remove the `generateModMetadata` task from the template's `build.gradle`
and keep only the static `src/main/resources/META-INF/neoforge.mods.toml` that
the build adapter writes to.

**Lesson**: The official MDK uses template expansion for IDE development
convenience. Our build system doesn't need it — the adapter handles property
substitution. Remove any task that conflicts with the adapter's file writes.

### Challenge 6: NeoForge — beta versions don't match Maven `+` wildcard

**Error**: `Could not find net.neoforged:neoforge:26.1.0.+:userdev in any repository`

**Root cause**: All NeoForge 26.1 versions have a `-beta` suffix (e.g.
`26.1.0.19-beta`). Maven's `+` wildcard only matches numeric suffixes, not
pre-release strings like `-beta`.

**How discovered**: Fetched the NeoForge Maven index:

```bash
curl -s "https://maven.neoforged.net/releases/net/neoforged/neoforge/" | grep "26\."
# → 26.1.0.1-beta/  26.1.0.2-beta/  ...  26.1.2.22-beta/
# No stable releases — all have -beta suffix
```

**Fix**: Use explicit beta version in `version-manifest.json` dependency_overrides:

```json
"26.1.2": {
    "neo_version": "26.1.2.22-beta",
    "minecraft_version": "26.1.2"
}
```

**Lesson**: Always check the Maven index before assuming `+` wildcards work.
Pre-release suffixes break wildcard resolution.

---

## Step 7 — Update documentation

After all templates are verified working:

1. Update `version-manifest.json` with the new range
2. Update `docs/IDE_AGENT_INSTRUCTION_SHEET.txt`:
   - Add to the supported version families list
   - Add to the target matrix table
3. Update `.github/workflows/ai-source-search.yml`:
   - Add version folder mapping for the new range
   - Add Java version detection rule
4. Write a `PROVENANCE.md` for each loader documenting the source MDK
5. Write `TEMPLATE_NOTES.md` for each loader with key API differences

---

## Complete API Differences Reference for 26.1.2

### Fabric 26.1.2

```groovy
// settings.gradle — REQUIRED for loom SNAPSHOT
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}

// gradle.properties
loom_version=1.16-SNAPSHOT
loader_version=0.19.2
fabric_api_version=0.146.1+26.1.2   // NOTE: fabric_api_version not fabric_version

// build.gradle — no mappings, implementation not modImplementation
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}
```

Mixin class names use Mojang mappings (same as Forge):
- `net.minecraft.world.level.block.FarmBlock` / `fallOn(...)`

### Forge 26.1.2

```groovy
// build.gradle — NO mappings block, range for ForgeGradle, new annotationProcessor
plugins {
    id 'net.minecraftforge.gradle' version '[7.0.17,8)'
}

minecraft {
    // No mappings block — obfuscation removed in 26.1
    runs { ... }
}

dependencies {
    implementation minecraft.dependency('net.minecraftforge:forge:26.1.2-64.0.4')
    annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.1'
}
```

EventBus 7 API changes:
```java
// @SubscribeEvent import changed
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;  // NOT eventbus.api

// Constructor uses getModBusGroup()
public ExampleMod(FMLJavaModLoadingContext context) {
    var modBusGroup = context.getModBusGroup();
    FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
}

// Cancellable game events use BUS.addListener(true, handler) returning boolean
BlockEvent.FarmlandTrampleEvent.BUS.addListener(
    /* alwaysCancelling = */ true,
    ExampleMod::onTrample
);
private static boolean onTrample(BlockEvent.FarmlandTrampleEvent event) {
    return true; // true = cancel
}
```

### NeoForge 26.1.2

```groovy
// build.gradle — ModDevGradle not NeoGradle
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

neoForge {
    version = project.neo_version  // 26.1.2.22-beta
    mods { "${mod_id}" { sourceSet(sourceSets.main) } }
}
```

Constructor injection (FMLJavaModLoadingContext removed):
```java
// Old (1.21.x)
public ExampleMod(FMLJavaModLoadingContext context) { ... }

// New (26.1+)
public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
    // modEventBus = mod lifecycle events
    // NeoForge.EVENT_BUS = game/world events
}
```

---

## Checklist for Adding a New Version

- [ ] Web search: confirm latest stable version and loader support
- [ ] Check Maven repos for exact version numbers (especially NeoForge beta)
- [ ] Download Fabric example mod from GitHub (correct branch)
- [ ] Download Forge MDK zip from maven.minecraftforge.net
- [ ] Download NeoForge MDK from github.com/NeoForgeMDKs
- [ ] Read all three `build.gradle` files and note differences from previous version
- [ ] Read all three `ExampleMod.java` files — constructor signatures change
- [ ] Create version folder with correct `build_adapter.py`
- [ ] Copy templates from official MDKs (don't copy from previous range)
- [ ] Add range to `version-manifest.json` with correct `supported_versions`
- [ ] Write minimal test mod generator script
- [ ] Run test build: `python3 scripts/run_build.py incoming/template-test-<version>.zip`
- [ ] Fix any failures (read build logs, compare to official MDK)
- [ ] Re-run test build until all three loaders pass
- [ ] Update `ai-source-search.yml` with new version mapping and Java version
- [ ] Update `docs/IDE_AGENT_INSTRUCTION_SHEET.txt` version list and target matrix
- [ ] Write PROVENANCE.md and TEMPLATE_NOTES.md for each loader
- [ ] Commit and push everything
