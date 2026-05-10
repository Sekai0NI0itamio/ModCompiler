# AI Agent Modrinth Upload Workflow

## Overview
This guide is for AI coding agents (like Claude, ChatGPT, Kiro, etc.) to help prepare Modrinth uploads by pre-generating all required metadata files. This bypasses the need for AI API calls in the Python script and is MUCH faster and more accurate.

## 🚀 RECOMMENDED DEFAULT WORKFLOW

**When a user asks to prepare a mod for Modrinth upload, AI agents should:**

1. **Automatically generate all 4 metadata files** in `ToBeUploaded/<number>/ai_metadata/`
2. **Run the generate command with `--use-ai-metadata` flag**
3. **Skip decompilation** (source is already provided locally)

This is the fastest, most accurate approach and should be the default for all locally-developed mods.

## Workflow

### Step 1: Create Bundle Folder Structure
When a mod is ready for upload, create this structure in `ToBeUploaded/<number>/`:
```
ToBeUploaded/<number>/
├── <ModName>-<version>.jar          # The compiled mod
├── source/                           # Source code folder
│   ├── asd/                         # Package structure
│   └── mcmod.info                   # Mod metadata
└── ai_metadata/                      # AI-generated metadata (NEW!)
    ├── project_info.json            # Project metadata
    ├── version_info.json            # Version metadata
    ├── description.md               # Full description
    └── summary.txt                  # Short summary
```

### Step 2: Generate Metadata Files

#### A. `project_info.json`
Contains Modrinth project information:
```json
{
  "name": "Mod Name",
  "slug": "mod-name-version",
  "summary": "One-line description (max 256 chars)",
  "categories": ["utility", "forge"],
  "client_side": "optional",
  "server_side": "optional",
  "license": "MIT"
}
```

**Categories**: Choose from Modrinth categories:
- adventure, cursed, decoration, economy, equipment, food
- game-mechanics, library, magic, management, minigame, mobs
- optimization, social, storage, technology, transportation, utility, worldgen

**Side compatibility**:
- `required`: Must be installed on this side
- `optional`: Works better with it, but not required
- `unsupported`: Cannot be installed on this side

#### B. `version_info.json`
Contains version-specific information:
```json
{
  "version_number": "1.0.0",
  "version_title": "Initial Release",
  "changelog": "- Feature 1\n- Feature 2\n- Bug fix 3",
  "game_versions": ["1.12.2"],
  "loaders": ["forge"],
  "featured": true
}
```

#### C. `description.md`
Full Modrinth project description (Markdown format):
```markdown
# Mod Name

## Description
Detailed description of what the mod does...

## Features
- Feature 1
- Feature 2
- Feature 3

## Installation
1. Download the mod
2. Place in mods folder
3. Launch Minecraft

## Usage
How to use the mod...

## Compatibility
What it works with...

## Known Issues
Any known issues...

## Credits
Author: Itamio
```

#### D. `summary.txt`
One-line summary (max 256 characters):
```
Brief description of the mod for search results and listings.
```

### Step 3: AI Agent Instructions

When preparing a mod for upload, the AI agent should:

1. **Analyze the mod** - Read source code, mcmod.info, and README files
2. **Generate all 4 metadata files** in `ToBeUploaded/<number>/ai_metadata/`
3. **Ensure accuracy** - Categories, compatibility, features match the actual mod
4. **Keep it concise** - Summary under 256 chars, description clear and scannable
5. **Use proper formatting** - Valid JSON, proper Markdown

### Step 4: Run Generate Script

Once metadata is created, run:
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle <YourModName-version.jar> --use-ai-metadata
```

**IMPORTANT**: Always use `--only-bundle` to target your specific mod and avoid processing all bundles!

The script will:
- **Skip AI API calls** (uses your pre-generated metadata)
- **Auto-detect source folder** (skips decompilation if source/ exists)
- Create Modrinth project and version JSON files
- Be **MUCH faster** (seconds instead of minutes)
- Be **more accurate** (you have full context of the mod)

**Note**: The script automatically detects when a `source/` folder exists in the bundle and skips the GitHub decompilation workflow. No additional flags needed!

### Step 5: Create Draft

```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle <slug> --verified
```

**IMPORTANT**: Always use `--only-bundle` to target your specific mod!

## Example: Instant Leaf Decay

### Bundle Structure
```
ToBeUploaded/4/
├── Instant-Leaf-Decay-1.0.0.jar
├── source/
└── ai_metadata/
    ├── project_info.json
    ├── version_info.json
    ├── description.md
    └── summary.txt
```

### Commands
```bash
# Generate bundle (only this mod)
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle Instant-Leaf-Decay-1.0.0.jar --use-ai-metadata

# Verify and create draft
echo "verified" > AutoCreateModrinthBundles/instant-leaf-decay-1.0.0/verify.txt
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle instant-leaf-decay-1.0.0 --verified
```

### project_info.json
```json
{
  "name": "Instant Leaf Decay",
  "slug": "instant-leaf-decay-1.0.0",
  "summary": "Leaves decay instantly when you cut down trees! No particles, all drops stacked at center to reduce lag.",
  "categories": ["utility", "game-mechanics"],
  "client_side": "optional",
  "server_side": "required",
  "license": "MIT"
}
```

### version_info.json
```json
{
  "version_number": "1.0.0",
  "version_title": "Initial Release",
  "changelog": "- Instant leaf decay when tree is cut\n- No particles for performance\n- Stacked item drops at center\n- Proper vanilla loot rates (saplings, apples, sticks)\n- Works with all tree types\n- Respects player-placed leaves",
  "game_versions": ["1.12.2"],
  "loaders": ["forge"],
  "featured": true
}
```

### description.md
```markdown
# Instant Leaf Decay

Tired of waiting for leaves to slowly decay after cutting down a tree? This mod makes leaves decay instantly!

## Features

- ✅ **Instant Decay** - All leaves disappear immediately when you break a log
- ✅ **No Particles** - Massive performance boost by removing leaf particle effects
- ✅ **Stacked Drops** - All items (saplings, apples, sticks) stack and drop at the center
- ✅ **Vanilla Loot Rates** - Respects proper drop chances for each tree type
- ✅ **All Tree Types** - Works with oak, birch, spruce, jungle, acacia, and dark oak
- ✅ **Smart Detection** - Only decays natural leaves, not player-placed ones

## How It Works

1. Break a log from any tree
2. All connected leaves instantly disappear (no particles!)
3. Drops are calculated using vanilla loot tables
4. Items spawn stacked at the center of the leaf cluster

## Drop Rates

The mod respects vanilla Minecraft drop rates:
- **Oak/Dark Oak**: 5% sapling, 0.5% apple, 2% sticks
- **Jungle**: 2.5% sapling, 2% sticks
- **Spruce/Birch/Acacia**: 5% sapling, 2% sticks

## Performance

This mod is designed for maximum performance:
- No particle rendering (major FPS improvement)
- Items are pre-stacked before spawning
- Efficient leaf detection algorithm
- Reduces entity count significantly

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed

## Compatibility

- **Server-side**: Required (handles leaf decay logic)
- **Client-side**: Optional (clients don't need the mod)
- **Mod Compatibility**: Works with most tree mods

## Credits

**Author**: Itamio  
**Package**: asd.itamio.instantleafdecay  
**Version**: 1.0.0
```

### summary.txt
```
Leaves decay instantly when you cut down trees! No particles, all drops stacked at center to reduce lag.
```

## Benefits of This Approach

1. **10x Faster** - No AI API calls, no decompilation, instant processing (seconds vs minutes)
2. **More Accurate** - AI agent has full context of the mod and user intent
3. **Consistent** - Same quality every time, no API variability
4. **Flexible** - Easy to customize metadata before upload
5. **Transparent** - All metadata visible and editable
6. **No External Dependencies** - Doesn't require C05 Local AI server running
7. **Auto-detects Source** - Script automatically skips decompilation when source/ folder exists

## AI Agent Checklist

When preparing a mod for Modrinth (DEFAULT WORKFLOW):

- [ ] Analyze the mod (read source code, mcmod.info, README files)
- [ ] Create `ai_metadata/` folder in `ToBeUploaded/<number>/`
- [ ] Generate `project_info.json` with accurate categories and side requirements
- [ ] Generate `version_info.json` with detailed changelog
- [ ] Write comprehensive `description.md` in Markdown
- [ ] Write concise `summary.txt` (under 256 chars)
- [ ] Verify all JSON is valid
- [ ] Ensure categories match mod functionality
- [ ] Set correct client/server side requirements
- [ ] Run: `python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle <YourModName-version.jar> --use-ai-metadata`
- [ ] Mark as verified: `echo "verified" > AutoCreateModrinthBundles/<slug>/verify.txt`
- [ ] Run: `python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle <slug> --verified`
- [ ] Provide user with the Modrinth URL

**CRITICAL**: Always use `--only-bundle` flag to target your specific mod and avoid processing all bundles!

**This should be the default workflow for all locally-developed mods!**
