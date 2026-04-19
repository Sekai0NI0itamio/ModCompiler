# Auto Replant - Modrinth Upload Commands

## Mod Information
- **Name**: Auto Replant
- **Version**: 1.0.0
- **Minecraft Version**: 1.12.2
- **Mod Loader**: Forge
- **Author**: Itamio
- **Package**: asd.itamio.autoreplant

## Description
Automatically replants crops and trees when you harvest them! Supports wheat, carrots, potatoes, beetroot, cocoa beans, and trees. Only replants fully grown crops and consumes seeds/saplings from your inventory.

## Bundle Location
`ToBeUploaded/11/`

## Commands

### 1. Generate Bundle (Process mod and create metadata)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 11
```

This will:
- Use the local source code (no decompilation needed - faster!)
- Extract mod information from mcmod.info
- Create project and version metadata
- Prepare for Modrinth upload

### 2. Create Draft Project (Upload to Modrinth)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle auto-replant-1.0.0 --verified --create-via github
```

This will:
- Create a draft project on Modrinth
- Upload the mod file
- Set project as verified
- Link to GitHub repository

## Notes
- The `--only-bundle 11` uses the bundle NUMBER for generate
- The `--only-bundle auto-replant-1.0.0` uses the bundle SLUG for create-drafts
- Using local source (in `source/` folder) skips decompilation - much faster!
- The mod is ready for production with no debug logging

## Features to Highlight
- ✅ Auto-replants wheat, carrots, potatoes, beetroot, cocoa beans, and trees
- ✅ Only replants fully grown crops
- ✅ Consumes seeds/saplings from inventory (free in creative mode)
- ✅ Works seamlessly - just harvest and it replants!
- ✅ No configuration needed
- ✅ Lightweight and efficient
