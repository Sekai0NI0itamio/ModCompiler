# Auto Totem

**Simple, fast, reliable auto-totem with 0-tick replacement.**

Auto Totem is a lightweight mod that automatically keeps Totem of Undying in your offhand at all times. When a totem is consumed, it's instantly replaced within 1 tick (0.05 seconds). Perfect for hardcore survival, PvP, and dangerous adventures.

## ✨ Key Features

### Simple & Effective
- ✅ **0-Tick Replacement** - Instant totem replacement (1 tick = 0.05 seconds)
- ✅ **Always Protected** - Keeps totem in offhand at all times when enabled
- ✅ **Toggle Keybind** - Press 'O' to enable/disable anytime
- ✅ **Smart Swapping** - Swaps old offhand item back to inventory
- ✅ **Minimal Config** - Just 2 options (enable/disable, show messages)

### Quality of Life
- 💬 **Optional Messages** - See when totem is equipped (configurable)
- 🚫 **No Complexity** - No health thresholds, no delays, just works
- 📦 **Lightweight** - Minimal performance impact
- 🖥️ **Server-Side** - Clients don't need the mod

## 🎮 How to Use

### Basic Usage
1. Install the mod
2. Have Totem of Undying in your inventory
3. Press 'O' to enable (enabled by default)
4. Totem automatically stays in your offhand!

### Toggle On/Off
- Press **O** (configurable keybind)
- Chat message confirms: `[Auto Totem] Enabled` or `[Auto Totem] Disabled`
- Useful when you want to use other offhand items

## ⚙️ Configuration

Config file: `config/autototem.cfg`

### Settings
```
Enable Auto Totem (default: true)
- Master on/off switch

Show Messages (default: true)
- Show chat messages when totem is equipped
```

That's it! Simple and effective.

## 💡 How It Works

The mod checks every tick (20 times per second) if you have a totem in your offhand:
- **No totem?** → Instantly finds one in inventory and equips it
- **Totem consumed?** → Replaced within 1 tick (0.05 seconds)
- **Old offhand item?** → Swapped back to inventory automatically

This gives you instant, reliable totem protection without any complexity.

## 🆚 Why This Mod?

### vs. Manual Totem Management
- **Auto Totem:** Instant, automatic, never forget
- **Manual:** Slow, requires attention, easy to forget
- **Winner:** Auto Totem

### vs. Complex Auto Totem Mods
- **This Mod:** Simple, 0-tick replacement, always works
- **Others:** Health thresholds, delays, complex configs
- **Winner:** This mod (simplicity + speed)

### vs. Vanilla
- **Auto Totem:** Automatic protection, instant replacement
- **Vanilla:** Manual management only
- **Winner:** Auto Totem

## 🎯 Use Cases

### Hardcore Survival
Never lose your hardcore world to a preventable death. Totem is always ready.

### PvP Combat
Instant totem replacement gives you a competitive edge. No fumbling in inventory.

### Boss Fights
Focus on the fight, not your inventory. Totem management is automatic.

### Exploration
Explore dangerous areas worry-free. Totem protection is always active.

### Speedrunning
Reduce deaths and save time with automatic totem management.

## 🔧 Technical Details

### Performance
- Checks every tick (20 times per second)
- Only searches inventory when needed
- Minimal CPU usage
- No continuous background processing

### Compatibility
- ✅ Singleplayer and multiplayer
- ✅ Server-side mod (clients don't need it)
- ✅ Works with vanilla Totem of Undying
- ✅ Compatible with most mods
- ✅ No conflicts with vanilla mechanics

### How Replacement Works
1. Every tick, check if offhand has totem
2. If not, search inventory for totem
3. If found, swap totem to offhand instantly
4. Put old offhand item back in inventory
5. Show message (if enabled)

This happens in less than 0.05 seconds!

## 📋 FAQ

**Q: Does this work on servers?**  
A: Yes! Only the server needs the mod installed.

**Q: How fast is the replacement?**  
A: 1 tick = 0.05 seconds. Instant!

**Q: Will this make me invincible?**  
A: No! You still need totems in your inventory. If you run out, you can still die.

**Q: Can I use other offhand items?**  
A: Yes! Press 'O' to disable the mod temporarily.

**Q: Does this work in hardcore?**  
A: Absolutely! Perfect for hardcore survival.

**Q: Does this work with modded totems?**  
A: It works with vanilla Totem of Undying. Modded totems may vary.

**Q: Will this get me banned on servers?**  
A: Check server rules first. If the server allows the mod, you're fine.

**Q: Can I change the keybind?**  
A: Yes! Change it in Controls menu under "Auto Totem".

## 🎮 Keybinds

**Toggle Auto Totem:** O (default)
- Can be changed in Controls menu
- Category: "Auto Totem"

## 💬 Chat Messages

```
[Auto Totem] Totem equipped
```

```
[Auto Totem] Enabled
```

```
[Auto Totem] Disabled
```

## 📦 Installation

1. Download the mod JAR
2. Place in `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge
4. Press 'O' to toggle on/off
5. Configure in `config/autototem.cfg` if desired

## 🎨 Philosophy

This mod follows the KISS principle: **Keep It Simple, Stupid.**

No health thresholds. No delays. No complex configs. Just simple, fast, reliable totem protection that works every time.

## 📝 Credits

**Author:** Itamio  
**Version:** 1.0.0  
**Minecraft:** 1.12.2  
**Mod Loader:** Forge

---

**Never die unexpectedly again!** 💀➡️💚
