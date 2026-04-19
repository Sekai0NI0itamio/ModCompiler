# Hostile Mobs - Source Code

A Minecraft 1.12.2 Forge mod that makes hostile mobs actively hunt players with enhanced AI.

## Key Features

- All hostile mobs target and pathfind to players when they join the world
- Zombies can place blocks (cobblestone, dirt, coarse dirt, mossy cobblestone) to reach players
- Each zombie spawns with 15-20 random blocks
- Custom pathfinding engine that calculates paths including block placement
- Continuous mob target updates every second

## Implementation Details

### Main Components

1. **HostileMobsMod.java** - Core mod class
   - Handles player login events to activate all hostile mobs
   - Manages entity spawn events to set up zombies with block-placing AI
   - Periodically updates mob targets every 20 ticks

2. **EntityAIZombieBlockPlace.java** - Custom AI task for zombies
   - Manages zombie block inventory (15-20 blocks per zombie)
   - Detects pathfinding obstacles
   - Places blocks strategically to overcome barriers
   - Uses WeakHashMap to persist inventory across AI resets

3. **ZombiePathfinder.java** - Advanced pathfinding engine
   - A* algorithm with block placement support
   - Calculates optimal paths considering block placement costs
   - Supports up to 500 search nodes
   - Block placement cost: 2.0 (encourages using existing paths)

4. **HostileMobsConfig.java** - Configuration system
   - Hot-reloadable config file
   - Controls zombie block-placing feature
   - Configurable block count ranges

## How It Works

1. When a player joins, all loaded hostile mobs (IMob interface) are activated
2. Each hostile mob's attack target is set to the nearest player
3. Zombies receive special AI task for intelligent block placement
4. Every 20 ticks, mob targets refresh to track nearest player
5. Zombies analyze terrain and place blocks when needed to reach targets

## Block Types

Zombies can place:
- Cobblestone
- Dirt
- Coarse Dirt
- Mossy Cobblestone

## Configuration

Config file: `config/hostilemobs.txt`

```
enable_zombie_block_placing=true
min_zombie_blocks=15
max_zombie_blocks=20
```

## Inspired By

Based on the Mob Vision mod's approach to mob targeting, extended with intelligent pathfinding and block placement.
