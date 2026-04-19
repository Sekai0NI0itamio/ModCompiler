# Area Dig - Changelog

## Version 1.0.0-FIXED (April 3, 2026)

### Bug Fixes

**Issue**: Area Dig enchantment not working - blocks weren't being broken when mining with enchanted tools.

**Root Cause**: 
1. The `canHarvestBlock` check was too restrictive
2. Block breaking logic wasn't properly handling different block types
3. No debug logging to diagnose issues

**Changes Made**:

1. **Improved Block Breaking Logic**
   - Changed from `tool.canHarvestBlock(state)` to `tool.getItem().canHarvestBlock(state, tool)`
   - Added proper hardness checks
   - Allow instantly breakable blocks (hardness = 0)
   - Skip unbreakable blocks (hardness < 0)

2. **Better Item Dropping**
   - Use `block.harvestBlock()` for proper item drops with player context
   - Fallback to `dropBlockAsItem()` for non-server players
   - Properly handle tile entities

3. **Creative Mode Support**
   - Don't damage tools in creative mode
   - Still break blocks normally

4. **Added Debug Logging**
   - Log when Area Dig activates
   - Log enchantment level
   - Log number of blocks to break
   - Log successful breaks
   - Log errors with details

5. **Error Handling**
   - Try-catch around block breaking
   - Continue on errors instead of crashing
   - Log errors for debugging

### Testing

Test with:
- Diamond shovel + Area Dig III on dirt/grass
- Diamond pickaxe + Area Dig III on stone
- Diamond axe + Area Dig III on wood

Expected behavior:
- Level 1: 5x5x5 cube (125 blocks)
- Level 2: 7x7x7 cube (343 blocks)
- Level 3: 9x9x9 cube (729 blocks)
- Level 4: 11x11x11 cube (1,331 blocks)
- Level 5: 13x13x13 cube (2,197 blocks)

### Files Changed

- `BlockBreakHandler.java` - Complete rewrite of block breaking logic

### Installation

Replace the old `Area-Dig-1.0.0.jar` with `Area-Dig-1.0.0-FIXED.jar` in your mods folder.

---

## Version 1.0.0 (April 3, 2026)

Initial release with Area Dig enchantment.

**Known Issues**: Enchantment not working (fixed in 1.0.0-FIXED)
