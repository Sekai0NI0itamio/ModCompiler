# STOP — Server Tracking & Observation Protection

A privacy-focused Fabric mod that protects your personal files from servers that attempt to scan your mods, datapacks, resource packs, and other game directories.

## Why STOP?

Some Minecraft servers use plugins or mods to query your client's installed mods, resource packs, datapacks, and configuration files — often without your knowledge or explicit consent. This information can be collected, stored, and used to profile players.

**STOP** stands for **Server Tracking & Observation Protection**. It intercepts file system queries from the game and redirects them to **decoy folders** that you control, so servers only see what you want them to see.

## How It Works

1. **Decoy Folders** — On first launch, STOP creates a `.stop-decoy/` directory inside your Minecraft folder with clean subdirectories: `mods/`, `datapacks/`, `resourcepacks/`, `config/`, and `saves/`.
2. **Interception** — Using Fabric Loader mixins, STOP redirects all file system queries (game directory, config directory, mods directory, mod lists, etc.) to the decoy folders instead of your real ones.
3. **You Control What Servers See** — Place only the mods and files you want servers to know about inside the `.stop-decoy/` folders. Everything else stays private.
4. **Toggle Protection** — Protection is enabled by default. You can disable it anytime via the config file.

## Features

- **Game directory protection** — Servers see the decoy game directory, not your real one.
- **Config directory protection** — Your real configuration files stay private.
- **Mods directory protection** — Only decoy mods are visible to server queries.
- **Mod list filtering** — `isModLoaded()` and `getAllMods()` only report decoy mods (STOP itself is always visible so the mod can function).
- **Zero dependencies** — No Fabric API required. Works with just Fabric Loader.
- **Configurable** — Enable/disable protection, customize intercepted paths, and manage decoy contents.

## Configuration

Config is stored at `.stop-decoy/config.json`:

```json
{
  "enabled": true,
  "interceptedPaths": ["mods", "datapacks", "resourcepacks", "config", "saves"]
}
```

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.18.2+
- No Fabric API required
- Client-side only

## Credits

**Author**: Itamio
