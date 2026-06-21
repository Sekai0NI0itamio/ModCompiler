---
id: FABRIC-SOURCE-SET-PLACEMENT-CLIENT-MIXIN
title: Client mixin code must go in src/client/java/ for split source set templates
tags: [fabric, compile-error, mixin, source-set, splitEnvironmentSourceSets]
versions: [1.20.1, 1.21.1, 1.21.8, 1.21.11, 26.1.2]
loaders: [fabric]
symbols: [splitEnvironmentSourceSets, MinecraftClient, src/client/java, src/main/java]
error_patterns: ["cannot find symbol.*MinecraftClient", "package net.minecraft.client does not exist"]
---

## Issue
When a mixin targets `MinecraftClient` (a client-only class) and the template uses `splitEnvironmentSourceSets()`, the mixin code MUST be placed in `src/client/java/` -- NOT in `src/main/java/`. Placing it in `src/main/java/` causes a compilation error because `MinecraftClient` is not visible to the common source set.

## Error
```
error: cannot find symbol
import net.minecraft.client.MinecraftClient;
  symbol:   class MinecraftClient
  location: package net.minecraft.client
```

## Root Cause
The Fabric templates for MC 1.20+ use `splitEnvironmentSourceSets()` in the build.gradle. This creates separate source sets:
- `src/main/java/` -- visible to both client and server (common code)
- `src/client/java/` -- visible only to client (MinecraftClient, GUI classes, etc.)

Since `MinecraftClient` is a client-only class, any code referencing it must be in `src/client/java/`.

## Fix
Move the mixin class from `src/main/java/` to `src/client/java/`. Move the mixin JSON from `src/main/resources/` to `src/client/resources/`.

For templates WITHOUT `splitEnvironmentSourceSets()` (1.16.5-1.19.4), everything goes in `src/main/java/` and `src/main/resources/`.

**DO NOT modify `adapters.py` or the build.gradle template to strip `splitEnvironmentSourceSets()`**. This is a hack that breaks other mods.

## Verified
PingFix mod -- mixin targeting MinecraftClient placed in `src/main/java/` failed to compile with 1.20+ templates. Moving to `src/client/java/` resolved the issue.