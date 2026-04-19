# Auto Crystal - Minecraft 1.12.2 Mod

## Overview
Auto Crystal is a PvP utility mod that automatically attacks end crystals when you place them. Perfect for crystal PvP combat!

## Features
- **Instant Attack**: Automatically left-clicks crystals when placed (0-tick by default)
- **Toggle On/Off**: Press C to enable/disable the mod
- **Configurable**: Adjust attack delay and range in config
- **Visual Feedback**: Chat message shows current status (ON/OFF)

## How It Works
1. Place an end crystal on obsidian/bedrock
2. The mod automatically attacks it to explode it
3. No manual clicking required!

## Controls
- **C Key**: Toggle Auto Crystal on/off

## Configuration
Config file: `config/autocrystal.cfg`

- `enabled`: Enable/disable the mod (default: true)
- `attackDelay`: Delay in ticks before attacking (default: 0 = instant)
- `attackRange`: Maximum range to attack crystals (default: 6.0 blocks)

## Technical Details
- **Package**: asd.itamio.autocrystal
- **Mod ID**: autocrystal
- **Version**: 1.0.0
- **Author**: Itamio
- **Minecraft Version**: 1.12.2
- **Mod Loader**: Forge

## Installation
1. Download `Auto-Crystal-1.0.0.jar`
2. Place in `.minecraft/mods/` folder
3. Launch Minecraft 1.12.2 with Forge

## PvP Usage
This mod is designed for crystal PvP combat. When enabled:
- Place crystals near opponents
- Crystals explode instantly without manual clicking
- Faster reaction time in combat
- Toggle off when not needed

## Files
- **JAR**: `Auto-Crystal-1.0.0.jar`
- **Source**: `src/main/java/asd/itamio/autocrystal/`
- **Config**: Auto-generated on first run

## Build Info
- Built with ForgeGradle 2.3
- Java 8 compatible
- No external dependencies

## Notes
- Works on both client and server (client-side mod)
- Respects attack range configuration
- Can be toggled mid-game
- Saves toggle state to config

---

**Ready for testing and Modrinth upload!**
