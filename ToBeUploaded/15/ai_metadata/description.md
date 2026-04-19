# Infinite Water Bucket

Never run out of water again! This simple yet powerful mod makes water buckets infinite - they never empty when you place water.

## Features

### Infinite Water (Enabled by Default)
- Water buckets never empty when placing water
- Works in survival and creative mode
- Perfect for building, farming, and redstone contraptions
- No more tedious bucket refilling

### Optional Infinite Lava
- Can be enabled in config (disabled by default)
- Lava buckets never empty when placing lava
- WARNING: Very overpowered - use with caution!

### Optional Infinite Milk
- Can be enabled in config (disabled by default)
- Milk buckets never empty when drinking
- Unlimited potion effect removal

## How It Works

When you use a water bucket to place water, it normally turns into an empty bucket. This mod detects the bucket use and automatically refills it after 1 tick, keeping the filled bucket in your inventory.

The same logic applies to lava and milk buckets when enabled in the config.

## Configuration

Fully customizable via `config/infinitebucket.cfg`:

```
general {
    # Enable infinite water buckets
    B:enableInfiniteWater=true
    
    # Enable infinite lava buckets (WARNING: Can be overpowered)
    B:enableInfiniteLava=false
    
    # Enable infinite milk buckets
    B:enableInfiniteMilk=false
}
```

## Use Cases

### Building & Landscaping
- Fill large pools, moats, or water features with a single bucket
- Create waterfalls and fountains without bucket management
- Build underwater structures more efficiently

### Farming
- Set up irrigation systems quickly
- Create infinite water sources for crops
- No need to carry multiple water buckets

### Redstone Contraptions
- Build water-based redstone devices without worrying about bucket supply
- Create water elevators and transportation systems
- Experiment with water mechanics freely

### Quality of Life
- Removes the tedium of refilling buckets
- Saves inventory space (only need 1 bucket)
- Works seamlessly in survival mode

## Balance & Design Philosophy

### Why Water is Enabled by Default
Water is already renewable in vanilla Minecraft through infinite water sources (2x2 pool). This mod simply removes the tedium of constantly refilling buckets. It doesn't add any new capabilities - just convenience.

### Why Lava is Disabled by Default
Unlike water, lava is not renewable in vanilla Minecraft (except in 1.17+ with dripstone). Infinite lava buckets would be extremely overpowered, providing unlimited fuel and building material. We've disabled it by default to preserve game balance, but you can enable it if you want.

### Why Milk is Disabled by Default
Milk buckets provide a powerful effect (removing all potion effects). Making them infinite could trivialize certain challenges. However, it's available as an option for those who want it.

## Compatibility

- **Client-side**: Optional (visual only)
- **Server-side**: Required (handles bucket logic)
- Works with vanilla buckets (water, lava, milk)
- No known mod conflicts
- Works in all dimensions (Overworld, Nether, End)

## Technical Details

- Bucket refills after 1 tick (0.05 seconds)
- No item duplication exploits
- Handles edge cases (full inventory, dropped buckets, etc.)
- Minimal performance impact
- Server-authoritative (prevents cheating)

## FAQ

**Q: Does this work on servers?**  
A: Yes! The mod must be installed on the server. Clients don't need it but can install it for config access.

**Q: Can I enable infinite lava?**  
A: Yes, set `enableInfiniteLava=true` in the config. Be aware this can be very overpowered.

**Q: Will this work with modded buckets?**  
A: Only vanilla buckets (water, lava, milk) are supported. Modded buckets may not work.

**Q: Does the bucket stay in the same inventory slot?**  
A: Yes, the bucket is refilled in place after 1 tick.

**Q: Is this balanced for survival?**  
A: Water is already renewable in vanilla, so this just removes tedium. Lava and milk are disabled by default for balance.

**Q: Can I disable the infinite water feature?**  
A: Yes, set `enableInfiniteWater=false` in the config to return to vanilla behavior.

## Known Limitations

- Only works with vanilla buckets (water, lava, milk)
- Modded buckets are not supported
- Brief 1-tick delay before bucket refills (usually not noticeable)

## Support

If you encounter issues or have suggestions, please report them on the issue tracker.
