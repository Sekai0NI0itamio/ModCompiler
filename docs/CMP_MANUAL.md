# CMP Bundle Manual for IDE Coding Agents

This document is a comprehensive reference for IDE coding agents (Trae, Cursor, Copilot, etc.) to programmatically create CMP bundles. It describes the bundle format, directory structure, manifest schema, and step-by-step instructions.

**For writing compelling mod names, summaries, and descriptions**, see [MOD_MAKING_MANUAL.md](./MOD_MAKING_MANUAL.md) — a category-specific guide with fill-in-the-blank templates derived from analyzing the most popular mods on Modrinth.

---

## 1. What Is CMP?

CMP (Center Mod Publishment) is an Electron desktop app that provides a unified workflow for packaging and publishing Minecraft mods to **Modrinth** and **GitHub**. A **CMP bundle** is a directory that contains all metadata and assets needed to publish a mod project — the manifest, the jar file, source code, icon, gallery images, and description images — in a single self-contained folder.

The CMP app reads these bundles, lets the user review/edit them visually, and then orchestrates:
1. GitHub repository creation (source push, wiki, issue templates)
2. Modrinth project creation (project, version upload, gallery, description images, links)

---

## 2. Directory Structure

CMP bundles live under the project root in two locations:

```
ModCompiler/
  CMP/
    app/                          # The Electron app source code
    BundleDrafts/                 # Draft bundles (not yet published)
      <slug>/                     # One directory per bundle
        manifest.json
        jar/
        source/
        icon.png
        gallery/
        description_images/
    BundlePublished/              # Published bundles (moved here after publishing)
      <slug>/
        ...
```

- **`CMP/BundleDrafts/<slug>/`** — Where new bundles are created. The app's "Refresh" button scans this directory.
- **`CMP/BundlePublished/<slug>/`** — Bundles are moved here after successful publishing. This separates published from draft state.
- **`CMP/app/`** — The Electron application source code. Not part of bundles.

---

## 3. Bundle Structure

Each bundle is a directory named by its **slug** containing:

```
my-cool-mod/
  manifest.json              # All metadata (see full schema below)
  jar/                       # The mod jar file
    my-cool-mod-1.0.0.jar
  source/                    # Source code (recursive directory)
    src/
      main/
        java/
          com/
            example/
              CoolMod.java
  icon.png                   # Project icon (square, typically 256x256+)
  gallery/                   # Gallery images, numbered
    0.png
    1.png
  description_images/        # Images referenced in description body via {{image:N}}
    0.png
    1.png
```

| Path | Required | Description |
|------|----------|-------------|
| `manifest.json` | Yes | All metadata for the project, version, description, links, and publishing config |
| `jar/` | Yes | Contains the mod jar file (the built artifact to upload to Modrinth) |
| `source/` | Yes | Contains the source code to push to GitHub |
| `icon.png` | No | Project icon image. Referenced in manifest as `"icon": "icon.png"` |
| `gallery/` | No | Gallery screenshots. Referenced in manifest `gallery[].file` as `"gallery/0.png"` etc. |
| `description_images/` | No | Images embedded in the description body. Referenced in manifest `description.images[].file` as `"description_images/0.png"` etc. |

---

## 4. Full manifest.json Schema

### 4.1 Top-Level Structure

```json
{
  "cmp_version": 1,
  "mod_info": { ... },
  "version_info": { ... },
  "description": { ... },
  "icon": "icon.png",
  "gallery": [ ... ],
  "files": { ... },
  "links": { ... },
  "publishing": { ... }
}
```

### 4.2 `cmp_version`

| Field | Type | Value | Description |
|-------|------|-------|-------------|
| `cmp_version` | integer | `1` | Always `1`. Schema version identifier. |

### 4.3 `mod_info`

```json
{
  "name": "My Cool Mod",
  "slug": "my-cool-mod",
  "summary": "A brief one-line description of the mod",
  "project_type": "mod",
  "categories": ["adventure", "utility"],
  "additional_categories": ["technology"],
  "license_id": "MIT",
  "license_url": "",
  "donation_urls": []
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Display name of the project |
| `slug` | string | Yes | URL-safe identifier. Lowercase, hyphens only. Auto-generated from name: `name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')` |
| `summary` | string | Yes | One-line description shown in search results |
| `project_type` | string | Yes | One of the valid project types (see below) |
| `categories` | string[] | Yes | Primary categories. Must match the `project_type` (see category table below) |
| `additional_categories` | string[] | No | Secondary categories. Same valid values as `categories`, must also match `project_type` |
| `license_id` | string | Yes | SPDX license identifier (see common licenses below) |
| `license_url` | string | No | Direct URL to the license text (e.g., for custom licenses) |
| `donation_urls` | DonationURL[] | No | List of donation links (see below) |

**Valid `project_type` values:**

| Value | Label |
|-------|-------|
| `mod` | Mod |
| `modpack` | Modpack |
| `resourcepack` | Resource Pack |
| `shader` | Shader |
| `plugin` | Plugin |
| `datapack` | Data Pack |
| `minecraft_java_server` | Server |

**Valid categories by project type** (fetched from Modrinth API — these are the current values as of 2025):

| Project Type | Valid Categories |
|---|---|
| `mod` | `adventure`, `cursed`, `decoration`, `economy`, `equipment`, `food`, `game-mechanics`, `library`, `magic`, `management`, `minigame`, `mobs`, `optimization`, `social`, `storage`, `technology`, `transportation`, `utility`, `worldgen` |
| `modpack` | `adventure`, `challenging`, `combat`, `kitchen-sink`, `lightweight`, `multiplayer`, `optimization`, `quests`, `technology` |
| `resourcepack` | `16x`, `32x`, `48x`, `64x`, `128x`, `256x`, `512x+`, `8x-`, `audio`, `blocks`, `combat`, `core-shaders`, `cursed`, `decoration`, `entities`, `environment`, `equipment`, `fonts`, `gui`, `items`, `locale`, `modded`, `models`, `realistic`, `simplistic`, `themed`, `tweaks`, `utility`, `vanilla-like` |
| `shader` | `atmosphere`, `bloom`, `cartoon`, `colored-lighting`, `cursed`, `fantasy`, `foliage`, `high`, `low`, `medium`, `path-tracing`, `pbr`, `potato`, `realistic`, `reflections`, `screenshot`, `semi-realistic`, `shadows`, `vanilla-like` |
| `plugin` | *(none — Modrinth has no categories for plugins currently)* |
| `datapack` | *(none — Modrinth has no categories for datapacks currently)* |
| `minecraft_java_server` | `adventure-mode`, `anarchy`, `battle-royale`, `bedwars`, `classes`, `competitive`, `creative-mode`, `creator-community`, `crossplay`, `custom-content`, `dungeons`, `economy`, `factions`, `gens`, `hardcore-mode`, `keep-inventory`, `kitpvp`, `lifesteal`, `media`, `microgames`, `minigames`, `mmo`, `network`, `offline-mode`, `oneblock`, `op`, `parkour`, `personal-worlds`, `plots`, `pokemon`, `prison`, `pve`, `pvp`, `questing`, `racing`, `recording-smp`, `roleplay`, `rpg`, `skyblock`, `smp`, `social`, `survival-mode`, `teams`, `technical`, `towns`, `vanilla-like`, `whitelisted`, `world-resets` |

**Common `license_id` values:**

| License ID | Full Name |
|---|---|
| `MIT` | MIT License |
| `Apache-2.0` | Apache License 2.0 |
| `GPL-3.0-or-later` | GNU General Public License v3.0+ |
| `LGPL-3.0-or-later` | GNU Lesser General Public License v3.0+ |
| `CC0-1.0` | Creative Commons Zero v1.0 Universal |
| `Unlicense` | The Unlicense |
| `All-Rights-Reserved` | All Rights Reserved |

**`DonationURL` structure:**

```json
{
  "id": "unique-uuid-string",
  "platform": "patreon",
  "url": "https://patreon.com/myuser"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier (use a UUID) |
| `platform` | string | One of: `patreon`, `bmac`, `paypal`, `github`, `ko-fi`, `other` |
| `url` | string | Full URL to the donation page |

### 4.4 `version_info`

```json
{
  "mod_version": "1.0.0",
  "loaders": ["fabric", "forge"],
  "client_side": "required",
  "server_side": "optional",
  "minecraft_versions": ["1.20.1", "1.20.4", "1.21.1"],
  "version_type": "release",
  "changelog": "Initial release!",
  "dependencies": [],
  "featured": true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `mod_version` | string | Yes | Semantic version of this mod release (e.g., `"1.0.0"`) |
| `loaders` | string[] | Yes | Mod loaders this version supports. Must be valid for the `project_type` (see loader table below) |
| `client_side` | string | Yes | Client side requirement: `required`, `optional`, `unsupported`, `unknown` |
| `server_side` | string | Yes | Server side requirement: `required`, `optional`, `unsupported`, `unknown` |
| `minecraft_versions` | string[] | Yes | Minecraft versions this mod supports (e.g., `["1.20.1", "1.20.4"]`) |
| `version_type` | string | Yes | One of: `release`, `beta`, `alpha` |
| `changelog` | string | No | Changelog text for this version (markdown supported) |
| `dependencies` | VersionDependency[] | No | List of version dependencies (see below) |
| `featured` | boolean | No | Whether this version is featured on the project page. Default: `false` |

**Valid loaders by project type** (from Modrinth API):

| Loader | Supported Project Types |
|--------|------------------------|
| `fabric` | mod, modpack |
| `forge` | mod, modpack |
| `neoforge` | mod, modpack |
| `quilt` | mod, modpack |
| `rift` | mod |
| `liteloader` | mod |
| `babric` | mod |
| `legacy-fabric` | mod |
| `ornithe` | mod |
| `nilloader` | mod |
| `modloader` | mod |
| `bta-babric` | mod |
| `java-agent` | mod |
| `datapack` | datapack, mod |
| `bukkit` | plugin, mod |
| `spigot` | plugin, mod |
| `paper` | plugin, mod |
| `purpur` | plugin, mod |
| `folia` | plugin, mod |
| `sponge` | plugin, mod |
| `velocity` | plugin, mod |
| `bungeecord` | plugin, mod |
| `waterfall` | plugin, mod |
| `geyser` | plugin, mod |
| `canvas` | shader |
| `iris` | shader |
| `optifine` | shader |
| `vanilla` | shader |
| `minecraft` | resourcepack |

**Side type meanings:**

| Value | Meaning |
|-------|---------|
| `required` | Must be present on this side for the mod to function |
| `optional` | Works on this side but is not required |
| `unsupported` | Does not work on this side; will not appear in search for that side |
| `unknown` | Side support is not determined |

**`VersionDependency` structure:**

```json
{
  "project_id": "abcdef12",
  "version_id": "12345678",
  "file_name": "",
  "dependency_type": "required"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `project_id` | string | Modrinth project ID (can be empty if `file_name` is set) |
| `version_id` | string | Modrinth version ID (optional — leave empty to match latest) |
| `file_name` | string | File name for projects not on Modrinth (used if `project_id` is empty) |
| `dependency_type` | string | One of: `required`, `optional`, `incompatible`, `embedded` |

### 4.5 `description`

```json
{
  "body": "## Features\n\nThis mod adds {{image:0}} cool things.\n\n### Details\n\nMore info here {{image:1}}.",
  "images": [
    { "index": 0, "file": "description_images/0.png", "caption": "Feature showcase" },
    { "index": 1, "file": "description_images/1.png", "caption": "Config screen" }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `body` | string | Yes | Markdown body of the project description. Use `{{image:N}}` placeholders to reference images by their index |
| `images` | DescriptionImage[] | No | List of images referenced in the body |

**`DescriptionImage` structure:**

| Field | Type | Description |
|-------|------|-------------|
| `index` | integer | Zero-based index matching the `{{image:N}}` placeholder in `body` |
| `file` | string | Relative path to the image file within the bundle (e.g., `"description_images/0.png"`) |
| `caption` | string | Alt text / caption for the image |

**How `{{image:N}}` works:**
- In the description `body`, you write `{{image:0}}`, `{{image:1}}`, etc.
- Each placeholder corresponds to an image in the `images` array by `index`.
- When publishing to Modrinth, the CMP app uploads each image, gets the hosted URL, and replaces `{{image:N}}` with the actual URL in the description body sent to Modrinth.
- The image files must exist in the `description_images/` directory of the bundle.

### 4.6 `icon`

```json
"icon": "icon.png"
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `icon` | string | No | Relative path to the project icon image within the bundle. Typically `"icon.png"`. Must be a square image, recommended 256x256 or larger. |

### 4.7 `gallery`

```json
"gallery": [
  { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Main screenshot", "description": "Shows the main feature" },
  { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Config screen", "description": "Configuration UI" }
]
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `index` | integer | Yes | Zero-based index for ordering |
| `file` | string | Yes | Relative path to the image file within the bundle (e.g., `"gallery/0.png"`) |
| `featured` | boolean | No | Whether this is the featured gallery image. Default: `false` |
| `title` | string | No | Title for the gallery image |
| `description` | string | No | Description for the gallery image |

### 4.8 `files`

```json
"files": {
  "jar": "jar/my-cool-mod-1.0.0.jar",
  "source": "source/"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `jar` | string | Yes | Relative path to the jar file within the bundle (e.g., `"jar/my-cool-mod-1.0.0.jar"`) |
| `source` | string | Yes | Relative path to the source directory within the bundle (e.g., `"source/"`) |

### 4.9 `links`

```json
"links": {
  "issues_url": "",
  "source_url": "",
  "wiki_url": "",
  "discord_url": ""
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `issues_url` | string | No | URL to the issue tracker. Auto-populated after GitHub repo creation: `https://github.com/<owner>/<repo>/issues` |
| `source_url` | string | No | URL to the source code. Auto-populated after GitHub repo creation: `https://github.com/<owner>/<repo>` |
| `wiki_url` | string | No | URL to the wiki. Auto-populated after GitHub repo creation: `https://github.com/<owner>/<repo>/wiki` |
| `discord_url` | string | No | URL to a Discord server for the project |

**Note:** `issues_url`, `source_url`, and `wiki_url` are automatically filled by the CMP app after the GitHub repository is created. You can leave them empty in the manifest.

### 4.10 `publishing`

```json
"publishing": {
  "modrinth_project_id": "",
  "github_owner": "myuser",
  "github_repo_name": "my-cool-mod",
  "requested_status": ""
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `modrinth_project_id` | string | No | Modrinth project ID. Leave empty for new projects (CMP will create a draft). Set to an existing project ID to publish a new version to an existing project. |
| `github_owner` | string | No | GitHub username or organization name that will own the repository |
| `github_repo_name` | string | No | Name of the GitHub repository to create |
| `requested_status` | string | No | Desired Modrinth project status after creation. One of: `approved`, `archived`, `unlisted`, `private`, `draft`, `""` (empty string = default draft) |

---

## 5. Step-by-Step: Creating a Bundle Programmatically

Follow these exact steps to create a CMP bundle:

### Step 1: Create the bundle directory

```
mkdir -p CMP/BundleDrafts/<slug>
```

Replace `<slug>` with the URL-safe version of the mod name (lowercase, hyphens, no special characters).

### Step 2: Create subdirectories

```
mkdir -p CMP/BundleDrafts/<slug>/jar
mkdir -p CMP/BundleDrafts/<slug>/source
mkdir -p CMP/BundleDrafts/<slug>/gallery
mkdir -p CMP/BundleDrafts/<slug>/description_images
```

### Step 3: Write manifest.json

Create `CMP/BundleDrafts/<slug>/manifest.json` with all fields filled. Here is a complete template:

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "My Cool Mod",
    "slug": "my-cool-mod",
    "summary": "A brief one-line description",
    "project_type": "mod",
    "categories": ["adventure", "utility"],
    "additional_categories": [],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric"],
    "client_side": "required",
    "server_side": "optional",
    "minecraft_versions": ["1.20.1"],
    "version_type": "release",
    "changelog": "",
    "dependencies": [],
    "featured": false
  },
  "description": {
    "body": "",
    "images": []
  },
  "icon": "",
  "gallery": [],
  "files": {
    "jar": "",
    "source": ""
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "",
    "requested_status": ""
  }
}
```

**Important:** Fill in every field. Even if a value is empty, the key must be present. The `cmp_version` must be `1`.

### Step 4: Copy the jar file

Copy the built mod jar into the `jar/` subdirectory:

```
cp /path/to/my-cool-mod-1.0.0.jar CMP/BundleDrafts/<slug>/jar/
```

Then update `manifest.json` → `files.jar` to the relative path: `"jar/my-cool-mod-1.0.0.jar"`

### Step 5: Copy source code

Copy the source code into the `source/` subdirectory:

```
cp -r /path/to/source/* CMP/BundleDrafts/<slug>/source/
```

Then update `manifest.json` → `files.source` to: `"source/"`

### Step 6: Copy icon and gallery images (if available)

**Icon:**
```
cp /path/to/icon.png CMP/BundleDrafts/<slug>/icon.png
```
Then set `manifest.json` → `icon` to `"icon.png"`

**Gallery images:**
```
cp /path/to/screenshot1.png CMP/BundleDrafts/<slug>/gallery/0.png
cp /path/to/screenshot2.png CMP/BundleDrafts/<slug>/gallery/1.png
```
Then add entries to `manifest.json` → `gallery`:
```json
"gallery": [
  { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Main screenshot", "description": "" },
  { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Another view", "description": "" }
]
```

**Description images:**
```
cp /path/to/desc_img1.png CMP/BundleDrafts/<slug>/description_images/0.png
```
Then add entries to `manifest.json` → `description.images`:
```json
"images": [
  { "index": 0, "file": "description_images/0.png", "caption": "Feature showcase" }
]
```
And reference in `description.body` with `{{image:0}}`.

### Step 7: Verify the bundle

The final directory should look like:

```
CMP/BundleDrafts/my-cool-mod/
  manifest.json
  jar/
    my-cool-mod-1.0.0.jar
  source/
    src/
      ...
  icon.png
  gallery/
    0.png
  description_images/
    0.png
```

---

## 6. Using the CMP App

After creating the bundle directory:

1. Open the CMP Electron app
2. Click **"Refresh"** — the app scans `BundleDrafts/` and `BundlePublished/` for bundles
3. The new bundle appears in the **Bundle Manager** list
4. Click the bundle to open it in the **Bundle Editor** — review and edit all fields
5. When ready, go to the **Publish Dashboard** to:
   - Create the GitHub repository (source code, wiki, issue templates)
   - Publish to Modrinth (project creation, jar upload, gallery, description images, links)

The app auto-populates `links.issues_url`, `links.source_url`, and `links.wiki_url` after GitHub repo creation.

---

## 7. Important Notes

### CRITICAL: AI Must NOT Generate Images

**The AI agent must NEVER generate, create, or synthesize any images (icons, screenshots, gallery images, or description images).** All images must be created by the human user themselves. The AI should:

- **NOT** use any image generation tools or APIs
- **NOT** create placeholder images or mock screenshots
- **NOT** suggest generating images programmatically
- **NOT** use AI image generation services

Instead, the AI should:
- Leave image fields empty (`"icon": ""`, `"gallery": []`, `"description.images": []`)
- Add a comment in the manifest or a note to the user: "Please add your own icon/gallery/description images using the CMP app's Icon Editor"
- The user will add images themselves using the CMP app's built-in Icon Editor (crop, resize, save) or by uploading their own files

This policy exists because:
1. AI-generated images look generic and unprofessional
2. Mod icons should reflect the mod's unique visual identity
3. Screenshots must show actual in-game content
4. Modrinth users expect authentic, human-crafted visuals

### Compile & Launch Integration

The CMP app now supports direct mod compilation and game client launching:

1. **Compile** — When the user selects a source code directory (from `Mod Development/` workspaces), the "Compile & Set as Jar" button will:
   - Auto-detect the workspace (1.12.2-forge or 1.21.1-fabric)
   - Run `build_mod.sh` in the appropriate workspace
   - Copy the built JAR to the bundle and set it as the version jar

2. **Launch Client** — When a JAR is set, the "Launch Client" button will:
   - Deploy the JAR to a Prism Launcher instance's mods folder
   - Auto-select the appropriate instance based on the workspace (e.g., 1.12.2 instance for forge mods)
   - Launch Prism Launcher so the user can test the mod in-game
   - This is useful for capturing gallery screenshots of the mod in action

3. **Icon Editor** — The CMP app includes a built-in icon editor that allows the user to:
   - Upload an image from their filesystem
   - Crop it to a square
   - Resize to standard mod icon sizes (64x64, 128x128, 256x256, 512x512)
   - Save the edited icon directly to the bundle

### Categories Must Match project_type
Modrinth validates that categories are valid for the selected `project_type`. If you set `project_type: "mod"`, you can only use categories from the mod category list. Using a category from another project type will cause a Modrinth API error.

### Loaders Must Support the project_type
Similarly, loaders must support the selected `project_type`. For example, `fabric` supports `mod` and `modpack` but not `shader`. The loader validation is based on the `supported_project_types` field from the Modrinth API.

### Side Types Affect Search Visibility
- `client_side: "unsupported"` means the mod will NOT appear in searches filtered for client-side mods
- `server_side: "unsupported"` means the mod will NOT appear in searches filtered for server-side mods
- Most mods should use `client_side: "required"` and `server_side: "optional"` (client-required, server-optional = works on both but client needs it)

### Description Images Use {{image:N}} Placeholders
The `{{image:N}}` syntax in `description.body` is a CMP-specific placeholder system. When publishing:
1. CMP uploads each description image to Modrinth
2. Modrinth returns a hosted URL for each image
3. CMP replaces `{{image:0}}`, `{{image:1}}`, etc. with the actual URLs
4. The final description with real image URLs is sent to Modrinth

Do NOT use markdown image syntax (`![alt](url)`) for description images that need uploading. Use `{{image:N}}` instead.

### GitHub Repo Is Created First
The publishing flow is:
1. **GitHub first** — Create the repo, push source, create wiki, get URLs
2. **Modrinth second** — Create project, upload jar, upload gallery, upload description images, apply GitHub URLs

This is why `links` fields start empty and are populated during publishing.

### Slug Generation
The slug must be URL-safe: lowercase letters, digits, and hyphens only. The standard algorithm is:
```
slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
```

### Empty vs Missing Fields
All keys in `manifest.json` must be present, even if their values are empty strings, empty arrays, or default values. The CMP app expects the full schema to exist. Do not omit any top-level or nested keys.

### File Paths in manifest.json
All file paths in the manifest (`icon`, `gallery[].file`, `description.images[].file`, `files.jar`, `files.source`) are **relative to the bundle directory root**. For example, if the bundle is at `CMP/BundleDrafts/my-cool-mod/`, then `"gallery/0.png"` refers to `CMP/BundleDrafts/my-cool-mod/gallery/0.png`.

---

## 8. Quick Reference: Complete manifest.json Example

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "VeinMiner",
    "slug": "veinminer",
    "summary": "Mine entire ore veins by breaking one block",
    "project_type": "mod",
    "categories": ["utility", "game-mechanics"],
    "additional_categories": ["technology"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": [
      {
        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "platform": "ko-fi",
        "url": "https://ko-fi.com/myuser"
      }
    ]
  },
  "version_info": {
    "mod_version": "1.2.0",
    "loaders": ["fabric", "forge", "neoforge"],
    "client_side": "required",
    "server_side": "optional",
    "minecraft_versions": ["1.20.1", "1.20.4", "1.20.6", "1.21.1"],
    "version_type": "release",
    "changelog": "## 1.2.0\n\n- Added support for 1.21.1\n- Fixed block detection edge case\n- Performance improvements",
    "dependencies": [
      {
        "project_id": "P7dR8mSH",
        "version_id": "",
        "file_name": "",
        "dependency_type": "required"
      }
    ],
    "featured": true
  },
  "description": {
    "body": "# VeinMiner\n\nMine entire ore veins by breaking just one block!\n\n{{image:0}}\n\n## Features\n\n- Break one ore block, mine the whole vein\n- Configurable block types\n- Adjustable max vein size\n- Works with any pickaxe\n\n{{image:1}}\n\n## Configuration\n\nAll settings are configurable via `/veinminer config` or the config file.",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "VeinMiner in action" },
      { "index": 1, "file": "description_images/1.png", "caption": "Configuration screen" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Mining a diamond vein", "description": "One break mines the entire vein" },
    { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Config screen", "description": "In-game configuration UI" }
  ],
  "files": {
    "jar": "jar/veinminer-1.2.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": "https://discord.gg/myinvite"
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "myuser",
    "github_repo_name": "veinminer",
    "requested_status": "approved"
  }
}
```
