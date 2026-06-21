---
id: FORGE-PACK-MCMETA-FORMAT-121
title: pack.mcmeta uses min_format/max_format instead of pack_format for Forge 1.21.1-1.21.8
tags: [forge, meta, LoadingErrorScreen, pack.mcmeta, format]
versions: [1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8]
loaders: [forge]
symbols: [pack.mcmeta, pack_format, min_format, max_format]
error_patterns: ["LoadingErrorScreen", "No key pack_format", "min_format", "max_format"]
---

## Issue
Forge 1.21.1-1.21.8 crashes with `LoadingErrorScreen` because the mod's `pack.mcmeta` uses the newer `min_format`/`max_format` format instead of the single `pack_format` integer. The `min_format`/`max_format` format was introduced in Minecraft 1.21.2+ and is not supported by Forge 1.21.1-1.21.8.

## Error
```
[Render thread/ERROR] Couldn't load pack metadata
com.google.gson.JsonParseException: No key pack_format in MapLike[{"description":"pingfix resources","max_format":94,"min_format":[94,1]}]
```

## Root Cause
The mod author compiled the jar against a 1.21.2+ template (which uses `min_format`/`max_format`) and deployed it to Forge 1.21.1-1.21.8 which only supports `pack_format` as a single integer.

## Fix
**The source code is correct.** When rebuilt through the ModCompiler build system, the template for 1.21.1-1.21.8 will generate the correct `pack_format` format. The build system handles per-version pack.mcmeta generation.

If the mod was compiled and deployed as a single jar across multiple versions, the fix is to rebuild through the build system with per-version compilation.

## Verified
PingFix mod -- Forge 1.21.9-1.21.11 works (supports min_format/max_format), Forge 1.21.1-1.21.8 fails. Rebuilding through the build system resolves the issue.