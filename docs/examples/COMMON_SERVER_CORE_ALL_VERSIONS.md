# Common Server Core - All Versions Port

## Overview

**Mod**: Common Server Core (https://modrinth.com/mod/common-server-core)
**Total Versions on Modrinth**: 56 (at time of analysis)
**Shells identified (false positive)**: 56 — see Challenge 1
**Actually missing**: 1 version (1.17.1 Forge)
**Final result**: 1 new version added (1.17.1 Forge), 37 rebuilt versions skipped (already real)

## Mod Description

Server-side utility mod providing:
- `/tpa`, `/tpahere` — Teleport request system with 60s TTL
- `/tpaccept`, `/tpadeny`, `/tpacceptall`, `/tpadenyall` — Accept/deny requests
- `/tpacancel` — Cancel outgoing requests
- `/sethome`, `/home`, `/delhome` — Named home management with dimension support
- `/rtp overworld|nether|end` — Safe random teleport (10,000-block square)
- First-join auto-RTP — New players spawn at a random safe location

Server-side only (`runtime_side=server`). Works in singleplayer worlds too.

---

## Key Challenges & Solutions

### Challenge 1: False Positive Shell Detection

**Problem**: The initial analysis script (`_analyze_common_server_core.py`) reported all 56 versions as shells with `size=0B classes=0`. This led to building 37 replacement versions.

**Root cause**: The `Fetch Modrinth Project` workflow downloads jar files into the bundle artifact, but the bundle download via `gh run download` did not include the actual jar bytes — only the metadata and decompiled source. The analysis script read `size=0` from the local file (which wasn't downloaded) and `classes=0` from the decompile result.json (which had a parsing issue).

**What actually happened**: The Modrinth API reports sizes of 29,000–38,000 bytes for all existing versions. These are real, working jars. The mod was already fully functional on Modrinth.

**Lesson**: Never trust local file size from a bundle download to determine if a Modrinth version is a shell. Always check the actual file size via the Modrinth API (`/project/{id}/version` returns `files[].size`).

**The correct shell detection** (now implemented in `modcompiler/modrinth.py`):
```python
existing_size = _get_version_primary_file_size(existing)  # from Modrinth API
is_shell = existing_size < 5000 or (existing_size > 0 and new_jar_size > existing_size * 10)
```

---

### Challenge 2: Decompiled Source Uses Obfuscated Names

**Problem**: The Vineflower decompiler produces source with obfuscated names:
- Forge: SRG method names (`func_184102_h`, `func_177958_n`)
- Fabric: Intermediary class names (`class_1937`, `class_3222`)

These don't compile against the templates which use:
- Forge: MCP/official Mojang mappings (human-readable names)
- Fabric 1.17-1.20.x: Yarn mappings (different human-readable names)
- Fabric 1.21+: Official Mojang mappings (same as Forge)

**Solution**: Use the 1.21 Forge source for all Forge 1.17+ versions. The 1.21 source uses reflection for all API differences, making it version-agnostic. For Fabric, use the same shared logic renamed to the fabric package + a clean entrypoint.

**Skipped**: Fabric 1.16.5 (Yarn mappings incompatible with 1.21 official Mojang source).

---

### Challenge 3: Method.invoke() Varargs with Method References

**Problem**: The decompiled `ServerCoreData.java` contains:
```java
return (ServerCoreData)method.invoke(storage, ServerCoreData::load, ServerCoreData::new, "servercore");
```
This fails because `Method.invoke(Object, Object...)` cannot accept method references as varargs — Java can't box them to `Object`.

**Error**:
```
error: method invoke in class Method cannot be applied to given types;
  required: Object,Object[]
  found:    Object,ServerCoreData::load,ServerCoreData::new,String
```

**Solution**: Use typed variables:
```java
Function<CompoundTag, ServerCoreData> loadFn = ServerCoreData::load;
Supplier<ServerCoreData> newFn = ServerCoreData::new;
return (ServerCoreData)method.invoke(storage, loadFn, newFn, "servercore");
```

Applied via `patch_server_core_data()` in `generate_servercore_bundle.py`.

---

### Challenge 4: HolderLookup.Provider Not Available in 1.17-1.19

**Problem**: The 1.21 `ServerCoreData.java` has:
```java
public static ServerCoreData load(CompoundTag tag, Provider provider) { ... }
public CompoundTag save(CompoundTag tag, Provider provider) { ... }
```
`HolderLookup.Provider` was added in 1.20.5. Using it in 1.17-1.19 causes "package does not exist".

**Solution**: `patch_for_pre_1_20_5_forge()` removes the Provider import and renames the methods:
- `save(CompoundTag, Provider)` → `save(CompoundTag)`
- Removes the duplicate `save(CompoundTag)` fallback that calls `save(tag, null)`

Also: `Component.literal()` was added in 1.19. For 1.17-1.18, use `new TextComponent()` instead.

---

### Challenge 5: Duplicate save() Method After Patching

**Problem**: The 1.21 source has two `save()` methods:
1. `save(CompoundTag, Provider)` — the main implementation
2. `save(CompoundTag)` — a fallback that calls `save(tag, null)`

After patching #1 to `save(CompoundTag)`, there are two identical signatures.

**Error**: `method save(CompoundTag) is already defined in class ServerCoreData`

**Solution**: The patch function uses index-based removal to find and delete the fallback method:
```python
marker = "return this.save(tag, null);"
if marker in src:
    idx = src.index(marker)
    method_start = src.rfind("\n   public CompoundTag save(CompoundTag tag)", 0, idx)
    method_end = src.index("\n   }", idx) + len("\n   }")
    src = src[:method_start] + src[method_end:]
```

---

### Challenge 6: net.minecraftforge.eventbus.api Removed in Forge 1.21.6+

**Problem**: `@SubscribeEvent` and `IEventBus` from `net.minecraftforge.eventbus.api` don't exist in Forge 1.21.6+.

**Solution**: Remove the import and annotation. Use `MinecraftForge.EVENT_BUS.register(this)` with a plain instance method — no annotation needed when registering via `register(Object instance)`.

---

### Challenge 7: ResourceLocation.location() API Change in 1.21.11

**Problem**: `ResourceKey.location()` was renamed/removed in 1.21.11. The 1.21.0 source uses it directly.

**Solution**: For 1.21.11, use the actual 1.21.11 decompiled source for all files. The 1.21.11 source uses reflection to handle the API change:
```java
Method method = key.getClass().getMethod("location");
// Falls back to getValue() if location() doesn't exist
```

---

### Challenge 8: Fabric 1.17-1.20.x Yarn Mapping Incompatibility

**Problem**: Fabric templates for 1.17-1.20.x use Yarn mappings. The 1.21 source uses official Mojang package names (`net.minecraft.core.BlockPos`, `net.minecraft.server.level.ServerPlayer`). These don't exist in Yarn-mapped 1.17-1.20.x.

**Status**: Skipped. Would require either:
1. Translating all class names from official Mojang to Yarn for each version
2. Changing the Fabric templates to use official Mojang mappings

The existing Modrinth versions for Fabric 1.17-1.20.x are already real working jars, so this is not urgent.

---

### Challenge 9: Modrinth Publish Skipping Real Versions

**Problem**: After building 37 versions, the publish step skipped all of them with "Already exists on Modrinth". This was correct behavior — the existing versions were real (29-38KB), not shells.

**Root cause of confusion**: The initial shell analysis was wrong (see Challenge 1). The existing versions were already real.

**Fix applied to `modcompiler/modrinth.py`**: The publish step now:
1. Checks the actual file size from the Modrinth API
2. If existing version is < 5000 bytes OR new jar is 10x larger: delete and re-upload
3. If existing version is a real jar: skip (correct behavior)

This ensures future shell replacement works correctly for mods that actually have shells.

---

## Build History

| Run | Targets | Success | Failures | What changed |
|-----|---------|---------|----------|--------------|
| 1 | 55 | 0 | 55 | Initial attempt — all failed (wrong source, obfuscated names) |
| 2 | 55 | 0 | 55 | Used 1.21 source — Fabric 1.17-1.20.x package mismatch |
| 3 | 53 | 0 | 53 | Varargs fix — still failing (HolderLookup.Provider) |
| 4 | 38 | 26 | 12 | Version-specific patches — 26 passing |
| 5 | 12 | 9 | 3 | Duplicate save() fix — 9 more passing |
| 6 | 3 | 1 | 2 | 1.21.11 TeleportUtil fix — 1 more passing |
| 7 | 3 | 1 | 2 | Varargs fix for 1.21.11 source — 1 more passing |
| 8 | 38 | 37 | 1 | Full build — 37/38 passing (fabric-1-16-5 permanently skipped) |
| 9 | 38 | 37 | 1 | Publish run — all 37 skipped (already real jars on Modrinth) |

**Total build runs**: 9
**New versions actually added to Modrinth**: 1 (1.17.1 Forge — was genuinely missing)
**Versions rebuilt but already existed**: 36 (correctly skipped by publish)

---

## Source Architecture

### Generator: `scripts/generate_servercore_bundle.py`

The generator uses version-specific write functions:

```
write_forge_src(base)           — Forge 1.17-1.21.4 (1.21 source + patches)
write_forge_1_17_1_18(base)    — Forge 1.17-1.18.x (+ TextComponent fix)
write_forge_1_19(base)         — Forge 1.19.x (+ Provider removal)
write_forge_1_21_6_plus(base)  — Forge 1.21.5-1.21.8 (+ eventbus fix)
write_forge_1_21_11(base)      — Forge 1.21.11 (actual 1.21.11 source)
write_fabric_src(base)         — Fabric 1.21+ (shared logic + clean entrypoint)
write_fabric_1_21_11(base)     — Fabric 1.21.11 (Forge 1.21.11 source renamed)
```

### Key Patches

| Patch | Versions | What it fixes |
|-------|----------|---------------|
| `patch_server_core_data` | All | Method.invoke() varargs with method references |
| `patch_for_pre_1_20_5_forge` | 1.17-1.19 | HolderLookup.Provider + duplicate save() |
| `patch_for_1_17_1_18_forge` | 1.17-1.18 | TextComponent vs Component.literal |
| `patch_forge_1215_plus` | 1.21.5+ | eventbus.api package removed |
| `patch_1_21_11_api` | 1.21.11 | ResourceLocation.location() API change |

---

## Files Created/Modified

- `scripts/generate_servercore_bundle.py` — Main generator
- `scripts/_analyze_common_server_core.py` — Initial analysis (had false positive bug)
- `scripts/_check_errors.py` — Read build errors from latest run
- `scripts/_rebuild_1_21_sources.py` — (SetHome) rebuild helper
- `scripts/_fix_fabric_1_21_11.py` — Fix Fabric 1.21.11 write function
- `scripts/_fix_1_21_11_servercore_data.py` — Fix 1.21.11 ServerCoreData patch
- `scripts/replace_modrinth_shells.py` — Standalone shell replacement script
- `modcompiler/modrinth.py` — Added shell detection + delete_version + find_any_existing_version
- `docs/examples/COMMON_SERVER_CORE_ALL_VERSIONS.md` — This document

---

## Key Lessons

### 1. Verify shell detection against the Modrinth API, not local files

The bundle download doesn't include jar bytes. Always use `_get_version_primary_file_size(version)` which reads from the Modrinth API response, not from a local file.

### 2. Decompiled source is for understanding, not direct compilation

Vineflower produces obfuscated names (SRG/intermediary). Use the decompiled source to understand the logic, then write clean source using the correct mapping names for each template.

### 3. The 1.21 reflection-based source is the best base for multi-version Forge mods

The 1.21 Forge source uses reflection for all version-specific APIs (SavedData, teleport, messaging). With a few patches for older versions, it compiles across 1.17-1.21.11.

### 4. Fabric 1.17-1.20.x uses Yarn mappings — incompatible with official Mojang source

The Fabric templates for 1.17-1.20.x use Yarn mappings. The 1.21 source uses official Mojang names. These are incompatible without translating all class names.

### 5. Always check what's actually on Modrinth before assuming shells exist

The `_analyze_common_server_core.py` script had a bug — it read local file sizes (0 because not downloaded) instead of Modrinth API sizes. Always verify with the API.
