# No Hostile Mobs — All Versions Port

**Mod:** https://modrinth.com/mod/no-hostile-mobs  
**Type:** Server-side only — prevents hostile mobs from spawning regardless of difficulty  
**Result:** 68 versions published across Forge, Fabric, NeoForge from 1.8.9 to 26.1.2  
**Build runs:** 2  

---

## Starting State

Only 1 version was published: `1.12.2 forge` (listed as 1.12, 1.12.1, 1.12.2 on Modrinth).  
67 targets were missing across all loaders and version ranges.

---

## What the Mod Does

Intercepts the mob spawn check and denies spawning for any entity whose
`MobCategory` (or `SpawnGroup` in yarn) is `MONSTER`. No config, no commands —
always active on the server side.

The original 1.12.2 implementation used `LivingSpawnEvent.CheckSpawn` with
`event.setResult(Result.DENY)`. Each version era requires a different API.

---

## API Strategy Per Era

### Forge

| Version range | Forge version | Event used | Method |
|---------------|--------------|------------|--------|
| 1.8.9 | 11.x | `LivingSpawnEvent.CheckSpawn` | `event.setResult(Result.DENY)` |
| 1.12.2 | 14.x | `LivingSpawnEvent.CheckSpawn` | `event.setResult(Result.DENY)` |
| 1.16.5–1.18.2 | 36.x–40.x | `LivingSpawnEvent.CheckSpawn` | `event.setResult(Result.DENY)` |
| 1.19–1.19.3 | 41.x–44.x | `EntityJoinLevelEvent` | `event.setCanceled(true)` |
| 1.19.4–1.21.5 | 45.x–55.x | `MobSpawnEvent.FinalizeSpawn` | `event.setSpawnCancelled(true)` |
| 1.21.6–26.1.2 | 56.x+ | `MobSpawnEvent.FinalizeSpawn.BUS.addListener()` | `event.setSpawnCancelled(true)` |

### Fabric

| Version range | Mappings | Approach | Class targeted |
|---------------|----------|----------|----------------|
| 1.16.5–1.20.6 | Yarn | Mixin on `MobEntity.canSpawn` | `net.minecraft.entity.mob.MobEntity` |
| 1.21–1.21.1 | Mojang | Mixin on `Mob.checkSpawnRules` | `net.minecraft.world.entity.Mob` (MobSpawnType arg) |
| 1.21.2–26.1.2 | Mojang | Mixin on `Mob.checkSpawnRules` | `net.minecraft.world.entity.Mob` (EntitySpawnReason arg) |

### NeoForge

| Version range | Event used | Method |
|---------------|------------|--------|
| 1.20.2–1.20.6 | `EntityJoinLevelEvent` | `event.setCanceled(true)` |
| 1.21–1.21.8 | `FinalizeSpawnEvent` | `event.setSpawnCancelled(true)` |
| 1.21.9–26.1.2 | `FinalizeSpawnEvent` | `event.setSpawnCancelled(true)` (ModContainer constructor) |

---

## Run 1 — Initial attempt (67 targets)

**Result:** 52 passed ✓, 15 failed ✗

**Failures:**
- `forge-1-8-9`
- `forge-1-19`, `forge-1-19-1`, `forge-1-19-2`, `forge-1-19-3`
- `fabric-1-16-5`
- `fabric-1-21-2`, `fabric-1-21-9`
- `fabric-26-1`, `fabric-26-1-1`, `fabric-26-1-2`
- `neoforge-1-20-2`, `neoforge-1-20-4`, `neoforge-1-20-5`
- `neoforge-1-21-7` (transient network failure)

---

### Issue 1: Forge 1.8.9 — EntityLivingBase vs EntityLiving

**Error:**
```
incompatible types: EntityLivingBase cannot be converted to EntityLiving
```

**Root cause:** In 1.8.9, `LivingSpawnEvent` stores the entity as `EntityLivingBase`,
not `EntityLiving`. The field `event.entityLiving` is typed as `EntityLivingBase`.

**Fix:** Use `EntityLivingBase` in the 1.8.9 handler:
```java
import net.minecraft.entity.EntityLivingBase;
EntityLivingBase entity = event.entityLiving;
if (entity instanceof IMob) { event.setResult(Result.DENY); }
```

See DIF: `FORGE-189-ENTITYLIVINGBASE-NOT-ENTITYLIVING`

---

### Issue 2: Forge 1.19–1.19.3 — MobSpawnEvent not in API jar

**Error:**
```
cannot find symbol
import net.minecraftforge.event.entity.living.MobSpawnEvent;
```

**Root cause:** `MobSpawnEvent` exists in the decompiled sources (generated from
a newer Forge) but is NOT exported to the public API jar in Forge 41.x–44.x.
This is the `SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE` pitfall.

`MobSpawnEvent.FinalizeSpawn` only became accessible from Forge 45.x (1.19.4).

**Fix:** For 1.19–1.19.3, use `EntityJoinLevelEvent` instead:
```java
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

@SubscribeEvent
public void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
        event.setCanceled(true);
    }
}
```

See DIF: `FORGE-MOBSPAWNEVENT-NOT-IN-41X-44X`

---

### Issue 3: Fabric 1.16.5 — MobEntity in wrong package

**Error:**
```
cannot find symbol: class MobEntity
Mixin has no targets
```

**Root cause:** In Fabric 1.16.5 (yarn), `MobEntity` is in `net.minecraft.entity.mob`,
not `net.minecraft.entity`. The import `net.minecraft.entity.MobEntity` does not exist.

**Fix:**
```java
// WRONG
import net.minecraft.entity.MobEntity;

// CORRECT for 1.16.5 fabric
import net.minecraft.entity.mob.MobEntity;
```

See DIF: `FABRIC-165-MOB-ENTITY-PACKAGE`

---

### Issue 4: Fabric 1.21.2+ — MobSpawnType renamed to EntitySpawnReason

**Error:**
```
cannot find symbol
import net.minecraft.world.entity.MobSpawnType;
```

**Root cause:** In Minecraft 1.21.2, `MobSpawnType` was renamed to `EntitySpawnReason`.
The `checkSpawnRules` method signature on `Mob` changed accordingly.

**Fix:** Use `EntitySpawnReason` for 1.21.2+ Fabric and NeoForge:
```java
// WRONG — 1.21.2+
import net.minecraft.world.entity.MobSpawnType;
private void blockHostileSpawn(LevelAccessor level, MobSpawnType spawnType, ...) { ... }

// CORRECT — 1.21.2+
import net.minecraft.world.entity.EntitySpawnReason;
private void blockHostileSpawn(LevelAccessor level, EntitySpawnReason spawnReason, ...) { ... }
```

See DIF: `FABRIC-MOBSPAWNTYPE-RENAMED-ENTITYSPAWNREASON-1212`

---

### Issue 5: NeoForge 1.20.2–1.20.6 — FinalizeSpawnEvent not in API jar

**Error:**
```
cannot find symbol
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
```

**Root cause:** Same as Issue 2 — `FinalizeSpawnEvent` exists in decompiled sources
but is NOT accessible in early NeoForge 20.x API jars.

**Fix:** Use `EntityJoinLevelEvent` for NeoForge 1.20.x:
```java
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@SubscribeEvent
public void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
        event.setCanceled(true);
    }
}
```

See DIF: `NEOFORGE-FINALIZESPAWNEVENT-NOT-IN-EARLY-20X`

---

### Issue 6: NeoForge 1.21.7 — transient network failure

**Error:**
```
Could not find any matches for net.neoforged:neoforge:21.7.+ as no versions available
```

**Root cause:** Transient Maven network failure during the build run. Not a code issue.

**Fix:** Simply retry. The target passed on the second run without any code changes.

---

## Run 2 — Fixed targets only (15 targets)

**Result:** 15/15 passed ✓ — workflow conclusion: SUCCESS

All 15 previously failed targets compiled and published successfully.

---

## Final State

```
Total manifest targets: 68
Published on Modrinth:  68 (+ 2 extra game_version entries from original 1.12 release)
MISSING: 0
```

---

## Key Lessons

1. **Forge 1.8.9 uses `EntityLivingBase`** — not `EntityLiving`. Always check the
   actual field type in the decompiled sources before writing 1.8.9 handlers.

2. **`MobSpawnEvent` is inaccessible in Forge 41.x–44.x** (1.19–1.19.3). Use
   `EntityJoinLevelEvent` for those versions. `MobSpawnEvent.FinalizeSpawn` only
   becomes accessible from Forge 45.x (1.19.4+).

3. **`FinalizeSpawnEvent` is inaccessible in NeoForge 20.x** (1.20.2–1.20.6). Use
   `EntityJoinLevelEvent` for those versions. `FinalizeSpawnEvent` becomes accessible
   from NeoForge 21.x (1.21+).

4. **Fabric 1.16.5 `MobEntity` is in `entity.mob`** — not `entity`. The Mixin
   target class must use the full `net.minecraft.entity.mob.MobEntity` import.

5. **`MobSpawnType` → `EntitySpawnReason` in 1.21.2+** — affects both Fabric and
   NeoForge. The `checkSpawnRules` method signature changed. Split Fabric Mixin
   sources at the 1.21.2 boundary.

6. **Transient network failures happen** — NeoForge 1.21.7 failed with a Maven
   resolution error unrelated to code. Always retry before debugging.

7. **Fabric Mixins for spawn blocking work cleanly** — injecting at `HEAD` of
   `checkSpawnRules` / `canSpawn` with `cancellable = true` and `cir.setReturnValue(false)`
   is the correct pattern across all Fabric versions.
