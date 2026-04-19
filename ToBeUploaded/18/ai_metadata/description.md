# Quick Stack

Tired of manually moving items from your inventory to chests? This mod brings Terraria's beloved Quick Stack feature to Minecraft! Press a single key to instantly stack all matching items to nearby containers.

## Features

- ✅ **One-Key Stacking** - Press V to quick stack items to nearby containers
- ✅ **Smart Matching** - Only stacks items that already exist in containers (prevents clutter)
- ✅ **Hotbar Protection** - Keeps your hotbar items safe by default (configurable)
- ✅ **Configurable Range** - Search up to 16 blocks for containers (default 8)
- ✅ **Audio Feedback** - Plays a satisfying sound when items are stacked
- ✅ **Visual Feedback** - Shows on-screen message with what was stacked
- ✅ **Multiple Container Types** - Works with chests, trapped chests, barrels, and shulker boxes
- ✅ **Fully Configurable** - Customize all behavior via config file
- ✅ **Multiplayer Ready** - Client-server synchronization for seamless multiplayer

## How It Works

1. Stand near your storage containers (within 8 blocks by default)
2. Press the V key (configurable in Minecraft controls)
3. All items in your inventory that match items already in nearby containers are automatically stacked
4. Your hotbar items stay safe (unless you enable includeHotbar in config)
5. Get instant feedback with sound and on-screen message

## Example Use Cases

**After Mining Trip:**
- You have cobblestone, iron ore, and coal in your inventory
- Your storage room has chests with these items already
- Press V and everything stacks to the right chests instantly

**Farming:**
- Return from farming with wheat, carrots, and potatoes
- Your farm storage has these items
- One keypress organizes everything

**Building Projects:**
- Carrying extra blocks and materials
- Storage has matching items
- Quick stack to free up inventory space

## Configuration

The mod creates a config file at `.minecraft/config/quickstack.cfg` with these options:

```
enableQuickStack (default: true)
  Enable or disable the Quick Stack feature

searchRange (default: 8, range: 1-16)
  Range in blocks to search for containers

includeHotbar (default: false)
  Include hotbar items in quick stack
  Set to true to stack hotbar items too

playSound (default: true)
  Play sound feedback when items are stacked

showMessage (default: true)
  Show on-screen message when items are stacked

stackToChests (default: true)
  Stack items to chests and trapped chests

stackToBarrels (default: true)
  Stack items to barrels

stackToShulkerBoxes (default: true)
  Stack items to shulker boxes
```

## Key Binding

The default key is V, but you can change it in:
- Minecraft Options → Controls → Key Binds → Inventory → "Quick Stack"

## How Matching Works

The mod only stacks items to containers that already have that item type. This prevents:
- Creating clutter in empty chests
- Mixing items you want to keep separate
- Accidentally filling the wrong chest

For example:
- If a chest has 10 cobblestone, your cobblestone will stack there
- If no chest has diamonds, your diamonds stay in your inventory
- This matches Terraria's Quick Stack behavior exactly

## Visual Feedback

When you quick stack, you'll see a message showing:
- Individual items if 3 or fewer types were moved (e.g., "64x Cobblestone, 32x Iron Ore")
- Total count if more than 3 types (e.g., "156 items (5 types)")

## Performance

- Lightweight and efficient
- No performance impact on gameplay
- Works seamlessly in multiplayer
- Instant stacking with no delay

## Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. (Optional) Edit config file to customize behavior

## Compatibility

- **Client-side**: Required (handles key input and UI)
- **Server-side**: Required (performs inventory operations)
- **Multiplayer**: Fully supported with client-server sync
- **Mod Compatibility**: Works with all container mods

## Comparison to Terraria

This mod faithfully recreates Terraria's Quick Stack feature:
- ✅ Only stacks to containers with matching items
- ✅ One-key activation
- ✅ Configurable range
- ✅ Visual and audio feedback
- ✅ Hotbar protection

## Known Limitations

- Only works with standard IInventory containers
- Requires both client and server to have the mod installed in multiplayer
- Does not work with player-specific containers (like ender chests in some mods)

## Tips

- Organize your storage by putting one of each item type in designated chests
- Use the hotbar for tools and frequently used items (they won't be moved)
- Increase search range if you have a large storage room
- Disable sound if you find it annoying

## Credits

**Author**: Itamio  
**Package**: asd.itamio.quickstack  
**Version**: 1.0.0  
**License**: MIT  
**Inspired by**: Terraria's Quick Stack feature
