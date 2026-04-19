# Auto Eat

Never worry about manually eating food again! Auto Eat automatically consumes food from your inventory when your hunger drops below a configurable threshold.

## Features

### Smart Food Selection
- Automatically selects the best food based on saturation values
- Prioritizes more efficient foods (e.g., cooked beef over bread)
- Checks your entire inventory for available food

### Configurable Hunger Threshold
- Set when the mod should start eating (default: 14/20 hunger)
- Fully customizable via config file
- Prevents wasting food by eating too early

### Food Blacklist
- Configure which foods should never be auto-eaten
- Default blacklist includes:
  - Spider Eyes
  - Rotten Flesh
  - Poisonous Potatoes
  - Golden Apples (both normal and enchanted)
  - Chorus Fruit
- Add or remove any food items from the blacklist

### Performance Optimized
- Checks hunger only once per second
- Minimal impact on game performance
- Works seamlessly in the background

## Configuration

The mod creates a config file at `config/autoeat.cfg` on first launch.

```
general {
    # Hunger level at which to auto-eat (0-20, where 20 is full)
    I:hungerThreshold=14
    
    # Foods that will never be auto-eaten
    # Format: modid:itemname
    S:blacklistedFoods <
        minecraft:spider_eye
        minecraft:rotten_flesh
        minecraft:poisonous_potato
        minecraft:golden_apple
        minecraft:chorus_fruit
    >
}
```

## How It Works

1. Every second, the mod checks your current hunger level
2. If hunger is below the threshold, it searches your inventory for food
3. It selects the food with the highest saturation value (most efficient)
4. It automatically eats one piece of that food
5. Blacklisted foods are always skipped

## Use Cases

- **AFK Farming**: Stay fed while farming or fishing
- **Exploration**: Focus on exploring without hunger management
- **Combat**: Keep your hunger up during long battles
- **Building**: Concentrate on building without interruptions

## Compatibility

- Works with all vanilla foods
- Compatible with modded foods
- Client-side mod (optional on servers)
- No known mod conflicts

## FAQ

**Q: Can I disable auto-eating for specific foods?**  
A: Yes! Add any food to the blacklist in the config file using the format `modid:itemname`.

**Q: Will it eat my golden apples?**  
A: No, golden apples are blacklisted by default to preserve them for important situations.

**Q: Does it work on servers?**  
A: Yes, but it's a client-side mod, so it only needs to be installed on the client.

**Q: Can I change when it starts eating?**  
A: Yes, adjust the `hungerThreshold` value in the config (0-20, where 20 is full hunger).

## Support

If you encounter any issues or have suggestions, please report them on the issue tracker.
