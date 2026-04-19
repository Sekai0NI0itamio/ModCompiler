# Auto Feeder - Testing Guide

## Overview
Automatically feeds animals from nearby chests to breed them. No manual feeding required!

## How It Works
- Scans for chests in loaded chunks
- Finds animals within 8 blocks of each chest
- Automatically feeds them appropriate food from the chest
- Animals enter love mode and can breed

## Supported Animals & Food
- **Cows**: Wheat
- **Sheep**: Wheat
- **Pigs**: Carrot, Potato, Beetroot
- **Chickens**: Seeds (wheat, melon, pumpkin, beetroot)
- **Horses**: Golden Apple, Golden Carrot
- **Rabbits**: Carrot, Golden Carrot

## Testing Instructions

1. **Install the mod** - Copy `Auto-Feeder-1.0.0.jar` to your mods folder

2. **Set up a test area**:
   - Place a chest
   - Put appropriate food in the chest (e.g., wheat for cows)
   - Spawn 2+ animals within 8 blocks of the chest
   - Wait for them to grow up if they're babies

3. **What to expect**:
   - Every 5 seconds, the mod checks for animals
   - If animals are ready to breed, it feeds them from the chest
   - You'll see heart particles and hear a sound
   - Animals enter love mode and can breed

4. **Things to verify**:
   - ✓ Animals get fed automatically
   - ✓ Food is consumed from chest
   - ✓ Heart particles appear
   - ✓ Sound plays when feeding
   - ✓ Animals enter love mode
   - ✓ Breeding works correctly
   - ✓ Only feeds adult animals
   - ✓ Doesn't feed animals already in love

## Configuration
Config file: `config/autofeeder.cfg`

- `enableAutoFeeder` - Enable/disable the mod (default: true)
- `searchRange` - Range to search for animals (default: 8 blocks)
- `feedingInterval` - Ticks between feeding (default: 100 = 5 seconds)
- `feedCows/Pigs/Sheep/Chickens/Horses/Rabbits` - Enable per animal type
- `playSound` - Play sound when feeding (default: true)
- `showParticles` - Show heart particles (default: true)

## Known Limitations
- Only works with chests (not hoppers, barrels, etc.)
- Feeds one animal per chest per cycle
- Requires animals to be within 8 blocks of chest

## Testing Checklist
- [ ] Cows with wheat
- [ ] Pigs with carrots
- [ ] Sheep with wheat
- [ ] Chickens with seeds
- [ ] Multiple animals at once
- [ ] Food consumption from chest
- [ ] Breeding after feeding
- [ ] Config options work
