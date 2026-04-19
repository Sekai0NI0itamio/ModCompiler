# Vein Miner - Performance Optimized

Mine entire ore veins and connected blocks at once with this highly optimized vein mining mod! Built from the ground up to address common complaints and implement the most requested features from the community.

## ✨ Key Features

### 🚀 Performance Optimizations
- **Zero Lag** - No block break particles (configurable)
- **Single Sound** - Only one break sound instead of hundreds
- **Smart Item Drops** - All items drop at one location with automatic stacking
- **Efficient Algorithm** - Breadth-first search with configurable block limits
- **Minimal Entities** - Combines identical items before spawning

### ⚖️ Balanced Gameplay
- **Durability Consumption** - Each block properly costs tool durability
- **Hunger System** - Configurable exhaustion multiplier (0.0-10.0)
- **Correct Tool Check** - Pickaxe for ores, axe for logs, shovel for dirt
- **Tool Break Protection** - Automatically stops before your tool breaks
- **Block Limit** - Default 64 blocks per vein (configurable 1-1000)
- **Optional Cooldown** - Add cooldown between uses for extra balance

### 🎮 Quality of Life
- **Sneak Activation** - Hold sneak while mining to activate (configurable)
- **Toggle Keybind** - Press 'V' to enable/disable vein mining anytime
- **Chat Feedback** - Shows current state when toggled
- **26-Direction Search** - Includes diagonals for better vein detection
- **Metadata Respect** - Won't mix different wood types
- **Per-Block Configuration** - Enable/disable each block type individually

## 📦 Supported Blocks

### Enabled by Default
- **All Ores**: Coal, Iron, Gold, Diamond, Emerald, Lapis, Redstone, Quartz
- **All Logs**: Oak, Birch, Spruce, Jungle, Acacia, Dark Oak (respects wood type)
- **Glowstone**: Nether glowstone blocks

### Configurable (Disabled by Default)
- Stone & Cobblestone
- Dirt & Grass
- Gravel
- Sand
- Clay
- Netherrack
- End Stone

## ⚙️ Configuration

Config file: `config/veinminer.cfg`

### 20+ Configurable Options

**General Settings**
- Enable/Disable master switch
- Require sneak activation
- Max blocks per vein (1-1000)

**Balance Settings**
- Consume durability toggle
- Consume hunger toggle
- Hunger multiplier (0.0-10.0)
- Correct tool requirement
- Cooldown in ticks (20 ticks = 1 second)

**Performance Settings**
- Drop at one location
- Disable particles
- Disable individual sounds

**Block Type Settings**
- Individual toggles for each block type
- Mix and match to your preference

## 🎯 How to Use

### Basic Usage
1. Hold sneak (default: Shift)
2. Mine any supported block with the correct tool
3. The entire connected vein mines instantly
4. All items drop at the first block location

### Toggle On/Off
- Press 'V' (configurable keybind)
- Chat message confirms current state
- Perfect for when you want to mine single blocks

### Requirements
- Correct tool type (pickaxe for ores, axe for logs, shovel for dirt/gravel)
- Survival mode (doesn't work in creative)
- Sneak enabled (if configured to require it)

## 📊 Recommended Configurations

### Survival Balanced
```
Max Blocks: 64
Hunger Multiplier: 1.0
Cooldown: 0 ticks
All balance features: ON
```

### Casual Play
```
Max Blocks: 100
Hunger Multiplier: 0.5
Cooldown: 0 ticks
All balance features: ON
```

### Modpack (Strict)
```
Max Blocks: 50
Hunger Multiplier: 1.5
Cooldown: 20 ticks (1 second)
All balance features: ON
```

## 🔧 Technical Details

### Algorithm
- Uses breadth-first search (BFS) for connected block detection
- Checks all 26 surrounding blocks (including diagonals)
- Respects block metadata (e.g., different wood types)
- Stops at configured block limit
- O(n) time complexity where n = blocks in vein

### Performance
- Single world update per vein
- Batch item spawning with pre-stacking
- No particle packets sent (optional)
- Minimal sound packets
- Efficient memory usage with HashSet

### Compatibility
- ✅ Works with Fortune enchantment
- ✅ Works with Silk Touch
- ✅ Compatible with modded ores (if configured)
- ✅ Compatible with modded tools (uses Forge tool classes)
- ✅ Server-side processing prevents cheating
- ✅ Client-side toggle for convenience

## 🛠️ Installation

1. Download the mod JAR file
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft 1.12.2 with Forge installed
4. Configure settings in `config/veinminer.cfg` if desired

## 🎨 Why This Mod?

This vein miner implementation was built specifically to address common complaints from other vein miner mods:

| Problem | Our Solution |
|---------|--------------|
| "Causes lag" | No particles, single sound, block limits |
| "Too overpowered" | Durability/hunger cost, cooldown, block limits |
| "Can't turn it off" | Toggle keybind with chat feedback |
| "Items everywhere" | Centralized drops with auto-stacking |
| "Breaks my tool" | Stops before tool breaks |
| "Uses too much hunger" | Configurable multiplier (0.0-10.0) |
| "Mines wrong blocks" | Per-block type configuration |
| "Mixes wood types" | Respects block metadata |

## 📝 Known Limitations

- Does not work in creative mode (intentional design choice)
- Maximum 1000 blocks per vein (configurable limit for performance)
- Requires Forge (not compatible with Fabric)

## 👤 Credits

**Author**: Itamio  
**Package**: `asd.itamio.veinminer`  
**Version**: 1.0.0  
**Minecraft**: 1.12.2  
**Mod Loader**: Forge

## 📄 License

MIT License - Feel free to use in modpacks!

---

**Need help?** Check the config file for all available options. Every feature is fully configurable to match your playstyle or server requirements.
