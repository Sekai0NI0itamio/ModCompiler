# Hostile Mobs Mod

A Minecraft 1.12.2 Forge mod that makes hostile mobs actively hunt players with enhanced AI capabilities.

## Features

### Core Functionality
- **Automatic Mob Activation**: When a player joins the world, all hostile mobs in loaded chunks are immediately set to target and pathfind to the player
- **Continuous Tracking**: Hostile mobs continuously update their targets to track the nearest player
- **Enhanced Follow Range**: Mobs can detect and pursue players from much greater distances

### Zombie Block Placement System
The mod introduces an advanced pathfinding system specifically for zombies:

- **Intelligent Block Placement**: Zombies can place blocks to overcome obstacles and reach players
- **Limited Inventory**: Each zombie spawns with 15-20 random blocks from the following types:
  - Cobblestone
  - Dirt
  - Coarse Dirt
  - Mossy Cobblestone
  
- **Smart Pathfinding**: Custom pathfinding engine that:
  - Calculates paths that may include block placements
  - Considers vertical obstacles and creates climbing paths
  - Uses A* algorithm with block placement cost calculations
  - Prioritizes efficient paths while conserving blocks

### AI Behavior
- Zombies will analyze terrain and determine when block placement is necessary
- Blocks are placed strategically to create bridges, stairs, or platforms
- The AI recalculates paths after each block placement
- Zombies can place blocks up to 4 blocks away from their position

## Configuration

The mod creates a configuration file at `config/hostilemobs.txt`:

```
# Hostile Mobs Configuration
# This mod makes hostile mobs actively hunt players

# Enable zombies to place blocks to reach players
enable_zombie_block_placing=true

# Minimum blocks zombies spawn with
min_zombie_blocks=15

# Maximum blocks zombies spawn with
max_zombie_blocks=20
```

## Technical Details

### Main Components

1. **HostileMobsMod.java**: Core mod class that handles:
   - Player login events
   - Entity spawn events
   - Periodic mob target updates
   - Zombie AI initialization

2. **EntityAIZombieBlockPlace.java**: Custom AI task that:
   - Manages zombie block inventory
   - Detects pathfinding obstacles
   - Places blocks strategically
   - Recalculates paths after placement

3. **ZombiePathfinder.java**: Advanced pathfinding engine that:
   - Implements A* pathfinding with block placement
   - Calculates optimal paths considering block costs
   - Supports up to 500 search nodes
   - Tracks blocks used in path calculation

4. **HostileMobsConfig.java**: Configuration management system

### How It Works

1. When a player joins, the mod scans all loaded entities
2. Hostile mobs (implementing IMob interface) are identified
3. Each hostile mob's attack target is set to the nearest player
4. Zombies receive special AI task for block placement
5. Every 20 ticks (1 second), mob targets are refreshed
6. Zombies continuously evaluate their path and place blocks when needed

### Pathfinding Algorithm

The custom pathfinding uses:
- **A* search algorithm** for optimal path calculation
- **Cost function**: Movement cost + block placement penalty
- **Heuristic**: Manhattan distance with vertical penalty
- **Block placement cost**: 2.0 (makes zombies prefer existing paths)
- **Climbing cost**: 1.5 (vertical movement is more expensive)

## Installation

1. Install Minecraft Forge 1.12.2
2. Place the mod JAR in your `mods` folder
3. Launch Minecraft
4. Configuration file will be auto-generated on first run

## Compatibility

- Minecraft Version: 1.12.2
- Forge Version: Compatible with standard 1.12.2 Forge
- Should be compatible with most other mods
- May conflict with mods that heavily modify mob AI

## Inspired By

This mod was inspired by the Mob Vision mod, which increases mob detection ranges. Hostile Mobs takes the concept further by adding intelligent pathfinding and block placement capabilities.

## Future Enhancements

Potential features for future versions:
- Configurable block types per zombie
- Other mob types with special abilities
- Difficulty scaling based on distance
- Block placement animations
- Sound effects for block placement
- Support for other Minecraft versions
