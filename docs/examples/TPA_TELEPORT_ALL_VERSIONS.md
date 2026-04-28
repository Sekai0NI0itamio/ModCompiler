# TPA Teleport — All-Versions Port

## Overview

**Mod**: TPA Teleport (https://modrinth.com/mod/tpa-teleport)  
**Starting coverage**: 21 versions (1.12–1.12.2 forge, 1.20–1.20.6 forge, 1.21–1.21.11 fabric)  
**Final coverage**: 68 total versions — all supported MC versions 1.8.9–26.1.2 across Forge, Fabric, NeoForge  
**Build runs**: 7 runs to reach full coverage  
**Generator**: `scripts/generate_tpa_bundle.py`

---

## Mod Description

Server-side only TPA (teleport request) mod. Players can send teleport requests
to each other with `/tpa` and `/tpahere`, accept/deny with `/tpaccept`/`/tpadeny`,
and cancel with `/tpacancel`. Requests expire after 60 seconds.

- Server-side only (no client code, no keybinds, no GUI)
- Single class: `TpaTeleportMod`
- Pure command-based using Brigadier (1.16.5+) or CommandBase (1.8.9/1.12.2)
- In-memory request tracking with `ConcurrentHashMap`

---

## Already Published at Start

Before building, `fetch_modrinth_project.py` revealed these were already published:
- `1.12-1.12.2` forge
- `1.20-1.20.6` forge
- `1.21-1.21.1` fabric
- `1.21.2-1.21.8` fabric
- `1.21.9-1.21.11` fabric

These were excluded from all build runs.

---

## Issues Encountered and Fixed

### Issue 1: Version string `26.1-26.x` rejected by prepare script

**Run**: 2  
**Error**: `Unsupported version format '26.x'`  
**Root cause**: The prepare script validates `minecraft_version` against
`supported_versions` in `version-manifest.json`. The folder name `26.1-26.x`
is not a valid version string.  
**Fix**: Use the anchor version `26.1.2` in `version.txt`.  
**DIF**: `VERSION-STRING-26X-ANCHOR`

---

### Issue 2: Java 6 compatibility in 1.8.9 Forge

**Run**: 3  
**Errors**:
```
error: underscores in literals are not supported in -source 1.6
error: diamond operator is not supported in -source 1.6
error: lambda expressions are not supported in -source 1.6
```
**Root cause**: The 1.8.9 template compiles with Java source level 1.6.  
**Fix**:
- `60_000L` → `60000L`
- `new ConcurrentHashMap<>()` → `new ConcurrentHashMap<String, Long>()`
- `removeIf(lambda)` → explicit iterator loop  
**DIF**: `JAVA6-COMPAT-189-FORGE`

---

### Issue 3: `CommandSource.asPlayer()` does not exist in Forge 1.16.5

**Run**: 3  
**Error**: `cannot find symbol: method asPlayer()`  
**Root cause**: `asPlayer()` was added in 1.17 as `getPlayerOrException()`. In
1.16.5, `CommandSource` (not `CommandSourceStack`) requires a manual cast.  
**Fix**: `(ServerPlayerEntity) src.getEntity()` with null check.  
**DIF**: `FORGE-COMMAND-SOURCE-1165`

---

### Issue 4: `sendMessage(Component, UUID)` removed in Forge 1.19+

**Run**: 3  
**Error**: `incompatible types: UUID cannot be converted to ResourceKey<ChatType>`  
**Root cause**: `ServerPlayer.sendMessage(Component, UUID)` was removed in 1.19.
Use `sendSystemMessage(Component)` instead.  
**Fix**: Replace all `player.sendMessage(comp, uuid)` with `player.sendSystemMessage(comp)`.  
**Pitfall**: When deriving 1.19 source from 1.17-1.18 via string replacement,
the replacement `.sendMessage(Component.literal(` → `.sendSystemMessage(Component.literal(`
only replaces the opening. The trailing `, uuid)` must also be stripped.  
**DIF**: `FORGE-SEND-MESSAGE-UUID-REMOVED`

---

### Issue 5: Forge 1.21.6+ EventBus 7 — `@SubscribeEvent` removed

**Run**: 3  
**Error**: `package net.minecraftforge.eventbus.api does not exist`  
**Root cause**: Forge 1.21.6 migrated to EventBus 7. `@SubscribeEvent` is gone.  
**Fix**: Use `RegisterCommandsEvent.BUS.addListener(MyMod::onRegisterCommands)` in
the constructor. Constructor must accept `FMLJavaModLoadingContext context`.  
**DIF**: `FORGE-EB7-EVENTBUS7-PATTERN`

---

### Issue 6: NeoForge 1.20 — template only supports 1.20.2, 1.20.4, 1.20.5, 1.20.6

**Run**: 3  
**Error**: `1.20-1.20.6/neoforge/template does not support exact Minecraft 1.20`  
**Root cause**: NeoForge didn't exist for 1.20 or 1.20.1.  
**Fix**: Split NeoForge 1.20 targets into `1.20.2`, `1.20.4`, `1.20.5-1.20.6`.  
**DIF**: `NEOFORGE-120-SUPPORTED-VERSIONS`

---

### Issue 7: Forge/Fabric 1.17 — template only supports 1.17.1

**Run**: 3  
**Error**: `1.17-1.17.1/forge/template does not support exact Minecraft 1.17`  
**Root cause**: Only 1.17.1 is in `supported_versions` for the 1.17 range.  
**Fix**: Use `1.17.1` as the exact version, not `1.17-1.17.1`.  
**DIF**: `FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY`

---

### Issue 8: Fabric `sendMessage(text, uuid)` — UUID is not a boolean

**Run**: 4  
**Error**: `incompatible types: UUID cannot be converted to boolean`  
**Root cause**: Fabric's `ServerPlayerEntity.sendMessage()` takes `(Text, boolean)`
where boolean is `overlay` (action bar vs chat). There is no UUID overload.  
**Fix**: `player.sendMessage(text, false)` — false = chat, not action bar.  
**DIF**: `FABRIC-SEND-MESSAGE-SIGNATURE`

---

### Issue 9: Fabric 1.17.1 — `command.v2` does not exist

**Run**: 4  
**Error**: `package net.fabricmc.fabric.api.command.v2 does not exist`  
**Root cause**: `command.v2` was introduced in 1.19. Versions 1.16.5–1.18.x use `command.v1`.  
**Fix**: Use `command.v1` with 2-arg callback `(dispatcher, dedicated)` for 1.16.5–1.18.x.
Use `command.v2` with 3-arg callback `(dispatcher, registryAccess, dedicated)` for 1.19+.  
**DIF**: `FABRIC-COMMAND-API-V1-VS-V2`

---

### Issue 10: Fabric 1.19 — `command.v1` does not exist

**Run**: 5  
**Error**: `package net.fabricmc.fabric.api.command.v1 does not exist`  
**Root cause**: `command.v1` was removed in 1.19. Must use `command.v2`.  
**Fix**: `SRC_119_FABRIC` must derive from a v1 base and then upgrade to v2.  
**DIF**: `FABRIC-COMMAND-API-V1-VS-V2`

---

### Issue 11: Lambda captures non-final `count` variable

**Run**: 5  
**Error**: `local variables referenced from a lambda expression must be final or effectively final`  
**Root cause**: In 1.20+, `sendSuccess()` takes a `Supplier<Component>` lambda.
A `count` variable incremented in a loop is not effectively final.  
**Fix**: Add `final int finalCount = count;` before the lambda.  
**Pitfall**: When building source via `.replace()` chains, the lambda is introduced
by the `sendSuccess` supplier replacement. The `finalCount` fix must be applied
AFTER the lambda replacement in the derivation chain.  
**DIF**: `LAMBDA-COUNT-CAPTURE`

---

### Issue 12: Fabric 1.16.5 — `getServer()` not found on `ServerCommandSource`

**Run**: 6  
**Error**: `cannot find symbol: method getServer()`  
**Root cause**: The `1.16.5/fabric/template` uses `anchor_only` mode with the
1.17 branch as its base. The `dependency_overrides` set `yarn_mappings=1.16.5+build.10`.
In 1.16.5 yarn, the method is `getMinecraftServer()`, not `getServer()`.
The decompiled sources in `DecompiledMinecraftSourceCode/1.16.5-fabric/` show
`getServer()` because they were generated with a different yarn version.  
**Fix**: Use `src.getMinecraftServer()` for 1.16.5 only. Use `src.getServer()` for 1.17+.  
**DIF**: `FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER`

---

### Issue 13: `getMinecraftServer()` propagated to 1.17+ via derivation

**Run**: 7  
**Error**: `cannot find symbol: method getMinecraftServer()`  
**Root cause**: `SRC_117_FABRIC = SRC_1165_FABRIC` inherited the `getMinecraftServer()`
fix from 1.16.5. In 1.17+, the correct method is `getServer()`.  
**Fix**: When deriving `SRC_117_FABRIC` from `SRC_1165_FABRIC`, revert
`getMinecraftServer()` back to `getServer()`.  
**DIF**: `FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER`

---

## Version-Specific Source Reference

### Forge command registration by version

| MC Version | Source class | Register commands |
|------------|-------------|-------------------|
| 1.8.9 | `CommandBase` | `FMLServerStartingEvent.registerServerCommand()` |
| 1.12.2 | `CommandBase` | `FMLServerStartingEvent.registerServerCommand()` |
| 1.16.5 | `CommandSource` | `@SubscribeEvent RegisterCommandsEvent` |
| 1.17–1.21.5 | `CommandSourceStack` | `@SubscribeEvent RegisterCommandsEvent` |
| 1.21.6–26.x | `CommandSourceStack` | `RegisterCommandsEvent.BUS.addListener()` (EventBus 7) |

### Fabric command registration by version

| MC Version | Package | Callback signature |
|------------|---------|-------------------|
| 1.16.5–1.18.x | `command.v1` | `(dispatcher, dedicated)` |
| 1.19–26.x | `command.v2` | `(dispatcher, registryAccess, dedicated)` |

### Player message sending by version

| MC Version | Loader | Send to player |
|------------|--------|---------------|
| 1.8.9 | Forge | `player.addChatMessage(new ChatComponentText("..."))` |
| 1.12.2 | Forge | `player.sendMessage(new TextComponentString("..."))` |
| 1.16.5 | Forge | `player.sendMessage(new StringTextComponent("..."), uuid)` |
| 1.17–1.18.x | Forge | `player.sendMessage(new TextComponent("..."), uuid)` |
| 1.19+ | Forge/NeoForge | `player.sendSystemMessage(Component.literal("..."))` |
| 1.16.5–1.18.x | Fabric | `player.sendMessage(new LiteralText("..."), false)` |
| 1.19–1.20.x | Fabric | `player.sendMessage(Text.literal("..."), false)` |
| 1.21+ | Fabric | `player.sendSystemMessage(Text.literal("..."))` |

### CommandSource.sendSuccess() by version

| MC Version | Loader | Signature |
|------------|--------|-----------|
| 1.16.5 | Forge | `src.sendSuccess(new StringTextComponent("..."), false)` |
| 1.17–1.19.4 | Forge | `src.sendSuccess(new TextComponent("...") / Component.literal("..."), false)` |
| 1.20+ | Forge/NeoForge | `src.sendSuccess(() -> Component.literal("..."), false)` |
| 1.16.5–1.18.x | Fabric | `src.sendFeedback(new LiteralText("..."), false)` |
| 1.19–1.20.x | Fabric | `src.sendFeedback(Text.literal("..."), false)` |
| 1.20+ | Fabric | `src.sendFeedback(() -> Text.literal("..."), false)` |

---

## Build Run Summary

| Run | Targets | Green | Red | Key fixes |
|-----|---------|-------|-----|-----------|
| 1 | 20 | 0 | 20 | Prepare failed: `26.x` version string |
| 2 | 20 | 0 | 20 | Fixed `26.x` → `26.1.2`; compile errors begin |
| 3 | 20 | 4 | 16 | Java6 compat, asPlayer, sendSystemMessage UUID, EB7 |
| 4 | 23 | 13 | 10 | Fabric sendMessage bool, v1/v2 split, finalCount |
| 5 | 23 | 23 | 0 | getMinecraftServer 1.16.5, v2 for 1.19, finalCount derivation |
| 6 | 16 | 15 | 1 | getMinecraftServer propagation to 1.17+ |
| 7 | 16 | 16 | 0 | All green — full success |

**Total**: 7 build runs, 68 versions published.

---

## Files Created/Modified

- `scripts/generate_tpa_bundle.py` — new generator for all versions
- `dif/FABRIC-COMMAND-API-V1-VS-V2.md` — new DIF entry
- `dif/FABRIC-SEND-MESSAGE-SIGNATURE.md` — new DIF entry
- `dif/FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER.md` — new DIF entry
- `dif/FORGE-SEND-MESSAGE-UUID-REMOVED.md` — new DIF entry
- `dif/FORGE-COMMAND-SOURCE-1165.md` — new DIF entry
- `dif/JAVA6-COMPAT-189-FORGE.md` — new DIF entry
- `dif/LAMBDA-COUNT-CAPTURE.md` — new DIF entry
- `dif/VERSION-STRING-26X-ANCHOR.md` — new DIF entry
- `dif/NEOFORGE-120-SUPPORTED-VERSIONS.md` — new DIF entry
- `dif/FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY.md` — new DIF entry
