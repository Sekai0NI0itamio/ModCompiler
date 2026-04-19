# Testing Area Dig - Fixed Version

## Installation

1. Remove old version (if installed):
   ```bash
   rm ~/Library/Application\ Support/minecraft/mods/Area-Dig-1.0.0.jar
   ```

2. Install fixed version:
   ```bash
   cp "Mod Developement/1.12.2-forge/ReadyMods/Area-Dig-1.0.0-FIXED.jar" \
      ~/Library/Application\ Support/minecraft/mods/
   ```

3. Launch Minecraft 1.12.2 with Forge

## Testing Steps

### Test 1: Basic Functionality

1. **Get a tool**: Diamond shovel, pickaxe, or axe
2. **Enchant it**: Use enchanting table
3. **Look for**: "Area Dig" enchantment (levels I-V)
4. **Apply enchantment**: Click to enchant

### Test 2: Shovel on Dirt (Recommended First Test)

1. Find a flat dirt/grass area
2. Enchant a diamond shovel with Area Dig III
3. Break one dirt block
4. **Expected**: A 9x9x9 cube of dirt/grass breaks around it
5. **Check logs**: Should see "Area Dig activated! Level: 3"

### Test 3: Pickaxe on Stone

1. Find a stone area (or go underground)
2. Enchant a diamond pickaxe with Area Dig II
3. Break one stone block
4. **Expected**: A 7x7x7 cube of stone breaks
5. **Check**: Stone drops should appear

### Test 4: Axe on Wood

1. Find a tree
2. Enchant a diamond axe with Area Dig I
3. Break one log block
4. **Expected**: A 5x5x5 cube of wood breaks
5. **Check**: Logs and leaves break if in range

### Test 5: Different Levels

Test each level to verify radius:

- **Level I**: Break 1 block → 5x5x5 cube (125 blocks total)
- **Level II**: Break 1 block → 7x7x7 cube (343 blocks total)
- **Level III**: Break 1 block → 9x9x9 cube (729 blocks total)
- **Level IV**: Break 1 block → 11x11x11 cube (1,331 blocks total)
- **Level V**: Break 1 block → 13x13x13 cube (2,197 blocks total)

### Test 6: Tool Durability

1. Check tool durability before mining
2. Mine one block with Area Dig
3. **Expected**: Tool loses durability for each block broken
4. **Note**: In creative mode, tool doesn't lose durability

### Test 7: Creative Mode

1. Switch to creative mode
2. Use enchanted tool
3. **Expected**: Blocks break, but tool doesn't lose durability

## Verification Checklist

- [ ] Enchantment appears in enchanting table
- [ ] Can apply to pickaxe
- [ ] Can apply to axe
- [ ] Can apply to shovel
- [ ] Breaking block triggers area effect
- [ ] Correct radius for enchantment level
- [ ] Items drop from broken blocks
- [ ] Tool loses durability (survival mode)
- [ ] Tool doesn't lose durability (creative mode)
- [ ] Logs show "Area Dig activated!" message
- [ ] Logs show correct level number
- [ ] Logs show number of blocks broken

## Checking Logs

### In-Game
Press F3 to see debug info (may show some messages)

### Log File Location
```
~/Library/Application Support/minecraft/logs/latest.log
```

### What to Look For
```
[Server thread/INFO] [Area Dig]: Area Dig activated! Level: 3
[Server thread/INFO] [Area Dig]: Breaking 729 blocks in radius 4
[Server thread/INFO] [Area Dig]: Successfully broke 150 additional blocks
```

(Actual number broken depends on what blocks are in the area)

## Troubleshooting

### Issue: Enchantment doesn't appear
- Make sure mod is installed correctly
- Check mods list in Minecraft
- Restart Minecraft

### Issue: Enchantment appears but doesn't work
- Check logs for "Area Dig activated!" message
- If message appears but blocks don't break, check for errors in log
- Make sure you're in survival or creative mode (not spectator)

### Issue: Only some blocks break
- This is normal - the mod only breaks blocks the tool can harvest
- Shovel: dirt, sand, gravel, grass
- Pickaxe: stone, ores, cobblestone
- Axe: wood, planks, logs

### Issue: Too many blocks break
- This is expected! Higher levels break LOTS of blocks
- Level V breaks 2,197 blocks in one hit
- Use lower levels for more control

## Performance Notes

- Level I-II: Should be smooth
- Level III: May cause brief lag
- Level IV-V: May cause noticeable lag on slower computers
- Breaking 2,000+ blocks at once is intensive!

## Success Criteria

✅ Enchantment appears in enchanting table  
✅ Can be applied to tools  
✅ Breaking one block breaks surrounding blocks  
✅ Radius matches enchantment level  
✅ Items drop correctly  
✅ Tool durability works  
✅ Logs show activation messages  

If all criteria pass, the mod is working correctly!

## Reporting Issues

If you find bugs:
1. Check the log file for errors
2. Note what you were doing when it failed
3. Note the enchantment level
4. Note the tool type
5. Note the block type you were breaking
