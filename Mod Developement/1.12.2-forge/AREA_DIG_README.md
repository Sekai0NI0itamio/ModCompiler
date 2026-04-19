# Area Dig - Minecraft 1.12.2 Forge Mod

## Description
This mod adds the "Area Dig" enchantment that can be applied to pickaxes, axes, and shovels. When you break a block with this enchantment, it automatically breaks blocks in a cube around it!

## Features
- New enchantment: Area Dig (levels 1-5)
- Can be obtained from enchanting tables
- Works on pickaxes, axes, and shovels
- Breaks blocks in a 3D cube around the mined block
- Cube radius = enchantment level + 1

## Enchantment Details

### Levels and Radius
- Level 1: 2-block radius cube (5x5x5 = 125 blocks)
- Level 2: 3-block radius cube (7x7x7 = 343 blocks)
- Level 3: 4-block radius cube (9x9x9 = 729 blocks)
- Level 4: 5-block radius cube (11x11x11 = 1,331 blocks)
- Level 5: 6-block radius cube (13x13x13 = 2,197 blocks)

### Compatible Tools
- Pickaxes (all types)
- Axes (all types)
- Shovels (all types)

### How It Works
1. Enchant your tool with Area Dig at an enchanting table
2. Mine any block with the enchanted tool
3. All blocks within the radius are automatically broken
4. Tool durability is consumed for each block broken
5. Drops are collected normally

### Smart Features
- Won't break bedrock
- Won't break air blocks
- Only breaks blocks the tool can harvest
- Tool takes durability damage for each block

## Usage Tips
- Start with Level 1 for controlled mining
- Higher levels are great for clearing large areas quickly
- Be careful with Level 5 - it breaks a LOT of blocks!
- Make sure your tool has good durability or Unbreaking
- Combine with Fortune for maximum ore collection

## Installation
1. Install Minecraft Forge 1.12.2
2. Place the mod jar file in your `mods` folder
3. Launch Minecraft
4. Enchant your tools at an enchanting table!

## Building from Source
```bash
cd "Mod Developement/1.12.2-forge"
./gradlew clean build
```

The compiled jar will be in `build/libs/`

## Version
1.0.0 - Initial Release

## License
Feel free to use and modify as needed.
