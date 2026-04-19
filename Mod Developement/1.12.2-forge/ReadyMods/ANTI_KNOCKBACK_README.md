# Anti Knockback - Minecraft 1.12.2 Mod

## Overview
Anti Knockback removes all knockback (both horizontal and vertical) that the player takes. Perfect for PvP combat!

## Features
- **No Horizontal Knockback**: Prevents being pushed sideways
- **No Vertical Knockback**: Prevents being launched upward
- **Toggle On/Off**: Press K to enable/disable
- **Visual Feedback**: Chat message shows current status (ON/OFF)
- **Configurable**: Adjust horizontal/vertical separately in config

## How It Works
1. Detects when knockback is applied to the player
2. Cancels the knockback event
3. Restores player motion to pre-knockback state
4. Works for all damage sources (mobs, players, explosions, etc.)

## Controls
- **K Key**: Toggle Anti Knockback on/off

## Configuration
Config file: `config/antikb.cfg`

- `enabled`: Enable/disable the mod (default: true)
- `cancelHorizontal`: Cancel horizontal knockback (default: true)
- `cancelVertical`: Cancel vertical knockback (default: true)

## Technical Details
- **Package**: asd.itamio.antikb
- **Mod ID**: antikb
- **Version**: 1.0.0
- **Author**: Itamio
- **Minecraft Version**: 1.12.2
- **Mod Loader**: Forge

## Installation
1. Download `Anti-Knockback-1.0.0.jar`
2. Place in `.minecraft/mods/` folder
3. Launch Minecraft 1.12.2 with Forge

## PvP Usage
This mod is designed for PvP combat:
- No knockback from enemy attacks
- Stay in position during fights
- Better combo potential
- Toggle off when not needed

## Implementation
- Uses `LivingKnockBackEvent` to cancel knockback
- Monitors player motion changes
- Restores motion to pre-knockback values
- Client-side only (works on any server)

## Files
- **JAR**: `Anti-Knockback-1.0.0.jar`
- **Source**: `src/main/java/asd/itamio/antikb/`
- **Config**: Auto-generated on first run

## Build Info
- Built with ForgeGradle 2.3
- Java 8 compatible
- No external dependencies

## Notes
- Client-side mod (server doesn't need it)
- Works on all servers
- Can be toggled mid-game
- Saves toggle state to config

---

**Ready for testing and Modrinth upload!**
