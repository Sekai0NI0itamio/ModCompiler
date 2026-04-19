# Auto Tool Swap - Testing Instructions

## Overview
Auto Tool Swap automatically switches to the best tool when you start breaking a block. No more manually switching between pickaxe, axe, and shovel!

## Features
- Automatically switches to the best tool for the block you're breaking
- Searches hotbar by default (configurable to search entire inventory)
- Prefers tools with Fortune or Silk Touch enchantments (configurable)
- Optional: Switch back to previous item after breaking block
- Considers tool speed and enchantments
- Works in survival and creative mode

## Configuration
Config file: `config/autotoolswap.cfg`

```
general {
    # Enable automatic tool swapping
    B:enableAutoSwap=true
    
    # Only search hotbar for tools (slots 0-8)
    # If false, searches entire inventory
    B:hotbarOnly=true
    
    # Switch back to previous item after breaking block
    B:switchBack=false
    
    # Prefer tools with Fortune enchantment
    B:preferFortune=true
    
    # Prefer tools with Silk Touch enchantment
    B:preferSilkTouch=false
}
```

## Testing Steps

### 1. Install the Mod
- Copy `Auto-Tool-Swap-1.0.0.jar` to mods folder
- Launch Minecraft 1.12.2 with Forge

### 2. Test Basic Tool Swapping
1. Create a new world
2. Give yourself multiple tools:
   - `/give @p minecraft:diamond_pickaxe`
   - `/give @p minecraft:diamond_axe`
   - `/give @p minecraft:diamond_shovel`
3. Place different blocks (stone, wood, dirt)
4. Hold any item (or empty hand)
5. Start breaking stone - should auto-switch to pickaxe
6. Start breaking wood - should auto-switch to axe
7. Start breaking dirt - should auto-switch to shovel

### 3. Test Hotbar vs Full Inventory
1. Put tools in hotbar (slots 0-8)
2. Break blocks - should find and use tools
3. Move tools to main inventory (slots 9+)
4. Break blocks - should NOT find tools (hotbarOnly=true)
5. Edit config: `hotbarOnly=false`
6. Break blocks - should find tools in main inventory

### 4. Test Enchantment Preferences
1. Give yourself enchanted tools:
   - `/give @p minecraft:diamond_pickaxe 1 0 {ench:[{id:35,lvl:3}]}` (Fortune III)
   - `/give @p minecraft:diamond_pickaxe 1 0 {ench:[{id:33,lvl:1}]}` (Silk Touch)
   - `/give @p minecraft:diamond_pickaxe` (no enchantments)
2. Break stone ore
3. Should use Fortune III pickaxe (preferFortune=true)
4. Edit config: `preferFortune=false`, `preferSilkTouch=true`
5. Break stone ore again
6. Should use Silk Touch pickaxe

### 5. Test Switch Back Feature
1. Hold a sword in your hand
2. Edit config: `switchBack=true`
3. Break a block
4. After breaking, should switch back to sword
5. This is useful for combat situations

### 6. Test Tool Speed Priority
1. Give yourself tools of different materials:
   - `/give @p minecraft:wooden_pickaxe`
   - `/give @p minecraft:stone_pickaxe`
   - `/give @p minecraft:iron_pickaxe`
   - `/give @p minecraft:diamond_pickaxe`
2. Break stone
3. Should use diamond pickaxe (fastest)
4. Remove diamond pickaxe
5. Should use iron pickaxe (next fastest)

### 7. Test Efficiency Enchantment
1. Give yourself pickaxes with different Efficiency levels:
   - `/give @p minecraft:diamond_pickaxe 1 0 {ench:[{id:32,lvl:5}]}` (Efficiency V)
   - `/give @p minecraft:diamond_pickaxe 1 0 {ench:[{id:32,lvl:1}]}` (Efficiency I)
   - `/give @p minecraft:diamond_pickaxe` (no enchantments)
2. Break stone
3. Should prefer Efficiency V (fastest mining)

### 8. Test Wrong Tool Scenarios
1. Hold a sword
2. Break stone
3. Should switch to pickaxe (correct tool)
4. Hold a pickaxe
5. Break wood
6. Should switch to axe (correct tool)

### 9. Test No Tool Available
1. Remove all tools from inventory
2. Break a block with your hand
3. Should not crash or error
4. Should continue breaking with hand

### 10. Test Disable Feature
1. Edit config: `enableAutoSwap=false`
2. Launch Minecraft
3. Break blocks - should NOT auto-switch tools
4. This confirms the config works

## Expected Behavior
- ✅ Automatically switches to best tool when breaking blocks
- ✅ Prefers faster tools (diamond > iron > stone > wood)
- ✅ Prefers tools with Fortune/Silk Touch (configurable)
- ✅ Considers Efficiency enchantment
- ✅ Only searches hotbar by default (configurable)
- ✅ Optional switch back to previous item
- ✅ No crashes with missing tools
- ✅ Works in survival and creative mode

## Use Cases
- **Mining**: Auto-switch between pickaxe for stone and shovel for dirt
- **Forestry**: Auto-switch to axe when chopping trees
- **Building**: Auto-switch to appropriate tool for each block
- **Combat**: Switch back to weapon after breaking blocks
- **Efficiency**: Never manually switch tools again

## Balance Considerations
- Only switches to tools in inventory (not infinite)
- Hotbar-only by default (prevents inventory clutter)
- Respects tool durability (uses available tools)
- Optional switch back (for combat situations)

## Known Limitations
- Only switches when you START breaking a block
- Doesn't switch mid-break
- Requires tools in inventory (hotbar by default)
- Fortune and Silk Touch preferences are mutually exclusive in practice

## Performance Notes
- Minimal performance impact
- Only checks when breaking blocks
- Efficient inventory search
- No lag with large inventories

## Files
- Mod JAR: `build/libs/Auto-Tool-Swap-1.0.0.jar`
- Source: `src/main/java/asd/itamio/autotoolswap/`
- Config: `config/autotoolswap.cfg` (generated on first run)

## Version
- Mod Version: 1.0.0
- Minecraft Version: 1.12.2
- Forge Version: 14.23.5.2860
