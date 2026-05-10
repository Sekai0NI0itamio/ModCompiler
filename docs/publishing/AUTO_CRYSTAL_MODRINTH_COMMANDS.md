# Auto Crystal - Modrinth Upload Commands

## Bundle Created: ToBeUploaded/25/

### Files Included
- `Auto-Crystal-1.0.0.jar` - The mod file
- `source/` - Source code
- `ai_metadata/` - AI-generated metadata files

## Commands Used

### 1. Generate Bundle
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle Auto-Crystal-1.0.0.jar --use-ai-metadata --force
```

### 2. Mark as Verified
```bash
echo "verified" > AutoCreateModrinthBundles/auto-crystal-1.0.0/verify.txt
```

### 3. Create Draft
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle auto-crystal-1.0.0 --verified
```

## Modrinth Links

- **Project**: https://modrinth.com/mod/world-auto-crystal
- **Version**: https://modrinth.com/mod/world-auto-crystal/version/Lreo0ZnH

## Notes

- Initial slug "auto-crystal" was taken, changed to "world-auto-crystal"
- Client-side only mod (server-side unsupported)
- Categories: utility, combat
- Toggle with C key
- Perfect for PvP crystal combat

## Status: ✅ PUBLISHED TO MODRINTH
