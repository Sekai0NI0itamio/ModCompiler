# Instant Bridge

Never fall into gaps again! Instant Bridge automatically places blocks beneath you while you're sneaking and walking, making bridging across chasms effortless.

## Features

### Automatic Block Placement
- Hold Shift (sneak) and walk forward
- Blocks automatically place beneath you
- Perfect for crossing gaps, lava lakes, and chasms
- No more tedious manual block placement

### Smart Activation
- Only activates when moving (prevents accidental placement)
- Requires sneaking by default (configurable)
- Configurable placement delay (default: 0.25 seconds)
- Won't place blocks where they already exist

### Safe & Intelligent
- Skips dangerous blocks (TNT, sand, gravel, bedrock)
- Uses any block from your inventory
- Consumes blocks in survival mode
- Free block placement in creative mode
- No duplication glitches

## How It Works

1. Make sure you have blocks in your inventory
2. Hold Shift to sneak
3. Walk forward
4. Blocks automatically place beneath you as you move
5. Cross gaps and build bridges effortlessly!

The mod searches your inventory for placeable blocks and uses them to create a bridge beneath you. It places blocks every 5 ticks (0.25 seconds) by default, giving you smooth bridging without being too fast.

## Configuration

Fully customizable via `config/instantbridge.cfg`:

```
general {
    # Enable instant bridge feature
    B:enableInstantBridge=true
    
    # Require player to be sneaking to place blocks
    B:requireSneaking=true
    
    # Only place blocks when player is moving
    B:placeOnlyWhenMoving=true
    
    # Delay between block placements in ticks (1-20)
    # 5 ticks = 0.25 seconds (default)
    # 1 tick = 0.05 seconds (very fast)
    # 20 ticks = 1 second (slow)
    I:placementDelay=5
}
```

## Use Cases

### Exploration & Adventure
- Cross chasms and ravines quickly
- Bridge across lava lakes in the Nether
- Traverse the End void safely
- Explore caves without stopping to place blocks

### Building & Construction
- Create long walkways and platforms efficiently
- Build bridges between structures
- Construct elevated pathways
- Speed up large-scale building projects

### Skyblock & Challenge Maps
- Bridge between islands effortlessly
- Navigate parkour challenges
- Build connections in void worlds
- Essential for skyblock gameplay

### PvP & Minigames
- Quick escapes across gaps
- Rapid bridge building in combat
- Efficient movement in arena maps
- Tactical positioning advantages

## Balance & Design

### Why It's Balanced
- **Requires Resources**: You need blocks in your inventory (not infinite)
- **Placement Delay**: Can't instant-bridge (default 0.25 second delay)
- **Movement Required**: Can't build towers (only works while moving)
- **Sneaking Required**: Prevents accidental activation
- **Block Consumption**: Uses real blocks from inventory in survival

### Safety Features
- Skips TNT (prevents accidental explosions)
- Skips sand/gravel (prevents falling blocks)
- Skips bedrock (prevents impossible placement)
- Only places in air/replaceable blocks

## Compatibility

- **Client-side**: Optional (visual only)
- **Server-side**: Required (handles block placement)
- Works in all dimensions (Overworld, Nether, End)
- Compatible with most building mods
- No known mod conflicts

## Technical Details

- Placement delay: Configurable 1-20 ticks (0.05-1 second)
- Block search: Scans entire inventory for placeable blocks
- Sound effects: Plays block placement sounds
- Creative mode: Doesn't consume blocks
- Survival mode: Consumes one block per placement

## FAQ

**Q: Does this work on servers?**  
A: Yes! The mod must be installed on the server. Clients don't need it but can install it for config access.

**Q: Can I change the activation key?**  
A: The mod uses the vanilla sneak key (Shift by default). You can disable the sneaking requirement in the config.

**Q: Will it place blocks when I'm standing still?**  
A: No, by default it only places blocks when you're moving. This prevents accidental placement.

**Q: What blocks does it use?**  
A: It uses the first placeable block it finds in your inventory, skipping dangerous blocks like TNT and falling blocks like sand.

**Q: Can I make it faster?**  
A: Yes, set `placementDelay=1` in the config for very fast placement (0.05 seconds between blocks).

**Q: Does it work in creative mode?**  
A: Yes, and it doesn't consume blocks in creative mode.

**Q: Can I disable the sneaking requirement?**  
A: Yes, set `requireSneaking=false` in the config. WARNING: This will place blocks whenever you walk, which can be annoying.

**Q: Will it work for building towers?**  
A: No, it only places blocks beneath you when moving horizontally. It's designed for bridging, not tower building.

## Known Limitations

- Only places blocks directly beneath player (not diagonal)
- Requires horizontal movement (doesn't work for towers)
- Uses first available block in inventory (not configurable)
- Skips falling blocks (sand, gravel) for safety

## Support

If you encounter issues or have suggestions, please report them on the issue tracker.
