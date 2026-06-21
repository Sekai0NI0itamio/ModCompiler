---
id: FORGE-MINECRAFT-GETINSTANCE-1201-1204
title: Minecraft.getInstance() does not exist in Forge 1.20.1-1.20.4
tags: [forge, api-change, runtime-error, NoSuchMethodError]
versions: [1.20.1, 1.20.2, 1.20.3, 1.20.4]
loaders: [forge]
symbols: [Minecraft, Minecraft.getInstance, MinecraftClient]
error_patterns: ["NoSuchMethodError.*Minecraft.getInstance", "net.minecraft.client.Minecraft.getInstance"]
---

## Issue
Mod calls `Minecraft.getInstance()` which is a static method added in Minecraft 1.20.5 (Forge). For Forge 1.20.1-1.20.4, this method does not exist, causing a `NoSuchMethodError` at runtime.

## Error
```
java.lang.NoSuchMethodError: 'net.minecraft.client.Minecraft net.minecraft.client.Minecraft.getInstance()'
    at com.itamio.pingfix.forge.PingFixForgeClient.onClientTick(PingFixForgeClient.java:24)
```

## Root Cause
The mod author compiled one jar against Forge 1.20.5+ and deployed it to all Forge 1.20.x versions. In Forge 1.20.1-1.20.4, `Minecraft.getInstance()` is not a static method -- the Minecraft instance is accessed differently.

## Fix
**The source code is correct.** When rebuilt through the ModCompiler build system (which compiles per-version), the build adapter will remap `Minecraft.getInstance()` to the correct method name for each Forge version.

If the mod was compiled and deployed as a single jar across multiple versions, the fix is to rebuild through the build system with per-version compilation.

## Verified
PingFix mod -- Forge 1.20.6 works (method exists), Forge 1.20.1-1.20.4 fails (method missing). Rebuilding through the build system resolves the issue.