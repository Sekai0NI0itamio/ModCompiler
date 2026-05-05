# Heart System

A lightweight, server-side permadeath mod for Minecraft 1.12.2 Forge. Every player has a heart count. Die and you lose one. Kill another player and you gain one. Reach zero hearts and you are permanently banned from the server.

No gimmicks. No altars. No commands. Just hearts.

---

## How It Works

| Event | Effect |
|---|---|
| You die (any cause) | −1 heart |
| You kill another player | +1 heart |
| Hearts reach 0 (configurable minimum) | Permanent ban |

Hearts are the **only** way to gain max health. Mob kills, items, and regeneration do not grant hearts. The only path to more hearts is PvP.

---

## Features

- **Death penalty** — Every death costs exactly 1 heart, regardless of cause.
- **Kill reward** — Killing another player grants exactly 1 heart, capped at the configured maximum.
- **Permadeath** — When a player's hearts reach the minimum (default 0), they are permanently banned with the reason "Permadeath: ran out of hearts".
- **Live max health** — The player's max health bar reflects their current heart count in real time.
- **Persistence** — Heart counts are saved per player UUID and survive server restarts.
- **Configurable** — Starting hearts, max hearts, and minimum hearts are all adjustable in the config file.
- **Server broadcast** — A message is sent to all online players when someone is permabanned.

---

## Configuration

The config file is generated at `config/heartsystem.cfg` on first launch.

```
# Number of hearts a new player starts with. (1 heart = 2 HP)
startHearts = 10

# Maximum hearts a player can have. Kills cannot push hearts above this.
maxHearts = 20

# Minimum hearts before permadeath triggers. 0 means ban on reaching 0 hearts.
minHearts = 0
```

All values are in **hearts** (1 heart = 2 HP).

---

## Installation

1. Install Minecraft Forge for 1.12.2.
2. Download `Heart-System-1.0.0.jar`.
3. Place it in your server's `mods/` folder.
4. Start the server — the config file is created automatically.
5. Edit `config/heartsystem.cfg` to adjust starting/max/min hearts if desired.

---

## Compatibility

- **Server-side**: Required (all logic runs on the server).
- **Client-side**: Optional (clients do not need the mod installed).
- Works alongside other Forge mods. Does not modify any vanilla game files.

---

## Credits

**Author**: Itamio  
**Package**: `asd.itamio.heartsystem`  
**Version**: 1.0.0  
**License**: MIT
