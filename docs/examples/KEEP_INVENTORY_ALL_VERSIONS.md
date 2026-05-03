# Keep Inventory — All Versions Port

**Mod:** https://modrinth.com/mod/keep-inventory  
**Type:** Server-side only — enforces `keepInventory` gamerule = `true` on all worlds  
**Result:** 69 versions published across Forge, Fabric, NeoForge from 1.8.9 to 26.1.2  
**Build runs:** 4  

---

## Starting State

Only 1 version was published: `1.12.2 forge`.  
68 targets were missing across all loaders and version ranges.

---

## What the Mod Does

Sets `keepInventory` gamerule to `true` on every world when it loads, and
periodically re-enforces it every 20 ticks so it cannot be changed by admins.

Two mechanisms:
1. **World/Level load event** — sets the gamerule immediately when any world loads
2. **Tick event** — checks every second and corrects it if changed

---

## Run 1 — Initial attempt

**Bundle:** 68 targets (all missing)

**Failures:** 53 — all Forge and NeoForge targets, plus Fabric 26.1

**Root causes found:**

### Issue 1: Wrong TickEvent package for Forge 1.16.5+

Used `net.minecraftforge.fml.common.gameevent.TickEvent` (correct for 1.8.9–1.12.2)
on all Forge versions. Forge 1.16.5+ moved `TickEvent` to `net.minecraftforge.event.TickEvent`.

**Fix:** Use `net.minecraftforge.fml.common.gameevent.TickEvent` only for 1.8.9–1.12.2.
Use `net.minecraftforge.event.TickEvent` for 1.16.5+.

See DIF: `FORGE-TICKEVENT-PACKAGE-HISTORY`

### Issue 2: Wrong NeoForge TickEvent package

Used `net.neoforged.fml.common.gameevent.TickEvent` for NeoForge — this package
doesn't exist. NeoForge uses `net.neoforged.neoforge.event.tick.LevelTickEvent`.

**Fix:** Use `net.neoforged.neoforge.event.tick.LevelTickEvent` for NeoForge.

### Issue 3: Fabric 26.1 — wrong GameRules package

Used `net.minecraft.world.level.GameRules` for Fabric 26.1. In 26.1, `GameRules`
moved to `net.minecraft.world.level.gamerules.GameRules` with a new `GameRule<Boolean>` API.

**Fix:** Use `net.minecraft.world.level.gamerules.GameRules` for 26.1+.

See DIF: `FABRIC-26-GAMERULES-NEW-API`

---

## Run 2 — After fixing TickEvent packages

**Bundle:** 50 failed targets from run 1

**Failures:** 31

**Root causes found:**

### Issue 4: WorldEvent vs LevelEvent boundary is 1.19, not 1.18

Used `LevelEvent.Load` and `TickEvent.LevelTickEvent` for Forge 1.18.x.
But the rename from `WorldEvent` → `LevelEvent` happened in **1.19**, not 1.18.
Forge 1.18.x still uses `net.minecraftforge.event.world.WorldEvent`.

**Fix:**
- Forge 1.12.2–1.18.2: `WorldEvent.Load` + `TickEvent.WorldTickEvent`
- Forge 1.19+: `LevelEvent.Load` + `TickEvent.LevelTickEvent`

See DIF: `FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY`

### Issue 5: WorldEvent.getWorld() returns IWorld, not World

In Forge 1.12.2 and 1.16.5, `WorldEvent.getWorld()` returns `IWorld` (interface).
Direct assignment to `World` fails without a cast.

**Fix:** Add `instanceof` check and cast:
```java
if (!(event.getWorld() instanceof World)) return;
World world = (World) event.getWorld();
```

See DIF: `FORGE-WORLDEVENT-GETWORLD-RETURNS-IWORLD`

### Issue 6: Level.getGameRules() removed in Forge 1.21.3+

`getGameRules()` was removed from `Level` in 1.21.3. Must cast to `ServerLevel`.

**Fix:**
```java
LevelAccessor la = event.getLevel();
if (la instanceof ServerLevel) {
    ServerLevel sl = (ServerLevel) la;
    sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
}
```

See DIF: `FORGE-LEVEL-GETGAMERULES-REMOVED-1213`

### Issue 7: NeoForge ServerTickEvent doesn't exist in early 20.2.x/20.4.x

`net.neoforged.neoforge.event.tick.ServerTickEvent` doesn't exist in NeoForge 20.2.93
(the version the 1.20.2 template resolves to). The decompiled sources were generated
with a newer NeoForge build and are misleading.

**Fix:** For NeoForge 1.20.2–1.20.6, use `ServerStartingEvent` only (no periodic tick).

See DIF: `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X`

---

## Run 3 — After fixing WorldEvent/LevelEvent and ServerLevel cast

**Bundle:** 31 failed targets from run 2

**Failures:** 5

**Root causes found:**

### Issue 8: GameRules package moved in 1.21.9+

`net.minecraft.world.level.GameRules` doesn't exist in Forge 1.21.9+, NeoForge 1.21.9+,
or Fabric 26.1. It moved to `net.minecraft.world.level.gamerules.GameRules` with a
new `GameRule<Boolean>` API.

**Fix:**
```java
// 1.21.9+ (Forge/NeoForge/Fabric)
import net.minecraft.world.level.gamerules.GameRules;

level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
boolean val = level.getGameRules().get(GameRules.KEEP_INVENTORY);
```

See DIF: `FORGE-GAMERULES-PACKAGE-MOVED-1219`

### Issue 9: NeoForge 1.21–1.21.8 needs SRC_121_NEO (not SRC_120_NEO)

NeoForge 1.21+ has `ServerTickEvent.Post` available (unlike 1.20.x). The generator
was incorrectly using the 1.20.x source (no periodic tick) for 1.21–1.21.8.

**Fix:** Split NeoForge into two source variants:
- `SRC_120_NEO` — 1.20.2–1.20.6: `ServerStartingEvent` only
- `SRC_121_NEO` — 1.21–1.21.8: `ServerStartingEvent` + `ServerTickEvent.Post`

---

## Run 4 — Final run

**Bundle:** 5 failed targets from run 3

**Result:** ✅ All 5 passed. Workflow conclusion: SUCCESS.

---

## Final State

69 versions published (68 new + original 1.12.2):

| Range | Forge | Fabric | NeoForge |
|-------|-------|--------|----------|
| 1.8.9 | ✅ | — | — |
| 1.12–1.12.2 | ✅ ✅ | — | — |
| 1.16.5 | ✅ | ✅ | — |
| 1.17–1.17.1 | ✅ | ✅ | — |
| 1.18–1.18.2 | ✅ ✅ ✅ | ✅ | — |
| 1.19–1.19.4 | ✅ ✅ ✅ ✅ ✅ | ✅ ✅ ✅ ✅ ✅ | — |
| 1.20.1–1.20.6 | ✅ ✅ ✅ ✅ ✅ | ✅ ✅ ✅ ✅ ✅ ✅ | ✅ ✅ ✅ ✅ |
| 1.21–1.21.1 | ✅ ✅ | ✅ | ✅ ✅ |
| 1.21.2–1.21.8 | ✅ ✅ ✅ ✅ ✅ ✅ | ✅ ✅ | ✅ ✅ ✅ ✅ ✅ ✅ ✅ |
| 1.21.9–1.21.11 | ✅ ✅ ✅ | ✅ | ✅ ✅ ✅ |
| 26.1–26.1.2 | ✅ | ✅ ✅ ✅ | ✅ ✅ ✅ |

---

## Complete API Reference Table

### Forge — World/Level load event

| Version | Import | Event class | Get world/level |
|---------|--------|-------------|-----------------|
| 1.8.9 | `event.world.WorldEvent` | `WorldEvent.Load` | `event.world` (public field) |
| 1.12.2–1.16.5 | `event.world.WorldEvent` | `WorldEvent.Load` | `(World) event.getWorld()` (cast from IWorld) |
| 1.17.1–1.18.2 | `event.world.WorldEvent` | `WorldEvent.Load` | `(Level) event.getWorld()` (cast) |
| 1.19–1.21.x | `event.level.LevelEvent` | `LevelEvent.Load` | `(Level) event.getLevel()` (cast) |

### Forge — World/Level tick event

| Version | Import | Event class | Get world/level | Phase check |
|---------|--------|-------------|-----------------|-------------|
| 1.8.9–1.12.2 | `fml.common.gameevent.TickEvent` | `TickEvent.WorldTickEvent` | `event.world` | `event.phase != TickEvent.Phase.END` |
| 1.16.5–1.18.2 | `event.TickEvent` | `TickEvent.WorldTickEvent` | `event.world` | same |
| 1.19–1.21.5 | `event.TickEvent` | `TickEvent.LevelTickEvent` | `event.level` | same |
| 1.21.6–1.21.8 | `event.TickEvent` | `TickEvent.LevelTickEvent.Post` | `event.level` (field) | none (Post fires at end) |
| 1.21.9–26.1.2 | `event.TickEvent` | `TickEvent.LevelTickEvent.Post` | `event.level()` (record accessor) | none |

### Forge — GameRules API

| Version | Import | Set keepInventory | Check keepInventory |
|---------|--------|-------------------|---------------------|
| 1.8.9–1.12.2 | `net.minecraft.world.GameRules` | `rules.setOrCreateGameRule("keepInventory", "true")` | `rules.getBoolean("keepInventory")` |
| 1.16.5–1.21.2 | `net.minecraft.world.level.GameRules` | `level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, null)` | `level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)` |
| 1.21.3–1.21.8 | `net.minecraft.world.level.GameRules` | `serverLevel.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server)` | `serverLevel.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)` |
| 1.21.9–26.1.2 | `net.minecraft.world.level.gamerules.GameRules` | `serverLevel.getGameRules().set(GameRules.KEEP_INVENTORY, true, server)` | `serverLevel.getGameRules().get(GameRules.KEEP_INVENTORY)` |

### Fabric — GameRules API

| Version | Import | Set keepInventory | Check keepInventory |
|---------|--------|-------------------|---------------------|
| 1.16.5–1.20.6 (yarn) | `net.minecraft.world.GameRules` | `world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server)` | `world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)` |
| 1.21–1.21.8 (Mojang) | `net.minecraft.world.level.GameRules` | `level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server)` | `level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)` |
| 1.21.9–26.1.2 (Mojang) | `net.minecraft.world.level.gamerules.GameRules` | `level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server)` | `level.getGameRules().get(GameRules.KEEP_INVENTORY)` |

### NeoForge — Tick event availability

| Version | ServerTickEvent.Post available? | Source variant |
|---------|--------------------------------|----------------|
| 1.20.2–1.20.6 | ❌ No (early 20.x build) | `ServerStartingEvent` only |
| 1.21–1.21.8 | ✅ Yes | `ServerStartingEvent` + `ServerTickEvent.Post` |
| 1.21.9–1.21.11 | ✅ Yes + ModContainer required | `ServerStartingEvent` + `ServerTickEvent.Post` |
| 26.1–26.1.2 | ✅ Yes + new GameRules API | `ServerStartingEvent` + `ServerTickEvent.Post` |

---

## DIF Entries Added

| DIF ID | Issue |
|--------|-------|
| `FORGE-TICKEVENT-PACKAGE-HISTORY` | TickEvent package moved from fml.common.gameevent to event.TickEvent in 1.16.5 |
| `FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY` | WorldEvent renamed to LevelEvent in 1.19 (not 1.18) |
| `FORGE-LEVEL-GETGAMERULES-REMOVED-1213` | Level.getGameRules() removed in 1.21.3, must cast to ServerLevel |
| `FORGE-GAMERULES-PACKAGE-MOVED-1219` | GameRules moved to gamerules subpackage in 1.21.9+ |
| `FORGE-WORLDEVENT-GETWORLD-RETURNS-IWORLD` | WorldEvent.getWorld() returns IWorld in 1.12.2–1.16.5, cast required |
| `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` | ServerTickEvent doesn't exist in NeoForge 20.2.93/20.4.x |
| `FABRIC-26-GAMERULES-NEW-API` | Fabric 26.1 GameRules moved to gamerules subpackage |

---

## Generator Script

`scripts/generate_keepinventory_bundle.py`

Source variants defined:
- `SRC_189_FORGE` — 1.8.9 (Java 6, fml.common.gameevent, event.world field)
- `SRC_1122_FORGE` — 1.12.2 (fml.common.gameevent, IWorld cast)
- `SRC_1165_FORGE` — 1.16.5 (event.TickEvent, WorldEvent, IWorld cast)
- `SRC_1171_FORGE` — 1.17.1 (event.TickEvent, WorldEvent, Level cast)
- `SRC_118_FORGE` — 1.18–1.18.2 (event.TickEvent, WorldEvent, WorldTickEvent)
- `SRC_119_FORGE` — 1.19–1.21.2 (LevelEvent, LevelTickEvent, Level.getGameRules())
- `SRC_121_FORGE` — alias for SRC_119_FORGE
- `SRC_1213_FORGE` — 1.21.3–1.21.5 (LevelEvent, LevelTickEvent, ServerLevel cast)
- `SRC_1216_FORGE` — 1.21.6–1.21.8 (EventBus 7, field access, ServerLevel cast)
- `SRC_1219_FORGE` — 1.21.9–26.1.2 (EventBus 7, record accessor, gamerules pkg)
- `SRC_1165_FABRIC` — 1.16.5–1.20.6 (yarn, ServerWorld, KEEP_INVENTORY)
- `SRC_121_FABRIC` — 1.21–1.21.8 (Mojang, ServerLevel, RULE_KEEPINVENTORY)
- `SRC_1219_FABRIC` — 1.21.9–1.21.11 (same as 121)
- `SRC_261_FABRIC` — 26.1–26.1.2 (gamerules pkg, GameRule<Boolean>)
- `SRC_120_NEO` — 1.20.2–1.20.6 (ServerStartingEvent only, no ServerTickEvent)
- `SRC_121_NEO` — 1.21–1.21.8 (ServerStartingEvent + ServerTickEvent.Post)
- `SRC_1219_NEO` — 1.21.9–1.21.11 (ModContainer, gamerules pkg)
- `SRC_261_NEO` — 26.1–26.1.2 (FMLJavaModLoadingContext removed, gamerules pkg)
