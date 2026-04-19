# No Hostile Mobs - Minecraft 1.12.2 Forge Mod

## Description
This mod prevents hostile mobs from spawning in your world, regardless of the difficulty setting. All blocked mobs are fully configurable through a config file that supports real-time updates.

## Features
- Blocks hostile mob spawns completely
- Works on any difficulty (Peaceful, Easy, Normal, Hard)
- Fully configurable mob list
- Real-time config reloading (changes apply within 5 seconds without restart)
- Includes all vanilla hostile mobs by default

## Default Blocked Mobs
The mod blocks these mobs by default:
- Zombie
- Skeleton
- Creeper
- Spider
- Cave Spider
- Enderman
- Blaze
- Ghast
- Slime
- Magma Cube
- Witch
- Silverfish
- Endermite
- Guardian
- Elder Guardian
- Shulker
- Husk
- Stray
- Zombie Villager
- Wither Skeleton
- Zombie Pigman
- Evoker
- Vindicator
- Vex

## Configuration
After running the mod for the first time, a config file will be created at:
`config/nohostilemobs.cfg`

### Editing the Config
1. Open `config/nohostilemobs.cfg` in any text editor
2. Find the `blockedMobs` section under `general`
3. Add or remove mob entity IDs as needed
4. Save the file
5. Changes will be applied automatically within 5 seconds (no restart needed!)

### Example Config Entry
```
general {
    S:blockedMobs <
        minecraft:zombie
        minecraft:skeleton
        minecraft:creeper
     >
}
```

### Adding Modded Mobs
To block mobs from other mods, add their entity ID to the list. For example:
```
modname:mob_entity_id
```

## Installation
1. Install Minecraft Forge 1.12.2
2. Place the mod jar file in your `mods` folder
3. Launch Minecraft
4. The config file will be created automatically on first run

## Building from Source
```bash
cd "Mod Developement/1.12.2-forge"
./gradlew build
```

The compiled jar will be in `build/libs/`

## Version
1.0.0 - Initial Release

## License
Feel free to use and modify as needed.
