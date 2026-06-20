# 1.12.2 Forge Mod Development System

## Requirements

- **Java 8** (required by ForgeGradle 2.3 for Minecraft 1.12.2)
  - The build scripts auto-detect Java 8 via `/usr/libexec/java_home -v 1.8` on macOS
  - If you have Java 8 installed, the script will use it automatically
  - If not, install Eclipse Temurin JDK 8 or set `JAVA_HOME` manually:
    ```bash
    export JAVA_HOME=/path/to/jdk8
    ./build_mod.sh my-mod
    ```

## Overview

This directory uses an **isolated mod build system** that allows you to develop and build multiple mods simultaneously without any risk of one mod's code leaking into another mod's build.

## Directory Structure

```
1.12.2-forge/
├── engine/              # Shared Gradle build engine (do NOT edit manually)
│   ├── build.gradle     # Template build file (placeholders are filled at build time)
│   ├── gradle.properties
│   ├── gradlew / gradlew.bat
│   └── gradle/wrapper/
├── mods/                # All mod source code lives here permanently
│   ├── easy-building/
│   │   ├── mod.properties    # Mod metadata (modid, name, group, version)
│   │   └── src/
│   │       └── main/
│   │           ├── java/     # Java source code
│   │           └── resources/ # mcmod.info, assets, pack.mcmeta
│   ├── always-rain/
│   ├── hostile-mobs/
│   └── ... (one folder per mod)
├── output/              # Built JAR files (one per mod, latest version only)
├── lib/                 # Third-party reference mods (not built by this system)
├── scripts/             # Utility scripts and patches
├── build_mod.sh         # Build a single mod
├── build_all.sh         # Build all mods
├── new_mod.sh           # Create a new mod from a template
└── .gitignore
```

## How It Works

### Isolation Guarantee

When you run `./build_mod.sh my-mod`, the following happens:

1. The script reads `mods/my-mod/mod.properties` to get the mod's identity
2. A **symlink** is created: `engine/src/` → `mods/my-mod/src/`
3. `engine/build.gradle` is temporarily configured with the mod's name, group, and version
4. Gradle builds only the source code that the symlink points to
5. The built JAR is copied to `output/`
6. The symlink is removed and `build.gradle` is reset to its template state

**Only one mod's source code is ever visible to the Gradle build at a time.** This means it is impossible for Mod A's classes to end up in Mod B's JAR.

### mod.properties Format

Every mod must have a `mod.properties` file in its root directory:

```properties
modid=easybuilding
name=Easy-Building
group=asd.itamio.easybuilding
version=1.0.0
```

| Field    | Description                                      | Example                  |
|----------|--------------------------------------------------|--------------------------|
| `modid`  | Forge mod ID (lowercase, no spaces)              | `easybuilding`           |
| `name`   | JAR filename / display name (kebab-case)         | `Easy-Building`          |
| `group`  | Java package group for Gradle                    | `asd.itamio.easybuilding`|
| `version`| Mod version string                               | `1.0.0`                  |

## Commands

### Build a Single Mod

```bash
./build_mod.sh <mod-name>
```

Example:
```bash
./build_mod.sh easy-building
```

This builds the mod located in `mods/easy-building/` and places the JAR in `output/`.

If you run it without arguments, it shows all available mods:
```bash
./build_mod.sh
```

### Build All Mods

```bash
./build_all.sh
```

This iterates through every mod in `mods/` that has a `mod.properties` file and builds them one at a time. A summary is printed at the end showing which mods succeeded and which failed.

### Create a New Mod

```bash
./new_mod.sh <mod-name> <modid> [group] [version]
```

Example:
```bash
./new_mod.sh my-cool-mod mycoolmod
./new_mod.sh super-pickaxe superpickaxe com.example.superpickaxe 2.0.0
```

This creates:
- `mods/<mod-name>/mod.properties`
- `mods/<mod-name>/src/main/java/<group>/Main.java` (with a basic `@Mod` class)
- `mods/<mod-name>/src/main/resources/mcmod.info`
- `mods/<mod-name>/src/main/resources/pack.mcmeta`
- `mods/<mod-name>/src/main/resources/assets/<modid>/` (empty, for textures/models)

## Preventing Cross-Contamination

The most important rule: **each mod lives in its own isolated directory under `mods/`**.

### Why mods cannot leak into each other

The build system uses a **symlink** to connect a single mod's source to the Gradle engine. At build time:

- `engine/src/` points to **only one** mod's `src/` directory
- Gradle only sees the files reachable through that symlink
- After the build completes, the symlink is removed immediately
- The next mod gets its own fresh symlink

This is fundamentally different from the old system where all mods shared a single `src/` directory and had to be manually swapped out (risking leftover files from previous builds).

### Checklist for safe mod development

1. **Always use `build_mod.sh`** — never run `./gradlew build` manually inside `engine/`
2. **Never put source files directly in `engine/src/`** — that directory is managed by the build script
3. **Each mod must have its own `mod.properties`** — without it, the build script cannot identify the mod
4. **Each mod's `src/` must be self-contained** — include your own `mcmod.info`, `pack.mcmeta`, and `assets/`
5. **Do not share packages between mods** — each mod should use a unique Java package (e.g., `asd.itamio.easybuilding` vs `asd.itamio.hostilemobs`)

### What happens if I add a file to the wrong mod?

If you accidentally put a Java file in `mods/easy-building/src/main/java/asd/itamio/hostilemobs/`, that file will be compiled into the Easy-Building JAR. The build system cannot detect misplaced packages — it trusts that each mod's `src/` directory only contains that mod's code. Always verify your package structure matches the mod you're working on.

## Adding Assets (Textures, Models, etc.)

Place assets in the standard Forge resource structure:

```
mods/my-mod/src/main/resources/
├── mcmod.info
├── pack.mcmeta
└── assets/
    └── mymodid/
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

## Updating Forge Version

Edit `engine/gradle.properties`:

```properties
forge_version=1.12.2-14.23.5.2847
mcp_version=stable_39
```

This applies to all mods built through the engine.

## Migrating from the Old System

The old system had these problems:
- Single `src/` directory shared by all mods (destructive — had to delete old mod before adding new one)
- `ReadyMods/` and `ModCollection/` contained duplicate JARs
- `.gradle/` and `build/` caches were tracked in git
- Mod-specific build scripts (`build_area_dig.sh`, `build_no_hostile_mobs.sh`)

All mod sources have been migrated to `mods/`. If you find a mod that wasn't migrated, create a new directory under `mods/` with the proper structure and a `mod.properties` file.

## Current Mods

| Mod Directory                    | modid                           | Display Name                    | Notes |
|----------------------------------|---------------------------------|---------------------------------|-------|
| absolute-darkness                | absolutedarkness                | Absolute-Darkness               |       |
| all-most-hate-the-sun            | sunburn                         | All-Most-Hate-The-SUN           |       |
| always-rain                      | alwaysrain                      | Always-Rain                     |       |
| auto-replant                     | autoreplant                     | Auto-Replant                    |       |
| bot-helpers                      | bothelpers                      | Bot-Helpers                     |       |
| day-counter                      | daycounter                      | Day-Counter                     |       |
| easy-building                    | easybuilding                    | Easy-Building                   |       |
| heart-system                     | heartsystem                     | Heart-System                    |       |
| hostile-mobs                     | hostilemobs                     | Hostile-Mobs                    |       |
| instant-hoppers                  | instanthoppers                  | Instant-Hoppers                 |       |
| instant-leaf-decay               | instantleafdecay                | Instant-Leaf-Decay              |       |
| longer-day                       | longer_day                      | Longer Day                      | Published to Modrinth |
| load-my-world-properly           | loadmyworldproperly             | Load-My-World-PROPERLY          |       |
| mob-vision                       | mobvision                       | Mob-Vision                      |       |
| multiplayer-in-singleplayer      | multiplayerlikesingleplayer     | MultiplayerInSingleplayer       | Coremod (IFMLLoadingPlugin) |
| no-hostile-mobs                  | nohostilemobs                   | No-Hostile-Mobs                 |       |
| seed-protect                     | seedprotect                     | Seed-Protect                    |       |
| signs-dont-break                 | signsdontbreak                  | Signs-Dont-Break                | Archived |
| strong-mobs                      | strongmobs                      | Strong-Mobs                     |       |
| strong-seeds                     | strongseeds                     | Strong-Seeds                    |       |
| tnt-duper                        | tntduper                        | TNT-Duper                       |       |
| trainlize-furnance-minecart      | trainlize_furnance_minecart     | Trainlize Furnance Minecart     | Coremod + network packets |
| wild-fire                        | wildfire                        | Wild-Fire                       |       |

## Key Technical Lessons for AI IDE Agents

This section documents the critical technical knowledge that AI IDE agents must understand when developing mods in this workspace. These lessons were learned through real debugging sessions and must be followed to avoid repeating the same mistakes.

### 1.12.2 Forge-Specific API Knowledge

#### SRG vs MCP Field Names (CRITICAL)

In Minecraft 1.12.2, production (obfuscated) builds use **SRG names** while development uses **MCP names**. When using `ReflectionHelper.findField()`, you MUST provide both names:

```java
// CORRECT — provides both MCP and SRG names
Field fuelF = ReflectionHelper.findField(EntityMinecartFurnace.class, "fuel", "field_94110_c");

// WRONG — only MCP name, will crash in production
Field fuelF = ReflectionHelper.findField(EntityMinecartFurnace.class, "fuel");
```

Known 1.12.2 SRG field mappings for `EntityMinecartFurnace`:
| MCP Name | SRG Name | Description |
|----------|----------|-------------|
| `fuel` | `field_94110_c` | Fuel remaining (int) |
| `pushX` | `field_94111_a` | Push direction X (double) |
| `pushZ` | `field_94109_b` | Push direction Z (double) |

**Never assume SRG names from other MC versions** — they change between versions. Always verify by checking the 1.12.2 MCP/SRG mappings.

#### Entity Lifecycle Methods

In 1.12.2, the method is `isEntityAlive()`, NOT `isAlive()` (which was added in later versions). Using `isAlive()` will cause a compile error.

#### Event Registration

`@EventBusSubscriber` with static `@SubscribeEvent` methods is unreliable in 1.12.2 Forge. Use **manual registration** instead:

```java
// CORRECT — manual registration
@EventHandler
public void init(FMLInitializationEvent event) {
    MinecraftForge.EVENT_BUS.register(new MyHandler());
}

// UNRELIABLE — @EventBusSubscriber often doesn't work in 1.12.2
@EventBusSubscriber
public class MyHandler {
    @SubscribeEvent
    public static void onTick(WorldTickEvent event) { ... }
}
```

#### Client-Server Communication

For key presses or other client-side input that needs to affect server-side logic, you MUST use network packets. The client is not trusted by the server.

```java
// In preInit — register the network channel
public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
NETWORK.registerMessage(MyPacket.Handler.class, MyPacket.class, 0, Side.SERVER);

// In client tick handler — send packet on key press
NETWORK.sendToServer(new MyPacket());

// In packet handler — process on server thread
public IMessage onMessage(MyPacket message, MessageContext ctx) {
    EntityPlayerMP player = ctx.getServerHandler().player;
    player.getServer().addScheduledTask(() -> {
        // Process on server thread
    });
    return null;
}
```

#### Client-Side Player View Control

In 1.12.2, the **client is authoritative** over the player's view direction. Setting `EntityPlayerMP.rotationYaw` on the server has no visible effect because the client continuously overrides it with mouse input. To control player view:

1. Use a **client-side tick handler** (`@SideOnly(Side.CLIENT)`)
2. Directly modify `Minecraft.getMinecraft().player.rotationYaw`
3. For additive rotation (preserving user look offset), track the entity's direction change each tick and add the delta to the player's current yaw

#### Vanilla Minecart Speed Cap

`EntityMinecart.getMaxSpeed()` returns 0.4 blocks/tick (8 blocks/second). To exceed this:
1. Track the target speed in your own data structure
2. After vanilla moves the cart each tick, use **position correction** — calculate where the cart should be at your target speed and teleport it there
3. Use step-wise rail following with forward-biased rail search to prevent derailing at high speed

### Coremod Development

If a mod needs bytecode transformation (ASM), it requires a coremod setup:

1. Create an `IFMLLoadingPlugin` implementation class
2. Add JAR manifest attributes in `engine/build.gradle`:
   ```gradle
   jar {
       manifest {
           attributes 'FMLCorePlugin': 'com.example.MyLoadingPlugin'
           attributes 'FMLCorePluginContainsFMLMod': 'true'
       }
   }
   ```
3. The `mod.properties` file stays the same — the build script handles manifest injection

### Action Bar Messages

To show temporary status messages to the player (like toggle states), use the action bar:

```java
player.sendStatusMessage(new TextComponentString("\u00a7a\u25b6 Cart: ON"), true);
// \u00a7 = § (color code), true = action bar (not chat)
```

Color codes: `\u00a7a` = green, `\u00a7c` = red, `\u00a76` = gold

## Publishing Mods to Modrinth (Manual)

This workspace includes a manual Modrinth publishing script at `scripts/publish_manual.py` in the ModCompiler repository. This is the recommended way to publish mods that were built locally.

### Prerequisites

1. A Modrinth API token (stored in `secrets.txt` one directory above the ModCompiler repo, or set as `MODRINTH_TOKEN` env variable)
2. A built JAR file (from `output/` after running `build_mod.sh`)
3. Project metadata JSON files (described below)

### Step 1: Create the Bundle Directory

Create a directory under `AutoCreateModrinthBundles/` in the ModCompiler repo:

```
AutoCreateModrinthBundles/<mod-name>-<version>/
├── modrinth.project.json    # Project metadata (title, description, categories, etc.)
├── modrinth.version.json    # Version metadata (game versions, loaders, changelog)
├── bundle_metadata.json     # Simple metadata linking slug and version
└── input/
    └── <Mod-Name>-<version>.jar   # The built JAR
```

### Step 2: Create modrinth.project.json

This file defines the Modrinth project listing:

```json
{
  "slug": "my-mod",
  "title": "My Mod",
  "description": "Short description of what the mod does.",
  "body": "# My Mod\n\nFull markdown description...",
  "categories": ["game-mechanics", "utility"],
  "client_side": "optional",
  "server_side": "required",
  "project_type": "mod",
  "license_id": "MIT",
  "status": "draft"
}
```

Key fields:
- `slug`: URL identifier (lowercase, hyphens only). Must be unique on Modrinth.
- `categories`: Modrinth category tags. Common ones: `game-mechanics`, `utility`, `adventure`, `storage`, `transportation`
- `client_side` / `server_side`: One of `required`, `optional`, `unsupported`
- `license_id`: SPDX identifier. Common: `MIT`, `Apache-2.0`, `All-Rights-Reserved`
- `status`: `draft` (not visible), `listed` (public), `unlisted` (link-only), `private`
- Remove empty URL fields (`issues_url`, `source_url`, `wiki_url`, `donation_urls`) — the API rejects empty strings

### Step 3: Create modrinth.version.json

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
- `version_type`: `release`, `beta`, or `alpha`
- `game_versions`: Array of exact Minecraft version strings (e.g. `["1.12.2"]`)
- `loaders`: Array of loader names (e.g. `["forge"]`, `["fabric"]`)
- `file_parts`: Must be `["file"]` — this maps to the JAR file in the upload

### Step 4: Run the Publish Script

From the ModCompiler repository root:

```bash
python3 scripts/publish_manual.py \
  --bundle-dir AutoCreateModrinthBundles/<mod-name>-<version> \
  --jar "AutoCreateModrinthBundles/<mod-name>-<version>/input/<Mod-Name>-<version>.jar"
```

Additional options:
- `--dry-run`: Preview what would be done without making API calls
- `--project-status draft|listed|unlisted|private`: Override project visibility (default: draft)
- `--version-status listed|unlisted|draft`: Override version visibility (default: listed)
- `--icon path/to/icon.png`: Upload a project icon
- `--modrinth-token <token>`: Provide token directly (otherwise auto-discovered)

### What the Script Does

1. Reads `modrinth.project.json` and `modrinth.version.json`
2. Discovers the Modrinth API token from `--modrinth-token`, `MODRINTH_TOKEN` env, or `secrets.txt`
3. Checks if a project with that slug already exists on Modrinth
4. If not, creates a new project with the initial version embedded (Modrinth requires at least one version when creating a project)
5. If yes, creates a new version under the existing project
6. Prints the project URL and version ID on success

### Common Issues

- **"Invalid body: issues_url"**: Remove empty URL fields from `modrinth.project.json`
- **"Duplicate mod" error**: Only one JAR per modid should be in the Minecraft mods folder. Delete old JARs before adding new ones.
- **Token not found**: Ensure `secrets.txt` exists at the parent directory of the ModCompiler repo, or pass `--modrinth-token` explicitly

### Publishing Workflow Summary

```
1. Build mod locally:     ./build_mod.sh my-mod
2. Create bundle dir:     AutoCreateModrinthBundles/my-mod-1.0.0/
3. Write project JSON:    modrinth.project.json (title, description, categories)
4. Write version JSON:    modrinth.version.json (game_versions, loaders, changelog)
5. Copy JAR to bundle:    input/My-Mod-1.0.0.jar
6. Dry run:               python3 scripts/publish_manual.py --bundle-dir ... --jar ... --dry-run
7. Publish:               python3 scripts/publish_manual.py --bundle-dir ... --jar ...
8. Review on Modrinth:    https://modrinth.com/mod/<slug>
```
