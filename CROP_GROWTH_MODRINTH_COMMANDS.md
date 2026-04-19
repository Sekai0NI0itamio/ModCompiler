# Crop Growth Accelerator - Modrinth Upload Summary

## Status: ✅ DRAFT CREATED

The Crop Growth Accelerator mod has been successfully uploaded to Modrinth as a draft!

## Modrinth Links
- **Project**: https://modrinth.com/mod/crop-growth-accelerator
- **Version**: https://modrinth.com/mod/crop-growth-accelerator/version/eRg1Z123

## Mod Details
- **Name**: Crop Growth Accelerator
- **Version**: 1.0.0
- **Minecraft**: 1.12.2
- **Loader**: Forge
- **License**: MIT

## Features
- Crops grow 3x faster when player is nearby (configurable 1-10x)
- 16 block radius around player (configurable 4-64 blocks)
- 50 bonus growth ticks when player wakes up from sleep (configurable 0-200)
- Weather-based growth modifiers:
  * Rain: +2 extra growth ticks per check
  * Thunder: +4 extra growth ticks per check
  * Snow: 0.5x growth speed (half speed in cold biomes)
- Works with all vanilla crops: wheat, carrots, potatoes, beetroot, pumpkins, melons, nether wart, cocoa, saplings, mushrooms, cactus, sugar cane
- Fully configurable - can disable nearby growth, sleep bonus, or weather effects independently
- Minimal performance impact (checks 10 random blocks per second)

## Configuration
```
config/cropgrowth.cfg:
- radius: 16 (4-64 blocks)
- growthSpeedMultiplier: 3 (1-10x)
- sleepGrowthBonus: 50 (0-200 ticks)
- enableWhilePlayerNearby: true
- enableWhileSleeping: true
- enableWeatherEffects: true
- rainGrowthBonus: 2 (0-10)
- thunderGrowthBonus: 4 (0-10)
- snowGrowthPenalty: 0.5 (0.0-1.0)
```

## Commands Used

### Generate Bundle
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 14 --use-ai-metadata
```

### Create Draft
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle crop-growth-accelerator-1.0.0
```

## Next Steps
1. Review the draft on Modrinth
2. Publish when ready
3. Test the published version

## Files Location
- Source: `Mod Developement/1.12.2-forge/src/main/java/asd/itamio/cropgrowth/`
- JAR: `Mod Developement/1.12.2-forge/ReadyMods/Crop-Growth-Accelerator-1.0.0.jar`
- ModCollection: `Mod Developement/1.12.2-forge/ModCollection/Crop-Growth-Accelerator-1.0.0.jar`
- Bundle: `AutoCreateModrinthBundles/crop-growth-accelerator-1.0.0/`
- ToBeUploaded: `ToBeUploaded/14/`
