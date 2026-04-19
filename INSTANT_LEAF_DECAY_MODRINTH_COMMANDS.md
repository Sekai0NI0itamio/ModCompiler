# Instant Leaf Decay - Modrinth Upload Commands

## Mod Information
- **Name**: Instant Leaf Decay
- **Version**: 1.0.0
- **Minecraft Version**: 1.12.2
- **Mod Loader**: Forge
- **Author**: Itamio
- **Package**: asd.itamio.instantleafdecay

## Description
Leaves decay instantly when you cut down a tree! No more waiting for leaves to slowly disappear. All drops are collected and stacked at the center to reduce lag. Respects vanilla loot tables for proper sapling, apple, and stick drops.

## Bundle Location
`ToBeUploaded/12/`

## Commands

### 1. Generate Bundle (Process mod and create metadata)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 12
```

This will:
- Use the local source code (no decompilation needed - faster!)
- Extract mod information from mcmod.info
- Create project and version metadata
- Prepare for Modrinth upload

### 2. Create Draft Project (Upload to Modrinth)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle instant-leaf-decay-1.0.0 --verified --create-via github
```

This will:
- Create a draft project on Modrinth
- Upload the mod file
- Set project as verified
- Link to GitHub repository

## Notes
- The `--only-bundle 12` uses the bundle NUMBER for generate
- The `--only-bundle instant-leaf-decay-1.0.0` uses the bundle SLUG for create-drafts
- Using local source (in `source/` folder) skips decompilation - much faster!
- The mod is production-ready with proper loot handling

## Features to Highlight
- ✅ Instant leaf decay when tree is cut down
- ✅ No particles (massive lag reduction)
- ✅ Items stack and drop at center location
- ✅ Respects vanilla loot tables (proper sapling/apple/stick rates)
- ✅ Works with all tree types (oak, birch, spruce, jungle, acacia, dark oak)
- ✅ Respects player-placed leaves (won't decay)
- ✅ Lightweight and efficient
- ✅ No configuration needed

## Loot Mechanics
The mod properly handles vanilla drop rates:
- **Oak/Dark Oak**: 5% sapling, 0.5% apple, 2% stick
- **Jungle**: 2.5% sapling, 2% stick
- **Spruce/Birch/Acacia**: 5% sapling, 2% stick

All drops are calculated per-leaf using vanilla loot tables, then stacked and spawned at the center.

## Performance Benefits
- No particle rendering (major FPS boost)
- Items are stacked before spawning (reduces entity count)
- Efficient leaf detection algorithm
- Single drop location (easier to collect)
