---
id: ANCHOR-ONLY-YARN-PITFALL
title: anchor_only mode — Loom downloads yarn for exact MC version, not the anchor
tags: [fabric, compile-error, manifest, yarn, anchor-only, dependency-overrides]
versions: [1.19, 1.19.1, 1.19.2]
loaders: [fabric]
symbols: [Registries, Registry, yarn_mappings, fabric_version]
error_patterns: ["package net.minecraft.registry does not exist", "package net.minecraft.util.registry does not exist", "Could not find net.fabricmc.fabric-api:fabric-api"]
---

## Issue

When a fabric range uses `exact_dependency_mode: anchor_only`, the template's
`yarn_mappings` is set to the anchor version. But when building for a non-anchor
version, the adapter sets `minecraft_version` to the exact version in
`gradle.properties`. Loom then downloads the yarn for THAT exact MC version —
which may be a completely different yarn generation than the anchor.

## Example

Template: `1.19-1.19.4/fabric/template/gradle.properties`
```
minecraft_version=1.19.4
yarn_mappings=1.19.4+build.2
```

When building for `1.19`, the adapter sets `minecraft_version=1.19`. Loom resolves
yarn for MC 1.19, which is `1.19+build.X` — using the OLD `net.minecraft.util.registry`
path, not the 1.19.4 path (`net.minecraft.registry.Registries`).

## Root Cause

The `anchor_only` mode only means "use the anchor's template as-is for all versions
in the range." It does NOT pin the yarn version. The fabric adapter always overwrites
`minecraft_version` in gradle.properties with the exact version being built, and
Loom resolves yarn based on that.

**Critical**: The decompiled sources in `DecompiledMinecraftSourceCode/<version>-fabric/`
are generated with the anchor's yarn. Do NOT use them to determine the correct
package paths for non-anchor versions — they will show the anchor's yarn paths,
not the paths that the actual build will use.

## Fix

Add explicit `dependency_overrides` in `version-manifest.json` for each non-anchor
version to pin their yarn and fabric-api versions:

```json
{
  "folder": "1.19-1.19.4",
  "loaders": {
    "fabric": {
      "exact_dependency_mode": "anchor_only",
      "anchor_version": "1.19.4",
      "dependency_overrides": {
        "1.19": {
          "yarn_mappings": "1.19+build.1",
          "fabric_version": "0.58.0+1.19",
          "minecraft_version": "1.19"
        },
        "1.19.1": {
          "yarn_mappings": "1.19.1+build.6",
          "fabric_version": "0.58.5+1.19.1",
          "minecraft_version": "1.19.1"
        },
        "1.19.2": {
          "yarn_mappings": "1.19.2+build.28",
          "fabric_version": "0.77.0+1.19.2",
          "minecraft_version": "1.19.2"
        }
      }
    }
  }
}
```

Then write source code using the yarn paths for those specific versions (not the
anchor's paths). See `FABRIC-119-REGISTRY-PATH-SPLIT` for the correct registry
paths per version.

## How to find correct yarn/fabric-api versions

1. Check https://fabricmc.net/develop for the recommended versions per MC version
2. Check https://mvnrepository.com/artifact/net.fabricmc.fabric-api/fabric-api
3. Look at existing working mods' `gradle.properties` for that MC version

## Verified

Confirmed in Optimized Vein Miner all-versions port (runs 6–7).
The 1.19 build succeeded once correct overrides were added to version-manifest.json.
