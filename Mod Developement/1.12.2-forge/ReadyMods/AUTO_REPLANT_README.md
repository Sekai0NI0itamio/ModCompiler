# Auto Replant - Minecraft 1.12.2 Forge Mod

## Description
Automatically replants crops and trees when you harvest them! No more tedious replanting - just harvest and the mod does the rest.

## Features
- ✅ Auto-replants wheat (uses wheat seeds from inventory)
- ✅ Auto-replants carrots (uses carrots from inventory)
- ✅ Auto-replants potatoes (uses potatoes from inventory)
- ✅ Auto-replants beetroot (uses beetroot seeds from inventory)
- ✅ Auto-replants cocoa beans (uses cocoa beans from inventory)
- ✅ Auto-replants trees (uses saplings from inventory)
- ✅ Only replants fully grown crops
- ✅ Consumes seeds/saplings from your inventory
- ✅ Works in survival mode
- ✅ Free replanting in creative mode

## How It Works

### Crops (Wheat, Carrots, Potatoes, Beetroot)
1. Harvest a fully grown crop
2. Mod checks your inventory for seeds/items
3. If found, automatically replants the crop
4. Consumes one seed/item from your inventory

### Cocoa Beans
1. Harvest fully grown cocoa pods
2. Mod checks for cocoa beans in inventory
3. Replants cocoa on the same jungle log
4. Maintains the same facing direction

### Trees
1. Break a log block
2. Mod finds the bottom of the tree
3. Checks for saplings in inventory
4. Plants a sapling at the base

## Installation

```bash
# Copy to your Minecraft mods folder
cp "Mod Developement/1.12.2-forge/ReadyMods/Auto-Replant-1.0.0.jar" \
   ~/Library/Application\ Support/minecraft/mods/
```

## Testing Guide

### Test 1: Wheat
1. Plant wheat seeds
2. Wait for them to grow (or use bonemeal)
3. Harvest the wheat
4. ✅ Should auto-replant if you have wheat seeds in inventory

### Test 2: Carrots
1. Plant carrots
2. Wait for them to grow
3. Harvest the carrots
4. ✅ Should auto-replant if you have carrots in inventory

### Test 3: Potatoes
1. Plant potatoes
2. Wait for them to grow
3. Harvest the potatoes
4. ✅ Should auto-replant if you have potatoes in inventory

### Test 4: Beetroot
1. Plant beetroot seeds
2. Wait for them to grow
3. Harvest the beetroot
4. ✅ Should auto-replant if you have beetroot seeds in inventory

### Test 5: Cocoa Beans
1. Find a jungle tree
2. Plant cocoa beans on the log
3. Wait for them to grow (or use bonemeal)
4. Harvest the cocoa pods
5. ✅ Should auto-replant if you have cocoa beans in inventory

### Test 6: Trees
1. Plant a sapling
2. Wait for it to grow into a tree
3. Break a log block
4. ✅ Should plant a sapling at the base if you have saplings in inventory

## Important Notes

### Inventory Requirements
- You MUST have the appropriate seed/sapling in your inventory
- The mod will consume one item per replant
- In creative mode, no items are consumed

### Crop Growth
- Only fully grown crops are replanted
- Partially grown crops won't trigger replanting
- This prevents accidental replanting

### Trees
- Replants at the base of the tree (where you broke the log)
- Requires dirt or grass below
- Uses oak saplings by default

## Tips

1. **Keep seeds in inventory**: Always carry extra seeds when farming
2. **Hotbar placement**: Keep seeds in hotbar for easy access
3. **Mass farming**: Great for large farms - just harvest everything!
4. **Tree farming**: Break logs from bottom up for best results
5. **Creative mode**: Test without consuming items

## Troubleshooting

### Crops not replanting?
- Check you have seeds/items in inventory
- Make sure crop was fully grown
- Verify you're in survival mode (or creative)

### Trees not replanting?
- Check you have saplings in inventory
- Make sure there's dirt/grass below
- Break the bottom log first

### Cocoa not replanting?
- Check you have cocoa beans in inventory
- Make sure cocoa was fully grown (brown color)
- Verify it's on a jungle log

## Version
1.0.0 - Initial Release

## Author
Itamio

## Package
asd.itamio.autoreplant

## License
Feel free to use and modify as needed.
