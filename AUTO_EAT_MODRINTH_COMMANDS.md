# Auto Eat - Modrinth Upload Summary

## Status: ✅ DRAFT CREATED

The Auto Eat mod has been successfully uploaded to Modrinth as a draft!

## Modrinth Links
- **Project**: https://modrinth.com/mod/world-auto-eat
- **Version**: https://modrinth.com/mod/world-auto-eat/version/8PyO03Vr

## Mod Details
- **Name**: Auto Eat
- **Version**: 1.0.0
- **Minecraft**: 1.12.2
- **Loader**: Forge
- **License**: MIT

## Features
- Automatically eats food when hunger drops below configurable threshold (default: 14/20)
- Smart food selection based on saturation values (eats most efficient food first)
- Configurable food blacklist (spider eyes, rotten flesh, poisonous potatoes, golden apples, chorus fruit)
- Minimal performance impact (checks once per second)
- Works with any food item in inventory

## Configuration
```
config/autoeat.cfg:
- hungerThreshold: 14 (0-20, where 20 is full)
- blacklistedFoods: List of foods to never auto-eat
```

## Commands Used

### Generate Bundle
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 13 --use-ai-metadata
```

### Create Draft
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle auto-eat-1.0.0
```

## Next Steps
1. Review the draft on Modrinth
2. Publish when ready
3. Test the published version

## Files Location
- Source: `Mod Developement/1.12.2-forge/src/main/java/asd/itamio/autoeat/`
- JAR: `Mod Developement/1.12.2-forge/ReadyMods/Auto-Eat-1.0.0.jar`
- Bundle: `AutoCreateModrinthBundles/auto-eat-1.0.0/`
- ToBeUploaded: `ToBeUploaded/13/`
