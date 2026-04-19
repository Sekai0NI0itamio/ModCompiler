# Keep Equipment

**Never lose your precious equipment to lava, void, or creepers again!**

Keep Equipment is a highly configurable death protection mod that lets you keep your armor, tools, inventory, XP, and potion effects when you die. Perfect for players who want to reduce death frustration while maintaining full control over difficulty balance.

## ✨ Key Features

### What You Can Keep
- ✅ **Armor** - Helmet, Chestplate, Leggings, Boots (individually configurable)
- ✅ **Hotbar** - All items or just tools/weapons (your choice)
- ✅ **Offhand** - Shield, totem, or whatever you're holding
- ✅ **Main Inventory** - All 3 inventory rows
- ✅ **Experience** - Keep all, keep percentage, or lose all
- ✅ **Potion Effects** - Keep all active buffs and debuffs

### Quality of Life
- 🎮 **Toggle Keybind** - Press 'K' to enable/disable anytime
- 💬 **Death Messages** - See exactly what you kept
- ⚙️ **15+ Config Options** - Customize every aspect
- 🚫 **No New Blocks** - Pure gameplay mod
- 📦 **Lightweight** - Minimal performance impact
- 🖥️ **Server-Side** - Clients don't need the mod

## 🎯 Default Behavior

Out of the box, Keep Equipment keeps **everything**:
- All armor pieces
- All hotbar items
- Offhand item
- Main inventory (rows 1-3)
- All XP
- All potion effects

This provides maximum convenience and eliminates death frustration. Want more challenge? Everything is configurable!

## ⚙️ Configuration

Config file: `config/keepequipment.cfg`

### Quick Config Examples

**Maximum Convenience (Default)**
```
Keep Everything: true
```
Death is just a respawn!

**Balanced Survival**
```
Keep Armor: true
Keep Hotbar: true (tools only)
Keep Main Inventory: false
Keep XP: false
Keep Effects: false
```
Keep equipment, lose resources and XP.

**Casual Play**
```
Keep Armor: true
Keep Hotbar: true (all items)
Keep Main Inventory: false
Keep XP: 50%
Keep Effects: true
```
Keep equipment and effects, half XP.

**Hardcore Lite**
```
Keep Armor: true
Keep Hotbar: true (tools only)
Keep Offhand: false
Keep Main Inventory: false
Keep XP: 25%
Keep Effects: false
```
Minimal protection, still challenging.

### All Config Options

**Armor Settings**
- Keep Helmet (default: true)
- Keep Chestplate (default: true)
- Keep Leggings (default: true)
- Keep Boots (default: true)

**Hotbar Settings**
- Keep Hotbar (default: true)
- Keep Only Tools (default: false) - If true, only keeps tools/weapons/shields

**Inventory Settings**
- Keep Offhand (default: true)
- Keep Main Inventory (default: true) - All 3 inventory rows

**Experience Settings**
- Keep All XP (default: true)
- XP Kept Percentage (default: 0%, range: 0-100%) - Used if not keeping all

**Effects Settings**
- Keep Potion Effects (default: true) - Keeps all active effects

**Message Settings**
- Show Death Message (default: true)
- Show Kept Items (default: true) - Lists what was kept

## 🎮 How to Use

### Basic Usage
1. Install the mod
2. Die in Minecraft
3. Respawn with your equipment intact!

### Toggle On/Off
- Press **K** (configurable keybind)
- Chat message confirms current state
- Useful when you want vanilla death behavior

### Keybind
- Default: **K**
- Change in Controls menu under "Keep Equipment"

## 💡 Why This Mod?

### Problems It Solves
- ❌ Losing hours of work to one mistake
- ❌ Diamond gear lost to lava forever
- ❌ Items disappearing in the void
- ❌ Long runbacks to death point
- ❌ Losing enchanted equipment
- ❌ XP grinding after every death

### Benefits
- ✅ Reduces death frustration
- ✅ Saves time and effort
- ✅ Fully configurable difficulty
- ✅ Works on servers (server-side only)
- ✅ Compatible with most mods
- ✅ Toggle on/off anytime

## 🔧 Technical Details

### Compatibility
- ✅ Singleplayer and multiplayer
- ✅ Server-side mod (clients don't need it)
- ✅ Works with modded armor and tools
- ✅ Compatible with most mods
- ✅ No conflicts with vanilla mechanics

### Performance
- Minimal CPU usage (only runs on death)
- No continuous background processing
- Lightweight memory footprint

### How It Works
- Intercepts item drops on death
- Stores items to keep
- Restores them on respawn
- Server-side processing prevents cheating

## 📋 FAQ

**Q: Does this work on servers?**  
A: Yes! Only the server needs the mod installed.

**Q: Can I keep some items but not others?**  
A: Yes! Every slot type is individually configurable.

**Q: Does this work with modded armor?**  
A: Yes! Works with any item in armor slots.

**Q: What if I want vanilla death sometimes?**  
A: Press 'K' to toggle the mod on/off anytime.

**Q: Is this overpowered?**  
A: By default yes, but it's fully configurable. Adjust settings for your preferred balance.

**Q: Does this work in creative mode?**  
A: Creative already keeps inventory, so this mod isn't needed there.

## 🆚 Comparison

### vs. /gamerule keepInventory
- **Keep Equipment:** Selective, configurable, toggle-able
- **keepInventory:** All or nothing, no flexibility

### vs. Gravestone Mods
- **Keep Equipment:** Instant, no retrieval needed
- **Gravestone:** Must return to death point

### vs. Vanilla
- **Keep Equipment:** Keep what you want, lose what you don't
- **Vanilla:** Lose everything (frustrating!)

## 📦 Installation

1. Download the mod JAR
2. Place in `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge
4. Configure in `config/keepequipment.cfg` if desired
5. Press 'K' in-game to toggle

## 🎨 Death Message Examples

```
[Keep Equipment] Kept 12 item(s): Armor, Hotbar, Offhand, Main Inventory, All XP, Potion Effects (3)
```

```
[Keep Equipment] Kept 7 item(s): Helmet, Chestplate, Leggings, Boots, Hotbar Tools, Offhand, 50% XP
```

## 📝 Credits

**Author:** Itamio  
**Version:** 1.0.0  
**Minecraft:** 1.12.2  
**Mod Loader:** Forge

---

**Enjoy never losing your diamond armor to lava again!** 💎🔥
