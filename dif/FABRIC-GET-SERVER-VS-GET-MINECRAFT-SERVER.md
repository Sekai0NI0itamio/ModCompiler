---
id: FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER
title: Fabric ServerCommandSource — getServer() vs getMinecraftServer() by version
tags: [fabric, compile-error, api-change, ServerCommandSource, getServer, getMinecraftServer, 1.16.5, anchor-only]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1]
loaders: [fabric]
symbols: [ServerCommandSource, getServer, getMinecraftServer]
error_patterns: ["cannot find symbol.*method getServer\\(\\)", "cannot find symbol.*method getMinecraftServer\\(\\)"]
---

## Issue

`ServerCommandSource` has different method names for getting the `MinecraftServer`
depending on the MC version and yarn mapping used. The 1.16.5 fabric template
uses yarn 1.17.1 mappings (anchor_only), which means the decompiled sources show
`getServer()` — but the actual 1.16.5 yarn build.10 uses `getMinecraftServer()`.

## Error

```
error: cannot find symbol
    ServerPlayerEntity to = src.getServer().getPlayerManager().getPlayer(target);
                                   ^
  symbol:   method getServer()
  location: variable src of type ServerCommandSource
```

or

```
error: cannot find symbol
    ServerPlayerEntity to = src.getMinecraftServer().getPlayerManager().getPlayer(target);
                                       ^
  symbol:   method getMinecraftServer()
```

## Root Cause

The `1.16.5/fabric/template` uses `anchor_only` mode with anchor `1.16.5` but
the template itself was cloned from the 1.17 branch. The `gradle.properties` in
the template has `yarn_mappings=1.17.1+build.63`. When building for 1.16.5, the
adapter overrides `minecraft_version=1.16.5` and the dependency override sets
`yarn_mappings=1.16.5+build.10`. In 1.16.5 yarn, the method is `getMinecraftServer()`.
In 1.17.1+ yarn, the method is `getServer()`.

The decompiled sources in `DecompiledMinecraftSourceCode/1.16.5-fabric/` show
`getServer()` because they were generated with a different yarn version than what
the actual build uses.

## Fix

| MC Version | Method to use |
|------------|--------------|
| 1.16.5 | `src.getMinecraftServer()` |
| 1.17.1+ | `src.getServer()` |

```java
// 1.16.5 fabric only
MinecraftServer server = src.getMinecraftServer();

// 1.17.1 and all later versions
MinecraftServer server = src.getServer();
```

## Important Note

Do NOT trust `DecompiledMinecraftSourceCode/1.16.5-fabric/` for method names when
the template uses `anchor_only` mode. The decompiled sources reflect the anchor's
yarn, not the yarn that the actual 1.16.5 build downloads. Always check the
`dependency_overrides` in `version-manifest.json` to find the actual yarn version
used, then verify against that yarn's mappings.

## Verified

Confirmed in TPA Teleport all-versions port (run 7, April 2026).
