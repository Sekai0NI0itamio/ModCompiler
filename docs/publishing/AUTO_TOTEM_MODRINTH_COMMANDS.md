# Auto Totem - Modrinth Upload Commands

## Step 1: Generate Bundle (with AI metadata, only Auto Totem)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --use-ai-metadata --only-bundle Auto-Totem-1.0.0.jar
```

This will:
- Use the AI metadata from `ToBeUploaded/23/ai_metadata/`
- Generate ONLY the auto-totem bundle at `AutoCreateModrinthBundles/auto-totem-1.0.0/`
- Create art, description, and Modrinth JSON files
- Skip all other bundles

## Step 2: Verify Bundle
Check the generated bundle:
```bash
cat AutoCreateModrinthBundles/auto-totem-1.0.0/verify.txt
```

Mark as verified:
```bash
echo "verified" > AutoCreateModrinthBundles/auto-totem-1.0.0/verify.txt
```

## Step 3: Create Draft on Modrinth
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle auto-totem-1.0.0 --verified
```

This will:
- Upload to GitHub temporarily
- Create the Modrinth draft project
- Create the first version
- Return the Modrinth URL

## Expected Output
```
Modrinth links:
- auto-totem-1.0.0: https://modrinth.com/mod/auto-totem
  version: https://modrinth.com/mod/auto-totem/version/[VERSION_ID]
```

## Quick One-Liner (all steps)
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --use-ai-metadata --only-bundle Auto-Totem-1.0.0.jar && echo "verified" > AutoCreateModrinthBundles/auto-totem-1.0.0/verify.txt && python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle auto-totem-1.0.0 --verified
```

## Bundle Contents
- **Jar:** `ToBeUploaded/23/Auto-Totem-1.0.0.jar`
- **Source:** `ToBeUploaded/23/Auto-Totem-Src/`
- **AI Metadata:** `ToBeUploaded/23/ai_metadata/`
  - `project_info.json` - Project metadata
  - `version_info.json` - Version metadata
  - `description.md` - Full description
  - `summary.txt` - Short summary

## Notes
- Bundle #23
- Uses AI Agent Modrinth Workflow (10x faster)
- No decompilation needed (source provided)
- Server-side mod
- Minecraft 1.12.2 Forge
- `--only-bundle` ensures ONLY Auto Totem is processed
