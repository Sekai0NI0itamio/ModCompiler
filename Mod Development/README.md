# Mod Development Workspace

This folder is the **local mod development workspace** for writing, building, testing, and publishing Minecraft mods. It is designed so that any AI coding IDE can immediately understand the purpose, structure, conventions, and workflows of every mod project inside it.

---

## Purpose

This workspace exists to:

1. **Write mod source code** in isolated per-mod directories
2. **Build mods locally** using shared Gradle engines with symlink-based isolation
3. **Test mods** by installing built JARs into Prism Launcher instances
4. **Publish mods to Modrinth** using the manual publish script (`scripts/publish_manual.py` in the ModCompiler repo root)

This workspace is **separate from** the GitHub Actions CI pipeline (`modcompiler/`, `aibasedversionupgrader/`). The CI system compiles mods in the cloud from zip bundles. This workspace is for **local development and iteration**.

---

## Workspace Structure

```
Mod Development/
├── 1.12.2-forge/          ← Minecraft 1.12.2 + Forge (ForgeGradle 2.3, Java 8)
├── 1.21.1-fabric/         ← Minecraft 1.21.1 + Fabric (Fabric Loom, Java 21)
└── README.md              ← This file
```

Each workspace subfolder is a **self-contained mod development environment** for a specific Minecraft version and mod loader. They share the same architectural pattern but differ in Gradle configuration, Java version, and loader-specific conventions.

---

## How Every Workspace Works — The Symlink Isolation System

All workspaces follow the same core architecture: an **engine + mods** split with **symlink-based build isolation**.

### Directory Layout (common to all workspaces)

```
<version-loader>/
├── engine/              ← Shared Gradle build engine (DO NOT edit manually)
│   ├── build.gradle     ← Template with placeholders (filled at build time, reset after)
│   ├── gradle.properties← Template with placeholders (filled at build time, reset after)
│   ├── gradlew / gradlew.bat
│   └── gradle/wrapper/
├── mods/                ← All mod source code lives here permanently
│   └── <mod-name>/
│       ├── mod.properties    ← Mod identity (modid, name, group, version)
│       └── src/              ← Java source + resources
├── output/              ← Built JAR files (latest version of each mod)
├── lib/                 ← Third-party reference mods (not built)
├── scripts/             ← Utility scripts and patches
├── build_mod.sh         ← Build a single mod
├── build_all.sh         ← Build all mods sequentially (1.12.2-forge only)
├── new_mod.sh           ← Scaffold a new mod from template (1.12.2-forge only)
└── .gitignore
```

### Build Process — Step by Step

When you run `./build_mod.sh <mod-name>`:

1. **Read identity** — The script reads `mods/<mod-name>/mod.properties` to get `modid`, `name`, `group`, `version`
2. **Create symlink** — `engine/src/` → `mods/<mod-name>/src/` (so Gradle only sees one mod's source)
3. **Patch build config** — Placeholders in `engine/build.gradle` or `engine/gradle.properties` are replaced with the mod's actual values
4. **Run Gradle** — `cd engine && ./gradlew build` — compiles only the symlinked source
5. **Copy JAR** — The built `<Mod-Name>-<version>.jar` is copied to `output/`
6. **Cleanup** — The symlink is removed and the build config is reset to its placeholder state

**Critical guarantee: Only one mod's source code is ever visible to Gradle at a time.** This makes it impossible for Mod A's classes to leak into Mod B's JAR.

### mod.properties Format

Every mod **must** have a `mod.properties` file in its root directory:

```properties
modid=mymodid
name=My-Mod
group=com.example.mymodid
version=1.0.0
```

| Field     | Description                                       | Example                  |
|-----------|---------------------------------------------------|--------------------------|
| `modid`   | Mod ID (lowercase, no spaces)                     | `mymodid`                |
| `name`    | JAR filename / display name (kebab-case)          | `My-Mod`                 |
| `group`   | Java package group for Gradle                     | `com.example.mymodid`    |
| `version` | Mod version string                                | `1.0.0`                  |

Optional fields (1.12.2-forge only):

| Field              | Description                                          | Example                                    |
|--------------------|------------------------------------------------------|--------------------------------------------|
| `fml_core_plugin`  | Coremod class for ASM/bytecode transformation        | `asd.itamio.multiplayerlikesingleplayer.core.MLSPLoadingPlugin` |

---

## Workspace: 1.12.2-forge

**Minecraft 1.12.2 · Forge · ForgeGradle 2.3 · Java 8**

### Requirements

- **Java 8** — auto-detected via `/usr/libexec/java_home -v 1.8` on macOS
- If not installed, set `JAVA_HOME` manually: `export JAVA_HOME=/path/to/jdk8`

### Build Config Patching

The build script uses a Python one-liner to replace placeholders in `engine/build.gradle`:

| Placeholder        | Replaced With       |
|--------------------|---------------------|
| `__MOD_VERSION__`  | `version` from mod.properties |
| `__MOD_GROUP__`    | `group` from mod.properties   |
| `__MOD_NAME__`     | `name` from mod.properties    |

If `fml_core_plugin` is set in `mod.properties`, the script injects a JAR manifest block:

```gradle
jar {
    manifest {
        attributes 'FMLCorePlugin': '<core_plugin_class>'
        attributes 'FMLCorePluginContainsFMLMod': 'true'
    }
}
```

### Commands

```bash
./build_mod.sh <mod-name>       # Build a single mod
./build_mod.sh                  # List all available mods
./build_all.sh                  # Build all mods sequentially
./new_mod.sh <name> <modid> [group] [version]  # Create a new mod scaffold
```

### Resource Structure

```
mods/<mod-name>/src/main/resources/
├── mcmod.info
├── pack.mcmeta
└── assets/<modid>/
    ├── blockstates/
    ├── models/
    │   ├── block/
    │   └── item/
    ├── textures/
    │   ├── block/
    │   └── items/
    └── lang/
        └── en_us.lang
```

### Current Mods (22)

| Mod Directory               | modid                        | Display Name                   | Notes |
|-----------------------------|------------------------------|--------------------------------|-------|
| absolute-darkness           | absolutedarkness             | Absolute-Darkness              |       |
| all-most-hate-the-sun       | sunburn                      | All-Most-Hate-The-SUN          |       |
| always-rain                 | alwaysrain                   | Always-Rain                    |       |
| auto-replant                | autoreplant                  | Auto-Replant                   |       |
| bot-helpers                 | bothelpers                   | Bot-Helpers                    |       |
| day-counter                 | daycounter                   | Day-Counter                    |       |
| easy-building               | easybuilding                 | Easy-Building                  |       |
| heart-system                | heartsystem                  | Heart-System                   |       |
| hostile-mobs                | hostilemobs                  | Hostile-Mobs                   |       |
| instant-hoppers             | instanthoppers               | Instant-Hoppers                |       |
| instant-leaf-decay          | instantleafdecay             | Instant-Leaf-Decay             |       |
| longer-day                  | longer_day                   | Longer Day                     | Published to Modrinth |
| load-my-world-properly      | loadmyworldproperly          | Load-My-World-PROPERLY         |       |
| mob-vision                  | mobvision                    | Mob-Vision                     |       |
| multiplayer-in-singleplayer | multiplayerlikesingleplayer  | MultiplayerInSingleplayer      | Coremod (IFMLLoadingPlugin + ASM) |
| no-hostile-mobs             | nohostilemobs                | No-Hostile-Mobs                |       |
| seed-protect                | seedprotect                  | Seed-Protect                   |       |
| signs-dont-break            | signsdontbreak               | Signs-Dont-Break               | Archived |
| strong-mobs                 | strongmobs                   | Strong-Mobs                    |       |
| strong-seeds                | strongseeds                  | Strong-Seeds                   |       |
| tnt-duper                   | tntduper                     | TNT-Duper                      |       |
| trainlize-furnance-minecart | trainlize_furnance_minecart  | Trainlize Furnance Minecart    | Coremod + network packets |
| wild-fire                   | wildfire                     | Wild-Fire                      |       |

### 1.12.2 Forge-Specific Technical Notes

#### SRG vs MCP Field Names (CRITICAL)

In Minecraft 1.12.2, production (obfuscated) builds use **SRG names** while development uses **MCP names**. When using `ReflectionHelper.findField()`, you MUST provide both names:

```java
// CORRECT — provides both MCP and SRG names
Field fuelF = ReflectionHelper.findField(EntityMinecartFurnace.class, "fuel", "field_94110_c");

// WRONG — only MCP name, will crash in production
Field fuelF = ReflectionHelper.findField(EntityMinecartFurnace.class, "fuel");
```

Never assume SRG names from other MC versions — they change between versions.

#### Event Registration

`@EventBusSubscriber` with static `@SubscribeEvent` methods is unreliable in 1.12.2 Forge. Use **manual registration** instead:

```java
@EventHandler
public void init(FMLInitializationEvent event) {
    MinecraftForge.EVENT_BUS.register(new MyHandler());
}
```

#### Client-Server Communication

For key presses or client-side input that needs to affect server-side logic, you MUST use network packets (`SimpleNetworkWrapper`). The client is not trusted by the server.

#### Client-Side Player View Control

In 1.12.2, the client is authoritative over the player's view direction. Setting `EntityPlayerMP.rotationYaw` on the server has no visible effect. Use a client-side tick handler (`@SideOnly(Side.CLIENT)`) and directly modify `Minecraft.getMinecraft().player.rotationYaw`.

#### Entity Lifecycle Methods

The method is `isEntityAlive()`, NOT `isAlive()` (which was added in later versions).

#### Action Bar Messages

```java
player.sendStatusMessage(new TextComponentString("\u00a7a\u25b6 Toggle: ON"), true);
// \u00a7 = § (color code), true = action bar (not chat)
```

Color codes: `\u00a7a` = green, `\u00a7c` = red, `\u00a76` = gold

#### Coremod Development

If a mod needs bytecode transformation (ASM), it requires a coremod setup:

1. Create an `IFMLLoadingPlugin` implementation class
2. Add `fml_core_plugin=<class>` to `mod.properties`
3. The build script automatically injects the JAR manifest attributes

---

## Workspace: 1.21.1-fabric

**Minecraft 1.21.1 · Fabric · Fabric Loom · Java 21**

### Requirements

- **Java 21** — required by Fabric Loom for Minecraft 1.21.1

### Build Config Patching

The build script uses `sed` to replace values in `engine/gradle.properties`:

| Property             | Replaced With       |
|----------------------|---------------------|
| `archives_base_name` | `name` from mod.properties   |
| `maven_group`        | `group` from mod.properties  |
| `mod_version`        | `version` from mod.properties |

### Commands

```bash
./build_mod.sh <mod-name>       # Build a single mod
./build_mod.sh                  # List all available mods
```

### Fabric-Specific: Split Source Sets

Fabric Loom supports `splitEnvironmentSourceSets()` which separates code into:

- `src/main/` — Common code (runs on both client and server)
- `src/client/` — Client-only code (rendering, input, client-side mixins)

Resource files follow the same split:
- `src/main/resources/` — `fabric.mod.json`, common mixins, assets
- `src/client/resources/` — client-side mixin configs

### Resource Structure

```
mods/<mod-name>/src/
├── main/
│   ├── java/com/example/modid/
│   │   └── MyMod.java
│   └── resources/
│       ├── fabric.mod.json
│       └── <modid>.mixins.json
└── client/
    ├── java/com/example/modid/client/
    │   └── MyModClient.java
    └── resources/
        └── <modid>.client.mixins.json
```

### macOS Double-Click Shortcuts

- `Setup Instance.command` — Creates a Prism Launcher instance for 1.21.1 Fabric
- `Build & Test.command` — Interactive menu: pick a mod → build it → install to Prism Launcher → launch the game

### Current Mods (1)

| Mod Directory | modid | Display Name | Notes |
|---------------|-------|--------------|-------|
| stop          | stop  | STOP         | Privacy mod — serves decoy folders to servers. Uses split source sets, mixins, JSON config. |

---

## Publishing Mods to Modrinth

The **primary publish method** for this workspace is `scripts/publish_manual.py` located in the ModCompiler repository root. This is a manual, fully-controlled publish flow — you decide what gets published, when, and with what metadata.

### Prerequisites

1. A **Modrinth API token** — discovered in this order:
   - `--modrinth-token` command-line flag
   - `MODRINTH_TOKEN` environment variable
   - `secrets.txt` file in the parent directory of the ModCompiler repo (first line = token)
2. A **built JAR file** — from `output/` after running `build_mod.sh`
3. **Two JSON metadata files** — `modrinth.project.json` and `modrinth.version.json`

### Full Publishing Workflow

```
1. Build mod locally:     ./build_mod.sh my-mod
2. Create bundle dir:     AutoCreateModrinthBundles/my-mod-1.0.0/
3. Write project JSON:    modrinth.project.json (title, description, categories)
4. Write version JSON:    modrinth.version.json (game_versions, loaders, changelog)
5. Copy JAR to bundle:    input/My-Mod-1.0.0.jar
6. (Optional) Add icon:   icon.webp or icon.png in bundle root
7. Dry run:               python3 scripts/publish_manual.py --bundle-dir ... --jar ... --dry-run
8. Publish:               python3 scripts/publish_manual.py --bundle-dir ... --jar ...
9. Review on Modrinth:    https://modrinth.com/mod/<slug>
```

### Step 1: Build the Mod

```bash
cd "Mod Development/1.12.2-forge"
./build_mod.sh my-mod
```

The built JAR appears in `output/My-Mod-1.0.0.jar`.

### Step 2: Create the Bundle Directory

From the ModCompiler repository root:

```bash
mkdir -p AutoCreateModrinthBundles/my-mod-1.0.0/input
```

Bundle structure:

```
AutoCreateModrinthBundles/<mod-name>-<version>/
├── modrinth.project.json    # Project metadata (title, description, categories, etc.)
├── modrinth.version.json    # Version metadata (game versions, loaders, changelog)
├── icon.webp                # (Optional) Project icon
└── input/
    └── <Mod-Name>-<version>.jar   # The built JAR
```

### Step 3: Create modrinth.project.json

This file defines the Modrinth project listing:

```json
{
  "slug": "my-mod",
  "title": "My Mod",
  "description": "Short description of what the mod does.",
  "body": "# My Mod\n\nFull markdown description of the mod.\n\n## Features\n- Feature 1\n- Feature 2",
  "categories": ["game-mechanics", "utility"],
  "client_side": "optional",
  "server_side": "required",
  "project_type": "mod",
  "license_id": "MIT",
  "status": "draft"
}
```

Key fields:
- `slug` — URL identifier (lowercase, hyphens only). Must be unique on Modrinth.
- `title` — Display name shown on the project page
- `description` — Short one-line summary
- `body` — Full markdown description for the project page
- `categories` — Modrinth category tags. Common: `game-mechanics`, `utility`, `adventure`, `storage`, `transportation`
- `client_side` / `server_side` — One of `required`, `optional`, `unsupported`
- `license_id` — SPDX identifier. Common: `MIT`, `Apache-2.0`, `All-Rights-Reserved`
- `status` — `draft` (not visible), `listed` (public), `unlisted` (link-only), `private`

**Important:** Remove empty URL fields (`issues_url`, `source_url`, `wiki_url`, `donation_urls`) — the Modrinth API rejects empty strings.

### Step 4: Create modrinth.version.json

This file defines the version being uploaded:

```json
{
  "version_number": "1.0.0",
  "name": "My Mod 1.0.0",
  "changelog": "- Initial release\n- Feature descriptions",
  "version_type": "release",
  "status": "listed",
  "game_versions": ["1.12.2"],
  "loaders": ["forge"],
  "featured": false,
  "dependencies": [],
  "file_parts": ["file"]
}
```

Key fields:
- `version_type` — `release`, `beta`, or `alpha`
- `game_versions` — Array of exact Minecraft version strings (e.g. `["1.12.2"]` or `["1.21.1"]`)
- `loaders` — Array of loader names (e.g. `["forge"]` or `["fabric"]`)
- `file_parts` — Must be `["file"]` — this maps to the JAR file in the upload

### Step 5: Copy the JAR

```bash
cp "Mod Development/1.12.2-forge/output/My-Mod-1.0.0.jar" \
   AutoCreateModrinthBundles/my-mod-1.0.0/input/
```

### Step 6: Run the Publish Script

From the ModCompiler repository root:

```bash
# Dry run first (no API calls made)
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/my-mod-1.0.0 \
  --jar "AutoCreateModrinthBundles/my-mod-1.0.0/input/My-Mod-1.0.0.jar" \
  --dry-run

# Actually publish
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/my-mod-1.0.0 \
  --jar "AutoCreateModrinthBundles/my-mod-1.0.0/input/My-Mod-1.0.0.jar"
```

### Publish Script Options

| Option | Description | Default |
|--------|-------------|---------|
| `--bundle-dir <path>` | Directory containing `modrinth.project.json` and `modrinth.version.json` | Required |
| `--jar <path>` | Path to the mod JAR file | Required |
| `--icon <path>` | Path to project icon (png/webp) | Auto-detected from bundle dir |
| `--modrinth-token <token>` | Modrinth API token | Auto-discovered |
| `--dry-run` | Preview what would be done without making API calls | Off |
| `--project-status <status>` | Project visibility: `draft`, `listed`, `unlisted`, `private` | `draft` |
| `--version-status <status>` | Version visibility: `listed`, `unlisted`, `draft` | `listed` |
| `--update-only` | Only update existing project metadata (source URLs, icon, etc.) without uploading a version | Off |

### What the Publish Script Does

1. Reads `modrinth.project.json` and `modrinth.version.json`
2. Discovers the Modrinth API token
3. Checks if a project with that slug already exists on Modrinth
4. If **new project**: creates a draft project with the initial version embedded (Modrinth requires at least one version when creating a project), then uploads the icon if provided
5. If **existing project**: creates a new version under the existing project
6. Prints the project URL and version ID on success

### Adding a New Version to an Existing Mod

If the project already exists on Modrinth, just create a new `modrinth.version.json` with an updated version number and run the same command. The script detects the existing project by slug and adds the version to it.

### Updating Project Metadata Only

To update a project's description, icon, or links without uploading a new version:

```bash
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/my-mod-1.0.0 \
  --update-only
```

### Common Issues

| Error | Cause | Fix |
|-------|-------|-----|
| "Invalid body: issues_url" | Empty URL fields in `modrinth.project.json` | Remove empty URL fields (`issues_url`, `source_url`, `wiki_url`, `donation_urls`) |
| "Duplicate mod" | Multiple JARs with same modid in Minecraft mods folder | Delete old JARs before adding new ones |
| Token not found | No `secrets.txt` or `MODRINTH_TOKEN` env var | Create `secrets.txt` in parent dir or pass `--modrinth-token` |

### Example: Publishing the "Always-Rain" Mod (1.12.2 Forge)

```bash
# 1. Build
cd "Mod Development/1.12.2-forge"
./build_mod.sh always-rain

# 2. Create bundle
cd ../../../
mkdir -p AutoCreateModrinthBundles/always-rain-1.0.0/input
cp "Mod Development/1.12.2-forge/output/Always-Rain-1.0.0.jar" \
   AutoCreateModrinthBundles/always-rain-1.0.0/input/

# 3. Write modrinth.project.json
cat > AutoCreateModrinthBundles/always-rain-1.0.0/modrinth.project.json << 'EOF'
{
  "slug": "always-rain",
  "title": "Always Rain",
  "description": "Forces rain weather in your world.",
  "body": "# Always Rain\n\nForces rain weather in your world. Simple and lightweight.",
  "categories": ["game-mechanics"],
  "client_side": "optional",
  "server_side": "required",
  "project_type": "mod",
  "license_id": "MIT",
  "status": "draft"
}
EOF

# 4. Write modrinth.version.json
cat > AutoCreateModrinthBundles/always-rain-1.0.0/modrinth.version.json << 'EOF'
{
  "version_number": "1.0.0",
  "name": "Always Rain 1.0.0",
  "changelog": "- Initial release",
  "version_type": "release",
  "status": "listed",
  "game_versions": ["1.12.2"],
  "loaders": ["forge"],
  "featured": false,
  "dependencies": [],
  "file_parts": ["file"]
}
EOF

# 5. Publish
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/always-rain-1.0.0 \
  --jar "AutoCreateModrinthBundles/always-rain-1.0.0/input/Always-Rain-1.0.0.jar"
```

### Example: Publishing the "STOP" Mod (1.21.1 Fabric)

```bash
# 1. Build
cd "Mod Development/1.21.1-fabric"
./build_mod.sh stop

# 2. Create bundle
cd ../../../
mkdir -p AutoCreateModrinthBundles/stop-1.0.0/input
cp "Mod Development/1.21.1-fabric/output/STOP-1.0.0.jar" \
   AutoCreateModrinthBundles/stop-1.0.0/input/

# 3. Write modrinth.project.json (same format, different categories/loaders)
# 4. Write modrinth.version.json with "game_versions": ["1.21.1"], "loaders": ["fabric"]

# 5. Publish
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/stop-1.0.0 \
  --jar "AutoCreateModrinthBundles/stop-1.0.0/input/STOP-1.0.0.jar"
```

---

## Rules for AI Coding IDEs

When working in this workspace, follow these rules strictly:

### Build System Rules

1. **Always use `build_mod.sh`** — never run `./gradlew build` manually inside `engine/`
2. **Never put source files directly in `engine/src/`** — that directory is managed by the build script via symlinks
3. **Each mod must have its own `mod.properties`** — without it, the build script cannot identify the mod
4. **Each mod's `src/` must be self-contained** — include your own `mcmod.info`/`fabric.mod.json`, `pack.mcmeta`, and `assets/`
5. **Do not share packages between mods** — each mod should use a unique Java package

### When Creating a New Mod

1. Use `./new_mod.sh` (1.12.2-forge) or manually create the directory structure
2. The mod directory name in `mods/` should be kebab-case (e.g., `my-cool-mod`)
3. The `modid` should be lowercase with no spaces (e.g., `mycoolmod`)
4. The `group` should be a reverse-domain Java package (e.g., `asd.itamio.mycoolmod`)
5. The `name` should be kebab-case matching the JAR output name (e.g., `My-Cool-Mod`)

### When Editing an Existing Mod

1. Only edit files inside `mods/<mod-name>/`
2. Never modify `engine/` files — they are templates that get patched at build time
3. After editing, build with `./build_mod.sh <mod-name>` to verify compilation
4. Check `output/` for the built JAR

### When Publishing a Mod

1. Build the mod first with `./build_mod.sh <mod-name>`
2. Create a bundle directory under `AutoCreateModrinthBundles/` in the ModCompiler root
3. Write `modrinth.project.json` and `modrinth.version.json` — remove empty URL fields
4. Copy the JAR into the bundle's `input/` directory
5. Always run with `--dry-run` first to verify
6. Use `--project-status draft` for initial publishes, review on Modrinth before setting to `listed`

### Version-Specific Conventions

| Workspace | Java Version | Loader | Resource Metadata | Entrypoint |
|-----------|-------------|--------|-------------------|------------|
| 1.12.2-forge | Java 8 | Forge | `mcmod.info` | `@Mod` annotation on main class |
| 1.21.1-fabric | Java 21 | Fabric | `fabric.mod.json` | `ModInitializer` interface in `fabric.mod.json` entrypoints |

---

## Relationship to the Rest of ModCompiler

This workspace is the **upstream source** for mods. The broader ModCompiler system provides:

| Component | Purpose | Connection to This Workspace |
|-----------|---------|------------------------------|
| `modcompiler/` | CI build pipeline (GitHub Actions) | Mods built here can be packaged into zips for `incoming/` to build across all MC versions |
| `aibasedversionupgrader/` | AI-powered mod porting agent | Can port mods developed here to new MC versions |
| `scripts/publish_manual.py` | Manual Modrinth publishing | **Primary publish method for this workspace** |
| `modcompiler/auto_create_modrinth.py` | AI-powered Modrinth listing generation | Alternative: place JAR + source in `ToBeUploaded/` for AI-generated listings |
| `dif/` | Knowledge base of version-specific API differences | Reference when porting mods between versions |
| `version-manifest.json` | Source of truth for all MC version ranges | Defines which versions/loaders are supported |
